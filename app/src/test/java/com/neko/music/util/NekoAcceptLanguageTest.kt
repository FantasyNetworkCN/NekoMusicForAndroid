package com.neko.music.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * 出站请求的语言标头：取值必须跟着「App 内设置的语言」走，服务端按它决定 API 文案的语言。
 *
 * 只覆盖显式语言码；「跟随系统」分支要读 Android 的 `Resources`（设备态），放到真机验证。
 */
class NekoAcceptLanguageTest {

    @Test
    fun headerNameMatchesServerContract() {
        assertEquals("Accept-Language", NEKO_ACCEPT_LANGUAGE_HEADER)
    }

    @Test
    fun explicitAppLanguagesMapToTheirOwnTags() {
        assertEquals("zh-CN", NekoAcceptLanguage.toHeaderValue("zh"))
        assertEquals("en", NekoAcceptLanguage.toHeaderValue("en"))
        assertEquals("nya", NekoAcceptLanguage.toHeaderValue("nya"))
    }

    @Test
    fun systemLocaleTagDropsUnicodeExtensions() {
        // Android 的系统 Locale 会带 `-u-mu-celsius` 这类扩展，不能原样发进请求头
        assertEquals("zh-CN", NekoAcceptLanguage.tagOf(Locale.forLanguageTag("zh-CN-u-mu-celsius")))
        assertEquals("zh-CN", NekoAcceptLanguage.tagOf(Locale.forLanguageTag("zh-Hans-CN-u-mu-celsius")))
        assertEquals("en", NekoAcceptLanguage.tagOf(Locale.forLanguageTag("en-u-hc-h12")))
        assertEquals("en-US", NekoAcceptLanguage.tagOf(Locale.forLanguageTag("en-US")))
        assertEquals("ja", NekoAcceptLanguage.tagOf(Locale.JAPANESE))
        // 未定语言回落到默认中文
        assertEquals("zh-CN", NekoAcceptLanguage.tagOf(Locale.ROOT))
    }
}
