import Foundation

@MainActor
final class HomeViewModel: ObservableObject {
    @Published private(set) var targetFileConfigured = false
    @Published private(set) var clipText: String?
    @Published var manualInputText = ""
    @Published private(set) var categories: [String] = []
    @Published private(set) var selectedCategory: String?
    @Published private(set) var isClipSaving = false
    @Published private(set) var isManualSaving = false
    @Published var showClearConfirmation = false
    @Published var feedback: Feedback?

    private let repository: ClipRepository
    private let clipboard: ClipboardReading
    private let payloads: SharedPayloadStoring
    private let quickSaveResults: QuickSaveResultStoring
    private var preferenceObserver: NSObjectProtocol?

    init(
        repository: ClipRepository,
        clipboard: ClipboardReading = SystemClipboardReader(),
        payloads: SharedPayloadStoring,
        quickSaveResults: QuickSaveResultStoring = QuickSaveResultStore()
    ) {
        self.repository = repository
        self.clipboard = clipboard
        self.payloads = payloads
        self.quickSaveResults = quickSaveResults
        reloadPreferences()
        preferenceObserver = NotificationCenter.default.addObserver(
            forName: .quickSavePreferencesDidChange,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.reloadPreferences()
            }
        }
    }

    deinit {
        if let preferenceObserver {
            NotificationCenter.default.removeObserver(preferenceObserver)
        }
    }

    func refreshClipboard() {
        clipText = clipboard.readString()
    }

    func consumeSharedPayload() {
        guard let text = payloads.consume() else { return }
        manualInputText = text
        feedback = Feedback(message: "已导入分享内容，请确认后保存", isError: false)
    }

    /// 消费控制中心控件留下的保存结果（L3 待办提示）。
    ///
    /// 只在失败时提示：成功时用户已通过控件态或 App 内 Toast 得知，无需重复打扰；
    /// 而失败若无人提示，就会变成「以为存了其实没存」的静默丢失。
    func consumeQuickSaveResult() {
        guard let result = quickSaveResults.consume(), !result.succeeded else { return }
        feedback = Feedback(message: result.message ?? "保存失败", isError: true)
    }

    func reloadPreferences() {
        targetFileConfigured = repository.targetFileBookmark != nil
        categories = repository.categories
        selectedCategory = repository.selectedCategory
    }

    func validateTargetFileAccess() {
        guard repository.targetFileBookmark != nil else {
            targetFileConfigured = false
            return
        }
        Task { [weak self] in
            guard let self else { return }
            targetFileConfigured = await repository.isTargetFileAccessible()
        }
    }

    func selectCategory(_ category: String?) {
        repository.setSelectedCategory(category)
        reloadPreferences()
    }

    func addCategory(_ name: String) {
        let normalized = normalizeCategory(name)
        guard !normalized.isEmpty, !categories.contains(normalized) else { return }
        repository.setCategories(categories + [normalized])
        repository.setSelectedCategory(normalized)
        reloadPreferences()
    }

    func saveClipboard() {
        guard let clipText else {
            feedback = Feedback(message: ClipError.emptyClipboard.errorDescription ?? "剪切板为空", isError: true)
            return
        }
        let category = selectedCategory
        isClipSaving = true
        Task { [weak self] in
            guard let self else { return }
            let result = await repository.saveEntry(text: clipText, category: category)
            isClipSaving = false
            handle(result)
        }
    }

    func saveManualInput() {
        guard !manualInputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        let text = manualInputText
        let category = selectedCategory
        isManualSaving = true
        Task { [weak self] in
            guard let self else { return }
            let result = await repository.saveEntry(text: text, category: category)
            isManualSaving = false
            if case .success = result {
                manualInputText = ""
            }
            handle(result)
        }
    }

    func clearSavedFile() {
        showClearConfirmation = false
        Task { [weak self] in
            guard let self else { return }
            let result = await repository.clearSavedFile()
            switch result {
            case .success:
                feedback = Feedback(message: "文件内容已清空", isError: false)
            case let .failure(error):
                feedback = Feedback(message: error.errorDescription ?? "清空失败", isError: true)
            }
        }
    }

    private func handle(_ result: Result<Void, ClipError>) {
        switch result {
        case .success:
            feedback = Feedback(message: "已保存", isError: false)
        case let .failure(error):
            feedback = Feedback(message: error.errorDescription ?? "保存失败", isError: true)
        }
    }

    private func normalizeCategory(_ name: String) -> String {
        name.replacingOccurrences(of: "\n", with: "").trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
