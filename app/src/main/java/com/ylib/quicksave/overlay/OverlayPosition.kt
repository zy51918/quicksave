package com.ylib.quicksave.overlay

/**
 * 悬浮窗位置。
 * @param edge 贴靠的边
 * @param yRatio 把手顶部相对屏幕高度的比例 [0f, 1f]，跨分辨率/旋转更稳健
 */
data class OverlayPosition(
    val edge: OverlayEdge,
    val yRatio: Float
) {
    companion object {
        /** 默认贴右、约屏幕 40% 高度处。 */
        val DEFAULT = OverlayPosition(OverlayEdge.RIGHT, 0.4f)
    }
}
