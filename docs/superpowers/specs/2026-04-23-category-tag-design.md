# QuickSave 内容分类标签功能 — 设计文档

> 日期：2026-04-23
> 状态：待实现

---

## 一、功能概述

在保存剪切板内容时，允许用户为每条记录附加一个分类标签。标签以文本前缀形式写入文件，兼容现有记录格式。

**保存格式：**
```
有分类：[工作][2026-04-23 10:30:00] 内容
无分类：[2026-04-23 10:30:00] 内容（现有格式，不变）
```

---

## 二、用户故事

| # | 用户故事 | 验收条件 |
|---|---------|---------|
| US-C01 | 保存时可选择一个分类标签 | 主页剪切板卡片显示分类 Chip 选择器，选中后保存带标签 |
| US-C02 | 当前分类在重启后保留 | 选中的分类持久化到 DataStore，下次打开自动恢复 |
| US-C03 | 可快速新增分类 | 主页 Chip 行末尾「＋ 新增」弹 AlertDialog 输入名称 |
| US-C04 | 可在设置页完整管理分类 | 设置页支持增、删、改、拖拽排序 |
| US-C05 | 不选分类时保持原格式 | 无 Chip 选中时，保存格式与现有记录一致 |

---

## 三、UI 设计

### 3.1 主页（HomeScreen）

剪切板内容卡片内，预览文字下方新增分类 Chip 行，保存按钮保持右对齐另起一行。

```
┌────────────────────────────────────────┐
│ 当前剪切板              labelSmall      │  ← PrimaryContainer 背景
│ [4dp]                                  │
│ 文字内容预览，最多 3 行……  bodyMedium   │
│ [10dp]                                 │
│ [✓ 工作] [学习] [生活] [＋ 新增]  →→→  │  ← ★ 新增 Chip 行，横向可滚动
│ [10dp]                                 │
│                      [保存到文件 ▶]    │  ← 右对齐，与现有一致
└────────────────────────────────────────┘
```

**Chip 状态：**

| 状态 | 样式 |
|------|------|
| 选中 | FilterChip selected（Primary 填充） |
| 未选中 | FilterChip unselected（描边） |
| 「＋ 新增」 | 虚线描边，点击弹 AlertDialog |

**「＋ 新增分类」对话框：**
- 标题：「新增分类」
- 输入框：单行文本，placeholder「分类名称」
- 错误提示：名称为空或与已有分类重复时显示内联错误
- 按钮：「取消」「确定」（输入有效时才可点击）
- 确定后：追加到列表末尾并自动选中

### 3.2 设置页（SettingsScreen）

在「保存目标文件」区块下方新增「分类管理」区块。

```
┌────────────────────────────────────────┐
│ 分类管理              titleMedium       │
│ ──────────────────────────────────     │
│                                        │
│ ⠿  工作           ✎ 重命名   ✕ 删除   │  ← 拖拽手柄在左侧
│ ⠿  学习           ✎ 重命名   ✕ 删除   │
│ ⠿  生活           ✎ 重命名   ✕ 删除   │
│                                        │
│ [        ＋ 新增分类        ]           │  ← OutlinedButton，全宽
│                                        │
│ 重命名分类不会修改已保存的记录。  bodySmall │
└────────────────────────────────────────┘
```

**拖拽排序：**
- 使用 `sh.calvin.reorderable` 库，`LazyColumn` + `ReorderableItem`
- 长按拖拽手柄（⠿）触发，拖动中目标行高亮
- 松手后顺序写入 DataStore

**「重命名」对话框：**
- 标题：「重命名分类」
- 输入框：预填当前名称
- 错误提示：与已有分类重复时显示内联错误
- 确定后：更新 DataStore（不修改已写入文件的历史记录）

**「删除」：**
- 直接删除，无二次确认（不影响已写入文件内容）
- 若删除的是当前选中分类，`selectedCategory` 重置为 null

**空列表状态：**
- 显示提示文字：「暂无分类，点击下方按钮添加」

---

## 四、数据层设计

### 4.1 DataStore 新增字段

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `categories` | `List<String>` | `emptyList()` | 分类名有序列表，序列化为 JSON 字符串 |
| `selectedCategory` | `String?` | `null` | 当前选中分类，null 表示无分类 |

`selectedCategory` 若存储值不在 `categories` 列表中（如被删除），读取时视为 null。

### 4.2 ClipRepository 变更

```kotlin
// 现有接口
suspend fun saveClip(uri: Uri, text: String): Result<Unit>

// 新接口
suspend fun saveClip(uri: Uri, text: String, category: String?): Result<Unit>
```

写入格式：
```kotlin
val prefix = if (category != null) "[$category]" else ""
val line = "$prefix[${timestamp}] $text\n"
```

---

## 五、代码变更范围

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `AppDataStore.kt` | 新增接口 | `categoriesFlow`, `selectedCategoryFlow`, `setCategories()`, `setSelectedCategory()` |
| `AppDataStoreImpl.kt` | 新增实现 | 实现上述接口，JSON 序列化分类列表 |
| `ClipRepository.kt` | 接口变更 | `saveClip` 新增 `category` 参数 |
| `ClipRepositoryImpl.kt` | 实现变更 | 调整写入格式 |
| `HomeViewModel.kt` | 新增状态与 action | `categories`、`selectedCategory`、`addCategory()`、`selectCategory()` |
| `HomeScreen.kt` | UI 新增 | 剪切板卡片内新增 Chip 行 |
| `SettingsViewModel.kt` | 新增 action | `addCategory()`、`renameCategory()`、`deleteCategory()`、`reorderCategories()` |
| `SettingsScreen.kt` | UI 新增 | 「分类管理」区块，含拖拽列表 |
| `gradle/libs.versions.toml` | 新增依赖 | `sh.calvin.reorderable` |

---

## 六、边界条件

| 场景 | 处理方式 |
|------|---------|
| 分类名为空 | 对话框「确定」按钮禁用 |
| 分类名与已有重复 | 对话框显示内联错误「分类名已存在」 |
| 删除当前选中分类 | `selectedCategory` 重置为 null |
| `selectedCategory` 不在列表中 | 读取时视为 null，不崩溃 |
| 分类列表为空 | 设置页显示提示文字，主页 Chip 行仅显示「＋ 新增」 |

---

## 七、不在本期范围

- 按分类筛选历史记录
- 修改历史记录的分类
- 分类颜色或图标自定义
- 多选分类
