package com.neko.music.ui.icons

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import com.neko.music.R

/** 按字体和字重缓存；整个进程只加载每种组合一次，失败时由图标组件降级显示。 */
internal object NekoSfTypefaceCache {
    private data class Key(val weight: Int, val subsetOnly: Boolean)

    private val cache = HashMap<Key, Typeface?>()

    @Synchronized
    fun obtain(context: Context, weight: Int, subsetOnly: Boolean = false): Typeface? {
        val key = Key(weight.coerceIn(1, 1000), subsetOnly)
        if (cache.containsKey(key)) return cache[key]
        val typeface = runCatching {
            val resource = if (subsetOnly) R.font.sf_pro_subset else R.font.sf_pro_vf
            ResourcesCompat.getFont(context, resource)?.let { font ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    Typeface.create(font, key.weight, false)
                } else {
                    // 旧系统的双参数重载接收样式，不能直接传入 400、500 等字重。
                    Typeface.create(font, if (key.weight >= 600) Typeface.BOLD else Typeface.NORMAL)
                }
            }
        }.onFailure {
            Log.w("NekoSfTypefaceCache", "加载图标字体失败，使用备用图标", it)
        }.getOrNull()
        cache[key] = typeface
        return typeface
    }
}
