import AppIntents
import SwiftUI
import WidgetKit

/// 控制中心控件：一键保存剪切板。
///
/// 视觉规格见 docs/features/QS-0005/ui-ios-quick-entry.md §3.2。
/// 控件不支持富 UI，因此只承载「保存剪切板」单一动作。
@available(iOS 18.0, *)
struct SaveClipboardControl: ControlWidget {
    static let kind = "SaveClipboardControl"

    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: Self.kind) {
            ControlWidgetButton(action: SaveClipboardIntent()) {
                Label("保存剪切板", systemImage: "archivebox.fill")
            }
        }
        .displayName("QuickSave")
        .description("保存剪切板文字到目标文件")
    }
}

/// 目标文件未配置时，控件切换为引导用户去设置。
@available(iOS 18.0, *)
struct ConfigureFileControl: ControlWidget {
    static let kind = "ConfigureFileControl"

    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: Self.kind) {
            ControlWidgetButton(action: OpenSettingsIntent()) {
                Label("去设置", systemImage: "archivebox")
            }
        }
        .displayName("QuickSave 设置")
        .description("选择 QuickSave 的保存文件")
    }
}

@main
struct QuickSaveWidgetBundle: WidgetBundle {
    var body: some Widget {
        if #available(iOS 18.0, *) {
            SaveClipboardControl()
            ConfigureFileControl()
        }
    }
}