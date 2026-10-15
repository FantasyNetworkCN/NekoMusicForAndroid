package com.neko.music.util

import android.content.Context
import android.content.res.Resources
import com.neko.music.config.AppConfig
import java.util.Locale

/**
 * 出站请求的语言标头 `Accept-Language`。
 *
 * 服务端按该头决定 API 文案（错误提示等）回哪种语言，因此取值必须是**用户在 App 内设置的
 * 语言**，而不是系统语言：
 *
 *   - `zh` → `zh-CN`
 *   - `en` → `en`
 *   - `nya`（喵语）→ `nya`；服务端暂无对应文案，会回落到默认语言
 *   - `system`（跟随系统）→ 设备当前语言
 *
 * 取值随时从设置里读，用户在设置页改语言后（该页会 `recreate()` Activity）无需重启进程即可生效。
 */
const val NEKO_ACCEPT_LANGUAGE_HEADER = "Accept-Language"

/** 语言设置所在 SharedPreferences：与设置页 / [com.neko.music.NekoMusicApplication] 保持一致。 */
private const val LANGUAGE_PREFS_NAME = "app_settings"

private const val TAG_ZH = "zh-CN"
private const val TAG_EN = "en"
private const val TAG_NYA = "nya"

/**
 * 语言标头的进程级入口。
 *
 * 网络层（Ktor 插件 / ExoPlayer / DownloadManager）拿不到设置所在的 Context，这里像
 * [com.neko.music.data.manager.TokenManager] 一样在启动时留一份 Application Context 用于读取设置。
 */
object NekoAcceptLanguage {

    @Volatile
    private var applicationContext: Context? = null

    /** 在 `Application.onCreate` 调用；未调用时一律按「跟随系统」处理。 */
    fun install(context: Context) {
        applicationContext = context.applicationContext
    }

    /** 用户当前设置的语言码（`zh` / `nya` / `en` / `system`）。 */
    fun languageCode(): String =
        applicationContext
            ?.getSharedPreferences(LANGUAGE_PREFS_NAME, Context.MODE_PRIVATE)
            ?.getString(AppConfig.PrefConfig.KEY_LANGUAGE, AppConfig.PrefConfig.DEFAULT_LANGUAGE)
            ?: AppConfig.PrefConfig.DEFAULT_LANGUAGE

    /** 当前该发送的 `Accept-Language` 取值。 */
    fun headerValue(): String = toHeaderValue(languageCode())

    /** 语言码 → `Accept-Language`；未知取值按「跟随系统」处理（与设置页展示口径一致）。 */
    fun toHeaderValue(languageCode: String): String = when (languageCode) {
        "zh" -> TAG_ZH
        "en" -> TAG_EN
        "nya" -> TAG_NYA
        else -> systemLanguageTag()
    }

    /**
     * 设备语言。
     *
     * 用 [Resources.getSystem] 而不是 `Locale.getDefault()`：后者会被本 App 的语言覆盖
     * （见 `NekoMusicApplication.applyLanguage`），设置成「跟随系统」时读到的是上一次选过的语言。
     */
    private fun systemLanguageTag(): String {
        val locales = Resources.getSystem().configuration.locales
        if (locales.isEmpty) return TAG_ZH
        return tagOf(locales[0])
    }

    /**
     * 语言标签：只保留「语言[-地区]」。
     *
     * 不能直接用 [Locale.toLanguageTag]：Android 的系统 Locale 常带 Unicode 扩展，会拼出
     * `zh-CN-u-mu-celsius` 这种带度量单位扩展的标签，超出服务端与 HTTP 头的常规预期。
     */
    internal fun tagOf(locale: Locale): String {
        val language = locale.language
        if (language.isBlank() || language.equals("und", ignoreCase = true)) return TAG_ZH
        val country = locale.country
        return if (country.isBlank()) language else "$language-$country"
    }
}
