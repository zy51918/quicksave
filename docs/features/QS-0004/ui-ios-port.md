# QuickSave QS-0004 — iOS UI 交互设计

> feature_id：`QS-0004`
> feature_name：`ios-port`
> 版本：1.0
> 日期：2026-09-22
> 作者：HIE 设计师
> 状态：已实现
> 依据：[arch-ios-port.md](arch-ios-port.md)、[design-ios-port.md](design-ios-port.md)、项目级 [UI.md](../../UI.md)

## 一、范围

本文档定义 iOS 原生版的界面与反馈规范，覆盖主页、设置页与 Share Extension 三处。iOS 版与 Android 版保持**信息架构与文案一致**，差异只在平台呈现方式上。

| 页面 | 平台载体 | 对齐 Android |
|---|---|---|
| 主页 | `HomeView`（SwiftUI） | `HomeScreen` |
| 设置页 | `SettingsView`（SwiftUI） | `SettingsScreen` |
| 分享接收 | `ShareViewController`（UIKit，Extension） | `ShareReceiverActivity` |

## 二、设计原则

沿用项目级 §一，iOS 侧补充一条平台约束：

| 原则 | iOS 落地 |
|---|---|
| **极简** | 主页 + 设置页两个页面，Share Extension 无常驻 UI |
| **即时反馈** | 结果反馈用 **Toast 浮层**，不使用阻塞式 alert |
| **危险操作醒目** | 清空仍用红色按钮 + 二次确认 alert |
| **单手操作** | 保存按钮在卡片内，拇指易触及 |

> **平台差异说明**：Android 用系统 `Toast`（浮在其他 App 之上，不阻塞）。iOS 无等价系统 API，主 App 内以自绘浮层还原；Share Extension 受系统 sheet 约束，呈现为 sheet 内的胶囊提示（见 §五）。

## 三、Toast 反馈规范

### 3.1 主 App Toast

组件：`QuickSaveToast` + `.quickSaveToast(_:)` modifier（`ios/QuickSave/Views/QuickSaveStyle.swift`）

**视觉规格**

```
┌────────────────────────────────────────┐
│  ✓  已导入分享内容，请确认后保存        │   ← 深色胶囊，圆角 14pt
└────────────────────────────────────────┘
   ↑ 图标 18×18          ↑ subheadline，白色
```

| 项目 | 规格 |
|---|---|
| 位置 | 屏幕底部，距安全区底 16pt，左右留白 20pt |
| 背景 | `quickSaveInk` 94% 不透明（深墨色） |
| 圆角 | 14pt continuous |
| 阴影 | 黑色 18% / radius 12 / y 4 |
| 图标 | 成功 `checkmark.circle.fill`（`quickSaveTealPale`）；失败 `exclamationmark.triangle.fill`（`quickSaveCoralPale`）；均 18×18 |
| 文字 | `.subheadline`，白色，左对齐，可多行 |
| 出现 | spring 动画（response 0.35 / damping 0.85），自底部移入 + 淡入 |
| 关闭 | 超时自动消失；点击浮层立即关闭 |
| 无障碍 | 合并为单元素，标记 `.isStaticText` |

**时长（对齐 Android `Toast`）**

| 场景 | 消息 | 时长 |
|---|---|---|
| 保存成功 | "已保存" | 2.0s（≈ Android `LENGTH_SHORT`） |
| 导入分享内容 | "已导入分享内容，请确认后保存" | 2.0s |
| 文件已清空 | "文件内容已清空" | 2.0s |
| 未配置文件 | "请先在设置中选择保存文件" | 3.5s（≈ Android `LENGTH_LONG`） |
| 权限丢失 | "文件无写入权限，请重新选择" | 3.5s |
| 保存失败（其他） | "保存失败：{原因}" | 3.5s |
| 剪切板为空 | "剪切板为空，请先复制文字" | 3.5s |
| 文件选择失败 | "无法选择文件：{原因}" | 3.5s |
| 文件创建失败 | "无法创建文件：{原因}" | 3.5s |

### 3.2 不用 Toast 的场景（保留 alert）

与 Android 保持一致的**输入 / 确认类**交互继续使用系统 alert：

| 场景 | 形式 | 对齐 Android |
|---|---|---|
| 清空保存文件 | 二次确认 alert | `AlertDialog` |
| 新增分类 | alert + 文本框 | `CategoryNameDialog` |
| 重命名分类 | alert + 文本框（重名/空值禁用确定） | `CategoryNameDialog` |

## 四、页面设计

### 4.1 主页（HomeView）

结构与项目级 §3.1 一致，自上而下：

| 区块 | 说明 |
|---|---|
| 未配置文件警告卡 | 仅 `targetFileConfigured == false` 时显示，含「去选择文件」跳设置页 |
| 分类 Chip 行 | 顶层共享，末尾「＋ 新增」；点击已选中 Chip 取消选中 |
| `FROM CLIPBOARD` / 刚刚复制的内容 | 读剪切板；无内容时显示空态提示 |
| `OR WRITE IT HERE` / 手动记录 | 多行输入 + 保存按钮 |
| 清空保存文件内容 | 仅已配置文件时显示 |

**反馈挂载点**：`.quickSaveToast($model.feedback)` 挂在 `NavigationStack` 根，位于滚动区之外，浮层覆盖全页。

### 4.2 设置页（SettingsView）

| 区块 | 说明 |
|---|---|
| 保存目标文件 | 已配置：显示状态 + 重新选择 + 移除；未配置：警告 + 「选择保存文件」 |
| 分类管理 | 列表 + 重命名/删除 + 拖拽排序 + 「＋ 新增分类」 |

**反馈挂载点**：`.quickSaveToast($errorMessage)` 处理文件选择/创建失败（`Feedback?` 类型）。

## 五、Share Extension

`ShareViewController`（UIKit）在系统分享面板被选中后弹出。

**平台约束**：Extension 由系统以 sheet 呈现，无法像 Android 透明 Activity 那样浮在原 App 之上；`view.backgroundColor = .clear` 也不能完全去掉系统底板。因此呈现为 **sheet 内的居中胶囊**，而非系统级 Toast。

**三态规格**

| 状态 | 图标 | 图标色 | 文案 | 停留 |
|---|---|---|---|---|
| 进行中 | `ellipsis.circle` | 白 | "正在保存分享内容…" | — |
| 成功 | `checkmark.circle.fill` | `quickSaveTealPale` | "已保存" | 1.5s 后关闭 |
| 失败 | `exclamationmark.triangle.fill` | `quickSaveCoralPale` | 具体错误 | 1.5s 后取消 |

胶囊规格与主 App Toast 一致（墨色 94%、圆角 14pt、图标 18×18、间距 10pt、内边距 14×12）。

**失败文案**

| 场景 | 消息 |
|---|---|
| 附件非单个 `public.text` 或含 URL | "不支持的分享内容" |
| 读取失败 | "读取失败：{原因}" |
| 内容为空 | "分享内容为空" |
| 未配置目标文件 | "请先在设置中选择保存文件" |
| 文件无权限 | "文件无写入权限，请重新选择" |

> 时长说明：Android 用 `LENGTH_SHORT`(2s)，但其 Toast 不阻塞界面；iOS 扩展会遮住原 App，故缩短为 1.5s。

## 六、与 Android 的差异汇总

| 项目 | Android | iOS | 原因 |
|---|---|---|---|
| 反馈载体 | 系统 `Toast` / `Snackbar` | 自绘浮层 `QuickSaveToast` | iOS 无公开系统 Toast API |
| 分享接收页 | 透明 Activity，Toast 浮在原 App 上 | 系统 sheet + 胶囊提示 | Extension 呈现方式由系统决定 |
| 分享提示时长 | 2s | 1.5s | sheet 遮挡原 App，缩短减少打断 |
| 清空确认 | `AlertDialog` | SwiftUI alert | 一致 |
| 主页返回 | 返回键 | 无（iOS 无全局返回键） | 平台差异 |
| 设置入口 | TopAppBar 图标 | 导航栏齿轮图标 | 一致 |
| 分类拖拽 | 长按拖拽 | `EditButton` 进入编辑态 | 平台惯例 |

## 七、测试与验证

- 单元测试覆盖业务层与 ViewModel（`ios/QuickSaveTests/`），不依赖真实文件选择器、剪切板或系统权限。
- 模拟器可验证：构建、安装、启动、toast 显示与自动消失、页面布局。
- 需真机验证：Share Sheet 导入、文件选择后重启、Files 权限撤销、VoiceOver。