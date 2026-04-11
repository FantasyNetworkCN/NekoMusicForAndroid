package com.neko.music

import android.app.Application
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import java.io.File
import java.util.Locale

class NekoMusicApplication : Application(), ImageLoaderFactory {
    
    private lateinit var prefs: SharedPreferences
    
    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("app_update", MODE_PRIVATE)
        
        // 应用语言设置
        applyLanguage()
        
        // 检查版本号是否变化，如果变化了说明更新成功，删除更新文件
        checkAndCleanupUpdateFiles()
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
    
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            // 强制禁用所有磁盘缓存
            .diskCache(null)
            // 强制禁用所有内存缓存
            .memoryCache(null)
            // 禁用所有缓存策略
            .allowHardware(false)
            .build()
    }
}