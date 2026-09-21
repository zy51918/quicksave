# QuickSave QS-0004 — iOS 版架构设计

> feature_id：`QS-0004`
> feature_name：`ios-port`
> 版本：1.0
> 日期：2026-09-21
> 状态：已批准，阶段 B 实现中
> 依据：Android 项目级 PRD v1.3、UI v1.4、ARCH v1.3；QS-0001 / QS-0002 / QS-0003

本文档定义 QuickSave iOS 原生版的架构边界。iOS 版与现有 Android 模块并存于同一仓库，但不引入 Kotlin Multiplatform；两端共享产品协议与保存格式，不共享 UI 或平台 I/O 实现。

---

## 一、平台适配结论

| Android 能力 | iOS 公开 API 适配 | iOS 版行为 |
|---|---|---|
| 主页前台读取剪切板 | `UIPasteboard.general.string`，仅在 App 进入前台或主页显示时读取 | 保留；不做后台轮询，遵守 iOS 剪切板隐私提示 |
| 常驻前台通知 / 前台 Service | iOS 没有应用常驻通知或等价 Service | 不实现；App 图标与系统通知权限不承担常驻状态 |
| SAF 选择并持久化文件 | `fileImporter` / `UIDocumentPickerViewController` + security-scoped URL bookmark | 保留；持久化 bookmark，写入前 `startAccessingSecurityScopedResource()` |
| `WindowManager` 全局悬浮窗 | iOS 第三方 App 无公开 API 在其他 App 上层绘制 | 不实现；用 Share Extension 接收其他 App 分享的文字，用 App Intents / Shortcuts 提供系统级快捷入口 |
| `MediaRecorder` 前台录音服务 | iOS 录音能力本阶段延期 | 暂不实现；后续 feature 单独评审 AVAudioSession、后台音频模式与中断恢复 |
| Android 公共 `Music/QuickSave` | 本阶段不创建录音目录 | 录音文件位置和导出策略随录音 feature 一并确定 |
| DataStore Preferences | `UserDefaults`（轻量配置） | 保留配置字段语义；分类以 `[String]` 原生数组保存 |

不使用私有 API、越狱能力或模拟 Android 悬浮窗的规避方案。

## 二、技术栈与部署目标

- Swift 5.9+，SwiftUI，Observation/`ObservableObject` 状态模型。
- iOS 16.0+：使用 `fileImporter`、`NavigationStack`、`ShareLink` 等稳定公开 API；Share Extension 与主 App 共用 App Group。
- 本地文件：Foundation `FileHandle` / `Data`，UTF-8；不上传网络。
- 配置：`UserDefaults`；文件访问凭据使用 security-scoped bookmark `Data`。
- 测试：XCTest（Foundation 业务层、Repository、ViewModel）；UI 测试由后续测试阶段补充。
- 工程目录：`ios/QuickSave/` 主 App、`ios/QuickSaveShare/` Share Extension、`ios/QuickSaveTests/` 单元测试；用 Xcode 工程承载两个 target。

## 三、模块划分

```
┌──────────────────────────── iOS UI Layer ────────────────────────────┐
│ QuickSaveApp + NavigationStack                                       │
│   ├── HomeView                                                       │
│   ├── SettingsView                                                   │
│   ├── CategoryNameSheet / ClearFileConfirmation                      │
│   └── ShareImportView (Share Extension)                              │
├──────────────────────────── ViewModel Layer ─────────────────────────┤
│ HomeViewModel : ObservableObject                                      │
│ SettingsViewModel : ObservableObject                                 │
├──────────────────────────── Repository Layer ─────────────────────────┤
│ ClipRepository                                                       │
│   ├── ClipRepositoryImpl                                             │
│   └── SharedContainerClipRepository (Share Extension adapter)        │
├──────────────────────────── Data Source Layer ────────────────────────┤
│ PreferencesStore                                                     │
│ BookmarkFileDataSource                                                │
├──────────────────────────── System Integration ──────────────────────┤
│ ClipboardReader · DocumentPicker                                     │
│ AppIntents / Shortcuts · Share Extension                              │
└───────────────────────────────────────────────────────────────────────┘
```

依赖方向：SwiftUI View → ViewModel → Repository → Data Source；系统适配器只由 Repository/Data Source 使用。Share Extension 不直接依赖主 App 的 ViewModel，通过 App Group 共享待导入文字或直接复用共享 Repository 协议。

## 四、跨模块协议

### 4.1 `ClipRepository`

```swift
protocol ClipRepository {
    func saveEntry(text: String, category: String?) async -> Result<Void, ClipError>
    func setTargetFile(bookmark: Data?) async
    var targetFileBookmark: AsyncStream<Data?> { get }
    func clearSavedFile() async -> Result<Void, ClipError>
    var categories: AsyncStream<[String]> { get }
    func setCategories(_ categories: [String]) async
    var selectedCategory: AsyncStream<String?> { get }
    func setSelectedCategory(_ category: String?) async
}
```

Repository 负责读取当前配置、校验文件可访问性、拼接格式并将具体 Foundation 错误映射为 `ClipError`。UI 不接触 URL bookmark 或 `FileHandle`。

### 4.2 保存格式

与 Android 完全一致，使用当前本地时区：

- 有分类：`[分类名][yyyy-MM-dd HH:mm:ss] 文字内容\n`
- 无分类：`[yyyy-MM-dd HH:mm:ss] 文字内容\n`

时间格式化集中在可注入的 `EntryFormatter`，测试使用固定 `Date` 和 `TimeZone`，避免直接测试当前时间。

### 4.3 配置字段

| 字段 | iOS 类型 | 默认值 | 说明 |
|---|---|---|---|
| `target_file_bookmark` | `Data?` | `nil` | security-scoped bookmark；不保存裸 URL |
| `categories` | `[String]` | `[]` | 单行分类名，去空白、去换行 |
| `selected_category` | `String?` | `nil` | 不在 categories 中时运行时视为 nil |

配置键统一放入 `PreferencesStore`，Share Extension 需要读取的键使用 App Group suite；不把剪切板原文长期写入 UserDefaults。

## 五、核心数据流

### 5.1 主 App 剪切板保存

```
scenePhase .active / HomeView appearance
  → ClipboardReader.readString()
  → HomeViewModel.clipText
  → User taps save
  → ClipRepositoryImpl.saveEntry(text, selectedCategory)
  → resolve bookmark + start security-scoped access
  → FileHandle seekToEnd + UTF-8 append
  → HomeViewModel SaveResult
  → SwiftUI alert/banner feedback
```

iOS 剪切板读取仅发生在 App 前台，避免后台访问和不必要的系统隐私提示；用户仍需显式点保存，不做复制即自动保存。

### 5.2 其他 App 分享文字

```
Other App Share → QuickSave Share Extension
  → 读取 NSExtensionItem / NSItemProvider public.text
  → 写入 App Group pending payload（含文本，不含网络数据）
  → 打开主 App（若系统允许）/ 用户回到 QuickSave 后消费 payload
  → 复用 HomeViewModel 手动输入保存链路
```

Extension 只负责导入文字和展示轻量确认；无法保证在所有宿主 App 中自动拉起主 App，因此 UI 必须提供「已保存到待处理内容」的确定反馈，并在主 App 激活时消费 pending payload。不可把 Share Extension 当作 Android 常驻悬浮窗的等价物。

## 六、状态与错误模型

### 6.1 Home 状态

`HomeViewModel` 持有与 Android 对齐的字段：`targetFileAvailable`、`clipText`、`manualInputText`、`categories`、`selectedCategory`、`isClipSaving`、`isManualSaving`、`showClearConfirmation`、`feedback`。手动输入成功清空，失败保留原文；两条保存状态独立。

### 6.2 错误映射

| `ClipError` | 触发 | UI 文案 |
|---|---|---|
| `.targetFileNotConfigured` | bookmark 为空 | 请先在设置中选择保存文件 |
| `.targetFileUnavailable` | bookmark 失效、用户撤销访问 | 文件无写入权限，请重新选择 |
| `.encodingFailed` / `.io(Error)` | UTF-8 或文件写入失败 | 保存失败：{原因} |
| `.emptyClipboard` | 无可保存文字 | 剪切板为空，请先复制文字 |

清空文件继续使用二次确认；成功反馈「文件内容已清空」，失败沿用文件错误映射。

## 七、并发、生命周期与安全

- `HomeViewModel` 和 Repository 方法使用 Swift concurrency；文件写入在 actor（`BookmarkFileDataSource`）内串行，避免同时追加导致行交错。
- `PreferencesStore` 在 MainActor 上发布 UI 状态，底层 UserDefaults 操作保持轻量。
- security-scoped URL 每次 I/O 成对调用 `startAccessing...` / `stopAccessing...`；bookmark stale 时返回 `.targetFileUnavailable`，不静默覆盖。
- Share Extension 与主 App 使用 App Group 最小共享数据；消费 pending payload 后立即删除，避免剪切板内容长期残留。
- 不实现后台剪切板轮询、不申请无关权限、不使用网络或私有 API。

## 八、备选方案与取舍

| 决策点 | 备选 | 采用方案 | 理由 |
|---|---|---|---|
| 跨端技术 | Kotlin Multiplatform | 独立 SwiftUI 原生实现 | 当前仓库为 Android Kotlin；iOS 平台 I/O 与生命周期差异大，KMP 会扩大首期改动面 |
| 其他 App 入口 | 私有悬浮窗 / 后台轮询 | Share Extension + App Intents/Shortcuts | 仅使用 App Store 合规公开能力 |
| 文件持久化 | 仅 App 沙盒 | 用户选文件 + security-scoped bookmark | 保留 Android「用户指定目标文件」核心价值 |
| 状态管理 | UIKit MVC / Redux | SwiftUI + ObservableObject | 与现有页面复杂度匹配，保持单向数据流且依赖少 |

## 九、测试边界

阶段 B 至少覆盖：

- `EntryFormatter` 两种分类格式、固定时区和换行结尾。
- `ClipRepositoryImpl` 未配置文件、bookmark 无权限、追加、清空、写入失败映射。
- `HomeViewModel` 空白输入守卫、成功清空、失败保留、两路保存状态独立、分类有效性过滤。
- Share Extension 的 public.text 提取与 pending payload 一次性消费。

真机验证（阶段 B/测试阶段）：文件选择后重启、Files 权限撤销、Share Sheet 导入和 VoiceOver。
