package com.neko.music.ui.screens

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.neko.music.R
import com.neko.music.data.manager.RecognitionHistoryEntry
import com.neko.music.data.model.Music
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.icons.NekoIcon
import com.neko.music.ui.icons.NekoSymbol
import com.neko.music.ui.icons.NekoSymbolIcon
import com.neko.music.ui.theme.isAppDarkTheme
import com.neko.music.util.recognitionCoverUrl

@Composable
internal fun RecognitionGlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val tint = LiquidGlassDefaults.screenListCard
    val dark = isAppDarkTheme()
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        backgroundAlpha = tint.background(dark),
        borderAlpha = tint.border(dark),
        highlightAlpha = tint.highlight(dark),
        material3Container = MaterialTheme.colorScheme.surfaceContainerLow,
        content = content,
    )
}

@Composable
internal fun RecognitionHistoryPanel(
    entries: List<RecognitionHistoryEntry>,
    onBack: () -> Unit,
    onMusicClick: (Music) -> Unit,
) {
    BackHandler(onBack = onBack)
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(scheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                NekoIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
            }
            Text(stringResource(R.string.recognition_history_title), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        if (entries.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                NekoSymbolIcon(NekoSymbol.Clock, null, Modifier.size(42.dp), scheme.primary)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.recognition_history_empty), color = scheme.onSurfaceVariant)
            }
        } else {
            Box(Modifier.align(Alignment.CenterHorizontally).widthIn(max = 720.dp)
                .fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
            RecognitionGlassCard {
            LazyColumn(
                Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)),
                contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp),
            ) {
                item {
                    Text(stringResource(R.string.recognition_history_hint), Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        color = scheme.onSurfaceVariant, fontSize = 12.sp)
                }
                items(entries, key = { it.music.id }) { entry ->
                        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { onMusicClick(entry.music) }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = recognitionCoverUrl(entry.music),
                                contentDescription = null,
                                placeholder = painterResource(R.drawable.music),
                                error = painterResource(R.drawable.music),
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(entry.music.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(entry.music.artist, color = scheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    DateUtils.getRelativeTimeSpanString(entry.recognizedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                                    color = scheme.onSurfaceVariant, fontSize = 11.sp,
                                )
                            }
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                NekoSymbolIcon(NekoSymbol.Play, null, Modifier.size(22.dp), scheme.primary)
                            }
                        }
                    HorizontalDivider(Modifier.padding(start = 82.dp, end = 14.dp), color = scheme.outlineVariant.copy(alpha = .4f))
                }
            }
            }
            }
        }
    }
}
