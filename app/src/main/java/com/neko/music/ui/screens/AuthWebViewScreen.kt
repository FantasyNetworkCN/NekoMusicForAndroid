package com.neko.music.ui.screens

import com.neko.music.ui.icons.NekoIcon

import android.net.Uri
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AuthWebViewScreen(
    url: String,
    title: String,
    onBackClick: () -> Unit,
    onSsoHandoff: (String) -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    fun goBack() {
        val current = webView
        if (current != null && current.canGoBack()) current.goBack() else onBackClick()
    }

    BackHandler(onBack = ::goBack)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = ::goBack) {
                NekoIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: android.webkit.WebResourceRequest,
                        ): Boolean {
                            val uri = request.url
                            if (request.isForMainFrame && isNativeSsoCallback(uri)) {
                                onSsoHandoff(uri.getQueryParameter("handoff").orEmpty())
                                return true
                            }
                            return false
                        }

                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            canGoBack = view.canGoBack()
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            canGoBack = view.canGoBack()
                        }
                    }
                    loadUrl(url)
                    webView = this
                }
            },
        )
    }
}

private fun isNativeSsoCallback(uri: Uri): Boolean {
    val customScheme = uri.scheme.equals("nekomusic", ignoreCase = true)
        && uri.host.equals("sso", ignoreCase = true)
        && uri.path == "/complete"
    val verifiedWebCallback = uri.scheme.equals("https", ignoreCase = true)
        && uri.host.equals("music.nekocore.cn", ignoreCase = true)
        && uri.path == "/sso/native-complete"
        && uri.getQueryParameter("client") == "android"
    return customScheme || verifiedWebCallback
}
