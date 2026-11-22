package com.neko.music.service

/** 服务端 LAME 裸 MPEG-1 Layer III 分片的解码布局，不包含 Xing gapless 标记。 */
internal class StemMp3ChunkLayout(val audioFrames: Int) {
    val leadingFrames = 1_105
    val audioEnd = leadingFrames + audioFrames
    val encodedFrames = ((audioEnd + 1_151) / 1_152) * 1_152

    init {
        require(audioFrames > 0)
    }

    fun isAudio(position: Int): Boolean = position in leadingFrames until audioEnd

    fun nextBoundary(position: Int): Int = when {
        position < leadingFrames -> leadingFrames
        position < audioEnd -> audioEnd
        else -> encodedFrames
    }
}
