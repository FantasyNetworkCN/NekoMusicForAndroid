package com.neko.music

import android.app.Application
import android.content.SharedPreferences
import android.content.res.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import java.util.Locale

class NekoMusicApplication : Application(), SingletonImageLoader.Factory {

    private lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("app_update", MODE_PRIVATE)

        try {
            cacheDir.resolve("image_cache").deleteRecursively()
        } catch (_: Exception) {
            // 忽略：彻底关闭 Coil 磁盘缓存后清理历史目录
        }

        // 应用语言设置
        applyLanguage()

        // 检查版本号是否变化，如果变化了说明更新成功，删除更新文件
        checkAndCleanupUpdateFiles()
    }

    /**
     * Coil 3 全局 [ImageLoader]：关闭内存与磁盘缓存（每次按需拉取/解码）。
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache(null)
            .diskCache(null)
            .build()
    }

    /**
     * 应用语言设置
     */
    private fun applyLanguage() {
        val languagePrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val language = languagePrefs.getString("language", "system") ?: "system"

        val config = resources.configuration
        val locale = when (language) {
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "nya" -> Locale.ROOT
            "en" -> Locale.ENGLISH
            else -> Locale.getDefault() // 跟随系统
        }

        Locale.setDefault(locale)
        config.setLocale(locale)

        // 更新resources的configuration
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    /**
     * 重写onConfigurationChanged以在配置更改时重新应用语言
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyLanguage()
    }

    private fun checkAndCleanupUpdateFiles() {
        try {
            val currentVersionCode = try {
                packageManager.getPackageInfo(packageName, 0).let {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        it.longVersionCode.toInt()
                    } else {
                        it.versionCode
                    }
                }
            } catch (e: Exception) {
                return
            }

            val lastVersionCode = prefs.getInt("last_version_code", -1)

            // 如果版本号变化了，说明更新成功，删除更新文件
            if (lastVersionCode != -1 && lastVersionCode != currentVersionCode) {
                android.util.Log.d("NekoMusicApplication", "检测到版本更新，清理更新文件")
                cleanupUpdateFiles()
            }

            // 更新当前版本号
            prefs.edit().putInt("last_version_code", currentVersionCode).apply()
        } catch (e: Exception) {
            android.util.Log.e("NekoMusicApplication", "检查更新文件失败", e)
        }
    }

    private fun cleanupUpdateFiles() {
        try {
            val externalDir = getExternalFilesDir(null)
            if (externalDir?.exists() == true) {
                externalDir.listFiles()?.filter {
                    it.name.endsWith(".apk") || it.name.startsWith("update_temp")
                }?.forEach {
                    android.util.Log.d("NekoMusicApplication", "删除更新文件: ${it.name}")
                    it.delete()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("NekoMusicApplication", "清理更新文件失败", e)
        }
    }
}
