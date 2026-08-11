# QuickSave — UI 交互设计文档（UI）

> 版本：1.4
> 日期：2026-08-11
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

> v1.4：上述间距/圆角值集中为代码常量 `ui/theme/DimTokens.kt`（`Dim` 对象 + `AppShapes`），所有页面与透明输入窗共用，替代散落在各 Composable 中的硬编码魔数。Card 圆角通过 `AppShapes.medium = RoundedCornerShape(12dp)` 显式注入 Theme，不再依赖 Material 默认值。

---

## 三、页面设计

### 3.1 主页（HomeScreen，v1.4）

#### 布局结构（从上到下）

```
┌─────────────────────────────────────┐
│  QuickSave               [⚙ 设置]   │  ← TopAppBar（surface 色），右侧设置 IconButton
├─────────────────────────────────────┤
│  [16dp]                             │
├── 未配置文件警告卡（仅未配置时显示）──┤
│  ┌───────────────────────────────┐  │
│  │▌ 未设置保存文件               │  │  ← Card，左侧 4dp 红色竖条
│  │  [前往设置]                   │  │
│  └───────────────────────────────┘  │
│  [8dp]                              │
├── ★ 共享分类 Chip 行（v1.2 顶层置顶）─┤
│  分类（可选）       labelSmall      │
│  [✓工作] [学习] [生活] [＋新增]    │  ← FilterChip 行，剪切板/手动输入两路共用
│  [8dp]                              │
├── 剪切板内容卡（剪切板有内容时显示）──┤
│  ┌───────────────────────────────┐  │
│  │ 当前剪切板      labelSmall    │  │  ← PrimaryContainer 背景
│  │ [4dp]                         │  │
│  │ 文字内容预览，最多 3 行…       │  │  ← bodyMedium
│  │ [10dp]                        │  │
│  │              [保存到文件]     │  │  ← FilledButton，右对齐
│  └───────────────────────────────┘  │
├── 剪切板空状态（剪切板为空时显示）────┤
│  剪切板为空，请先在其他应用复制文字  │  ← bodySmall, OnSurfaceVariant, 居中
│  [8dp]                              │
├── ★ 手动输入卡（v1.2 新增）───────── ┤
│  ┌───────────────────────────────┐  │
│  │ 手动输入        labelSmall    │  │  ← Surface 背景 + Outline 1dp 描边
│  │ [4dp]                         │  │
│  │ ┌───────────────────────────┐ │  │
│  │ │ 在此输入要保存的文字       │ │  │  ← OutlinedTextField, minLines=3, maxLines=6
│  │ └───────────────────────────┘ │  │
│  │ [10dp]                        │  │
│  │              [保存到文件]     │  │  ← FilledButton，右对齐
│  └───────────────────────────────┘  │
├── 清空操作（已配置文件时显示）────────┤
│  [        清空保存文件内容        ]  │  ← OutlinedButton，Error 色，全宽
│  [16dp]                             │
└─────────────────────────────────────┘
```

> v1.4 关键变更：
> - **新增 surface 色 TopAppBar**：主页加顶栏（标题 "QuickSave" + 右侧设置图标按钮），与设置页顶栏风格统一；原滚动区右下角的「设置」OutlinedButton 移除，设置入口上移到顶栏 action。
> - **去装饰性箭头**：「保存到文件 ▶」「选择保存文件 ▶」等文案中的 `▶` 移除，回归"界面不添加装饰性元素"原则（§一）。
> - **手动输入卡补描边**：`ManualInputCard` 按 §3.1 规格补上 1dp `outline` 描边（此前仅设了 surface 背景未描边，与文档不符）。
> - **间距统一为 Dim 常量**：所有硬编码 dp 改用 `Dim` 对象。
>
> v1.2 关键变更：分类 Chip 行从剪切板卡内**抽出并上移到主页最顶层**，让用户线性决策"先选分类 → 再选保存来源 → 点保存"；新增手动输入卡，与剪切板卡并存。

#### FilterChip 行规范（顶层共享区块）

| 状态 | 样式 |
|------|------|
| 选中 | FilterChip selected（Primary 填充，白色文字） |
| 未选中 | FilterChip unselected（OutlineVariant 描边） |
| 「＋ 新增」 | Outline 色描边，点击弹 AlertDialog |

- 分类多时横向滚动，「＋ 新增」始终在末尾
- 点击已选中的 Chip 取消选中（切换为无分类）
- 分类列表为空时 Chip 行仅显示「＋ 新增」
- 剪切板保存与手动输入保存**共享同一个 `selectedCategory`**，无需重复选择

#### 手动输入卡规格

| 元素 | 规格 |
|------|------|
| 容器 | Card，圆角 12dp，背景 Surface，描边 Outline 1dp（v1.4 起显式 `BorderStroke` 落地） |
| TextField | `OutlinedTextField`，`minLines=3, maxLines=6`，超出滚动 |
| placeholder | 「在此输入要保存的文字」 |
| 保存按钮启用条件 | `text.trim().isNotBlank() && !isManualSaving` |
| 保存成功后 | TextField 自动清空，分类选中**保留** |
| 保存失败后 | TextField 内容**保留**，便于修改重试 |

#### 保存按钮状态（剪切板卡 / 手动输入卡 各自独立）

| 状态 | 按钮文字 | 触发条件 |
|------|---------|---------|
| 禁用（手动输入） | 保存到文件 ▶（低对比） | 输入为空 / 全空白 |
| 可保存 | 保存到文件 ▶ | 内容非空 + 非保存中 |
| 保存中 | 保存中…（禁用） | IO 进行中 |

> 两个保存按钮的「保存中」状态相互**独立**，剪切板保存进行中不影响手动输入按钮可点击性，反之亦然。

---

### 3.2 设置页（SettingsScreen，v1.4）

```
┌─────────────────────────────────────┐
│  ←  设置         TopAppBar（surface色）
├─────────────────────────────────────┤
│  保存目标文件           titleMedium  │  ← SectionHeader（onSurface 色）
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
│  │  [选择保存文件]               │  │
│  └───────────────────────────────┘  │
│                                     │
├── ★ 分类管理区块 ───────────────────── ┤
│  分类管理               titleMedium │  ← SectionHeader（onSurface 色）
│  ─────────────────────────────────  │
│                                     │
│  ⠿  工作       ✎ 重命名   ✕ 删除   │  ← 自绘 2×3 圆点拖拽手柄在左侧
│  ⠿  学习       ✎ 重命名   ✕ 删除   │
│  ⠿  生活       ✎ 重命名   ✕ 删除   │
│                                     │
│  【空列表状态（替代上方列表）】        │
│  暂无分类，点击下方按钮添加  bodySmall│
│                                     │
│  [       ＋ 新增分类       ]         │  ← OutlinedButton，全宽
│                                     │
│  重命名分类不会修改已保存的记录。      │  ← bodySmall, OnSurfaceVariant 色
└─────────────────────────────────────┘
```

> v1.4 关键变更：
> - **顶栏去 Primary**：TopAppBar 从 Primary 填充改为 surface 色（M3 推荐做法），与主页顶栏基调一致，减少色彩冲击。
> - **分区标题统一**：抽出 `SectionHeader` Composable，「保存目标文件 / 全局悬浮窗 / 分类管理」三块共用，标题色从 `primary` 改为 `onSurface`。
> - **拖拽手柄升级**：从文本字符 `⠿` 改为自绘 2×3 圆点 `DragHandle`（`Canvas`），不依赖 material-icons-extended，视觉更规整、零体积代价。
> - **去装饰性箭头**：「选择保存文件 ▶」改为「选择保存文件」。

#### 分类拖拽排序规范

- 使用 `sh.calvin.reorderable` 库（`LazyColumn` + `ReorderableItem`）
- 长按拖拽手柄（自绘 2×3 圆点 `DragHandle`）触发，拖动中使用 `animateDpAsState` 提升阴影高度（0dp → 8dp）
- 松手后顺序实时写入 DataStore
- 外层为 `Column + verticalScroll`；内层 LazyColumn 设 `userScrollEnabled=false`、`heightIn(max=400.dp)` 避免嵌套滚动冲突

#### 设置页「全局悬浮窗」区块（v1.3 新增，v1.4 标题色随 SectionHeader 统一）

```
├── ★ 全局悬浮窗区块（v1.3）────────────── ┤
│  全局悬浮窗            titleMedium  │  ← SectionHeader（onSurface 色）
│  ─────────────────────────────────  │
│  启用全局悬浮窗            [ ●—— ]  │  ← 标签 + Switch（右侧）
│  开启后将在所有应用之上显示一个贴边     │  ← bodySmall 说明
│  悬浮窗，点击展开快捷操作。需要授予     │
│  "显示在其他应用上层"权限。            │
```

- Switch 反映持久化的 `overlayEnabled`；开启时若无权限跳系统授权页，授权返回后才置开并启动服务
- 进设置页时对账：若 `overlayEnabled` 为开但权限已被撤销，自动回退为关

---

### 3.3 全局悬浮窗（OverlayService，v1.3 — QS-0003）

叠加在所有 App 之上的 `TYPE_APPLICATION_OVERLAY`（经典 Android View，非 Compose）。

#### 三种状态

```
① 贴边收起（常驻）          ② 点击展开                ③ 录音中
┌──────────────┐          ┌──────────────┐          ┌──────────────┐
│           ▎  │          │   ┌────────┐ │          │   ┌────────┐ │
│           ▎  │ ←半透明   │   │文字│录音│ │ ←横排    │   │文字│●录音中│ │ ←红色+计时
│           ▎  │   竖条把手 │   └────────┘ │   面板    │   └────────┘ │
└──────────────┘          └──────────────┘          └──────────────┘
  可上下拖动改位置            点按钮/点面板外收回         收起态把手变红+红点
```

#### 规格

| 元素 | 规格 |
|------|------|
| 把手 | 半透明竖条（约 14×54dp），圆角；待机蓝 `argb(140,80,140,255)`、录音中红 `argb(170,255,70,70)` |
| 红点 | 8dp 圆点，录音中显示在把手右上角 |
| 展开面板 | 深色圆角横排容器，含【文字输入】【录音】两按钮；点面板外区域（`FLAG_WATCH_OUTSIDE_TOUCH`）收回 |
| 录音按钮态 | 待机「录音」；录音中「录音中 mm:ss」红色。**固定宽度 + 等宽数字（`tnum`）**，计时更新不引起面板重排（防抖动） |
| 交互 | 点把手展开；拖动把手改纵向位置、松手吸附最近左/右边；点按钮或点面板外收回贴边 |
| 位置持久化 | 贴边方向 + 纵向比例存 DataStore，重开 App 恢复 |

#### 透明输入窗（InputActivity）

点【文字输入】拉起：半透明遮罩 + 居中卡片，**复用主页**的分类 Chip 行 + 多行输入卡（`ManualInputCard`）+ 保存按钮。

| 行为 | 规格 |
|------|------|
| 主题 | `Theme.QuickSave.Transparent`（半透明、背景调暗） |
| 内容 | 分类 Chip 行（含「＋ 新增」对话框）+ 多行输入框 + 「保存到文件 ▶」 |
| 保存成功 | Toast「已保存」并 `finish()` |
| 保存失败 | Toast「保存失败：{原因}」，窗口保留 |
| 取消 | 点卡片外遮罩或返回键 → `finish()`，不写入 |

---

## 四、通知设计

### 4.1 常驻通知（应用唯一一条）

| 属性 | 规格 |
|------|------|
| 渠道 | `quicksave_channel`，IMPORTANCE_LOW |
| 标题 | "QuickSave" |
| 正文 | "点击打开应用"（v1.3：原"…保存剪切板内容"改为通用文案，因应用已含手动输入/悬浮窗/录音） |
| 优先级 | LOW（不弹横幅，不响铃） |
| 样式 | Ongoing（不可滑除） |
| 点击动作 | 打开 MainActivity |
| 生命周期 | 应用运行期间持续显示 |

> v1.3：悬浮窗服务降级为非前台、不再单独发通知，故常驻通知收成**一条**。（系统因 `SYSTEM_ALERT_WINDOW` 会另显示一条「正在其他应用上层显示」的系统提示，非本应用通知、不可控。）

### 4.2 录音通知（临时，仅录音期间，v1.3）

| 属性 | 规格 |
|------|------|
| 渠道 | `quicksave_recorder_channel`，IMPORTANCE_LOW |
| 标题 | "正在录音" |
| 正文 | 计时 `mm:ss`（每秒更新） |
| 操作 | 「停止」（结束并落盘） |
| 生命周期 | 录音开始时出现，停止后消失 |

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

### 6.1 悬浮窗 Toast（v1.3，无 Scaffold 环境用 Toast）

| 场景 | 消息 |
|------|------|
| 悬浮窗输入保存成功 | "已保存" |
| 悬浮窗输入保存失败 | "保存失败：{原因}" |
| 录音停止落盘 | "录音已保存" |
| 录音权限被拒 | "需要麦克风权限才能录音" |
| 录音启动失败 | "录音启动失败：{原因}" / "无法创建录音文件" |

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

---

## 十、Feature UI 文档索引

| feature_id | 范围 | 状态 | 文档 | mockups |
|------------|------|------|------|---------|
| `QS-0001` | MVP（剪切板保存、目标文件配置、清空、常驻通知） | 已交付 | [features/QS-0001/ui-mvp.md](features/QS-0001/ui-mvp.md) | [features/QS-0001/mockups/](features/QS-0001/mockups/) |
| `QS-0002` | 手动输入保存（主页新增多行输入卡，Chip 行上移到最顶层） | 已交付 v1.2 | [features/QS-0002/ui-manual-input.md](features/QS-0002/ui-manual-input.md) | [features/QS-0002/mockups/](features/QS-0002/mockups/) |
| `QS-0003` | 全局悬浮窗（贴边把手 + 横排面板 + 透明输入窗 + 录音态可视化）、设置页总开关、通知收成一条 | 已交付 v1.3 | [features/QS-0003/design-floating-window.md](features/QS-0003/design-floating-window.md) | — |
