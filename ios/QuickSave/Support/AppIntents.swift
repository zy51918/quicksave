import AppIntents

struct OpenQuickSaveIntent: AppIntent {
    static var title: LocalizedStringResource = "打开 QuickSave"
    static var description = IntentDescription("打开 QuickSave 快速归档文字。")
    static var openAppWhenRun = true

    func perform() async throws -> some IntentResult {
        .result()
    }
}

struct QuickSaveShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: OpenQuickSaveIntent(),
            phrases: ["打开 \(.applicationName)"],
            shortTitle: "打开 QuickSave",
            systemImageName: "archivebox"
        )
    }
}
