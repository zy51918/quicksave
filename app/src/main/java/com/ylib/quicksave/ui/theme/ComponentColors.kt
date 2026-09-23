package com.ylib.quicksave.ui.theme

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable

/**
 * 组件前景色辅助（对应 docs/UI.md §2.1 的 accent 规则）。
 *
 * M3 的 [androidx.compose.material3.OutlinedButton]、[androidx.compose.material3.TextButton]、
 * [androidx.compose.material3.OutlinedTextField] 默认拿 `primary` 当前景色用 —— 描边、文字、光标。
 * 但本项目的 `primary` 是品牌浅绿 `#C5E166`，只适合作填充：作前景落在 Paper 上仅 1.36:1，
 * 渲染出来几乎看不见。
 *
 * 这类「主色当文字/描边」的用法统一改走 `brandColors.accent`（浅色主题 `#465511`，
 * 深色主题浅绿），主页与设置页共用这里的函数，避免两个页面的按钮配色再次跑偏。
 *
 * 注意：填充类组件（`Button`、选中态 `FilterChip`）仍用 `primary` + `onPrimary`，不要用这里。
 */
@Composable
fun appOutlinedButtonColors(): ButtonColors =
    ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.brandColors.accent)

/**
 * 危险操作（清空）用的描边按钮配色：取 `error` 色（浅色主题 `#B94D3D`，
 * 在 Paper 上 4.65:1），与 §一「危险操作醒目」对齐，不用品牌强调色。
 */
@Composable
fun appDangerOutlinedButtonColors(): ButtonColors =
    ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)

@Composable
fun appTextButtonColors(): ButtonColors =
    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.brandColors.accent)

/**
 * 只覆盖品牌相关的强调色；`isError` 仍走 M3 默认的 error 配色，不干扰表单校验态。
 */
@Composable
fun appOutlinedTextFieldColors(): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.brandColors.accent,
        focusedLabelColor = MaterialTheme.brandColors.accent,
        cursorColor = MaterialTheme.brandColors.accent
    )