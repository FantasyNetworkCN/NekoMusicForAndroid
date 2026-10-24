package com.neko.music.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow as TextShadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil3.compose.AsyncImage
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow as BackdropShadow
import com.kyant.shapes.Capsule
import com.neko.music.R
import com.neko.music.ui.theme.isAppDarkTheme
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.launch

/** 底栏高度；迷你播放器等底部 chrome 以此为基准留白。 */
internal val BottomNavigationBarHeight: Dp = 64.dp

sealed class BottomNavItem(
    val route: String,
    val titleResId: Int,
    val icon: ImageVector,
) {
    object Home : BottomNavItem("home", R.string.nav_home, Icons.Filled.Home)
    object Mine : BottomNavItem("mine", R.string.nav_mine, Icons.Filled.Person)
    object MyPlaylists : BottomNavItem(
        "my_playlists",
        R.string.nav_my_playlists,
        Icons.AutoMirrored.Filled.List,
    )
}

internal val LocalLiquidTabScale = staticCompositionLocalOf<() -> Float> { { 1f } }

/**
 * 悬浮胶囊底栏：按 AndroidLiquidGlass Catalog 的 `LiquidBottomTabs` 完整移植。
 *
 * 页面在 [MainActivity] 中先铺底色再 `layerBackdrop` 录屏，这里作为兄弟层直接 `drawBackdrop` 采样。
 * 包含整条按压缩放、拖动选中胶囊、速度形变、选中内容二次折射、径向按压高光与松手回弹。
 */
@Composable
fun BottomNavigationBar(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Mine,
        BottomNavItem.MyPlaylists,
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val pageBackdrop = LocalLiquidLayerBackdrop.current
    val isDarkBar = isAppDarkTheme()
    val useLiquidGlass = pageBackdrop != null && isLiquidGlassEffectActive(pageBackdrop)
    val view = LocalView.current

    fun navigateToItem(index: Int) {
        val item = items[index.coerceIn(0, items.lastIndex)]
        if (currentRoute != item.route) {
            navController.navigate(item.route) {
                popUpTo(item.route) { inclusive = true }
                launchSingleTop = true
            }
            @Suppress("DEPRECATION")
            val haptic = if (Build.VERSION.SDK_INT >= 30) {
                HapticFeedbackConstants.SEGMENT_TICK
            } else {
                HapticFeedbackConstants.CONTEXT_CLICK
            }
            view.performHapticFeedback(haptic)
        } else {
            navController.popBackStack(item.route, inclusive = false)
        }
    }

    if (useLiquidGlass) {
        LiquidTabStrip(
            selectedTabIndex = selectedIndex,
            onTabSelected = ::navigateToItem,
            backdrop = pageBackdrop,
            tabsCount = items.size,
            isDark = isDarkBar,
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = modifier
                .fillMaxWidth()
                .height(BottomNavigationBarHeight),
        ) {
            items.forEachIndexed { index, item ->
                BottomNavigationTab(
                    item = item,
                    selected = index == selectedIndex,
                    isDark = isDarkBar,
                    onClick = { navigateToItem(index) },
                )
            }
        }
    } else {
        BottomNavigationFallback(
            items = items,
            selectedIndex = selectedIndex,
            onTabSelected = ::navigateToItem,
            modifier = modifier
                .fillMaxWidth(),
        )
    }
}

@Composable
internal fun LiquidTabStrip(
    selectedTabIndex: Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    isDark: Boolean,
    accentColor: Color,
    barHeight: Dp = BottomNavigationBarHeight,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val safeTabCount = tabsCount.coerceAtLeast(1)
    val uiScale = LocalLiquidGlassUiScale.current
    val glass = LiquidGlassDefaults.bottomNavigation
    fun tintedAlpha(base: Float) = (base * uiScale.tintStrength).coerceIn(0f, 1f)
    val frost = liquidGlassFrost(
        isDarkTheme = isDark,
        backgroundAlpha = tintedAlpha(glass.tint.background(isDark)),
        highlightAlpha = tintedAlpha(glass.tint.highlight(isDark)),
    )
    val selectedSurfaceColor = (if (isDark) glass.selectedSurfaceColorDark else glass.selectedSurfaceColorLight)
        .copy(
            alpha = tintedAlpha(
                if (isDark) glass.selectedSurfaceAlphaDark else glass.selectedSurfaceAlphaLight,
            ),
        )
    val mainBlur = glass.mainLiquid.blur.scaledBy(uiScale.blurStrength)
    val mainLensHeight = glass.mainLiquid.lensHeight.scaledBy(uiScale.lensHeightStrength)
    val mainLensAmount = glass.mainLiquid.lensAmount.scaledBy(uiScale.lensAmountStrength)
    val pressedBlur = glass.pressedLiquid.blur.scaledBy(uiScale.blurStrength)
    val pressedLensHeight = glass.pressedLiquid.lensHeight.scaledBy(uiScale.lensHeightStrength)
    val pressedLensAmount = glass.pressedLiquid.lensAmount.scaledBy(uiScale.lensAmountStrength)
    val selectedLensHeight = glass.selectedLiquid.lensHeight.scaledBy(uiScale.lensHeightStrength)
    val selectedLensAmount = glass.selectedLiquid.lensAmount.scaledBy(uiScale.lensAmountStrength)
    val tabsBackdrop = rememberLayerBackdrop()
    val latestOnTabSelected by rememberUpdatedState(onTabSelected)

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            ((constraints.maxWidth.toFloat() - 8.dp.toPx()) / safeTabCount).coerceAtLeast(1f)
        }
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density, constraints.maxWidth) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth.coerceAtLeast(1))
                    .fastCoerceIn(-1f, 1f)
                with(density) {
                    4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()

        val dragAnimation = remember(animationScope, safeTabCount, constraints.maxWidth, isLtr) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex.toFloat(),
                valueRange = 0f..(safeTabCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, safeTabCount - 1)
                    animateToValue(targetIndex.toFloat())
                    latestOnTabSelected(targetIndex)
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (safeTabCount - 1).toFloat()),
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                },
            )
        }

        LaunchedEffect(selectedTabIndex, dragAnimation) {
            val target = selectedTabIndex.toFloat()
            if (dragAnimation.value != target || dragAnimation.targetValue != target) {
                dragAnimation.animateToValue(target)
            }
        }

        val interactiveHighlight = remember(animationScope, dragAnimation, tabWidth, isLtr) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) {
                            (dragAnimation.value + 0.5f) * tabWidth + panelOffset
                        } else {
                            size.width - (dragAnimation.value + 0.5f) * tabWidth + panelOffset
                        },
                        size.height / 2f,
                    )
                },
            )
        }

        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(with(density) { mainBlur.toPx() })
                        lens(
                            refractionHeight = with(density) { mainLensHeight.toPx() },
                            refractionAmount = with(density) { mainLensAmount.toPx() },
                        )
                    },
                    layerBlock = {
                        val progress = dragAnimation.pressProgress
                        if (size.width > 0f) {
                            val scale = lerp(1f, 1f + 16.dp.toPx() / size.width, progress)
                            scaleX = scale
                            scaleY = scale
                        }
                    },
                    onDrawSurface = {
                        drawRect(frost.base)
                        drawRect(Brush.verticalGradient(listOf(frost.top, frost.bottom)))
                    },
                )
                .then(interactiveHighlight.modifier)
                .height(barHeight)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )

        CompositionLocalProvider(
            LocalLiquidTabScale provides {
                lerp(1f, 1.2f, dragAnimation.pressProgress)
            },
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics { }
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            val progress = dragAnimation.pressProgress
                            vibrancy()
                            blur(with(density) { pressedBlur.toPx() })
                            lens(
                                refractionHeight = with(density) { pressedLensHeight.toPx() } * progress,
                                refractionAmount = with(density) { pressedLensAmount.toPx() } * progress,
                            )
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = dragAnimation.pressProgress)
                        },
                        onDrawSurface = {
                            drawRect(frost.base)
                            drawRect(Brush.verticalGradient(listOf(frost.top, frost.bottom)))
                        },
                    )
                    .then(interactiveHighlight.modifier)
                    .height(barHeight - 8.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .graphicsLayer { colorFilter = ColorFilter.tint(accentColor) },
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }

        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX = if (isLtr) {
                        dragAnimation.value * tabWidth + panelOffset
                    } else {
                        size.width - (dragAnimation.value + 1f) * tabWidth + panelOffset
                    }
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val progress = dragAnimation.pressProgress
                        lens(
                            refractionHeight = with(density) { selectedLensHeight.toPx() } * progress,
                            refractionAmount = with(density) { selectedLensAmount.toPx() } * progress,
                            chromaticAberration = true,
                        )
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = dragAnimation.pressProgress)
                    },
                    shadow = {
                        BackdropShadow(alpha = dragAnimation.pressProgress)
                    },
                    innerShadow = {
                        val progress = dragAnimation.pressProgress
                        InnerShadow(
                            radius = 8.dp * progress,
                            alpha = progress,
                        )
                    },
                    layerBlock = {
                        scaleX = dragAnimation.scaleX
                        scaleY = dragAnimation.scaleY
                        val velocity = dragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dragAnimation.pressProgress
                        drawRect(selectedSurfaceColor, alpha = 1f - progress)
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    },
                )
                .height(barHeight - 8.dp)
                .fillMaxWidth(1f / safeTabCount),
        )
    }
}

@Composable
private fun RowScope.BottomNavigationTab(
    item: BottomNavItem,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (isDark) Color.White else Color.Black
    LiquidBottomTab(onClick = onClick) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = contentColor.copy(alpha = if (selected) 0.96f else 0.62f),
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = stringResource(id = item.titleResId),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor.copy(alpha = if (selected) 0.96f else 0.62f),
            letterSpacing = if (selected) 0.2.sp else 0.1.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RowScope.LiquidBottomTab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scale = LocalLiquidTabScale.current
    Column(
        modifier
            .clip(Capsule())
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val tabScale = scale()
                scaleX = tabScale
                scaleY = tabScale
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun BottomNavigationFallback(
    items: List<BottomNavItem>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp),
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainer,
        contentColor = scheme.onSurface,
        shadowElevation = 1.dp,
    ) {
        NavigationBar(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0),
        ) {
            items.forEachIndexed { index, item ->
                NavigationBarItem(
                    selected = index == selectedIndex,
                    onClick = { onTabSelected(index) },
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(id = item.titleResId),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = scheme.onSecondaryContainer,
                        selectedTextColor = scheme.onSurface,
                        indicatorColor = scheme.secondaryContainer,
                        unselectedIconColor = scheme.onSurfaceVariant,
                        unselectedTextColor = scheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

@Composable
fun MiniPlayer(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    songTitle: String = "",
    artist: String = "",
    coverUrl: String? = null,
    progress: Float = 0f,
    onPlayPauseClick: () -> Unit = {},
    onPlayerClick: () -> Unit = {},
    onPlaylistClick: () -> Unit = {}
) {
    val playerInteractionSource = remember {
        androidx.compose.foundation.interaction.MutableInteractionSource()
    }
    val isPressed by playerInteractionSource.collectIsPressedAsState()
    val scaleValue by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    // 脉冲动画
    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val colorScheme = MaterialTheme.colorScheme
    val useLiquidStyle = isLiquidGlassEffectActive(LocalLiquidLayerBackdrop.current)
    val isDarkTheme = isAppDarkTheme()
    val titleColor = if (useLiquidStyle) {
        if (isDarkTheme) Color.White.copy(alpha = 0.98f) else colorScheme.onSurface
    } else {
        colorScheme.onSurface
    }
    val subtitleColor = if (useLiquidStyle) {
        if (isDarkTheme) Color.White.copy(alpha = 0.65f) else colorScheme.onSurfaceVariant
    } else {
        colorScheme.onSurfaceVariant
    }
    val progressTrackColor = if (useLiquidStyle) {
        if (isDarkTheme) Color.White.copy(alpha = 0.15f) else colorScheme.onSurface.copy(alpha = 0.12f)
    } else {
        colorScheme.surfaceVariant
    }
    val progressActiveColor = if (useLiquidStyle) {
        if (isDarkTheme) Color.White.copy(alpha = 0.9f) else colorScheme.primary
    } else {
        colorScheme.primary
    }
    val playButtonBg = if (useLiquidStyle) {
        if (isDarkTheme) Color.White.copy(alpha = 0.95f) else colorScheme.primary
    } else {
        colorScheme.primary
    }
    val playButtonIconTint = if (useLiquidStyle) {
        if (isDarkTheme) Color(0xFF1A1A2E) else colorScheme.onPrimary
    } else {
        colorScheme.onPrimary
    }
    val playlistButtonBg = if (useLiquidStyle) {
        if (isDarkTheme) Color.White.copy(alpha = 0.12f) else colorScheme.onSurface.copy(alpha = 0.08f)
    } else {
        colorScheme.secondaryContainer
    }
    val playlistButtonBorder = if (useLiquidStyle) {
        if (isDarkTheme) Color.White.copy(alpha = 0.18f) else colorScheme.outline.copy(alpha = 0.35f)
    } else {
        Color.Transparent
    }
    val playlistIconTint = if (useLiquidStyle) {
        if (isDarkTheme) Color.White.copy(alpha = 0.85f) else colorScheme.onSurface.copy(alpha = 0.88f)
    } else {
        colorScheme.onSecondaryContainer
    }
    val coverPlaceholderColor = if (useLiquidStyle) {
        Color(0xFF1A1A2E).copy(alpha = 0.4f)
    } else {
        colorScheme.surfaceContainerHighest
    }

    val miniTint = LiquidGlassDefaults.miniPlayerBar
    val miniLiq = LiquidGlassDefaults.liquidSoft
    val miniShape = if (useLiquidStyle) RoundedCornerShape(34.dp) else RoundedCornerShape(16.dp)
    val coverShape = if (useLiquidStyle) RoundedCornerShape(14.dp) else RoundedCornerShape(16.dp)
    val coverSize = if (useLiquidStyle) 48.dp else 44.dp
    val contentSpacing = if (useLiquidStyle) 14.dp else 12.dp
    val playButtonSize = if (useLiquidStyle) 36.dp else 40.dp
    val playIconSize = if (useLiquidStyle) 18.dp else 20.dp
    val playerClickModifier = if (useLiquidStyle) {
        Modifier.clickable(
            interactionSource = playerInteractionSource,
            indication = null,
            onClick = onPlayerClick,
        )
    } else {
        Modifier.clickable(role = Role.Button, onClick = onPlayerClick)
    }
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(if (useLiquidStyle) 68.dp else 64.dp),
        // 液态路径保持胶囊；Material 3 回退使用 M3 large 圆角与高一级的 tonal 容器。
        shape = miniShape,
        material3Shape = RoundedCornerShape(16.dp),
        material3Container = colorScheme.surfaceContainerHigh,
        backgroundAlpha = miniTint.backgroundAlpha,
        borderAlpha = miniTint.borderAlpha,
        highlightAlpha = miniTint.highlightAlpha,
        liquidBlur = miniLiq.blur,
        liquidLensHeight = miniLiq.lensHeight,
        liquidLensAmount = miniLiq.lensAmount,
        material3Elevation = if (useLiquidStyle) 0.dp else 1.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(playerClickModifier)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = 16.dp,
                        vertical = if (useLiquidStyle) 10.dp else 8.dp,
                    )
                    .then(if (useLiquidStyle) Modifier.scale(scaleValue) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左侧：封面、歌曲信息
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(contentSpacing),
                    modifier = Modifier.weight(1f)
                ) {
                    // 封面
                    Box(
                        modifier = Modifier
                            .size(coverSize)
                            .clip(coverShape)
                            .background(coverPlaceholderColor)
                            .then(
                                if (isPlaying && useLiquidStyle) {
                                    Modifier.scale(pulseScale)
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!coverUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = stringResource(id = R.string.content_description_cover),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Image(
                                painter = painterResource(R.drawable.music),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // 歌曲信息
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = songTitle.ifEmpty { stringResource(id = R.string.player_no_music) },
                            fontSize = if (useLiquidStyle) 15.sp else 14.sp,
                            fontWeight = if (useLiquidStyle) FontWeight.Bold else FontWeight.SemiBold,
                            color = titleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = if (useLiquidStyle) {
                                androidx.compose.ui.text.TextStyle(
                                    shadow = if (isDarkTheme) {
                                        TextShadow(
                                            color = Color.Black.copy(alpha = 0.5f),
                                            offset = Offset(0f, 1f),
                                            blurRadius = 4f
                                        )
                                    } else {
                                        TextShadow(
                                            color = Color.Black.copy(alpha = 0.08f),
                                            offset = Offset(0f, 1f),
                                            blurRadius = 2f
                                        )
                                    }
                                )
                            } else {
                                MaterialTheme.typography.titleSmall
                            }
                        )
                        Text(
                            text = artist.ifEmpty { stringResource(id = R.string.player_click_to_play) },
                            fontSize = 12.sp,
                            color = subtitleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = if (useLiquidStyle) {
                                androidx.compose.ui.text.TextStyle(
                                    shadow = if (isDarkTheme) {
                                        TextShadow(
                                            color = Color.Black.copy(alpha = 0.4f),
                                            offset = Offset(0f, 1f),
                                            blurRadius = 3f
                                        )
                                    } else {
                                        TextShadow(
                                            color = Color.Black.copy(alpha = 0.06f),
                                            offset = Offset(0f, 1f),
                                            blurRadius = 2f
                                        )
                                    }
                                )
                            } else {
                                MaterialTheme.typography.bodySmall
                            }
                        )
                    }
                }

                // 右侧：播放/暂停按钮、播放列表
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 播放/暂停按钮（带圆形进度条）
                    Box(
                        modifier = Modifier.size(46.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (useLiquidStyle) {
                            // 液态路径保留播放键外圈的圆形进度。
                            Canvas(
                                modifier = Modifier.size(46.dp)
                            ) {
                                val strokeWidth = 3.dp.toPx()
                                val radius = size.minDimension / 2 - strokeWidth / 2 - 2.dp.toPx()
                                val center = Offset(size.width / 2, size.height / 2)

                                drawCircle(
                                    color = progressTrackColor,
                                    radius = radius,
                                    center = center,
                                    style = Stroke(
                                        width = strokeWidth,
                                        cap = StrokeCap.Round
                                    )
                                )

                                if (progress > 0f) {
                                    drawArc(
                                        color = progressActiveColor,
                                        startAngle = -90f,
                                        sweepAngle = 360f * progress,
                                        useCenter = false,
                                        style = Stroke(
                                            width = strokeWidth,
                                            cap = StrokeCap.Round
                                        ),
                                        size = Size(radius * 2, radius * 2),
                                        topLeft = Offset(center.x - radius, center.y - radius)
                                    )
                                }
                            }
                        }

                        // 播放/暂停按钮
                        Box(
                            modifier = Modifier
                                .size(playButtonSize)
                                .clip(CircleShape)
                                .background(playButtonBg)
                                .clickable(onClick = onPlayPauseClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (isPlaying) R.drawable.pause else R.drawable.play
                                ),
                                contentDescription = if (isPlaying) stringResource(id = R.string.content_description_pause) else stringResource(id = R.string.content_description_play),
                                tint = playButtonIconTint,
                                modifier = Modifier.size(playIconSize)
                            )
                        }
                    }

                    // 播放列表按钮
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(playlistButtonBg)
                            .then(
                                if (useLiquidStyle) {
                                    Modifier.border(
                                        width = 0.5.dp,
                                        color = playlistButtonBorder,
                                        shape = CircleShape,
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clickable(onClick = onPlaylistClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.playlist),
                            contentDescription = stringResource(id = R.string.content_description_playlist),
                            tint = playlistIconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Material 3 迷你播放器使用贴底的线性进度，不再用播放键外圈的装饰环。
            if (!useLiquidStyle) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(colorScheme.surfaceVariant),
                ) {
                    if (progress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(colorScheme.primary),
                        )
                    }
                }
            }
        }
    }
}
