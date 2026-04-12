package com.neko.music.data.manager

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ACW 挑战求解器
 * 使用系统 WebView 在后台执行 JavaScript 来绕过阿里云 ACW 防护
 */
class ACWChallengeSolver(private val context: Context) {

    companion object {
        private const val BASE_URL = "https://music.cnmsb.xin"
        private const val COOKIE_NAME = "acw_sc__v2"
        private const val MAX_WAIT_TIME = 10000L // 10秒超时
    }

    /**
     * 获取 ACW Cookie
     * @return Cookie 字符串，如果失败返回 null
     */
    suspend fun getCookie(): String? = suspendCancellableCoroutine { continuation ->
        val handler = Handler(Looper.getMainLooper())
        var webView: WebView? = null
        var hasResumed = false

        // 在主线程上创建 WebView
        handler.post {
            if (hasResumed) {
                return@post
            }
            webView = createInvisibleWebView()

            val timeoutRunnable = Runnable {
                if (!hasResumed) {
                    hasResumed = true
                    webView?.destroy()
                    continuation.resume(null)
                }
            }

            handler.postDelayed(timeoutRunnable, MAX_WAIT_TIME)

            webView?.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // 等待 JavaScript 执行完成
                    handler.postDelayed({
                        if (!hasResumed) {
                            hasResumed = true
                            handler.removeCallbacks(timeoutRunnable)

                            try {
                                val cookie = extractCookie()
                                webView?.destroy()

                                if (cookie != null) {
                                    continuation.resume(cookie)
                                } else {
                                    continuation.resume(null)
                                }
                            } catch (e: Exception) {
                                webView?.destroy()
                                continuation.resumeWithException(e)
                            }
                        }
                    }, 2000) // 延迟2秒，确保 JavaScript 执行完成
                }

                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    if (!hasResumed) {
                        hasResumed = true
                        handler.removeCallbacks(timeoutRunnable)
                        webView?.destroy()
                        continuation.resume(null)
                    }
                }
            }

            // 加载登录页面触发 ACW 挑战
            webView?.loadUrl("$BASE_URL/api/user/login")
        }

        // 清理回调
        continuation.invokeOnCancellation {
            if (!hasResumed) {
                hasResumed = true
                handler.post {
                    webView?.destroy()
                }
            }
        }
    }

    /**
     * 创建不可见的 WebView
     */
    private fun createInvisibleWebView(): WebView {
        return WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            }
            layoutParams = android.view.ViewGroup.LayoutParams(1, 1)
        }
    }

    /**
     * 提取 Cookie
     */
    private fun extractCookie(): String? {
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie(BASE_URL) ?: return null

        // 提取 acw_sc__v2 cookie
        val cookiePairs = cookies.split(";")
        for (cookiePair in cookiePairs) {
            val parts = cookiePair.trim().split("=", limit = 2)
            if (parts.size == 2 && parts[0] == COOKIE_NAME) {
                return "$COOKIE_NAME=${parts[1]}"
            }
        }

        return null
    }
}