# QuickSave — 设计文档（DESIGN）

> 版本：1.0
> 日期：2026-04-29
> 作者：HIE 设计师

---

## 一、设计原则

| 原则 | 说明 |
|------|------|
| **极简** | 只有两个页面（主页 + 设置），界面不添加装饰性元素 |
| **即时反馈** | 每次操作都有 Snackbar 或按钮状态变化响应 |
| **危险操作醒目** | 清空操作使用红色，并有二次确认弹窗 |
| **单手操作** | 保存按钮在内容卡片内，拇指易触及 |

---

## 二、设计规范

### 2.1 色彩系统（Material You / Dynamic Color）

| Token | 用途 | 默认值（Light） |
|-------|------|----------------|
| `Primary` | 主操作按钮、标题强调、选中 Chip | `#6650A4`（紫） |
| `OnPrimary` | 主色上的文字/图标 | `#FFFFFF` |
| `PrimaryContainer` | 剪切板预览卡背景 | `#EADDFF` |
| `OnPrimaryContainer` | 预览卡内文字 | `#21005D` |
| `Error` | 未配置警告、危险操作 | `#B3261E` |
| `Surface` | 卡片背景 | `#FFFBFE` |
| `OnSurfaceVariant` | 辅助文字、标签 | `#49454F` |
| `Outline` | 边框、分割线 | `#79747E` |
| `OutlineVariant` | 未选中 Chip 描边 | `#CAC4D0` |

> Android 12+ 自动使用系统 Dynamic Color；低版本使用上表默认值。

### 2.2 字体层级

| Style | Size / Weight | 用途 |
|-------|--------------|------|
| `headlineMedium` | 28sp / 500 | 主页标题 "QuickSave" |
| `titleMedium` | 16sp / 500 | 设置页区块标题 |
| `bodyMedium` | 14sp / 400 | 剪切板内容预览、卡片正文 |
| `bodySmall` | 12sp / 400 | 说明文字、文件路径 |
| `labelSmall` | 11sp / 500 | 卡片标签（"当前剪切板"） |

### 2.3 圆角 & 间距

| 元素 | 圆角 |
|------|------|
| Card | 12dp |
| Filled / Outlined Button | 100dp（胶囊） |
| FilterChip | 8dp（Material 3 默认） |
| AlertDialog | 28dp（Material 3 默认） |

- 页面水平边距：`16dp`
- 组件垂直间距：`8dp`
- 卡片内边距：`12dp`
- Chip 行横向间距：`6dp`

---

## 三、页面设计

### 3.1 主页（HomeScreen）

#### 布局结构（从上到下）

```
┌─────────────────────────────────────┐
│  QuickSave          headlineMedium  │
│  [16dp]                             │
├── 未配置文件警告卡（仅未配置时显示）──┤
│  ┌───────────────────────────────┐  │
│  │▌ 未设置保存文件               │  │  ← Card，左侧 4dp 红色竖条
│  │  [前往设置]                   │  │
│  └───────────────────────────────┘  │
│  [8dp]                              │
├── 操作栏 ──────────────────────────── ┤
│                          [设置]     │  ← OutlinedButton，右对齐
│  [8dp]                              │
├── 剪切板内容卡（剪切板有内容时显示）──┤
│  ┌───────────────────────────────┐  │
│  │ 当前剪切板      labelSmall    │  │  ← PrimaryContainer 背景
│  │ [4dp]                         │  │
│  │ 文字内容预览，最多 3 行…       │  │  ← bodyMedium
│  │ [10dp]                        │  │
│  │ [✓工作] [学习] [生活] [＋新增] │  │  ← ★ FilterChip 行，横向可滚动
│  │ [10dp]                        │  │
│  │              [保存到文件 ▶]   │  │  ← FilledButton，右对齐
│  └───────────────────────────────┘  │
├── 剪切板空状态（剪切板为空时显示）────┤
│  剪切板为空，请先在其他应用复制文字  │  ← bodySmall, OnSurfaceVariant, 居中
│  [8dp]                              │
├── 清空操作（已配置文件时显示）────────┤
│  [        清空保存文件内容        ]  │  ← OutlinedButton，Error 色，全宽
│  [16dp]                             │
└─────────────────────────────────────┘
```

#### FilterChip 行规范

| 状态 | 样式 |
|------|------|
| 选中 | FilterChip selected（Primary 填充，白色文字） |
| 未选中 | FilterChip unselected（OutlineVariant 描边） |
| 「＋ 新增」 | Outline 色描边，点击弹 AlertDialog |

- 分类多时横向滚动，「＋ 新增」始终在末尾
- 点击已选中的 Chip 取消选中（切换为无分类）
- 分类列表为空时 Chip 行仅显示「＋ 新增」

#### 保存按钮状态

| 状态 | 按钮文字 |
|------|---------|
| 可保存 | 保存到文件 ▶ |
| 保存中 | 保存中… （禁用） |

---

### 3.2 设置页（SettingsScreen）

```
┌─────────────────────────────────────┐
│  ←  设置         TopAppBar（Primary色）
├─────────────────────────────────────┤
│  保存目标文件           titleMedium  │
│  ─────────────────────────────────  │
│                                     │
│  【已配置状态】                      │
│  ┌───────────────────────────────┐  │
│  │ 当前文件：          bodySmall │  │
│  │ /storage/.../quicksave.txt   │  │
│  └───────────────────────────────┘  │
│  [      重新选择文件       ]         │  ← OutlinedButton，全宽
│                                     │
│  【未配置状态（替代上方）】            │
│  ┌───────────────────────────────┐  │
│  │▌ 尚未设置，请选择保存文件      │  │  ← 红色左条 Card
│  │  [选择保存文件 ▶]             │  │
│  └───────────────────────────────┘  │
│                                     │
├── ★ 分类管理区块 ───────────────────── ┤
│  分类管理               titleMedium │
│  ─────────────────────────────────  │
│                                     │
│  ⠿  工作       ✎ 重命名   ✕ 删除   │  ← 拖拽手柄在左侧
│  ⠿  学习       ✎ 重命名   ✕ 删除   │
│  ⠿  生活       ✎ 重命名   ✕ 删除   │
│                                     │
│  【空列表状态（替代上方列表）】        │
│  暂无分类，点击下方按钮添加  bodySmall│
│                                     │
│  [       ＋ 新增分类       ]         │  ← OutlinedButton，全宽
│                                     │
│  重命名分类不会修改已保存的记录。      │  ← bodySmall, Outline 色
└─────────────────────────────────────┘
```

#### 分类拖拽排序规范

- 使用 `sh.calvin.reorderable` 库（`LazyColumn` + `ReorderableItem`）
- 长按拖拽手柄（`⠿`）触发，拖动中使用 `animateDpAsState` 提升阴影高度（0dp → 8dp）
- 松手后顺序实时写入 DataStore
- 外层为 `Column + verticalScroll`；内层 LazyColumn 设 `userScrollEnabled=false`、`heightIn(max=400.dp)` 避免嵌套滚动冲突

---

## 四、通知设计

### 4.1 常驻通知

| 属性 | 规格 |
|------|------|
| 渠道 | `quicksave_channel`，IMPORTANCE_LOW |
| 标题 | "QuickSave" |
| 正文 | "点击打开应用保存剪切板内容" |
| 优先级 | LOW（不弹横幅，不响铃） |
| 样式 | Ongoing（不可滑除） |
| 点击动作 | 打开 MainActivity |
| 生命周期 | 应用运行期间持续显示 |

---

## 五、对话框设计

### 5.1 清空保存文件确认

```
┌─────────────────────────────────┐
│  清空保存文件                    │
│                                 │
│  确认清空文件内全部内容？         │
│  此操作不可恢复。                 │
│                                 │
│                  [取消]  [清空] │  ← 清空为 Error 色
└─────────────────────────────────┘
```

### 5.2 新增 / 重命名分类

```
┌─────────────────────────────────┐
│  新增分类  /  重命名分类          │
│                                 │
│  ┌─────────────────────────┐    │
│  │ 分类名称（预填/空）       │    │  ← OutlinedTextField
│  └─────────────────────────┘    │
│  分类名已存在（错误状态时显示）   │  ← Error 色内联提示
│                                 │
│                  [取消]  [确定] │  ← 空或重复时「确定」禁用
└─────────────────────────────────┘
```

- 输入框自动过滤换行符
- `rememberSaveable(key = initialName)` 保证重新打开时状态重置

---

## 六、Snackbar 反馈规范

| 场景 | 消息 | 时长 |
|------|------|------|
| 保存成功 | "已保存" | SHORT |
| 文件已清空 | "文件内容已清空" | SHORT |
| 未配置文件 | "请先在设置中选择保存文件" | LONG |
| 权限丢失 | "文件无写入权限，请重新选择" | LONG |
| 保存失败（其他） | "保存失败：{错误信息}" | LONG |

---

## 七、页面导航

```
MainActivity
  └── NavHost
        ├── HomeScreen（默认路由）
        │     └── [设置] 按钮 → SettingsScreen
        └── SettingsScreen
              └── ← TopAppBar 返回键
```

---

## 八、数据层设计

### 8.1 保存格式

```
有分类：[分类名][yyyy-MM-dd HH:mm:ss] 文字内容
无分类：[yyyy-MM-dd HH:mm:ss] 文字内容
```

两种格式均以 `\n` 结尾，追加写入目标文件，向后兼容。

### 8.2 DataStore 字段

| 字段 | 类型 | 默认值 | 序列化方式 |
|------|------|--------|----------|
| `target_file_uri` | `String?` | `null` | 原始 URI 字符串 |
| `categories` | `List<String>` | `emptyList()` | 换行符（`\n`）分隔；列表为空时删除 key |
| `selected_category` | `String?` | `null` | 原始字符串；为 null 时删除 key |

`selected_category` 若不在 `categories` 列表中，运行时视为 `null`（不修改存储值，等待下次有效写入）。

### 8.3 架构分层

```
UI Layer
  HomeScreen / SettingsScreen
       ↕ StateFlow<UiState>
ViewModel Layer
  HomeViewModel / SettingsViewModel
       ↕ Flow / suspend fun
Repository Layer
  ClipRepository（接口）/ ClipRepositoryImpl
       ↕
Data Layer
  AppDataStore（接口）/ AppDataStoreImpl   +   DocumentFile（SAF 文件 I/O）
```

---

## 九、设计图索引

| 文件 | 内容 |
|------|------|
| [mockups/home-normal.svg](mockups/home-normal.svg) | 主页 — 正常状态（有剪切板内容 + 已配置文件） |
| [mockups/home-no-file.svg](mockups/home-no-file.svg) | 主页 — 未配置文件警告状态 |
| [mockups/settings.svg](mockups/settings.svg) | 设置页（已配置 / 未配置两种状态） |
| [mockups/notification.svg](mockups/notification.svg) | 常驻通知设计 |
| [mockups/index.html](mockups/index.html) | 全部设计图查看器 |
