package com.neko.music

import android.app.Application
import android.content.SharedPreferences
import android.content.res.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

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
    
    // 全局 Cookie 缓存
    private var cachedCookie: String? = null
    private var cookieExpireTime = 0L
    private val COOKIE_CACHE_DURATION = 30 * 60 * 1000L // 30分钟

    /**
     * 获取缓存的 Cookie
     */
    fun getCachedCookie(): String? {
        if (System.currentTimeMillis() > cookieExpireTime) {
            cachedCookie = null
            return null
        }
        return cachedCookie
    }

    /**
     * 设置缓存的 Cookie
     */
    fun setCachedCookie(cookie: String) {
        cachedCookie = cookie
        cookieExpireTime = System.currentTimeMillis() + COOKIE_CACHE_DURATION
    }

    override fun newImageLoader(): ImageLoader {
        // 创建 OkHttp 客户端，添加 ACW Cookie 拦截器
        val okHttpClient = OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val originalRequest = chain.request()

                // 检查是否是请求音乐.cnmsb.xin 域名的图片
                val host = originalRequest.url.host
                if (host.contains("music.cnmsb.xin")) {
                    // 使用缓存的 Cookie
                    val cookie = getCachedCookie()

                    // 添加 Cookie 头
                    val newRequest = if (cookie != null) {
                        originalRequest.newBuilder()
                            .header("Cookie", cookie)
                            .build()
                    } else {
                        originalRequest
                    }

                    chain.proceed(newRequest)
                } else {
                    // 非音乐.cnmsb.xin 域名的请求，直接继续
                    chain.proceed(originalRequest)
                }
            }
            .build()

        return ImageLoader.Builder(this)
            // 强制禁用所有磁盘缓存
            .diskCache(null)
            // 强制禁用所有内存缓存
            .memoryCache(null)
            // 禁用所有缓存策略
            .allowHardware(false)
            // 使用自定义的 OkHttp 客户端
            .okHttpClient(okHttpClient)
            .build()
    }
}