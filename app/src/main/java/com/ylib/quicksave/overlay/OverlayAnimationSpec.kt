package com.ylib.quicksave.overlay

/** 悬浮把手按压/长按蓄力动画参数。把手视觉宽度不得超过触控窗宽（5dp×5=25dp），
 * 否则超出部分会被 WRAP_CONTENT 窗口边界裁剪（表现为"把手只剩一半"）。 */
internal object OverlayAnimationSpec {
    const val PRESS_SCALE = 3f
    const val RELEASE_SCALE = 1f
    const val PRESS_DURATION_MS = 120L
    const val RELEASE_DURATION_MS = 180L

    /** 长按蓄力态：把手放大到该倍数（恰好填满触控窗）表示拖拽已解锁。 */
    const val HOLD_SCALE = 5f
    const val HOLD_DURATION_MS = 150L
}
