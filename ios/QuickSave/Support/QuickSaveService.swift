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

/// 控制中心控件背后的保存逻辑。
///
/// 与 `HomeViewModel` 共用同一套 `ClipRepository` 与剪切板抽象，区别在于它跑在扩展进程里：
/// 没有 UI 可画，因此结果必须落到 `QuickSaveResultStoring` 交给主 App 补提示。
struct QuickSaveService {
    let repository: ClipRepository
    let clipboard: ClipboardReading
    let results: QuickSaveResultStoring

    init(
        repository: ClipRepository,
        clipboard: ClipboardReading = SystemClipboardReader(),
        results: QuickSaveResultStoring = QuickSaveResultStore()
    ) {
        self.repository = repository
        self.clipboard = clipboard
        self.results = results
    }

    /// 用 App Group 中的配置装配，供扩展进程调用。
    static func fromAppGroup() -> QuickSaveService {
        let preferences = AppGroupPreferencesStore(appGroupIdentifier: SharedPayloadStore.appGroupIdentifier)
        let repository = ClipRepositoryImpl(preferences: preferences, files: BookmarkFileDataSource())
        return QuickSaveService(repository: repository)
    }

    func saveClipboard() async -> QuickSaveResult {
        guard let text = clipboard.readString()?.trimmingCharacters(in: .whitespacesAndNewlines),
              !text.isEmpty
        else {
            let result = QuickSaveResult.failure(
                message: ClipError.emptyClipboard.errorDescription ?? "剪切板为空"
            )
            results.write(result)
            return result
        }

        let outcome = await repository.saveEntry(text: text, category: repository.selectedCategory)
        let result: QuickSaveResult
        switch outcome {
        case .success:
            result = .success()
        case let .failure(error):
            result = .failure(message: error.errorDescription ?? "保存失败")
        }
        results.write(result)
        return result
    }
}