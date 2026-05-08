# QuickSave QS-0002 (Manual Input) — 详细设计文档

> feature_id：`QS-0002`
> feature_name：`manual-input`
> 版本：1.0
> 日期：2026-05-08
> 作者：开发工程师
> 状态：实现中（v1.2）
> 关联文档：[`prd-manual-input.md`](prd-manual-input.md) · [`ui-manual-input.md`](ui-manual-input.md) · [`docs/ARCH.md`](../../ARCH.md)

本文档为 QS-0002 的实现层设计。架构无变更（详见阶段 A 评估），改动**全部局限在 UI 层**。

---

## 一、变更范围一览

| 文件 | 变更类型 |
|------|---------|
| `ui/viewmodel/HomeViewModel.kt` | 修改：`HomeUiState` 增字段、字段重命名、新增方法、构造注入 repo（便于单测） |
| `ui/screens/HomeScreen.kt` | 重构：抽出 3 个 Composable，重排布局（Chip 行置顶 + 输入卡新增） |
| `app/src/test/.../HomeViewModelTest.kt` | **新增** 单元测试覆盖手动输入逻辑与状态独立性 |
| Repository / DataStore / Service / Navigation | 不变 |

---

## 二、`HomeUiState` 变更

```kotlin
data class HomeUiState(
    val targetFileUri: String? = null,
    val clipText: String? = null,

    val isClipSaving: Boolean = false,    // ← 重命名自 isSaving
    val isManualSaving: Boolean = false,  // ← 新增

    val manualInputText: String = "",     // ← 新增

    val showClearDialog: Boolean = false,
    val lastSaveResult: SaveResult? = null,
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val showAddCategoryDialog: Boolean = false
)
```

### 字段语义

| 字段 | 取值 / 范围 | 触发更新 |
|------|------------|---------|
| `manualInputText` | 任意字符串（含空白、含换行） | UI `OutlinedTextField.onValueChange` → `viewModel.updateManualInput(text)` |
| `isClipSaving` | 仅在 `saveClipboard()` 协程进行中为 `true` | 进入/退出 `saveClipboard()` |
| `isManualSaving` | 仅在 `saveManualInput()` 协程进行中为 `true` | 进入/退出 `saveManualInput()` |

**两个 `is*Saving` 字段独立**：剪切板保存的进行中状态不影响手动输入按钮的可点击性，反之亦然（PRD §六 边界条件）。

---

## 三、`HomeViewModel` 新增/修改方法

```kotlin
class HomeViewModel(
    app: Application,
    private val repo: ClipRepository = (app as QuickSaveApplication).clipRepository
) : AndroidViewModel(app) {

    // 既有方法（不变）：readClipboard, selectCategory, addCategory,
    // showAddCategoryDialog, dismissAddCategoryDialog, showClearDialog,
    // dismissClearDialog, clearLastSaveResult, clearSavedFile

    // 修改：使用 isClipSaving
    fun saveClipboard() { ... }

    // 新增
    fun updateManualInput(text: String) { ... }
    fun saveManualInput() { ... }
}
```

> 构造函数引入第二参数 `repo` 提供测试注入点（默认值保持生产行为不变）。这是 TDD 压力下的最小改动，未引入新接口或包结构。

### 3.1 `updateManualInput(text: String)` 行为

```
input → _uiState.update { copy(manualInputText = text) }
```

无业务规则、无边界处理；纯粹反映 UI 输入。换行符、超长字符串、空白都按原样保留（PRD §六）。

### 3.2 `saveManualInput()` 行为（状态机）

```
state.manualInputText.isBlank()?
    └─ yes → 立即 return（saveButton 应已禁用，此为防御）
    └─ no  → 进入流程：

_uiState.update { copy(isManualSaving = true) }
        │
        ▼
viewModelScope.launch {
    val result = repo.saveEntry(
        text = state.manualInputText,
        category = state.selectedCategory
    )
    _uiState.update {
        if (result.isSuccess) {
            it.copy(
                isManualSaving = false,
                manualInputText = "",                  // ← 清空
                lastSaveResult = SaveResult.Success
            )
        } else {
            it.copy(
                isManualSaving = false,
                // manualInputText 不清空，保留用户输入
                lastSaveResult = SaveResult.Failure(
                    result.exceptionOrNull()?.message ?: "保存失败"
                )
            )
        }
    }
}
```

| 场景 | 输入保留 | manualInputText 清空 | lastSaveResult |
|------|---------|---------------------|---------------|
| Blank → return early | ✓（无变化） | ✗ | 不动 |
| 成功 | ✗ | ✓ | `Success` |
| 失败（含未配置 / 权限丢失 / IO） | ✓ | ✗ | `Failure(message)` |

### 3.3 `saveClipboard()` 修改点

只是把 `isSaving` 重命名为 `isClipSaving`。语义、流程、错误处理完全不变。

---

## 四、UI 状态 → Composable 映射

### 4.1 顶层布局（HomeScreen `LazyColumn`）

```
┌── item: 标题 ────────────┐
├── item: 警告卡（条件） ───┤
├── item: 设置按钮 ────────┤
├── item: ★ CategoryChipRow（NEW，从剪切板卡内移出） ┤
├── item: ★ ClipboardCard（条件，简化）/ ClipboardEmptyHint（条件） ┤
├── item: ★ ManualInputCard（NEW） ┤
├── item: 清空按钮（条件） ────┤
└── item: 底部 Spacer ──────┘
```

### 4.2 抽出的 Composable

#### `CategoryChipRow`（顶层共享）

```kotlin
@Composable
private fun CategoryChipRow(
    categories: List<String>,
    selectedCategory: String?,
    onSelect: (String?) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
)
```

- 上方 label「分类（可选）」
- `LazyRow` 横向滚动，Chip + 「＋ 新增」末尾
- 行为与 v1.1 完全一致，仅位置变化

#### `ClipboardCard`（剪切板专用，瘦身）

```kotlin
@Composable
private fun ClipboardCard(
    clipText: String,
    isSaving: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
)
```

- 移除内嵌 Chip 行
- 保留 PrimaryContainer 背景、内容预览、保存按钮
- 高度从 ~142dp 减至 ~110dp

#### `ManualInputCard`（新增）

```kotlin
@Composable
private fun ManualInputCard(
    text: String,
    onTextChange: (String) -> Unit,
    isSaving: Boolean,
    canSave: Boolean,            // = text.trim().isNotBlank()
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
)
```

- Surface 背景 + Outline 描边
- 标题「手动输入」
- `OutlinedTextField` `minLines=3, maxLines=6`，placeholder「在此输入要保存的文字」
- 保存按钮：`enabled = canSave && !isSaving`
- 文字：可保存态 `保存到文件 ▶` / 进行中 `保存中…`

---

## 五、状态独立性证明

| 触发动作 | 影响字段 | 不影响字段 |
|---------|---------|-----------|
| `saveClipboard()` 进行中 | `isClipSaving = true` | `isManualSaving`、`manualInputText` |
| `saveManualInput()` 进行中 | `isManualSaving = true` | `isClipSaving`、`clipText` |
| `updateManualInput()` | `manualInputText` | 其他全部 |
| `selectCategory()` | `selectedCategory`（DataStore 持久化后由 Flow 反推） | 其他全部 |

两路保存可同时运行（虽然 UI 上极不可能）；ViewModel 不强制串行。

---

## 六、错误处理

完全沿用 QS-0001 约定（[ARCH.md §六](../../ARCH.md)）：
- Repository 用 `runCatching` 包装 → `Result<Unit>`
- ViewModel 在协程内拿到 `Result`，更新 `lastSaveResult`
- UI `LaunchedEffect(lastSaveResult)` 显示 Snackbar 后立即调用 `clearLastSaveResult()`

新增：手动输入失败时 `manualInputText` **不清空**，让用户保留输入便于重试或修改。

---

## 七、并发与生命周期

| 行为 | Dispatcher | 备注 |
|------|-----------|------|
| `viewModelScope.launch { repo.saveEntry(...) }` | `Dispatchers.Main.immediate` → `Dispatchers.IO`（在 `SafFileDataSource` 内 `withContext`） | 不变 |
| `_uiState.update { ... }` | Main | `MutableStateFlow.update` 线程安全 |
| `manualInputText` 同步更新 | Main | 无 IO，纯 state copy |

`rememberSaveable` 在 Composable 层不参与 ViewModel 状态管理。设计选择：**`manualInputText` 由 ViewModel 持有**而不是 Composable 内 `rememberSaveable`：
- 让"清空输入"由 `saveManualInput()` 在 success path 中**主动触发**，避免 Composable 与 ViewModel 双源
- ViewModel `_uiState` 是 `StateFlow`，Composable 通过 `collectAsState()` 订阅；`MutableStateFlow` 在配置变更（旋转）期间随 ViewModel 一同保留
- 进程被强杀后丢失（PRD §4.3 不在范围）

---

## 八、单元测试覆盖矩阵

| 测试用例 | 类型 | 验证 |
|---------|------|------|
| `updateManualInput updates state text` | 状态 | 字段写入正确 |
| `saveManualInput is no-op when text is blank` | 守卫 | 不调用 repo，不修改状态 |
| `saveManualInput is no-op when text is whitespace only` | 守卫 | 同上（含 `\n`、`\t`、空格） |
| `saveManualInput passes selectedCategory to repo` | 集成 | 验证 `repo.saveEntry(text, category)` 参数 |
| `saveManualInput passes null category when none selected` | 集成 | 验证 category=null |
| `saveManualInput on success clears manualInputText` | 状态 | 成功后字段为 "" |
| `saveManualInput on success sets lastSaveResult Success` | 状态 | 触发 Snackbar |
| `saveManualInput on failure preserves manualInputText` | 状态 | 输入保留 |
| `saveManualInput on failure sets lastSaveResult Failure with message` | 状态 | 错误透传 |
| `saveManualInput sets isManualSaving true during execution and false after` | 状态机 | 守卫 UI 按钮禁用 |
| `saveManualInput does not affect isClipSaving` | 隔离 | 状态独立性 |
| `saveClipboard now uses isClipSaving field` | 重命名验证 | 旧字段已废弃 |

不写 Composable 测试：UI 层测试由 `/test` 阶段在 `androidTest/` 用 Compose Testing 完成。
