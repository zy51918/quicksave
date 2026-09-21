# QuickSave iOS

这是 QuickSave 的 iOS 原生实现，最低部署版本 iOS 16.0。

## 当前范围

- SwiftUI 主页与设置页
- 前台读取文字剪切板
- 手动输入保存
- 分类新增、重命名、删除、排序
- 用户选择文本文件并通过 security-scoped bookmark 持久化访问
- 追加保存、清空文件、错误反馈
- Share Extension 导入其他 App 分享的文字
- App Intents / Shortcuts 打开 QuickSave 的系统快捷入口

录音、全局悬浮窗、常驻通知和后台剪切板轮询暂不实现。录音需单独的后续 feature 评审。

## 打开工程

使用 Xcode 打开 `QuickSave.xcodeproj`，选择 `QuickSave` scheme。首次运行需要：

1. 为 `QuickSave` 与 `QuickSaveShare` targets 设置同一开发团队。
2. 在 Signing & Capabilities 中启用 App Groups，并将 `group.com.ylib.quicksave` 替换为团队可用的 App Group（同时修改 Swift 常量和两个 entitlements）。
3. 在真机或模拟器运行主 App；通过系统文件选择器配置目标文本文件。
4. 在其他 App 的 Share Sheet 中启用 QuickSave，导入内容后回到主 App 点击保存。

## 验证

完整的 Xcode 构建和 XCTest 需要安装 Xcode。当前开发环境只有 Command Line Tools，因此本仓库已执行 Swift parser、Foundation 核心层 type-check、plist 和 xcodeproj 语法检查；请在 Xcode 环境执行 `xcodebuild test -project QuickSave.xcodeproj -scheme QuickSave`。
