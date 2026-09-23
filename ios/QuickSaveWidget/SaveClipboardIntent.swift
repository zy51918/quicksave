import AppIntents
import Foundation

/// 保存剪切板到目标文件。
///
/// `openAppWhenRun` 是形态开关：
/// - `false`（当前）：后台静默保存，成功与否由控件态与 L3 待办提示承载，用户不必离开当前 App。
/// - `true`：拉起 App 再保存，把剪切板授权提示与 Toast 都放在 App 前台，代价是打断当前操作。
///
/// 两种形态共用 `QuickSaveService`，切换只改这一个布尔值。
struct SaveClipboardIntent: AppIntent {
    static var title: LocalizedStringResource = "保存剪切板"
    static var description = IntentDescription("把当前剪切板文字保存到 QuickSave 目标文件。")
    static var openAppWhenRun: Bool = false

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let result = await QuickSaveService.fromAppGroup().saveClipboard()
        if result.succeeded {
            return .result(dialog: "已保存")
        }
        return .result(dialog: IntentDialog(stringLiteral: result.message ?? "保存失败"))
    }
}

/// 打开 App 去选择保存文件。未配置目标文件时，控件本身无法补救。
struct OpenSettingsIntent: AppIntent {
    static var title: LocalizedStringResource = "打开 QuickSave 设置"
    static var description = IntentDescription("打开 QuickSave 以选择保存文件。")
    static var openAppWhenRun: Bool = true

    func perform() async throws -> some IntentResult {
        .result()
    }
}