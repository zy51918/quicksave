package com.ylib.quicksave.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 间距规范（对应 docs/UI.md §2.3）
 *
 * 集中所有页面共用的尺寸常量，替代散落在各 Composable 中的硬编码魔数，
 * 让主页 / 设置页 / 透明输入窗的视觉节奏保持一致。
 */
object Dim {
    /** 页面水平边距 */
    val screenHorizontal = 20.dp

    /** 组件之间的常规垂直间距 */
    val itemSpacing = 12.dp

    /** 卡片内边距 */
    val cardPadding = 16.dp

    /** Chip 行横向间距 */
    val chipSpacing = 6.dp

    /** 页面首/尾额外留白 */
    val screenVertical = 20.dp

    /** 设置页分区之间的间距 */
    val sectionSpacing = 32.dp

    /** 标签与内容的间距（如「分类（可选）」与 Chip 行） */
    val labelToContent = 8.dp

    /** 卡片内 label 与正文之间的间距 */
    val cardLabelToBody = 8.dp

    /** 卡片内正文与底部按钮的间距 */
    val cardBodyToAction = 16.dp
}

/**
 * 圆角规范（对应 docs/UI.md §2.3）
 *
 * Card 统一 12dp 圆角，显式指定以避免依赖 Material 默认值带来的版本漂移。
 */
val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)
