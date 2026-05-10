package com.neko.music.data.manager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.os.Handler
import android.os.Looper
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
import java.util.concurrent.atomic.AtomicBoolean
import com.neko.music.util.UrlConfig
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.discard

/**
 * 版本信息 JSON 数据类
 */
@Serializable
data class VersionResponse(
    val ver: String,
    val updateUrl: String,
)

/**
 * 应用更新信息
 */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val updateUrl: String,
    val isUpdateAvailable: Boolean,
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
    
    private val mainHandler = Handler(Looper.getMainLooper())

    private val json = Json { ignoreUnknownKeys = true }
    
    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(json)
        }
    }

    /** 专用于 APK：无 ContentNegotiation，且对 CDN 使用 identity 编码，便于出现 Content-Length */
    private val downloadClient = HttpClient(OkHttp) {
        expectSuccess = false
        engine {
            config {
                followRedirects(true)
            }
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
                isUpdateAvailable = isUpdateAvailable,
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

    private fun Headers.contentLengthBytes(): Long =
        get(HttpHeaders.ContentLength)
            ?.substringBefore(',')
            ?.trim()
            ?.toLongOrNull()
            ?: 0L

    private fun HttpRequestBuilder.apkStreamHeaders() {
        accept(ContentType.Application.OctetStream)
        header(HttpHeaders.AcceptEncoding, "identity")
        header(HttpHeaders.UserAgent, "NekoMusic-AppUpdate (Android)")
    }

    private fun resolveApkDownloadUrl(url: String): String = when {
        url.startsWith("http://", ignoreCase = true) -> url
        url.startsWith("https://", ignoreCase = true) -> url
        url.startsWith("/") -> UrlConfig.buildFullUrl(url)
        else -> UrlConfig.buildFullUrl("/$url")
    }

    /**
     * 从 Content-Range（如 bytes 0-0/1234567）解析完整资源总字节。
     */
    private fun parseTotalBytesFromContentRange(header: String?): Long {
        if (header.isNullOrBlank()) return 0L
        val slash = header.lastIndexOf('/')
        if (slash < 0 || slash >= header.length - 1) return 0L
        val token = header.substring(slash + 1).trim()
        if (token == "*") return 0L
        return token.toLongOrNull() ?: 0L
    }

    /**
     * 当 GET 响应无 Content-Length（如 chunked）时，尝试 HEAD 与 Range 探测总大小。
     */
    private suspend fun probeApkContentLengthBytes(url: String): Long {
        runCatching {
            val headResponse: HttpResponse = downloadClient.head(url) {
                apkStreamHeaders()
            }
            val fromHead = headResponse.headers.contentLengthBytes()
            if (fromHead > 0L) {
                Log.d("AppUpdateManager", "HEAD 探测到 Content-Length: $fromHead")
                return fromHead
            }
        }.onFailure {
            Log.d("AppUpdateManager", "HEAD 探测不可用: ${it.message}")
        }

        runCatching {
            val rangeResponse = downloadClient.prepareGet(url) {
                apkStreamHeaders()
                header(HttpHeaders.Range, "bytes=0-0")
            }.execute()
            try {
                when (rangeResponse.status) {
                    HttpStatusCode.PartialContent -> {
                        val total = parseTotalBytesFromContentRange(
                            rangeResponse.headers[HttpHeaders.ContentRange]
                        )
                        if (total > 0L) {
                            Log.d("AppUpdateManager", "Range 206 探测到总大小: $total")
                        }
                        val ch = rangeResponse.body<ByteReadChannel>()
                        ch.discard(Long.MAX_VALUE)
                        return total
                    }
                    HttpStatusCode.OK -> {
                        val cl = rangeResponse.headers.contentLengthBytes()
                        runCatching { rangeResponse.body<ByteReadChannel>().cancel(null) }
                        if (cl > 0L) {
                            Log.d("AppUpdateManager", "Range 请求返回 200，使用 Content-Length: $cl")
                            return cl
                        }
                    }
                    else -> {
                        runCatching { rangeResponse.body<ByteReadChannel>().cancel(null) }
                    }
                }
            } finally {
                runCatching {
                    if (!rangeResponse.status.isSuccess()) {
                        rangeResponse.body<ByteReadChannel>().cancel(null)
                    }
                }
            }
        }.onFailure {
            Log.d("AppUpdateManager", "Range 探测失败: ${it.message}")
        }

        return 0L
    }
    
    /**
     * 下载 APK 文件
     */
    suspend fun downloadApk(
        url: String,
        onProgress: (Long, Long) -> Unit,
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

            val resolvedUrl = resolveApkDownloadUrl(url)
            val probed = probeApkContentLengthBytes(resolvedUrl)

            // IO 上更新 pending；主线程用 Handler 节流刷新。
            // 注意：若每次 removeCallbacks + postDelayed(50)，在 read 极快时会不断把 Runnable 往后推，导致直到下载结束前从不执行 → 条子“不动”。
            var pendingDone = 0L
            var pendingTotal = 0L
            val coalesceScheduled = AtomicBoolean(false)
            val flushRunnable = Runnable {
                onProgress(pendingDone, pendingTotal)
                coalesceScheduled.set(false)
            }
            fun scheduleProgress(done: Long, total: Long, immediate: Boolean) {
                pendingDone = done
                pendingTotal = total
                if (immediate) {
                    mainHandler.removeCallbacks(flushRunnable)
                    coalesceScheduled.set(false)
                    mainHandler.post(flushRunnable)
                } else {
                    if (coalesceScheduled.compareAndSet(false, true)) {
                        mainHandler.postDelayed(flushRunnable, 50L)
                    }
                }
            }

            // GET 可能要等很久才返回响应头；先让界面显示探测到的总量
            if (probed > 0L) {
                scheduleProgress(0L, probed, immediate = true)
            }

            val getResponse = downloadClient.prepareGet(resolvedUrl) {
                apkStreamHeaders()
            }.execute()
            val headerCl = getResponse.headers.contentLengthBytes()
            // 探测（HEAD/Range）常比 GET 经 CDN 后的头更可靠；无探测再用 GET 的 Content-Length
            val contentLength = when {
                probed > 0L -> probed
                headerCl > 0L -> headerCl
                else -> 0L
            }
            Log.d(
                "AppUpdateManager",
                "开始下载 APK 总字节=$contentLength (GET Content-Length=$headerCl, 探测=$probed)"
            )

            val byteReadChannel = getResponse.body<ByteReadChannel>()
            scheduleProgress(0L, contentLength, immediate = true)
            
            val outputStream = FileOutputStream(tempFile)
            val buffer = java.nio.ByteBuffer.allocate(8192)
            var totalBytes = 0L
            var lastLogDone = 0L
            
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
                    scheduleProgress(totalBytes, contentLength, immediate = false)
                    if (totalBytes - lastLogDone >= 512L * 1024L || totalBytes == contentLength) {
                        lastLogDone = totalBytes
                        Log.d("AppUpdateManager", "下载进度: $totalBytes / $contentLength")
                    }
                }
            } finally {
                mainHandler.removeCallbacks(flushRunnable)
                coalesceScheduled.set(false)
                outputStream.close()
                byteReadChannel.cancel(null)
            }
            
            scheduleProgress(totalBytes, contentLength, immediate = true)
            
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