package com.neko.music.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.neko.music.R

/**
 * 编辑式合集页头，用于最新、热门等“一页一组歌”的浏览场景。
 * 视觉层级固定为：栏目说明、主标题、内容说明、数量/时间、封面。
 */
@Composable
fun EditorialCollectionHero(
    eyebrow: String,
    title: String,
    description: String,
    coverUrl: String,
    meta: String,
    badge: String,
    accent: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val glass = LiquidGlassDefaults.playlistDetail
    val titleColor = if (isDark) Color.White else scheme.onSurface
    val bodyColor = if (isDark) Color.White.copy(alpha = 0.70f) else scheme.onSurface.copy(alpha = 0.66f)
    val badgeContentColor = if (accent.luminance() > 0.62f) Color.Black else Color.White

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(196.dp),
        shape = RoundedCornerShape(28.dp),
        backgroundAlpha = if (isDark) {
            glass.batchDockBackgroundAlphaDark
        } else {
            glass.batchDockBackgroundAlphaLight
        },
        borderAlpha = if (isDark) glass.batchDockBorderAlphaDark else glass.batchDockBorderAlphaLight,
        highlightAlpha = if (isDark) {
            glass.batchDockHighlightAlphaDark
        } else {
            glass.batchDockHighlightAlphaLight
        },
        borderColor = if (isDark) Color.White else scheme.outline,
        liquidBlur = glass.batchDockLiquidBlur,
        liquidLensHeight = glass.batchDockLiquidLensHeight,
        liquidLensAmount = glass.batchDockLiquidLensAmount,
        surfaceTint = accent.copy(alpha = if (isDark) 0.07f else 0.035f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(accent, CircleShape),
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = eyebrow,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = accent,
                        maxLines = 1,
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = title,
                    fontSize = 27.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = bodyColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = meta,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor.copy(alpha = 0.66f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(modifier = Modifier.size(112.dp)) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accent.copy(alpha = 0.30f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    accent.copy(alpha = 0.34f),
                                    scheme.surfaceVariant.copy(alpha = 0.18f),
                                ),
                            ),
                        )
                        .border(
                            width = 0.5.dp,
                            color = Color.White.copy(alpha = 0.24f),
                            shape = RoundedCornerShape(24.dp),
                        ),
                ) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = stringResource(id = R.string.cover),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(R.drawable.music),
                        error = painterResource(R.drawable.music),
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(accent)
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = badge,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeContentColor,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun EditorialPlayAllButton(
    label: String,
    countText: String,
    accent: Color,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val glass = LiquidGlassDefaults.playlistDetail
    val titleColor = if (isDark) Color(0xFFF4F4F7) else Color.Black
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "editorial-play-all-press",
    )

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(18.dp),
        backgroundAlpha = if (isDark) {
            glass.batchDockBackgroundAlphaDark
        } else {
            glass.batchDockBackgroundAlphaLight
        },
        borderAlpha = if (isDark) glass.batchDockBorderAlphaDark else glass.batchDockBorderAlphaLight,
        highlightAlpha = if (isDark) {
            glass.batchDockHighlightAlphaDark
        } else {
            glass.batchDockHighlightAlphaLight
        },
        borderColor = if (isDark) Color.White else scheme.outline,
        liquidBlur = glass.batchDockLiquidBlur,
        liquidLensHeight = glass.batchDockLiquidLensHeight,
        liquidLensAmount = glass.batchDockLiquidLensAmount,
        surfaceTint = accent.copy(alpha = if (isDark) 0.10f else 0.06f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(accent.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
            )
            Text(
                text = countText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = titleColor.copy(alpha = 0.52f),
            )
        }
    }
}

@Composable
fun EditorialCollectionLoading(
    isDark: Boolean,
    message: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val glass = LiquidGlassDefaults.playlistDetail
    val skeletonColor = if (isDark) Color.White else scheme.onSurface
    val transition = rememberInfiniteTransition(label = "editorial-loading")
    val pulse by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "editorial-loading-pulse",
    )

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(196.dp)
                .alpha(0.5f + pulse * 0.25f),
            shape = RoundedCornerShape(28.dp),
            backgroundAlpha = if (isDark) {
                glass.batchDockBackgroundAlphaDark
            } else {
                glass.batchDockBackgroundAlphaLight
            },
            borderAlpha = if (isDark) glass.batchDockBorderAlphaDark else glass.batchDockBorderAlphaLight,
            highlightAlpha = if (isDark) {
                glass.batchDockHighlightAlphaDark
            } else {
                glass.batchDockHighlightAlphaLight
            },
            borderColor = if (isDark) Color.White else scheme.outline,
            liquidBlur = glass.batchDockLiquidBlur,
            liquidLensHeight = glass.batchDockLiquidLensHeight,
            liquidLensAmount = glass.batchDockLiquidLensAmount,
            surfaceTint = accent.copy(alpha = if (isDark) 0.05f else 0.025f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    EditorialSkeletonBlock(
                        modifier = Modifier
                            .width(58.dp)
                            .height(8.dp),
                        color = skeletonColor,
                    )
                    EditorialSkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .height(25.dp),
                        color = skeletonColor,
                    )
                    EditorialSkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp),
                        color = skeletonColor,
                    )
                    EditorialSkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.56f)
                            .height(10.dp),
                        color = skeletonColor,
                    )
                }
                EditorialSkeletonBlock(
                    modifier = Modifier.size(112.dp),
                    color = skeletonColor,
                    corner = 24.dp,
                )
            }
        }

        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .alpha(0.5f + pulse * 0.25f),
            shape = RoundedCornerShape(18.dp),
            backgroundAlpha = if (isDark) {
                glass.batchDockBackgroundAlphaDark
            } else {
                glass.batchDockBackgroundAlphaLight
            },
            borderAlpha = if (isDark) glass.batchDockBorderAlphaDark else glass.batchDockBorderAlphaLight,
            highlightAlpha = if (isDark) {
                glass.batchDockHighlightAlphaDark
            } else {
                glass.batchDockHighlightAlphaLight
            },
            borderColor = if (isDark) Color.White else scheme.outline,
            liquidBlur = glass.batchDockLiquidBlur,
            liquidLensHeight = glass.batchDockLiquidLensHeight,
            liquidLensAmount = glass.batchDockLiquidLensAmount,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EditorialSkeletonBlock(
                    modifier = Modifier.size(28.dp),
                    color = skeletonColor,
                    corner = 14.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                EditorialSkeletonBlock(
                    modifier = Modifier
                        .width(104.dp)
                        .height(12.dp),
                    color = skeletonColor,
                )
            }
        }

        repeat(4) {
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .alpha(0.45f + pulse * 0.22f),
                shape = RoundedCornerShape(glass.musicRowCornerDp),
                backgroundAlpha = if (isDark) {
                    glass.musicRowBackgroundAlphaDark
                } else {
                    glass.musicRowBackgroundAlphaLight
                },
                borderAlpha = if (isDark) {
                    glass.musicRowBorderAlphaDark
                } else {
                    glass.musicRowBorderAlphaLight
                },
                highlightAlpha = if (isDark) {
                    glass.musicRowHighlightAlphaDark
                } else {
                    glass.musicRowHighlightAlphaLight
                },
                borderColor = if (isDark) Color.White else scheme.outline,
                liquidBlur = glass.musicRowLiquidBlur,
                liquidLensHeight = glass.musicRowLiquidLensHeight,
                liquidLensAmount = glass.musicRowLiquidLensAmount,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EditorialSkeletonBlock(
                        modifier = Modifier
                            .width(22.dp)
                            .height(9.dp),
                        color = skeletonColor,
                    )
                    EditorialSkeletonBlock(
                        modifier = Modifier.size(46.dp),
                        color = skeletonColor,
                        corner = 10.dp,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        EditorialSkeletonBlock(
                            modifier = Modifier
                                .fillMaxWidth(0.66f)
                                .height(11.dp),
                            color = skeletonColor,
                        )
                        EditorialSkeletonBlock(
                            modifier = Modifier
                                .fillMaxWidth(0.42f)
                                .height(8.dp),
                            color = skeletonColor,
                        )
                    }
                    EditorialSkeletonBlock(
                        modifier = Modifier
                            .width(26.dp)
                            .height(8.dp),
                        color = skeletonColor,
                    )
                }
            }
        }

        Text(
            text = message,
            modifier = Modifier.padding(top = 4.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (isDark) Color.White.copy(alpha = 0.62f) else scheme.onSurface.copy(alpha = 0.54f),
        )
    }
}

@Composable
fun EditorialCollectionStateCard(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    accent: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val glass = LiquidGlassDefaults.playlistDetail
    val titleColor = if (isDark) Color.White else scheme.onSurface
    val bodyColor = if (isDark) Color.White.copy(alpha = 0.68f) else scheme.onSurface.copy(alpha = 0.62f)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "editorial-state-action-press",
    )

    Box(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            backgroundAlpha = if (isDark) {
                glass.batchDockBackgroundAlphaDark
            } else {
                glass.batchDockBackgroundAlphaLight
            },
            borderAlpha = if (isDark) glass.batchDockBorderAlphaDark else glass.batchDockBorderAlphaLight,
            highlightAlpha = if (isDark) {
                glass.batchDockHighlightAlphaDark
            } else {
                glass.batchDockHighlightAlphaLight
            },
            borderColor = if (isDark) Color.White else scheme.outline,
            liquidBlur = glass.batchDockLiquidBlur,
            liquidLensHeight = glass.batchDockLiquidLensHeight,
            liquidLensAmount = glass.batchDockLiquidLensAmount,
            surfaceTint = accent.copy(alpha = if (isDark) 0.04f else 0.025f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(accent.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(23.dp),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(7.dp))
                Text(
                    text = message,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = bodyColor,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(20.dp))
                GlassSurface(
                    modifier = Modifier
                        .height(42.dp)
                        .scale(scale)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onAction,
                        ),
                    shape = RoundedCornerShape(21.dp),
                    backgroundAlpha = if (isDark) {
                        glass.batchDockBackgroundAlphaDark
                    } else {
                        glass.batchDockBackgroundAlphaLight
                    },
                    borderAlpha = if (isDark) {
                        glass.batchDockBorderAlphaDark
                    } else {
                        glass.batchDockBorderAlphaLight
                    },
                    highlightAlpha = if (isDark) {
                        glass.batchDockHighlightAlphaDark
                    } else {
                        glass.batchDockHighlightAlphaLight
                    },
                    borderColor = if (isDark) Color.White else scheme.outline,
                    liquidBlur = glass.batchDockLiquidBlur,
                    liquidLensHeight = glass.batchDockLiquidLensHeight,
                    liquidLensAmount = glass.batchDockLiquidLensAmount,
                    surfaceTint = accent.copy(alpha = if (isDark) 0.10f else 0.06f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = actionLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = titleColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorialSkeletonBlock(
    modifier: Modifier,
    color: Color,
    corner: androidx.compose.ui.unit.Dp = 6.dp,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(color.copy(alpha = 0.13f)),
    )
}
