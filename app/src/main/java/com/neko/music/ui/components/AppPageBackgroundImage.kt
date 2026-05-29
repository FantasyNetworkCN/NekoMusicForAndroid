package com.neko.music.ui.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.neko.music.data.manager.AppBackgroundKind
import com.neko.music.data.manager.AppPageBackgroundStore

@Composable
fun AppPageBackgroundImage(
  kind: AppBackgroundKind = AppBackgroundKind.SubPage,
  modifier: Modifier = Modifier,
  contentScale: ContentScale = ContentScale.Crop,
  contentDescription: String? = null,
) {
  val context = LocalContext.current
  var revision by remember { mutableIntStateOf(0) }

  DisposableEffect(context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
      if (AppPageBackgroundStore.isBackgroundPrefKey(key)) {
        revision++
      }
    }
    prefs.registerOnSharedPreferenceChangeListener(listener)
    onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
  }

  val customFile = remember(revision, kind) {
    if (AppPageBackgroundStore.hasCustom(context, kind)) {
      AppPageBackgroundStore.customFile(context, kind)
    } else {
      null
    }
  }

  if (customFile != null) {
    AsyncImage(
      model = ImageRequest.Builder(context)
        .data(customFile)
        .memoryCacheKey("page_bg_${kind.name}_${customFile.lastModified()}")
        .diskCacheKey("page_bg_${kind.name}_${customFile.lastModified()}")
        .crossfade(true)
        .build(),
      contentDescription = contentDescription,
      modifier = modifier,
      contentScale = contentScale,
    )
  } else {
    Image(
      painter = painterResource(kind.defaultResId),
      contentDescription = contentDescription,
      modifier = modifier,
      contentScale = contentScale,
    )
  }
}

private const val PREFS_NAME = "app_settings"
