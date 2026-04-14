package com.neko.music.data.manager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import com.neko.music.util.UrlConfig

/**
 * 版本信息 JSON 数据类
 */
@Serializable
data class VersionResponse(
    val ver: String,
    val updateUrl: String
)

/**
 * 应用更新信息
 */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val updateUrl: String,
    val isUpdateAvailable: Boolean
)

/**
 * 安装权限请求回调接口
 */
interface InstallPermissionCallback {
    fun onRequestPermission()
    fun onPermissionGranted()
    fun onPermissionDenied()
}

/**
 * 应用更新管理器
 */
class AppUpdateManager(private val context: Context) {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(json)
        }
    }
    
    private val versionCheckUrl = UrlConfig.getVersionCheckUrl()
    
    /**
     * 获取当前应用版本信息
     */
    fun getCurrentVersion(): Pair<String, Int> {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                0
            )
            val versionName = packageInfo.versionName ?: "1.0.0"
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                packageInfo.versionCode
            }
            Pair(versionName, versionCode)
        } catch (e: Exception) {
            Log.e("AppUpdateManager", "获取当前版本失败", e)
            Pair("1.0.0", 1)
        }
    }
    
    /**
     * 检查更新
     */
    suspend fun checkUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d("AppUpdateManager", "开始检查更新...")
            val response: VersionResponse = client.get(versionCheckUrl).body()
            val currentVersion = getCurrentVersion()
            val currentVersionName = currentVersion.first
            val currentVersionCode = currentVersion.second
            
            val versionName = response.ver
            val updateUrl = response.updateUrl
            
            Log.d("AppUpdateManager", "当前版本: $currentVersionName ($currentVersionCode)")
            Log.d("AppUpdateManager", "服务器版本: $versionName")
            Log.d("AppUpdateManager", "更新URL: $updateUrl")
            
            // 提取 versionCode（括号中的数字）
            val versionCode = extractVersionCode(versionName)
            
            Log.d("AppUpdateManager", "提取的版本号: $versionCode")
            
            // 判断是否需要更新：两个版本数据都必须比当前版本新
            val isUpdateAvailable = versionCode > currentVersionCode
            
            Log.d("AppUpdateManager", "是否需要更新: $isUpdateAvailable")
            
            UpdateInfo(
                versionName = versionName,
                versionCode = versionCode,
                updateUrl = updateUrl,
                isUpdateAvailable = isUpdateAvailable
            )
        } catch (e: Exception) {
            Log.e("AppUpdateManager", "检查更新失败", e)
            null
        }
    }    
    /**
     * 从 versionName 中提取 versionCode
     * 支持多种格式：
     * - versionName(versionCode)
     * - versionName-VERSIONCODE
     * - versionName-BETA-VERSIONCODE
     */
    private fun extractVersionCode(versionName: String): Int {
        // 尝试多种提取方式
        val patterns = listOf(
            "\\((\\d+)\\)".toRegex(),  // (20)
            "-(\\d+)$".toRegex(),         // -21
            "-BETA-(\\d+)$".toRegex(),    // -BETA-21
            "-RC-(\\d+)$".toRegex(),      // -RC-21
            "-ALPHA-(\\d+)$".toRegex()    // -ALPHA-21
        )
        
        for (pattern in patterns) {
            val match = pattern.find(versionName)
            if (match != null) {
                return match.groupValues.get(1)?.toIntOrNull() ?: 1
            }
        }
        
        // 如果都不匹配，尝试从末尾提取所有数字
        val lastDash = versionName.lastIndexOf('-')
        if (lastDash != -1) {
            val afterDash = versionName.substring(lastDash + 1)
            val number = afterDash.toIntOrNull()
            if (number != null) {
                return number
            }
        }
        
        Log.w("AppUpdateManager", "无法从版本名中提取版本号: $versionName")
        return 1
    }
    
    /**
     * 下载 APK 文件
     */
    suspend fun downloadApk(
        url: String,
        onProgress: (Long, Long) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        return@withContext try {
            // 删除旧的更新文件
            val oldApkFile = File(context.getExternalFilesDir(null), "update.apk")
            if (oldApkFile.exists()) {
                oldApkFile.delete()
                Log.d("AppUpdateManager", "已删除旧的更新文件")
            }
            
            // 创建临时文件用于下载
            val tempFile = File(context.getExternalFilesDir(null), "update_temp.apk")
            
            val httpResponse = client.prepareGet(url) {
                accept(io.ktor.http.ContentType.Application.OctetStream)
            }.execute()
            val contentLength = httpResponse.headers["Content-Length"]?.firstOrNull()?.toLong() ?: 0L
            
            if (contentLength > 0) {
                onProgress(0L, contentLength)
            }
            
            // 使用ByteReadChannel流式下载
            val byteReadChannel = httpResponse.body<io.ktor.utils.io.ByteReadChannel>()
            val outputStream = FileOutputStream(tempFile)
            val buffer = java.nio.ByteBuffer.allocate(8192)
            var totalBytes = 0L
            
            Log.d("AppUpdateManager", "开始下载，Content-Length: $contentLength")
            
            try {
                while (!byteReadChannel.isClosedForRead) {
                    buffer.clear()
                    val bytesRead = byteReadChannel.readAvailable(buffer)
                    if (bytesRead < 0) break  // 流结束
                    if (bytesRead == 0) continue  // 暂时没有数据，继续等待
                    
                    buffer.flip()
                    val data = ByteArray(bytesRead)
                    buffer.get(data)
                    outputStream.write(data)
                    totalBytes += bytesRead
                    
                    // 每下载8KB更新一次进度
                    if (totalBytes % 8192 == 0L || (contentLength > 0 && totalBytes == contentLength)) {
                        Log.d("AppUpdateManager", "下载进度: $totalBytes / $contentLength")
                        onProgress(totalBytes, contentLength)
                    }
                }
            } finally {
                outputStream.close()
                byteReadChannel.cancel(null)
            }
            
            // 确保最后一次更新进度
            onProgress(totalBytes, contentLength)
            
            // 下载完成后重命名为正式文件名
            if (tempFile.exists() && tempFile.length() > 0) {
                val apkFile = File(context.getExternalFilesDir(null), "update.apk")
                if (tempFile.renameTo(apkFile)) {
                    Log.d("AppUpdateManager", "下载完成: ${apkFile.absolutePath}, 大小: ${apkFile.length()} 字节")
                    apkFile
                } else {
                    Log.e("AppUpdateManager", "重命名文件失败")
                    tempFile.delete()  // 清理临时文件
                    null
                }
            } else {
                Log.e("AppUpdateManager", "下载文件为空或不存在")
                tempFile.delete()  // 清理临时文件
                null
            }
        } catch (e: Exception) {
            Log.e("AppUpdateManager", "下载 APK 失败", e)
            // 清理临时文件
            val tempFile = File(context.getExternalFilesDir(null), "update_temp.apk")
            if (tempFile.exists()) {
                tempFile.delete()
                Log.d("AppUpdateManager", "已清理失败的临时文件")
            }
            null
        }
    }
    
    /**
     * 清理所有更新相关的文件（包括临时文件）
     */
    fun cleanupUpdateFiles(): Boolean {
        return try {
            val externalDir = context.getExternalFilesDir(null)
            if (externalDir != null && externalDir.exists()) {
                externalDir.listFiles()?.filter { 
                    it.name.endsWith(".apk") || it.name.startsWith("update_temp")
                }?.forEach { 
                    Log.d("AppUpdateManager", "删除文件: ${it.name}")
                    it.delete() 
                }
                Log.d("AppUpdateManager", "清理更新文件成功")
            }
            true
        } catch (e: Exception) {
            Log.e("AppUpdateManager", "清理更新文件失败", e)
            false
        }
    }
    
    /**
     * 检查是否有安装权限
     */
    fun canInstallPackages(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }
    
    /**
     * 安装 APK
     * @param apkFile APK文件
     * @param callback 安装权限回调（可选）
     * @return 是否成功开始安装流程
     */
    fun installApk(apkFile: File, callback: InstallPermissionCallback? = null): Boolean {
        // 检查安装权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                // 没有安装权限，通过回调请求权限
                callback?.onRequestPermission()
                return false
            }
        }
        
        // 有权限，直接安装
        return installApkWithPermission(apkFile)
    }
    
    /**
     * 检查是否有安装权限
     */
    fun hasInstallPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return context.packageManager.canRequestPackageInstalls()
        }
        return true
    }
    
    /**
     * 获取安装权限请求Intent
     */
    fun getInstallPermissionIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )
    }
    
    /**
     * 使用已授予的权限安装APK
     */
    private fun installApkWithPermission(apkFile: File): Boolean {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(
                    uri,
                    "application/vnd.android.package-archive"
                )
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            Log.e("AppUpdateManager", "安装 APK 失败", e)
            return false
        }
    }
}