package com.ylib.quicksave.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ylib.quicksave.ui.theme.QuickSaveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * QS-0002 系统测试 — 直接驱动 ManualInputCard / CategoryChipRow，
 * 不经过 ViewModel / NavHost / Scaffold / DisposableEffect 等会引入
 * 不可控副作用的链路，避免设备端死锁。ViewModel 行为由 HomeViewModelTest 单元测试覆盖。
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenManualInputTest {

    @get:Rule
    val composeRule = createComposeRule()

    // --------------------- ManualInputCard ---------------------

    // US-01: 输入卡可见 + placeholder 正确
    @Test
    fun manualInputCard_isDisplayedWithPlaceholder() {
        composeRule.setContent {
            QuickSaveTheme {
                ManualInputCard(
                    text = "",
                    onTextChange = {},
                    isSaving = false,
                    onSave = {}
                )
            }
        }

        composeRule.onNodeWithTag(TAG_MANUAL_INPUT_CARD).assertIsDisplayed()
        composeRule.onNodeWithText("在此输入要保存的文字").assertIsDisplayed()
    }

    // US-05: 空输入 → 保存按钮禁用
    @Test
    fun manualInputCard_saveDisabledWhenEmpty() {
        composeRule.setContent {
            QuickSaveTheme {
                ManualInputCard(text = "", onTextChange = {}, isSaving = false, onSave = {})
            }
        }

        composeRule.onNodeWithTag(TAG_MANUAL_SAVE_BUTTON).assertIsNotEnabled()
    }

    // US-05 边界：全空白 → 禁用
    @Test
    fun manualInputCard_saveDisabledWhenWhitespaceOnly() {
        composeRule.setContent {
            QuickSaveTheme {
                ManualInputCard(text = "   \n\t  ", onTextChange = {}, isSaving = false, onSave = {})
            }
        }

        composeRule.onNodeWithTag(TAG_MANUAL_SAVE_BUTTON).assertIsNotEnabled()
    }

    // US-01 + US-05: 非空 + 非保存中 → 启用
    @Test
    fun manualInputCard_saveEnabledWhenTextNonBlank() {
        composeRule.setContent {
            QuickSaveTheme {
                ManualInputCard(text = "hello", onTextChange = {}, isSaving = false, onSave = {})
            }
        }

        composeRule.onNodeWithTag(TAG_MANUAL_SAVE_BUTTON).assertIsEnabled()
    }

    // 状态机：保存中 → 按钮禁用 + 文案「保存中…」
    @Test
    fun manualInputCard_savingStateShowsLabelAndDisablesButton() {
        composeRule.setContent {
            QuickSaveTheme {
                ManualInputCard(text = "hello", onTextChange = {}, isSaving = true, onSave = {})
            }
        }

        composeRule.onNodeWithTag(TAG_MANUAL_SAVE_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithText("保存中…").assertIsDisplayed()
    }

    // 输入回调：用户键入 → onTextChange 收到 + UI 反映
    @Test
    fun manualInputCard_typingPropagatesToCallbackAndShowsText() {
        var captured = ""
        composeRule.setContent {
            QuickSaveTheme {
                var text by remember { mutableStateOf("") }
                ManualInputCard(
                    text = text,
                    onTextChange = {
                        text = it
                        captured = it
                    },
                    isSaving = false,
                    onSave = {}
                )
            }
        }

        composeRule.onNodeWithTag(TAG_MANUAL_INPUT_FIELD).performTextInput("note text")

        assertEquals("note text", captured)
        composeRule.onNodeWithText("note text").assertIsDisplayed()
    }

    // US-02: 启用态点击保存 → onSave 触发
    @Test
    fun manualInputCard_clickSaveTriggersCallback() {
        var saveCount = 0
        composeRule.setContent {
            QuickSaveTheme {
                ManualInputCard(text = "hello", onTextChange = {}, isSaving = false, onSave = { saveCount++ })
            }
        }

        composeRule.onNodeWithTag(TAG_MANUAL_SAVE_BUTTON).performClick()

        assertEquals(1, saveCount)
    }

    // 防御：禁用态点击保存 → onSave 不触发
    @Test
    fun manualInputCard_clickWhenDisabledDoesNotTriggerCallback() {
        var saveCount = 0
        composeRule.setContent {
            QuickSaveTheme {
                ManualInputCard(text = "", onTextChange = {}, isSaving = false, onSave = { saveCount++ })
            }
        }

        composeRule.onNodeWithTag(TAG_MANUAL_SAVE_BUTTON).performClick()

        assertEquals(0, saveCount)
    }

    // --------------------- CategoryChipRow ---------------------

    // US-03: Chip 行渲染分类列表 + label
    @Test
    fun categoryChipRow_displaysCategoriesAndLabel() {
        composeRule.setContent {
            QuickSaveTheme {
                CategoryChipRow(
                    categories = listOf("工作", "学习", "生活"),
                    selectedCategory = null,
                    onSelect = {},
                    onAddClick = {}
                )
            }
        }

        composeRule.onNodeWithTag(TAG_CATEGORY_CHIP_ROW).assertIsDisplayed()
        composeRule.onNodeWithText("分类（可选）").assertIsDisplayed()
        composeRule.onNodeWithText("工作").assertIsDisplayed()
        composeRule.onNodeWithText("学习").assertIsDisplayed()
        composeRule.onNodeWithText("生活").assertIsDisplayed()
        composeRule.onNodeWithText("＋ 新增").assertIsDisplayed()
    }

    // US-03 选中：点击未选中 Chip → onSelect 收到分类名
    @Test
    fun categoryChipRow_clickUnselectedChipPassesCategory() {
        var selected: String? = "INIT"
        composeRule.setContent {
            QuickSaveTheme {
                CategoryChipRow(
                    categories = listOf("工作", "学习"),
                    selectedCategory = null,
                    onSelect = { selected = it },
                    onAddClick = {}
                )
            }
        }

        composeRule.onNodeWithText("工作").performClick()

        assertEquals("工作", selected)
    }

    // US-03 取消选中：点击已选中 Chip → onSelect(null)
    @Test
    fun categoryChipRow_clickSelectedChipPassesNull() {
        var selected: String? = "INIT"
        composeRule.setContent {
            QuickSaveTheme {
                CategoryChipRow(
                    categories = listOf("工作"),
                    selectedCategory = "工作",
                    onSelect = { selected = it },
                    onAddClick = {}
                )
            }
        }

        composeRule.onNodeWithText("工作").performClick()

        assertNull(selected)
    }

    // 「＋ 新增」 → onAddClick 触发
    @Test
    fun categoryChipRow_clickAddTriggersOnAddClick() {
        var addClicks = 0
        composeRule.setContent {
            QuickSaveTheme {
                CategoryChipRow(
                    categories = emptyList(),
                    selectedCategory = null,
                    onSelect = {},
                    onAddClick = { addClicks++ }
                )
            }
        }

        composeRule.onNodeWithText("＋ 新增").performClick()

        assertEquals(1, addClicks)
    }

    // 空分类列表：只显示「＋ 新增」
    @Test
    fun categoryChipRow_emptyListShowsOnlyAddChip() {
        composeRule.setContent {
            QuickSaveTheme {
                CategoryChipRow(
                    categories = emptyList(),
                    selectedCategory = null,
                    onSelect = {},
                    onAddClick = {}
                )
            }
        }

        composeRule.onNodeWithText("＋ 新增").assertIsDisplayed()
        composeRule.onNodeWithText("分类（可选）").assertIsDisplayed()
    }

    // --------------------- ClipboardCard ---------------------

    // 剪切板卡渲染 + 内容预览 + 保存按钮
    @Test
    fun clipboardCard_displaysContentAndSaveButton() {
        composeRule.setContent {
            QuickSaveTheme {
                ClipboardCard(
                    clipText = "from clipboard",
                    isSaving = false,
                    onSave = {}
                )
            }
        }

        composeRule.onNodeWithTag(TAG_CLIPBOARD_CARD).assertIsDisplayed()
        composeRule.onNodeWithText("当前剪切板").assertIsDisplayed()
        composeRule.onNodeWithText("from clipboard").assertIsDisplayed()
    }

    // 状态独立性回归：剪切板卡不持有 ManualInputCard 的保存按钮 testTag
    @Test
    fun clipboardCard_doesNotContainManualSaveButtonTag() {
        composeRule.setContent {
            QuickSaveTheme {
                ClipboardCard(
                    clipText = "from clipboard",
                    isSaving = false,
                    onSave = {}
                )
            }
        }

        // 剪切板卡内不应该有 manual save button testTag（保证两个保存按钮在 HomeScreen 中分别归属各自的卡）
        composeRule.onAllNodes(
            androidx.compose.ui.test.hasTestTag(TAG_MANUAL_SAVE_BUTTON)
        ).fetchSemanticsNodes().also {
            assertEquals(0, it.size)
        }
    }
}
