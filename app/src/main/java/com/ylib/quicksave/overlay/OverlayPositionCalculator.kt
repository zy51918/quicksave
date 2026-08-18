package com.ylib.quicksave.overlay

/** 悬浮窗位置相关的纯计算，无 Android 依赖，便于单测。 */
object OverlayPositionCalculator {

    /** 根据视图中心 X 与屏幕宽度返回最近的边；正中点归到 RIGHT。 */
    fun nearestEdge(centerX: Int, screenWidth: Int): OverlayEdge =
        if (centerX < screenWidth / 2) OverlayEdge.LEFT else OverlayEdge.RIGHT

    fun clampX(left: Int, viewWidth: Int, screenWidth: Int): Int {
        val max = (screenWidth - viewWidth).coerceAtLeast(0)
        return left.coerceIn(0, max)
    }

    fun windowLeftForPointer(
        rawX: Int,
        touchOffsetX: Int,
        screenWidth: Int,
        viewWidth: Int
    ): Int = clampX(rawX - touchOffsetX, viewWidth, screenWidth)

    fun edgeWindowLeft(edge: OverlayEdge, screenWidth: Int, viewWidth: Int): Int =
        if (edge == OverlayEdge.LEFT) 0 else (screenWidth - viewWidth).coerceAtLeast(0)

    /** 将顶部 Y 钳制到 [0, screenHeight - viewHeight]，避免拖出屏幕。 */
    fun clampY(y: Int, viewHeight: Int, screenHeight: Int): Int {
        val max = (screenHeight - viewHeight).coerceAtLeast(0)
        return y.coerceIn(0, max)
    }

    /** 像素 Y → 屏高比例，结果钳制到 [0f, 1f]。 */
    fun yToRatio(y: Int, screenHeight: Int): Float {
        if (screenHeight <= 0) return 0f
        return (y.toFloat() / screenHeight).coerceIn(0f, 1f)
    }

    /** 屏高比例 → 像素 Y。 */
    fun ratioToY(ratio: Float, screenHeight: Int): Int {
        if (screenHeight <= 0) return 0
        return (ratio.coerceIn(0f, 1f) * screenHeight).toInt()
    }
}
