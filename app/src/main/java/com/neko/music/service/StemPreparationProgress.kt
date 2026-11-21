package com.neko.music.service

internal enum class StemPreparationPhase {
    IDLE, CHECKING_MODEL, DOWNLOADING_MODEL, LOADING_MODEL, READING_TRACK, DOWNLOADING_TRACK, DECODING, SEPARATING, READY,
}

internal data class StemPreparationProgress(
    val phase: StemPreparationPhase,
    val fraction: Float? = null,
    val receivedBytes: Long = 0,
    val totalBytes: Long? = null,
)
