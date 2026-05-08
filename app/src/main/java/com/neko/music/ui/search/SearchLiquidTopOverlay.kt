package com.neko.music.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neko.music.R
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.screens.SearchTypeButton
import com.neko.music.ui.theme.RoseRed

@Composable
fun SearchLiquidTopOverlay(
    state: SearchLiquidBarState,
    onBackClick: () -> Unit,
    onBarHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    val density = LocalDensity.current
    val extraTopGap = remember(density) {
        with(density) { (5f / this.density).dp }
    }
    val singleText = stringResource(id = R.string.single)
    val playlistText = stringResource(id = R.string.playlist)
    val artistText = stringResource(id = R.string.artist)
    val searchText = stringResource(id = R.string.search)
    val searchMusicText = stringResource(id = R.string.search_music)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = extraTopGap)
            .statusBarsPadding()
            .onSizeChanged { onBarHeightChanged(it.height) }
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .height(52.dp),
            shape = RoundedCornerShape(22.dp),
            backgroundAlpha = if (isDark) 0.35f else 0.30f,
            borderAlpha = if (isDark) 0.18f else 0.20f,
            highlightAlpha = if (isDark) 0.08f else 0.10f,
            borderColor = if (isDark) Color.White else scheme.outline,
            liquidBlur = 8.dp,
            liquidLensHeight = 16.dp,
            liquidLensAmount = 26.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(start = 4.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(id = R.string.back),
                        tint = if (isDark) Color.White.copy(alpha = 0.92f) else scheme.onSurface
                    )
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = searchText,
                        tint = if (isDark) Color.White.copy(alpha = 0.72f) else scheme.onSurfaceVariant,
                        modifier = Modifier.width(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (state.searchQuery.isEmpty()) {
                            Text(
                                text = searchMusicText,
                                fontSize = 15.sp,
                                color = if (isDark) Color.White.copy(alpha = 0.55f) else scheme.onSurfaceVariant
                            )
                        }
                        BasicTextField(
                            value = state.searchQuery,
                            onValueChange = { state.searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(
                                color = if (isDark) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface,
                                fontSize = 15.sp
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(RoseRed),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { state.onImeSearchAction() }
                            )
                        )
                    }
                }
            }
        }

        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            backgroundAlpha = if (isDark) 0.32f else 0.28f,
            borderAlpha = if (isDark) 0.15f else 0.20f,
            highlightAlpha = if (isDark) 0.08f else 0.11f,
            borderColor = if (isDark) Color.White else scheme.outline,
            liquidBlur = 10.dp,
            liquidLensHeight = 18.dp,
            liquidLensAmount = 28.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchTypeButton(
                    text = singleText,
                    isSelected = state.searchType == "music",
                    onClick = { state.searchType = "music" }
                )
                SearchTypeButton(
                    text = playlistText,
                    isSelected = state.searchType == "playlist",
                    onClick = { state.searchType = "playlist" }
                )
                SearchTypeButton(
                    text = artistText,
                    isSelected = state.searchType == "artist",
                    onClick = { state.searchType = "artist" }
                )
            }
        }
    }
}
