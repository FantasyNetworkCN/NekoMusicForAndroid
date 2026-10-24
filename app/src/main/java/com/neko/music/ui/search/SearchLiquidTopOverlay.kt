package com.neko.music.ui.search

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.shapes.Capsule
import com.neko.music.R
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LiquidTabStrip
import com.neko.music.ui.components.LocalLiquidGlassHardwareEffectsEnabled
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.components.LocalLiquidTabScale
import com.neko.music.ui.theme.RoseRed
import com.neko.music.ui.theme.isAppDarkTheme

@Composable
fun SearchLiquidTopOverlay(
    state: SearchLiquidBarState,
    onBackClick: () -> Unit,
    onBarHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** 与页内 [androidx.compose.ui.Modifier.layerBackdrop] 同一实例；与首页顶栏一致，避免仅依赖 Local 时取错。 */
    sampleBackdrop: LayerBackdrop? = null
) {
    val backdropForGlass = sampleBackdrop ?: LocalLiquidLayerBackdrop.current
    val scheme = MaterialTheme.colorScheme
    val isDark = isAppDarkTheme()
    val density = LocalDensity.current
    val extraTopGap = remember(density) {
        with(density) { (5f / this.density).dp }
    }
    val singleText = stringResource(id = R.string.single)
    val playlistText = stringResource(id = R.string.playlist)
    val artistText = stringResource(id = R.string.artist)
    val searchText = stringResource(id = R.string.search)
    val searchMusicText = stringResource(id = R.string.search_music)
    val clearQueryText = stringResource(id = R.string.search_clear_query)
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    val hardwareLiquid = LocalLiquidGlassHardwareEffectsEnabled.current
    val typeOptions = listOf(
        "music" to singleText,
        "playlist" to playlistText,
        "artist" to artistText,
    )
    val selectedTypeIndex = typeOptions.indexOfFirst { it.first == state.searchType }.coerceAtLeast(0)
    val onTypeSelected: (String) -> Unit = { type ->
        if (state.searchType != type) {
            state.searchType = type
            @Suppress("DEPRECATION")
            val haptic = if (Build.VERSION.SDK_INT >= 30) {
                HapticFeedbackConstants.SEGMENT_TICK
            } else {
                HapticFeedbackConstants.CONTEXT_CLICK
            }
            view.performHapticFeedback(haptic)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = extraTopGap + 4.dp)
            .statusBarsPadding()
            .onSizeChanged { onBarHeightChanged(it.height) }
    ) {
        val searchFieldGlass = LiquidGlassDefaults.searchTopSearchField
        val iconButtonGlass = LiquidGlassDefaults.authIconButton
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassSurface(
                modifier = Modifier
                    .size(44.dp),
                shape = CircleShape,
                sampleBackdrop = backdropForGlass,
                backgroundAlpha = iconButtonGlass.tint.background(isDark),
                borderAlpha = iconButtonGlass.tint.border(isDark),
                highlightAlpha = iconButtonGlass.tint.highlight(isDark),
                borderColor = if (isDark) Color.White.copy(alpha = 0.16f) else scheme.outline,
                liquidBlur = iconButtonGlass.liquid.blur,
                liquidLensHeight = iconButtonGlass.liquid.lensHeight,
                liquidLensAmount = iconButtonGlass.liquid.lensAmount,
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(id = R.string.back),
                        tint = if (isDark) Color.White.copy(alpha = 0.92f) else scheme.onSurface,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }

            GlassSurface(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(20.dp),
                sampleBackdrop = backdropForGlass,
                backgroundAlpha = searchFieldGlass.tint.background(isDark),
                borderAlpha = searchFieldGlass.tint.border(isDark),
                highlightAlpha = searchFieldGlass.tint.highlight(isDark),
                borderColor = if (isDark) Color.White else scheme.outline,
                liquidBlur = searchFieldGlass.liquid.blur,
                liquidLensHeight = searchFieldGlass.liquid.lensHeight,
                liquidLensAmount = searchFieldGlass.liquid.lensAmount,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = searchText,
                        tint = if (isDark) Color.White.copy(alpha = 0.72f) else scheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (state.searchQuery.isEmpty()) {
                            Text(
                                text = searchMusicText,
                                fontSize = 15.sp,
                                color = if (isDark) Color.White.copy(alpha = 0.55f) else scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BasicTextField(
                            value = state.searchQuery,
                            onValueChange = { state.searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(
                                color = if (isDark) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface,
                                fontSize = 15.sp,
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(RoseRed),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    state.onImeSearchAction()
                                    focusManager.clearFocus()
                                },
                            ),
                        )
                    }
                    AnimatedVisibility(
                        visible = state.searchQuery.isNotEmpty(),
                        enter = fadeIn() + scaleIn(initialScale = 0.8f),
                        exit = fadeOut() + scaleOut(targetScale = 0.8f),
                    ) {
                        IconButton(
                            onClick = { state.searchQuery = "" },
                            modifier = Modifier.size(38.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = clearQueryText,
                                tint = if (isDark) Color.White.copy(alpha = 0.66f) else scheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }

        val typeRowModifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .height(48.dp)
        if (hardwareLiquid && backdropForGlass != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            LiquidTabStrip(
                selectedTabIndex = selectedTypeIndex,
                onTabSelected = { index ->
                    onTypeSelected(typeOptions[index.coerceIn(0, typeOptions.lastIndex)].first)
                },
                backdrop = backdropForGlass,
                tabsCount = typeOptions.size,
                isDark = isDark,
                accentColor = scheme.primary,
                barHeight = 48.dp,
                modifier = typeRowModifier,
            ) {
                typeOptions.forEach { (type, label) ->
                    SearchTypeTab(
                        label = label,
                        selected = state.searchType == type,
                        isDark = isDark,
                        onClick = { onTypeSelected(type) },
                    )
                }
            }
        } else {
            val typeRowGlass = LiquidGlassDefaults.searchTopTypeRow
            GlassSurface(
                modifier = typeRowModifier,
                shape = RoundedCornerShape(24.dp),
                sampleBackdrop = backdropForGlass,
                backgroundAlpha = typeRowGlass.tint.background(isDark),
                borderAlpha = typeRowGlass.tint.border(isDark),
                highlightAlpha = typeRowGlass.tint.highlight(isDark),
                borderColor = if (isDark) Color.White else scheme.outline,
                liquidBlur = typeRowGlass.liquid.blur,
                liquidLensHeight = typeRowGlass.liquid.lensHeight,
                liquidLensAmount = typeRowGlass.liquid.lensAmount,
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                ) {
                    val segmentWidth = maxWidth / typeOptions.size
                    val indicatorOffset by animateDpAsState(
                        targetValue = segmentWidth * selectedTypeIndex,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        label = "searchTypeIndicator",
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = indicatorOffset)
                            .width(segmentWidth)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(
                                if (isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.82f),
                            )
                            .border(
                                width = 0.5.dp,
                                color = if (isDark) Color.White.copy(alpha = 0.14f) else scheme.outline.copy(alpha = 0.16f),
                                shape = CircleShape,
                            ),
                    )
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        typeOptions.forEach { (type, label) ->
                            SearchTypeTab(
                                label = label,
                                selected = state.searchType == type,
                                isDark = isDark,
                                onClick = { onTypeSelected(type) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.SearchTypeTab(
    label: String,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val textColor by animateColorAsState(
        targetValue = when {
            selected && isDark -> Color.White.copy(alpha = 0.96f)
            selected -> Color.Black.copy(alpha = 0.86f)
            isDark -> Color(0xFFB8B8D1).copy(alpha = 0.76f)
            else -> scheme.onSurfaceVariant.copy(alpha = 0.76f)
        },
        animationSpec = androidx.compose.animation.core.tween(180),
        label = "searchTypeText",
    )
    val tabScale = LocalLiquidTabScale.current

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(Capsule())
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.graphicsLayer {
                val scale = tabScale()
                scaleX = scale
                scaleY = scale
            },
        )
    }
}
