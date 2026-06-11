package com.ylib.quicksave.overlay

/** 悬浮窗贴靠的屏幕边缘。 */
enum class OverlayEdge {
    LEFT,
    RIGHT;

    companion object {
        /** 从持久化字符串还原，无法识别时回退到 RIGHT。 */
        fun fromStorage(value: String?): OverlayEdge =
            entries.firstOrNull { it.name == value } ?: RIGHT
    }
}
