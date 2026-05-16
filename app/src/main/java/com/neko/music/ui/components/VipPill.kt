package com.neko.music.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 与 Web 顶栏 [header-vip-pill] 一致：非会员灰胶囊，会员金色渐变 + 呼吸光晕。
 */
@Composable
fun VipPill(
    isVip: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val shape = RoundedCornerShape(999.dp)
    val horizontalPad = if (compact) 12.dp else 16.dp
    val verticalPad = if (compact) 5.dp else 7.dp
    val fontSize = if (compact) 10.sp else 11.sp

    val infiniteTransition = rememberInfiniteTransition(label = "vip_glow")
    val glowElevation by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vip_glow_elevation"
    )

    val activeBrush = Brush.linearGradient(
        colors = listOf(Color(0xFFFFE082), Color(0xFFFFB300))
    )
    val inactiveBg = Color(0xFF6E6E78).copy(alpha = 0.22f)
    val inactiveBorder = Color(0xFF6E6E78).copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .then(
                if (isVip) {
                    Modifier.shadow(
                        elevation = glowElevation.dp,
                        shape = shape,
                        spotColor = Color(0xFFFFC107).copy(alpha = 0.55f),
                        ambientColor = Color(0xFFFFB300).copy(alpha = 0.35f)
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .then(
                if (isVip) {
                    Modifier.background(activeBrush)
                } else {
                    Modifier.background(inactiveBg)
                }
            )
            .border(
                width = 1.dp,
                color = if (isVip) Color(0xFFFFB300).copy(alpha = 0.7f) else inactiveBorder,
                shape = shape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = horizontalPad, vertical = verticalPad),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "VIP",
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.2.sp,
            color = if (isVip) Color(0xFF3D2A00) else Color(0xFF6B6B70)
        )
    }
}
