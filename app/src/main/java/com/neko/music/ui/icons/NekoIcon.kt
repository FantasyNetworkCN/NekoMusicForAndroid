package com.neko.music.ui.icons

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import com.neko.music.R

/** 现有界面的兼容入口；没有对应 SF 字形的业务图标继续使用原资源。 */
@Composable
fun NekoIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val spec = nekoIconSpec(imageVector.name)
    if (spec == null) {
        Icon(imageVector, contentDescription, modifier, tint)
    } else {
        val mirror = imageVector.autoMirror && LocalLayoutDirection.current == LayoutDirection.Rtl
        NekoSymbolIcon(
            symbol = spec.symbol,
            contentDescription = contentDescription,
            modifier = modifier
                .graphicsLayer { scaleX = if (mirror) -1f else 1f }
                .rotate(spec.rotation),
            color = tint,
            variant = spec.variant,
        )
    }
}

@Composable
fun NekoIcon(
    @DrawableRes drawableRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val spec = nekoDrawableIconSpec(drawableRes)
    if (spec == null) {
        Icon(painterResource(drawableRes), contentDescription, modifier, tint)
    } else {
        NekoSymbolIcon(
            symbol = spec.symbol,
            contentDescription = contentDescription,
            modifier = modifier.rotate(spec.rotation),
            color = tint,
            variant = spec.variant,
        )
    }
}

internal data class NekoIconSpec(
    val symbol: NekoSymbol,
    val variant: NekoSymbolVariant = NekoSymbolVariant.Regular,
    val rotation: Float = 0f,
)

internal fun nekoIconSpec(vectorName: String): NekoIconSpec? = when (vectorName.substringAfterLast('.')) {
    "Home" -> NekoIconSpec(NekoSymbol.Home)
    "Person" -> NekoIconSpec(NekoSymbol.Person)
    "List" -> NekoIconSpec(NekoSymbol.Library)
    "Search" -> NekoIconSpec(NekoSymbol.Search)
    "Settings" -> NekoIconSpec(NekoSymbol.Settings)
    "ArrowBack" -> NekoIconSpec(NekoSymbol.ChevronLeft)
    "KeyboardArrowRight" -> NekoIconSpec(NekoSymbol.ChevronRight)
    "KeyboardArrowDown", "ArrowDropDown" -> NekoIconSpec(NekoSymbol.ChevronLeft, rotation = -90f)
    "Close", "Clear" -> NekoIconSpec(NekoSymbol.Xmark)
    "Add" -> NekoIconSpec(NekoSymbol.Plus)
    "Check" -> NekoIconSpec(NekoSymbol.Check)
    "CheckCircle" -> NekoIconSpec(NekoSymbol.CheckCircle, NekoSymbolVariant.Fill)
    "Info" -> NekoIconSpec(NekoSymbol.Info)
    "Favorite" -> NekoIconSpec(NekoSymbol.Heart, NekoSymbolVariant.Fill)
    "FavoriteBorder" -> NekoIconSpec(NekoSymbol.Heart)
    "PlayArrow" -> NekoIconSpec(NekoSymbol.Play, NekoSymbolVariant.Fill)
    "Refresh" -> NekoIconSpec(NekoSymbol.Refresh)
    "MoreVert" -> NekoIconSpec(NekoSymbol.MoreVertical)
    "Share" -> NekoIconSpec(NekoSymbol.Share)
    "Delete" -> NekoIconSpec(NekoSymbol.Trash)
    else -> null
}

internal fun nekoDrawableIconSpec(@DrawableRes resource: Int): NekoIconSpec? = when (resource) {
    R.drawable.play, R.drawable.ic_desktop_lyric_play ->
        NekoIconSpec(NekoSymbol.Play, NekoSymbolVariant.Fill)
    R.drawable.pause, R.drawable.ic_desktop_lyric_pause ->
        NekoIconSpec(NekoSymbol.Pause, NekoSymbolVariant.Fill)
    R.drawable.previous_song -> NekoIconSpec(NekoSymbol.Previous)
    R.drawable.next_song -> NekoIconSpec(NekoSymbol.Next)
    R.drawable.playlist -> NekoIconSpec(NekoSymbol.Queue)
    R.drawable.ic_lyric_toggle -> NekoIconSpec(NekoSymbol.Lyrics)
    R.drawable.ic_notification_message -> NekoIconSpec(NekoSymbol.Message)
    R.drawable.ic_music_recognition -> NekoIconSpec(NekoSymbol.Microphone)
    R.drawable.ic_more_horizontal -> NekoIconSpec(NekoSymbol.Ellipsis)
    R.drawable.ic_quick_search -> NekoIconSpec(NekoSymbol.Search)
    R.drawable.ic_favorite_filled -> NekoIconSpec(NekoSymbol.Heart, NekoSymbolVariant.Fill)
    R.drawable.ic_favorite_border -> NekoIconSpec(NekoSymbol.Heart)
    R.drawable.ic_vip_star -> NekoIconSpec(NekoSymbol.Star, NekoSymbolVariant.Fill)
    R.drawable.music -> NekoIconSpec(NekoSymbol.MusicNote)
    R.drawable.user -> NekoIconSpec(NekoSymbol.Person)
    R.drawable.setting -> NekoIconSpec(NekoSymbol.Settings)
    R.drawable.about -> NekoIconSpec(NekoSymbol.Info)
    R.drawable.recently_played -> NekoIconSpec(NekoSymbol.Clock)
    R.drawable.download -> NekoIconSpec(NekoSymbol.Download)
    R.drawable.email -> NekoIconSpec(NekoSymbol.Mail)
    else -> null
}
