package com.neko.music.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.neko.music.data.api.VipPayCreateData

/**
 * 拉起支付：ZPay 返回多为 H5（z-pay.cn / qr.alipay.com），需在 WebView 内加载并由页面跳转 weixin:// / alipays://。
 */
object PayLauncher {
    private const val TAG = "PayLauncher"
    private const val WECHAT_PACKAGE = "com.tencent.mm"
    private const val ALIPAY_PACKAGE = "com.eg.android.AlipayGphone"

    fun launch(context: Context, payType: String, data: VipPayCreateData): PayLaunchOutcome {
        val candidates = collectUrls(data)
        if (candidates.isEmpty()) {
            return PayLaunchOutcome.Fail("empty")
        }
        Log.d(TAG, "payType=$payType candidates=$candidates")

        return when (payType.lowercase()) {
            "wxpay" -> launchWeChat(context, candidates)
            "alipay" -> launchAlipay(context, candidates)
            else -> launchGeneric(context, candidates)
        }
    }

    private fun launchWeChat(context: Context, urls: List<String>): PayLaunchOutcome {
        val ordered = prioritizeWeChatUrls(urls)

        for (url in ordered) {
            if (isWeChatDeepLink(url) && tryView(context, url, preferPackage = null)) {
                return PayLaunchOutcome.Ok
            }
        }

        val h5 = ordered.firstOrNull { needsWebViewForGateway(it) }
        if (h5 != null) {
            if (!isAppInstalled(context, WECHAT_PACKAGE)) {
                return PayLaunchOutcome.Fail("wechat_missing")
            }
            return PayLaunchOutcome.OpenWeb(h5)
        }

        for (url in ordered) {
            if (tryView(context, url, preferPackage = null)) {
                return PayLaunchOutcome.Ok
            }
        }
        return PayLaunchOutcome.Fail("wechat_failed")
    }

    private fun launchAlipay(context: Context, urls: List<String>): PayLaunchOutcome {
        val ordered = prioritizeAlipayUrls(urls)

        for (url in ordered) {
            if (isAlipayDeepLink(url) && tryView(context, url, preferPackage = null)) {
                return PayLaunchOutcome.Ok
            }
        }

        val h5 = ordered.firstOrNull { needsWebViewForGateway(it) || isAlipayQrUrl(it) }
        if (h5 != null) {
            return PayLaunchOutcome.OpenWeb(h5)
        }

        if (isAppInstalled(context, ALIPAY_PACKAGE)) {
            for (url in ordered) {
                if (tryView(context, url, preferPackage = ALIPAY_PACKAGE)) {
                    return PayLaunchOutcome.Ok
                }
            }
        }
        for (url in ordered) {
            if (tryView(context, url, preferPackage = null)) {
                return PayLaunchOutcome.Ok
            }
        }
        return PayLaunchOutcome.Fail("alipay_failed")
    }

    private fun launchGeneric(context: Context, urls: List<String>): PayLaunchOutcome {
        val url = urls.first()
        if (needsWebViewForGateway(url)) {
            return PayLaunchOutcome.OpenWeb(url)
        }
        return if (tryView(context, url, preferPackage = null)) {
            PayLaunchOutcome.Ok
        } else {
            PayLaunchOutcome.Fail("generic_failed")
        }
    }

    private fun prioritizeWeChatUrls(urls: List<String>): List<String> =
        urls.sortedByDescending { scoreWeChatUrl(it) }

    private fun scoreWeChatUrl(url: String): Int = when {
        isWeChatDeepLink(url) -> 100
        url.contains("h5.php", ignoreCase = true) -> 90
        url.contains("mall.z-pay", ignoreCase = true) -> 80
        url.contains("wxpay", ignoreCase = true) -> 70
        isLikelyWeChatH5(url) -> 60
        else -> 10
    }

    private fun prioritizeAlipayUrls(urls: List<String>): List<String> =
        urls.sortedByDescending { scoreAlipayUrl(it) }

    private fun scoreAlipayUrl(url: String): Int = when {
        isAlipayDeepLink(url) -> 100
        isAlipayQrUrl(url) -> 85
        url.contains("alipay", ignoreCase = true) -> 50
        else -> 10
    }

    private fun needsWebViewForGateway(url: String): Boolean {
        val host = Uri.parse(url).host?.lowercase() ?: return false
        return host.contains("z-pay.cn") ||
            host.contains("zpay") ||
            host.contains("tenpay.com")
    }

    private fun isAlipayQrUrl(url: String): Boolean {
        val host = Uri.parse(url).host?.lowercase() ?: return false
        return host == "qr.alipay.com" || host.endsWith(".alipay.com")
    }

    private fun collectUrls(data: VipPayCreateData): List<String> {
        return listOfNotNull(data.payUrl, data.payUrl2, data.qrCode)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun isWeChatDeepLink(url: String): Boolean {
        val scheme = Uri.parse(url).scheme?.lowercase() ?: return false
        return scheme == "weixin" || scheme == "wxp"
    }

    private fun isLikelyWeChatH5(url: String): Boolean {
        val host = Uri.parse(url).host?.lowercase() ?: return false
        return host.contains("tenpay.com") ||
            host.contains("weixin.qq.com") ||
            host.contains("wechatpay")
    }

    private fun isAlipayDeepLink(url: String): Boolean {
        val scheme = Uri.parse(url).scheme?.lowercase() ?: return false
        return scheme == "alipays" || scheme == "alipay"
    }

    private fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun tryView(context: Context, url: String, preferPackage: String?): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                preferPackage?.let { setPackage(it) }
            }
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "无法打开支付链接 package=$preferPackage url=$url: ${e.message}")
            false
        } catch (e: Exception) {
            Log.w(TAG, "打开支付异常: ${e.message}")
            false
        }
    }

    sealed class PayLaunchOutcome {
        data object Ok : PayLaunchOutcome()
        data class OpenWeb(val url: String) : PayLaunchOutcome()
        data class Fail(val reason: String) : PayLaunchOutcome()
    }
}
