import Foundation
import UIKit

protocol ClipboardReading {
    func readString() -> String?
}

struct SystemClipboardReader: ClipboardReading {
    func readString() -> String? {
        guard let text = UIPasteboard.general.string,
              !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else { return nil }
        return text
    }
}

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
    private var preferenceObserver: NSObjectProtocol?

    init(
        repository: ClipRepository,
        clipboard: ClipboardReading = SystemClipboardReader(),
        payloads: SharedPayloadStoring
    ) {
        self.repository = repository
        self.clipboard = clipboard
        self.payloads = payloads
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
