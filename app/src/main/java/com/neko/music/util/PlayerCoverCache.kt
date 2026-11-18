package com.neko.music.util

internal const val PlayerCoverPrefetchSizePx = 1_600

internal fun playerCoverMemoryKey(url: String): String = "neko-player-cover:$url"
