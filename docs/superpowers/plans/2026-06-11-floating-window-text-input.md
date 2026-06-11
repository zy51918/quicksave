# 全局悬浮窗 · 计划 B：文字输入按钮 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 点悬浮窗面板的【文字输入】按钮，拉起一个轻量透明 `InputActivity`，复用主页手动输入的 UI（分类 Chip 行 + 输入框）与保存逻辑；保存成功后 Toast「已保存」并关闭，悬浮窗收回。

**Architecture:** 新增透明主题的 `InputActivity`，直接复用 `HomeScreen.kt` 中已抽好的 `internal` 可复用 Composable（`CategoryChipRow`、`ManualInputCard`、`CategoryNameDialog`）和现有 `HomeViewModel`（已含 `updateManualInput`/`saveManualInput`/`selectCategory`/`addCategory`/`showAddCategoryDialog` 与 `lastSaveResult`）。`OverlayService` 的【文字输入】按钮由弹 Toast 占位改为 `startActivity` 拉起 `InputActivity`（持有 `SYSTEM_ALERT_WINDOW`，享后台启动豁免）。无新增数据层/业务逻辑，保存链路完全复用 QS-0002。

**Tech Stack:** Kotlin、Jetpack Compose、Android 透明 Activity 主题、现有 HomeViewModel/ClipRepository。

**关联：** 设计文档 `docs/features/QS-0003/design-floating-window.md` §4.2；前置已合并的「计划 A：悬浮窗基础设施」。本计划只覆盖【文字输入】；【录音】是计划 C，本计划中录音按钮保持 Toast 占位不动。

**范围确认（已与用户敲定）：** 透明输入窗**含分类 Chip 行**，与主页手动输入完全一致。

---

## 文件结构

**新建：**
- `app/src/main/java/com/ylib/quicksave/ui/InputActivity.kt` — 透明输入 Activity，复用三件套 Composable + HomeViewModel

**修改：**
- `app/src/main/res/values/themes.xml` — 新增透明 Activity 主题 `Theme.QuickSave.Transparent`
- `app/src/main/AndroidManifest.xml` — 声明 `InputActivity`
- `app/src/main/java/com/ylib/quicksave/overlay/OverlayService.kt` — 【文字输入】按钮改为拉起 `InputActivity`
- `app/src/main/java/com/ylib/quicksave/ui/screens/HomeScreen.kt` — 将 `CategoryNameDialog` 由文件级 `private`/默认可见性改为 `internal`（若当前不可被 `ui` 包外引用）。**先核对**：`CategoryChipRow`、`ManualInputCard` 已是 `internal`；`CategoryNameDialog` 当前为顶层 `fun`（无修饰符=public，可直接复用，无需改动）。仅当编译期发现不可见时才加 `internal`。

---

## Task B1：透明主题 + InputActivity

**Files:**
- Modify: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/java/com/ylib/quicksave/ui/InputActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1：新增透明 Activity 主题**

在 `themes.xml` 的 `</resources>` 之前追加：
```xml
    <style name="Theme.QuickSave.Transparent" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:windowIsTranslucent">true</item>
        <item name="android:windowBackground">@android:color/transparent</item>
        <item name="android:windowContentOverlay">@null</item>
        <item name="android:backgroundDimEnabled">true</item>
    </style>
```

- [ ] **Step 2：创建 InputActivity**

`InputActivity.kt`（完整内容）：
```kotlin
package com.ylib.quicksave.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ylib.quicksave.ui.screens.CategoryChipRow
import com.ylib.quicksave.ui.screens.CategoryNameDialog
import com.ylib.quicksave.ui.screens.ManualInputCard
import com.ylib.quicksave.ui.theme.QuickSaveTheme
import com.ylib.quicksave.ui.viewmodel.HomeViewModel
import com.ylib.quicksave.ui.viewmodel.SaveResult

/**
 * 透明输入窗：从悬浮窗【文字输入】按钮拉起。
 * 复用主页手动输入的分类 Chip 行 + 输入卡片 + 保存逻辑（QS-0002）。
 * 保存成功后 Toast「已保存」并关闭；点窗口外区域或返回键取消。
 */
class InputActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuickSaveTheme {
                val uiState by viewModel.uiState.collectAsState()
                val context = LocalContext.current

                LaunchedEffect(uiState.lastSaveResult) {
                    when (val result = uiState.lastSaveResult) {
                        is SaveResult.Success -> {
                            Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                            viewModel.clearLastSaveResult()
                            finish()
                        }
                        is SaveResult.Failure -> {
                            Toast.makeText(context, "保存失败：${result.message}", Toast.LENGTH_SHORT).show()
                            viewModel.clearLastSaveResult()
                        }
                        else -> {}
                    }
                }

                // 全屏半透明遮罩：点遮罩空白处关闭
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { finish() },
                    contentAlignment = Alignment.Center
                ) {
                    // 卡片本体：吸收点击，避免冒泡到遮罩
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = 6.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { /* 吸收点击，无操作 */ }
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            CategoryChipRow(
                                categories = uiState.categories,
                                selectedCategory = uiState.selectedCategory,
                                onSelect = viewModel::selectCategory,
                                onAddClick = viewModel::showAddCategoryDialog
                            )
                            Spacer(Modifier.height(12.dp))
                            ManualInputCard(
                                text = uiState.manualInputText,
                                onTextChange = viewModel::updateManualInput,
                                isSaving = uiState.isManualSaving,
                                onSave = viewModel::saveManualInput
                            )
                        }
                    }
                }

                if (uiState.showAddCategoryDialog) {
                    CategoryNameDialog(
                        title = "新增分类",
                        initialName = "",
                        existingNames = uiState.categories,
                        onConfirm = { name ->
                            viewModel.addCategory(name)
                            viewModel.dismissAddCategoryDialog()
                        },
                        onDismiss = { viewModel.dismissAddCategoryDialog() }
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3：声明 InputActivity**

在 `AndroidManifest.xml` 的 `<application>` 内（与 `MainActivity` 同级，建议放在 `MainActivity` 之后）追加：
```xml
        <activity
            android:name=".ui.InputActivity"
            android:exported="false"
            android:excludeFromRecents="true"
            android:theme="@style/Theme.QuickSave.Transparent"
            android:windowSoftInputMode="adjustResize" />
```

- [ ] **Step 4：编译验证**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL。
若报 `CategoryChipRow`/`ManualInputCard`/`CategoryNameDialog` 不可见（Unresolved / cannot access），到 `HomeScreen.kt` 把对应顶层 `fun` 前加 `internal`（`CategoryChipRow`、`ManualInputCard` 已是 `internal`；`CategoryNameDialog` 为 public 顶层 fun，应可直接引用）。仅在编译报错时才改可见性。

- [ ] **Step 5：构建 Debug 验证 manifest 合并**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 6：提交**

```bash
git add app/src/main/res/values/themes.xml app/src/main/java/com/ylib/quicksave/ui/InputActivity.kt app/src/main/AndroidManifest.xml
git commit -m "feat(overlay): add transparent InputActivity reusing manual-input UI"
```
提交信息追加（空行后）：
```
Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```
（若 Step 4 改了 `HomeScreen.kt` 的可见性，一并 `git add` 该文件。）

---

## Task B2：OverlayService【文字输入】按钮拉起 InputActivity

**Files:**
- Modify: `app/src/main/java/com/ylib/quicksave/overlay/OverlayService.kt`

- [ ] **Step 1：核对当前按钮代码**

`OverlayService.kt` 的 `buildPanelView()` 中，【文字输入】按钮当前为：
```kotlin
        addView(buildPanelButton("文字输入") {
            Toast.makeText(this@OverlayService, "文字输入（待计划 B 实现）", Toast.LENGTH_SHORT).show()
            collapse()
        })
```

- [ ] **Step 2：新增拉起方法**

在 `OverlayService` 类内（如紧跟 `buildPanelButton` 之后）新增：
```kotlin
    private fun launchInputActivity() {
        val intent = Intent(this, com.ylib.quicksave.ui.InputActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
```
说明：从 Service（非 Activity 上下文）启动 Activity 必须带 `FLAG_ACTIVITY_NEW_TASK`；本应用持有 `SYSTEM_ALERT_WINDOW`，享后台 Activity 启动豁免。

- [ ] **Step 3：替换按钮行为**

将【文字输入】按钮的点击体改为先收起再拉起：
```kotlin
        addView(buildPanelButton("文字输入") {
            collapse()
            launchInputActivity()
        })
```
（【录音】按钮保持原样的 Toast 占位，不动。）

- [ ] **Step 4：确认 import**

`OverlayService.kt` 已 import `android.content.Intent`（计划 A 已有）。无需新增 import（`InputActivity` 用全限定名引用）。`Toast` 仍被【录音】按钮使用，保留其 import。

- [ ] **Step 5：编译验证**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 6：整包构建 + 全部单测（确认未破坏既有）**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL；既有单测全过（本计划不新增单测：保存逻辑已由 `HomeViewModelTest` 覆盖，InputActivity/OverlayService 为框架代码，留 Task B3 设备验证）。

- [ ] **Step 7：提交**

```bash
git add app/src/main/java/com/ylib/quicksave/overlay/OverlayService.kt
git commit -m "feat(overlay): launch InputActivity from the text-input button"
```
提交信息追加（空行后）：
```
Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

## Task B3：设备手动验证

需真机/模拟器（API 29+），且已通过设置开关开启悬浮窗、已设置保存目标文件。

- [ ] **Step 1：安装**

Run: `./gradlew installDebug`
Expected: 安装成功。

- [ ] **Step 2：拉起输入窗**

操作：在任意 App（或桌面）点贴边把手展开面板 → 点【文字输入】。
Expected：面板收回；弹出半透明输入窗，含「分类（可选）」Chip 行 + 「手动输入」输入框 + 「保存到文件 ▶」按钮，背景变暗。

- [ ] **Step 3：保存成功路径**

操作：输入一段文字（可选点一个分类 Chip）→ 点「保存到文件 ▶」。
Expected：Toast「已保存」；输入窗关闭；目标文件追加一行（带时间戳，若选了分类则带 `[分类]` 前缀）。可回 QuickSave 主页或文件管理器核对内容。

- [ ] **Step 4：新增分类**

操作：再次拉起输入窗 → 点 Chip 行「＋ 新增」→ 输入分类名 → 确定。
Expected：弹出新增分类对话框；确定后该分类出现在 Chip 行并选中（与主页一致）。

- [ ] **Step 5：取消路径**

操作：拉起输入窗 → 点输入卡片**外**的暗色区域（或按返回键）。
Expected：输入窗关闭，未写入文件。

- [ ] **Step 6：未设置目标文件的失败反馈**

操作：在设置里清掉/未设目标文件时，拉起输入窗输入文字并保存。
Expected：Toast「保存失败：未设置目标文件」；输入窗保持打开（不关闭），文字保留。

---

## 自检备注（计划作者完成）

- **Spec 覆盖**：覆盖设计文档 §4.2「点【文字输入】→ 拉起透明 InputActivity → 复用 QS-0002 输入与保存 → 成功反馈 → finish → 悬浮窗收回」。录音（§4.3）属计划 C，本计划不动录音按钮。
- **DRY**：直接复用 `CategoryChipRow`/`ManualInputCard`/`CategoryNameDialog`/`HomeViewModel`，无重复实现保存逻辑；无新增数据层。
- **类型一致性**：`CategoryChipRow(categories, selectedCategory, onSelect, onAddClick)`、`ManualInputCard(text, onTextChange, isSaving, onSave)`、`CategoryNameDialog(title, initialName, existingNames, onConfirm, onDismiss)` 的签名与 `HomeScreen.kt` 现有定义一致；`HomeViewModel` 方法/`SaveResult` 与现有定义一致。
- **占位符**：无 TBD/TODO 代码占位。【录音】按钮的 Toast 文案为面向用户文案，非代码缺口。
- **后台启动豁免**：从 Service 拉起 Activity 用 `FLAG_ACTIVITY_NEW_TASK` + 应用持有 `SYSTEM_ALERT_WINDOW`，符合计划 A 设计前提。
