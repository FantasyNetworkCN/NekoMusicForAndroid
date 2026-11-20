package com.neko.music.service

internal data class RecognitionOverlayPlacement(val x: Int, val y: Int, val width: Int, val height: Int)

internal fun interpolateRecognitionOverlay(
    from: RecognitionOverlayPlacement,
    to: RecognitionOverlayPlacement,
    fraction: Float,
): RecognitionOverlayPlacement {
    val progress = fraction.coerceIn(0f, 1f)
    fun mix(start: Int, end: Int) = (start + (end - start) * progress).toInt()
    return RecognitionOverlayPlacement(
        mix(from.x, to.x), mix(from.y, to.y),
        mix(from.width, to.width).coerceAtLeast(1), mix(from.height, to.height).coerceAtLeast(1),
    )
}

/** 所有模式共用安全区域约束，旋转和窄屏下也不会把操作按钮放到屏幕外。 */
internal fun recognitionOverlayPlacement(
    screenWidth: Int,
    screenHeight: Int,
    insetLeft: Int,
    insetTop: Int,
    insetRight: Int,
    insetBottom: Int,
    requestedWidth: Int,
    requestedHeight: Int,
    x: Int,
    y: Int,
    margin: Int,
): RecognitionOverlayPlacement {
    val safeWidth = screenWidth.coerceAtLeast(1)
    val safeHeight = screenHeight.coerceAtLeast(1)
    val left = (insetLeft + margin).coerceIn(0, safeWidth - 1)
    val top = (insetTop + margin).coerceIn(0, safeHeight - 1)
    val right = (safeWidth - insetRight - margin).coerceIn(left + 1, safeWidth)
    val bottom = (safeHeight - insetBottom - margin).coerceIn(top + 1, safeHeight)
    val width = requestedWidth.coerceIn(1, right - left)
    val height = requestedHeight.coerceIn(1, bottom - top)
    return RecognitionOverlayPlacement(x.coerceIn(left, right - width), y.coerceIn(top, bottom - height), width, height)
}
