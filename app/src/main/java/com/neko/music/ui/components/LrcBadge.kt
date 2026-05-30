package com.neko.music.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neko.music.R

@Composable
fun LrcBadge(
    visible: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF9370DB)
) {
    if (!visible) return
    Icon(
        painter = painterResource(R.drawable.ic_lrc_badge),
        contentDescription = stringResource(R.string.has_lyrics),
        modifier = modifier.size(16.dp),
        tint = tint
    )
}
