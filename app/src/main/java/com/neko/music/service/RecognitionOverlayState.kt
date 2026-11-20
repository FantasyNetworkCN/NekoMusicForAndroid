package com.neko.music.service

import com.neko.music.data.api.MusicApi

internal enum class RecognitionOverlayMode { Bubble, Idle, Panel, Result, Message }

/** 收起只改变展示方式；结果不会因停止、隐藏或再次展开被清空。 */
internal class RecognitionOverlayState {
    var mode = RecognitionOverlayMode.Bubble
        private set
    var expandedMode = RecognitionOverlayMode.Idle
        private set
    var lastMatch: MusicApi.RecognitionMatch? = null
        private set
    var recording = false
        private set

    fun start() {
        recording = true
        expandedMode = RecognitionOverlayMode.Panel
        collapse()
    }

    fun stop() {
        recording = false
        expandedMode = if (lastMatch == null) RecognitionOverlayMode.Idle else RecognitionOverlayMode.Result
        collapse()
    }

    fun match(value: MusicApi.RecognitionMatch) {
        lastMatch = value
        recording = false
        expandedMode = RecognitionOverlayMode.Result
        mode = RecognitionOverlayMode.Result
    }

    fun message() {
        recording = false
        expandedMode = RecognitionOverlayMode.Message
        mode = RecognitionOverlayMode.Message
    }

    fun collapse() { mode = RecognitionOverlayMode.Bubble }
    fun expand() { mode = expandedMode }
}
