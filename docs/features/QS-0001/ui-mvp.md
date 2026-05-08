# QuickSave QS-0001 (MVP) — UI 交互设计文档

> feature_id：`QS-0001`
> feature_name：`mvp`
> 版本：1.0
> 日期：2026-05-07
> 作者：HIE 设计师
> 状态：已交付（v1.0 首发）
> 关联文档：[`docs/features/QS-0001/prd-mvp.md`](prd-mvp.md) · [`docs/UI.md`](../../UI.md)

本文档约束 MVP（QS-0001）相关的页面、组件、对话框与通知，**不涉及分类标签**（已分别由后续 `category-tag` feature 在 `docs/UI.md` 整合）。

---

## 一、页面与状态

QS-0001 包含两个页面、若干状态：

| 页面 | 状态 |
|------|------|
| 主页 HomeScreen | 正常态（有剪切板 + 已配置文件） / 未配置文件警告态 / 剪切板为空态 |
| 设置页 SettingsScreen | 已配置文件态 / 未配置文件态 |

---

## 二、主页（HomeScreen）

### 2.1 布局结构

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

> 后续 `category-tag` feature 会在剪切板内容卡的"内容预览"和"保存按钮"之间插入 `FilterChip` 行，本 feature 范围内不存在该行。

### 2.2 组件规格

| 组件 | 规格 |
|------|------|
| 标题「QuickSave」 | `headlineMedium` 28sp / 500，OnSurface 色 |
| 「设置」按钮 | OutlinedButton，宽 72dp / 高 36dp，Primary 描边 1.5px，胶囊（radius=18dp） |
| 剪切板内容卡 | Card，宽全屏减 32dp（左右 16dp 边距），内边距 12dp，圆角 12dp，背景 PrimaryContainer (`#EADDFF`) |
| 卡内标签 | `labelSmall` 11sp / 500，OnSurfaceVariant |
| 卡内内容预览 | `bodyMedium` 14sp，OnPrimaryContainer (`#21005D`)，最多 3 行 + 省略号 |
| 「保存到文件 ▶」按钮 | FilledButton，宽 120dp / 高 36dp，Primary 填充，胶囊 |
| 「清空保存文件内容」按钮 | OutlinedButton，全宽，高 40dp，Error 色描边 1.5px，文字 Error 色 |
| 警告卡（未配置文件） | Card，左侧 4dp Error 色竖条，背景 Surface，内含 Error 色标题 + 「前往设置」OutlinedButton |
| 空剪切板提示 | `bodySmall` 12sp，OnSurfaceVariant，水平居中 |

### 2.3 保存按钮状态

| 状态 | 按钮文字 | 可点击 |
|------|---------|--------|
| 可保存（剪切板有内容 + 已配置文件） | `保存到文件 ▶` | ✓ |
| 保存中（IO 进行中） | `保存中…` | ✗ |
| 剪切板为空 | 隐藏（卡片不渲染，显示空态文字） | — |
| 未配置文件 | 隐藏（顶部仅显示警告卡） | — |

---

## 三、设置页（SettingsScreen）

### 3.1 布局结构

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
│  │  [选择保存文件 ▶]             │  │  ← FilledButton，Primary，全宽
│  └───────────────────────────────┘  │
│                                     │
│  保存的文字将追加到文件末尾，          │  ← bodySmall, OnSurfaceVariant
│  每条记录包含时间戳。                  │
│  示例：[2026-04-21 09:00:00] 这是…    │  ← bodySmall, Outline
└─────────────────────────────────────┘
```

> 后续 `category-tag` feature 会在本页底部追加「分类管理」区块，本 feature 范围内不存在该区块。

### 3.2 组件规格

| 组件 | 规格 |
|------|------|
| TopAppBar | 高 56dp，Primary 背景，左侧返回箭头 + 标题「设置」（白色，20sp / 500） |
| 区块标题「保存目标文件」 | `titleMedium` 16sp / 500，Primary 色 |
| 文件路径卡 | Card，背景 `#F7F2FA`，描边 OutlineVariant，圆角 12dp，内边距 12dp |
| 路径文字 | `bodyMedium` 13sp，OnSurface，单行省略尾部 |
| 「重新选择文件」按钮 | OutlinedButton，全宽，高 40dp，Primary 描边 |
| 警告卡（未配置） | 与主页一致：左 4dp Error 竖条 + Surface 背景 |
| 「选择保存文件 ▶」按钮 | FilledButton，卡内全宽（300dp），高 38dp，Primary 填充 |
| 说明 / 示例文字 | `bodySmall` 12sp，前者 OnSurfaceVariant，后者 Outline |

### 3.3 文件选择交互

1. 点击「重新选择文件」或「选择保存文件 ▶」
2. 调起系统 `ACTION_OPEN_DOCUMENT` 或 `ACTION_CREATE_DOCUMENT`（用户可在系统选择器自行切换）
3. MIME 类型限定为 `text/plain`，默认文件名 `quicksave.txt`
4. 选择后通过 `ContentResolver.takePersistableUriPermission()` 持久化授权
5. 路径回填到设置页文件路径卡

---

## 四、对话框设计

### 4.1 清空保存文件确认（US-05）

```
┌─────────────────────────────────┐
│  清空保存文件                    │
│                                 │
│  确认清空文件内全部内容？         │
│  此操作不可恢复。                 │
│                                 │
│                  [取消]  [清空] │  ← 「清空」为 Error 色
└─────────────────────────────────┘
```

| 元素 | 规格 |
|------|------|
| 容器 | AlertDialog，圆角 28dp，背景 Surface |
| 标题 | `headlineSmall` 20sp / 500，OnSurface |
| 正文 | `bodyMedium` 14sp，OnSurfaceVariant |
| 「取消」按钮 | TextButton，Primary 色 |
| 「清空」按钮 | TextButton，**Error 色**（与「取消」并列右下，强调危险性） |
| 触发 | 主页底部「清空保存文件内容」OutlinedButton 点击 |
| 关闭行为 | 点击对话框外（scrim）或返回键 → 关闭，不执行清空 |

---

## 五、Snackbar 反馈规范（MVP 范围）

| 场景 | 消息 | 时长 | 触发用户故事 |
|------|------|------|-------------|
| 保存成功 | `已保存` | SHORT | US-02 |
| 文件已清空 | `文件内容已清空` | SHORT | US-05 |
| 未配置文件即点保存 | `请先在设置中选择保存文件` | LONG | 边界条件 |
| 权限丢失 | `文件无写入权限，请重新选择` | LONG | 边界条件 |
| 保存失败（其他 IO） | `保存失败：{错误信息}` | LONG | 边界条件 |

> Snackbar 默认在屏幕底部、TopAppBar 之外显示；同一时刻最多一条，新消息替换旧消息。

---

## 六、通知设计

### 6.1 常驻通知（US-01）

| 属性 | 规格 |
|------|------|
| 渠道 ID | `quicksave_channel` |
| 渠道名 | `QuickSave 常驻通知` |
| 重要性 | `IMPORTANCE_LOW`（不弹横幅、不响铃、不震动） |
| 标题 | `QuickSave` |
| 正文 | `点击打开应用保存剪切板内容` |
| 图标 | 应用图标（紫色圆形，白色 Q 字） |
| 样式 | Ongoing（用户不可滑动移除） |
| 点击动作 | `PendingIntent` 启动 `MainActivity`（清栈到根） |
| 生命周期 | 应用前台服务运行期间持续展示，服务停止后自动撤销 |

---

## 七、页面导航

```
MainActivity
  └── NavHost
        ├── HomeScreen（默认路由 "home"）
        │     └── [设置] 按钮 → navigate("settings")
        └── SettingsScreen（路由 "settings"）
              └── ← TopAppBar 返回键 → popBackStack()
```

- 通知点击 → MainActivity → 默认进入 HomeScreen
- 不使用底部导航栏，仅 push / pop 两层栈

---

## 八、主流程串联（流程 A：保存剪切板）

```
通知栏点击「QuickSave」常驻通知
        ↓
MainActivity 启动 → HomeScreen
        ↓
读取系统剪切板 → 渲染剪切板内容卡
        ↓
（如未配置文件）顶部展示警告卡 → 用户跳转设置页（流程 B）
        ↓
用户点击「保存到文件 ▶」
        ↓
按钮切换为「保存中…」并禁用
        ↓
追加 [yyyy-MM-dd HH:mm:ss] 内容\n 到目标文件
        ↓
按钮恢复 → Snackbar「已保存」
```

---

## 九、设计图索引

| 文件 | 内容 | 关联用户故事 |
|------|------|-------------|
| [mockups/home-normal.svg](mockups/home-normal.svg) | 主页 — 正常状态（有剪切板内容 + 已配置文件） | US-02, US-05 |
| [mockups/home-no-file.svg](mockups/home-no-file.svg) | 主页 — 未配置文件警告状态 | US-04 |
| [mockups/settings.svg](mockups/settings.svg) | 设置页（已配置 / 未配置两种状态） | US-04 |
| [mockups/notification.svg](mockups/notification.svg) | 常驻通知设计 | US-01 |
| [mockups/dialog-clear-confirm.svg](mockups/dialog-clear-confirm.svg) | 清空保存文件 — 二次确认对话框 | US-05 |
| [mockups/index.html](mockups/index.html) | 全部设计图查看器 | — |

---

## 十、与项目级设计的关系

- 本 feature 的色彩、字体、圆角、间距、按钮形状全部沿用项目级 [`docs/UI.md`](../../UI.md) §二「设计规范」，不引入新 Token
- 本 feature 不修改任何全局组件
- `category-tag` feature 在本 feature 设计基础上**增量**叠加 FilterChip 行与设置页「分类管理」区块；MVP 设计本身保持不变
