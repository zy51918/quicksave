# QuickSave QS-0005 — iOS 快捷入口架构设计

> feature_id：`QS-0005`
> feature_name：`ios-quick-entry`
> 版本：1.0
> 日期：2026-09-22
> 状态：待评审
> 依据：[prd-ios-quick-entry.md](prd-ios-quick-entry.md)、[ui-ios-quick-entry.md](ui-ios-quick-entry.md)、[QS-0004 arch-ios-port.md](../QS-0004/arch-ios-port.md)、项目级 [ARCH.md](../../ARCH.md)

## 一、范围与结论

在 iOS 版新增**控制中心控件**（`ControlWidget`，iOS 18+）作为快捷保存入口，复用既有保存链路，不引入第二套存储。

**核心结论（先读这一段）：**

1. **控件本身可行** —— `ControlWidget`/`ControlWidgetButton` API 已在 iOS 18 SDK 中稳定提供，见 §二 查证结果。
2. **文件写入可行** —— Share Extension 已在扩展进程内成功解析主 App 的 security-scoped bookmark 并追加写入（`QuickSaveShare/ShareViewController.swift:112-127`），该先例直接覆盖控件场景。
3. **⚠ 最大风险不是写文件，而是读剪切板** —— iOS 16+ 对程序化读取 `UIPasteboard` 会弹系统粘贴授权提示。在控件路径下，这个提示会把「一键保存」变成「一键 + 再点一次系统弹窗」，**可能使 UI 文档中的形态一（静默保存）不可行**。详见 §十一 R1。
4. **因此架构采用「形态无关」设计** —— 两种交互形态共用同一套 Intent 实现，仅由一个 `openAppWhenRun` 静态属性切换。无论实测结论如何，都不需要重构。

## 二、关键 API 查证结果

以下结论来自本地 iOS 27.0 SDK 的 `.swiftinterface` 头文件（权威一手来源），非推测。

### 2.1 控件 API（WidgetKit / SwiftUI）

| 符号 | 声明位置 | 可用性 |
|---|---|---|
| `ControlWidget` protocol | `SwiftUI.swiftinterface:6107` | `iOS 18.0+`，tvOS/visionOS unavailable |
| `StaticControlConfiguration<Content>` | `WidgetKit.swiftinterface:1127` | `iOS 18.0+` |
| `AppIntentControlConfiguration<Configuration, Content>` | `WidgetKit.swiftinterface:494` | `iOS 18.0+` |
| `ControlWidgetButton<Label, ActionLabel, Action>` | `WidgetKit.swiftinterface:1277` | `iOS 18.0+` |
| `ControlWidgetToggle<Label, ValueLabel, Action>` | `WidgetKit.swiftinterface:1182` | `iOS 18.0+` |
| `ControlValueProvider` protocol | `WidgetKit.swiftinterface:1119` | `iOS 18.0+`（`previewValue` + `currentValue()`） |

`ControlWidgetButton` 的三个初始化器（`WidgetKit.swiftinterface:1292-1298`）：

```swift
// ① 带动作态标签（点击后短暂显示 actionLabel）
init(action: Action, label: () -> Label, actionLabel: (Bool) -> ActionLabel) where Action: AppIntent
// ② 普通 AppIntent
init(action: Action, label: () -> Label) where Action: AppIntent
// ③ OpenIntent 专用（系统据此打开 App）
init(action: Action, label: () -> Label) where Action: OpenIntent
```

> 初始化器 ② 与 ③ 并存这一事实说明：**系统对「留在原处」和「打开 App」是两种显式区分的行为**，由 Action 的协议遵从决定，而非运行期猜测。这为 §一 第 4 点的「形态无关」设计提供了 API 层面的依据。

### 2.2 执行模式与落地位置

| 符号 | 位置 | 说明 |
|---|---|---|
| `AppIntent.openAppWhenRun` | `AppIntents.swiftinterface:3105` | **默认 false**（即后台执行是默认）；iOS 26 起标记 deprecated，改用 `supportedModes` |
| `AppIntent.supportedModes: IntentModes` | `AppIntents.swiftinterface:3107` | `anyAppleOS 26.0+`；`IntentModes` 含 `.background`、`.foreground(.immediate/.deferred/.dynamic)` |
| `AppIntent.allowedExecutionTargets` | `AppIntents.swiftinterface:3112` | `anyAppleOS 27.0+`；`IntentExecutionTargets` 含 `.main`、`.appIntentsExtension`、**`.widgetKitExtension`** |
| `ControlConfigurationIntent` | `AppIntents.swiftinterface:59` | 控件的配置意图（用户编辑控件参数时用），本 feature 不使用 |

> `.widgetKitExtension` 作为合法执行目标被显式列出，说明控件扩展进程内执行 Intent 是**被支持的路径**，而非未定义行为。

### 2.3 剪切板 API 在扩展中的可用性

`UIPasteboard.h` 中 `generalPasteboard`（第 52 行）**未标注** `NS_EXTENSION_UNAVAILABLE`，即 API 层面在扩展进程可访问。

但 API 可访问 ≠ 无副作用：iOS 16 起程序化读取剪切板内容会触发系统粘贴授权提示（详见 §十一 R1）。这是**运行时策略**，不体现在头文件标注上，必须真机实测。

## 三、Target 结构

新增一个 Widget Extension target，与既有 target 并列。

```
ios/
├── QuickSave/                 主 App（既有）
├── QuickSaveShare/            Share Extension（既有）
├── QuickSaveWidget/           ★ 新增：Widget Extension
│   ├── QuickSaveWidgetBundle.swift      @main WidgetBundle
│   ├── SaveClipboardControl.swift       ControlWidget 定义
│   ├── SaveClipboardIntent.swift        AppIntent 实现
│   └── Info.plist                       NSExtensionPointIdentifier
├── QuickSaveTests/            单元测试（既有）
└── QuickSave.xcodeproj
```

### 3.1 新增 target 配置要点

| 配置项 | 值 | 说明 |
|---|---|---|
| productType | `com.apple.product-type.app-extension` | 与 Share Extension 同类 |
| `PRODUCT_BUNDLE_IDENTIFIER` | `com.ylib.quicksave.ios.widget` | 必须以主 App bundle id 为前缀，否则安装报错（QS-0004 踩过） |
| `IPHONEOS_DEPLOYMENT_TARGET` | `18.0` | **控件要求 18.0**；主 App 保持 16.0 |
| `NSExtensionPointIdentifier` | `com.apple.widgetkit-extension` | 由 `GENERATE_INFOPLIST_FILE` 之外的 Info.plist 提供 |
| `CODE_SIGN_ENTITLEMENTS` | 复用 App Group entitlement | 需同时含 `group.com.ylib.quicksave` |
| `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION` | `1.0` / `1` | 缺失会导致安装失败（QS-0004 踩过） |
| `CFBundleExecutable` | `$(EXECUTABLE_NAME)` | 同上 |

> **部署目标 18.0 的影响面**：仅该 target 为 18.0，主 App 仍为 16.0。iOS 17 设备不加载控件扩展，主 App 功能不受影响 —— 满足 US-Q06。这与 PRD §十二「不提升主 App 最低版本」的决策一致。

### 3.2 源文件共享策略

控件扩展需要复用主 App 的业务层。沿用 Share Extension 已验证的做法 —— **同一源文件加入多个 target 的 Sources build phase**（见现有 pbxproj 中 `ClipModels.swift`、`PreferencesStore.swift`、`BookmarkFileDataSource.swift`、`ClipRepository.swift` 同属 App 与 Share 两侧）。

新增需共享到 Widget target 的文件：

| 文件 | 是否需要共享 | 理由 |
|---|---|---|
| `Models/ClipModels.swift` | ✅ | `ClipError`、格式定义 |
| `Storage/PreferencesStore.swift` | ✅ | 读 App Group 配置 |
| `Storage/BookmarkFileDataSource.swift` | ✅ | 解析 bookmark 并写文件 |
| `Repositories/ClipRepository.swift` | ✅ | 复用 `saveEntry`，不重写保存逻辑 |
| `Storage/SharedPayloadStore.swift` | ✅ | 读写快捷保存结果（见 §六） |
| `ViewModels/*` | ❌ | 控件无 ViewModel 层 |

> 不引入 Swift Package 或 framework 抽层。理由：现有两个 target 已用「源文件多 target 归属」方式工作良好，为一个扩展引入模块化重构属于超范围改动（且 QS-0004 刚稳定）。

## 四、跨模块协议

### 4.1 App Intent

```swift
struct SaveClipboardIntent: AppIntent {
    static var title: LocalizedStringResource = "保存剪切板"
    static var description = IntentDescription("把当前剪切板文字保存到 QuickSave 目标文件。")

    // 形态开关：见 §一 第 4 点。默认 false（后台执行）；
    // 若实测确认读剪切板必须在前台，改为 true 即退化为「形态二」。
    static var openAppWhenRun: Bool = false

    func perform() async throws -> some IntentResult & ProvidesDialog
}
```

`perform()` 职责：

1. 读取剪切板（`UIPasteboard.general.string`）。
2. 构造 `AppGroupPreferencesStore` + `BookmarkFileDataSource` + `ClipRepositoryImpl`（与 `ShareViewController.swift:112-115` 同构）。
3. 调 `repository.saveEntry(text:category:)`。
4. 按 §六 写入结果 payload。
5. 返回 `IntentResult`（含 dialog 文案供系统呈现）。

> Intent 内**不新增保存逻辑**，全部经既有 `ClipRepository`，保证格式与错误映射与主 App 一致（PRD §九「不引入第二套存储」）。

### 4.2 与主 App 的配置边界

控件不新增配置项，全部读取既有 App Group 键：

| 键 | 来源 | 用途 |
|---|---|---|
| `target_file_bookmark` | 主 App 写入 | 写入目标 |
| `selected_category` | 主 App 写入 | 分类前缀 |
| `categories` | 主 App 写入 | 分类有效性校验 |

新增一个键，仅用于快捷保存结果回传（见 §六）：

| 键 | 类型 | 写入方 | 读取方 |
|---|---|---|---|
| `last_quick_save_result` | `Data`（JSON） | Intent | 主 App |

## 五、模块划分

```
┌──────────────── QuickSaveWidget (iOS 18+) ────────────────┐
│ QuickSaveWidgetBundle : WidgetBundle                       │
│   └── SaveClipboardControl : ControlWidget                 │
│         └── StaticControlConfiguration                     │
│               └── ControlWidgetButton(action: SaveClipboardIntent()) │
├──────────────────────── Intent Layer ─────────────────────┤
│ SaveClipboardIntent : AppIntent                            │
├──────────────────────── 复用既有业务层 ───────────────────┤
│ ClipRepositoryImpl  ← 与 Share Extension 同一套实现        │
│   ├── AppGroupPreferencesStore                             │
│   └── BookmarkFileDataSource                               │
├──────────────────────── System Integration ───────────────┤
│ UIPasteboard · App Group · WidgetKit                       │
└───────────────────────────────────────────────────────────┘
                          ▲
                          │ App Group（group.com.ylib.quicksave）
                          ▼
┌──────────────────────── QuickSave 主 App ─────────────────┐
│ HomeViewModel  ← 启动时消费 last_quick_save_result（L3）   │
└───────────────────────────────────────────────────────────┘
```

依赖方向：Widget → Intent → 既有 Repository → 既有 DataSource。**主 App 不依赖 Widget**，反向依赖仅通过 App Group 数据。

## 六、核心数据流

### 6.1 快捷保存（形态一：后台）

```
用户下拉控制中心，点控件
  → SaveClipboardIntent.perform()
  → UIPasteboard.general.string
  → ClipRepositoryImpl.saveEntry(text, selectedCategory)
  → resolve bookmark + start security-scoped access
  → FileHandle seekToEnd + UTF-8 append
  → 写入 last_quick_save_result（成功/失败 + 时间戳）
  → IntentResult(dialog: "已保存")
```

### 6.2 快捷保存（形态二：打开 App）

```
用户下拉控制中心，点控件
  → 系统打开 QuickSave（openAppWhenRun = true）
  → SaveClipboardIntent.perform() 在 App 进程内执行
  → 同上保存链路
  → 写入 last_quick_save_result
  → IntentResult(dialog) → 主 App 以 QuickSaveToast 呈现
```

> 两种形态的差异仅在 `perform()` 的执行进程，**保存链路与结果记录完全一致**。

### 6.3 失败待办提示（L3）

```
主 App 启动 / 回到前台
  → HomeViewModel.consumeQuickSaveResult()
  → 读取并清除 last_quick_save_result
  → 若为失败：feedback = Feedback(message: <错误文案>, isError: true)
  → QuickSaveToast 呈现一次，消费即清
```

对应 [ui-ios-quick-entry.md](ui-ios-quick-entry.md) §4.1 的 L3 层，防止「以为存了其实没存」。

### 6.4 未配置引导

```
控件检测 target_file_bookmark == nil
  → 控件渲染为「未配置」态（§3.2 of UI 文档）
  → 点击走 OpenIntent 分支 → 打开主 App
  → 主 App 检测到待配置标记 → 导航至设置页
```

## 七、状态模型

控件状态由 `ControlValueProvider.currentValue()` 计算（形态无关）：

| 状态 | 判定 | UI 表现 |
|---|---|---|
| 就绪 | bookmark 存在且可访问 | `archivebox.fill` + teal |
| 未配置 | bookmark 为 nil | `archivebox` + `!` 角标 + coral |
| 保存中 / 刚成功 / 刚失败 | Intent 执行期由 `actionLabel` 通道呈现 | 见 UI 文档 §3.2 |

> **待验证**：`currentValue()` 在控件中刷新的时机与频率由系统决定，能否稳定支撑「刚成功/刚失败」瞬时态需实测（§十一 R2）。若不可靠，退化方案是仅保留「就绪 / 未配置」两态，结果反馈全部走 L2/L3。

## 八、并发、生命周期与安全

- 保存经既有 `BookmarkFileDataSource` actor 串行化，与主 App、Share Extension 共用同一条串行路径，避免并发追加行交错。
- security-scoped URL 成对 `startAccessing...` / `stopAccessing...`（既有实现已保证）。
- 连续点击控件：`.disabled(_:)` 修饰符 + actor 串行双重防护。
- 控件不常驻、不轮询、不申请新权限；不引入网络。
- App Group 中不长期保存剪切板原文 —— 仅保存结果摘要（成功/失败 + 错误码 + 时间戳）。

## 九、备选方案与取舍

| 决策点 | 备选 | 采用 | 理由 |
|---|---|---|---|
| 控件位置 | 主屏 Widget / 锁屏 Widget | 控制中心控件 | 用户诉求是「随时一键」；控制中心下拉即达，主屏 Widget 需先回桌面 |
| 业务层复用 | 抽 Swift Package / framework | 源文件多 target 归属 | 沿用既有已验证做法；抽层属超范围重构 |
| 形态策略 | 只实现一种形态 | 同一 Intent + `openAppWhenRun` 开关 | 实测结论未定时避免返工（§一 第 4 点） |
| 控件类型 | `ControlWidgetToggle` | `ControlWidgetButton` | 保存是瞬时动作，不是开关状态；toggle 语义不符 |
| 结果反馈 | 系统通知 / Live Activity | L1 控件态 + L2 Toast + L3 待办 | 见 UI 文档 §4.3「不采用」理由 |
| 剪切板来源 | 扩展直读 | 扩展直读（形态一）/ App 进程读（形态二） | 受 §十一 R1 系统提示约束，需实测决定 |

## 十、测试边界

**单元测试（可自动化，加入现有 `QuickSaveTests`）**

- Intent 的保存调用：分类传递、无分类为 `nil`、空剪切板守卫。
- 结果 payload 的写入与一次性消费（`consumeQuickSaveResult`）。
- 未配置 bookmark 时返回引导结果而非崩溃。
- 既有 `ClipRepositoryTests` / `HomeViewModelTests` 全量回归。

**模拟器可验证**

- Widget target 能编译、能被主 App 嵌入。
- iOS 17 模拟器下 App 正常启动、控件不出现、无崩溃。
- 设置页两版引导区块（UI 文档 §五）。

**需真机验证（模拟器不支持，见 §十一）**

- 控件能否添加到控制中心。
- **读剪切板是否弹系统粘贴提示**（R1，决定形态）。
- 控件动态态的刷新可靠性（R2）。
- 扩展进程内 bookmark 解析（预期可行，Share Extension 已证）。

## 十一、风险与待验证项

| 编号 | 风险 | 影响 | 处置 |
|---|---|---|---|
| **R1** | **扩展进程读剪切板触发系统粘贴授权提示** | **高** — 出现提示则形态一（静默保存）失去意义，每次保存需用户二次确认 | **首要待验证项**。真机实测。若确认提示存在：改用形态二（`openAppWhenRun = true`），由主 App 前台读剪切板 —— 主 App 路径下用户已有心理预期，且 QS-0004 已验证该路径。**架构已为此预留开关，改动量为 1 行** |
| R2 | 控件动态态（`ControlValueProvider`）刷新时机由系统决定 | 中 — L1 层可能不可靠 | 实测。退化方案：仅保留就绪/未配置两态，反馈走 L2/L3 |
| R3 | 控件扩展无法访问 bookmark | 低 — Share Extension 已证明可行 | 实测确认；若失败，退化为形态二（App 进程写） |
| R4 | iOS 26 起 `openAppWhenRun` deprecated | 低 — 仅编译警告 | 后续按 `supportedModes` 迁移；本次为兼容 iOS 18 仍用 `openAppWhenRun` |

> **R1 是唯一可能改变产品形态的风险**，其余均有明确降级路径且不改变架构。建议在实现前优先完成 R1 的真机验证。

## 十二、验收对照

| PRD 验收项 | 架构支撑 |
|---|---|
| US-Q01 控件可添加 | §三 target 配置 + §四.1 控件定义 |
| US-Q02 一键保存 | §六.1 / §六.2 数据流 |
| US-Q03 未配置状态表达 | §六.4 引导流程 + §七 状态模型 |
| US-Q04 Siri / 快捷指令可用 | 既有 `OpenQuickSaveIntent` 不动 |
| US-Q05 无回归 | §十 既有测试全量回归 |
| US-Q06 低版本可用 | §三.1 Widget target 单独 18.0，主 App 保持 16.0 |
| 保存格式 / 分类语义一致 | §四.2 复用既有 `ClipRepository` |

---

> 后续：人工 approve → `/dev` 详细设计（`design-ios-quick-entry.md`）→ 功能实现与测试。**建议实现前先完成 R1 真机验证。**