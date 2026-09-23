package com.ylib.quicksave.ui.theme

import androidx.compose.ui.graphics.Color

// QuickSave palette: cool paper, ink, a soft lime signal colour, and a small coral warning note.
//
// Lime (#C5E166) is a very light colour — white text on it only reaches 1.46:1, far below the
// 4.5:1 needed for body copy. So it is only ever used as a *fill*; anything drawn on top of it
// takes LimeInk, and anything drawn on paper/surface uses LimeDark instead. The three came from
// the same hue/saturation ramp, so they stay in tune with the primary.
val Ink = Color(0xFF102A36)
val InkSoft = Color(0xFF45616A)
val Paper = Color(0xFFF4F7F7)
val SurfaceWhite = Color(0xFFFFFFFF)
val Mist = Color(0xFFE7EFF0)

/** 品牌主色（浅绿），仅作填充：按钮底、选中 Chip 底、强调图形。 */
val Lime = Color(0xFFC5E166)

/** 主色填充之上的文字/图标（8.6:1）。 */
val LimeInk = Color(0xFF2D370B)

/** 浅色底上的主色文字/描边，以及深色主题里的容器底（7.6:1 on Paper）。 */
val LimeDark = Color(0xFF465511)

/** 浅色容器底（剪切板卡背景、选中 Chip 底）。 */
val LimePale = Color(0xFFEDF6D0)

val Coral = Color(0xFFB94D3D)
val CoralPale = Color(0xFFFFDAD3)
val Night = Color(0xFF0D1C22)
val NightSurface = Color(0xFF172B33)
val NightVariant = Color(0xFF29434B)