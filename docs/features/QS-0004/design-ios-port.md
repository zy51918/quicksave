# QuickSave QS-0004 — iOS 详细设计

> feature_id：`QS-0004`
> feature_name：`ios-port`
> 版本：1.0
> 日期：2026-09-21
> 状态：实现中
> 依据：[arch-ios-port.md](arch-ios-port.md)

## 一、范围

本阶段实现 iOS 原生主 App 与 Share Extension：

- SwiftUI 主页与设置页
- 前台读取文字剪切板
- 手动输入保存
- 分类新增、重命名、删除、排序
- 用户选择目标文本文件、security-scoped bookmark 持久化
- 追加保存、清空文件及错误反馈
- Share Sheet 直接自动保存文字到当前目标文件
- App Group 共享配置与文件 bookmark
- App Intents / Shortcuts 提供打开 QuickSave 的系统快捷入口

本阶段不实现录音、全局悬浮窗、常驻通知和后台剪切板监听。

## 二、目录与职责

| 路径 | 职责 |
|---|---|
| `ios/QuickSave/Models/` | 保存格式、错误、UI 状态模型 |
| `ios/QuickSave/Storage/` | UserDefaults、bookmark 文件访问、App Group payload |
| `ios/QuickSave/Repositories/` | ClipRepository 协议和实现 |
| `ios/QuickSave/ViewModels/` | Home/Settings ObservableObject 状态与动作编排 |
| `ios/QuickSave/Views/` | SwiftUI 页面、卡片、分类编辑控件 |
| `ios/QuickSaveShare/` | Share Extension，提取 `public.text` 并写入 App Group |
| `ios/QuickSaveTests/` | XCTest 业务层与 ViewModel 测试 |

## 三、数据流

### 3.1 保存

`HomeView` → `HomeViewModel.saveClipboard/saveManualInput` → `ClipRepository.saveEntry` → `EntryFormatter` 格式化 → `BookmarkFileDataSource.appendLine` → `Feedback`。

`ClipRepository` 在同一个 `FileIOActor` 中串行所有追加和清空操作；目标文件不可用时返回结构化 `ClipError`，不让文件 URL 或 Foundation 异常穿透到 View。

### 3.2 文件选择

`SettingsView.fileImporter` 返回 security-scoped URL → `BookmarkFileDataSource.makeBookmark` → `PreferencesStore.targetFileBookmark` → 后续 I/O 解析 bookmark 并成对调用 `startAccessingSecurityScopedResource/stopAccessing...`。

### 3.3 Share Extension

`ShareViewController` 要求单个 `NSExtensionItem`、单个 `public.text` provider，并拒绝 URL 或混合附件；提取文字后复用 `ClipRepositoryImpl.saveEntry`，读取 App Group 中的当前分类和目标文件 bookmark，自动保存并关闭 Extension。成功显示「已保存」，失败显示具体错误后取消 Extension。

## 四、状态模型

`HomeViewModel`：

- `targetFileConfigured`
- `clipText`
- `manualInputText`
- `categories`
- `selectedCategory`
- `isClipSaving`
- `isManualSaving`
- `showClearConfirmation`
- `feedback`

保存成功仅清空 `manualInputText`；失败保留输入。`isClipSaving` 与 `isManualSaving` 相互独立。`selectedCategory` 始终由分类列表校验，不存在的持久化值视为 nil。

`SettingsViewModel`：

- 显示文件 bookmark 是否存在
- 新增/重命名/删除/排序分类
- 选择或清除目标文件

## 五、测试设计

- `EntryFormatterTests`：有/无分类、固定日期、固定时区、换行结尾。
- `ClipRepositoryTests`：未配置文件、不可用文件、追加、清空、IO 错误映射。
- `HomeViewModelTests`：空白守卫、成功清空、失败保留、分类传递、两路 saving 状态独立。
- `SharedPayloadStoreTests`：写入、读取、一次性消费。

测试使用临时目录和内存 `PreferencesStore`，不依赖真实 `UIDocumentPicker`、剪切板或系统权限。
