import Foundation

/// 快捷保存（控制中心控件 / App Intent）的结果，供主 App 在下次进入时以 Toast 提示一次。
///
/// 存在意义：控件以静默方式保存时，用户看不到结果。若不记录失败，会出现
/// 「以为存了其实没存」的静默数据丢失。主 App 消费后立即清除，不重复打扰。
struct QuickSaveResult: Codable, Equatable {
    let succeeded: Bool
    /// 失败原因（`ClipError.errorDescription`），成功时为 nil。
    let message: String?
    let date: Date

    static func success(date: Date = Date()) -> QuickSaveResult {
        QuickSaveResult(succeeded: true, message: nil, date: date)
    }

    static func failure(message: String, date: Date = Date()) -> QuickSaveResult {
        QuickSaveResult(succeeded: false, message: message, date: date)
    }
}

protocol QuickSaveResultStoring {
    func write(_ result: QuickSaveResult)
    func consume() -> QuickSaveResult?
}

/// 经 App Group 在控件扩展与主 App 之间传递结果。
final class QuickSaveResultStore: QuickSaveResultStoring {
    private let defaults: UserDefaults
    private let key = "last_quick_save_result"

    init(defaults: UserDefaults? = nil) {
        self.defaults = defaults ?? UserDefaults(suiteName: SharedPayloadStore.appGroupIdentifier) ?? .standard
    }

    func write(_ result: QuickSaveResult) {
        guard let data = try? JSONEncoder().encode(result) else { return }
        defaults.set(data, forKey: key)
    }

    func consume() -> QuickSaveResult? {
        guard let data = defaults.data(forKey: key) else { return nil }
        defaults.removeObject(forKey: key)
        return try? JSONDecoder().decode(QuickSaveResult.self, from: data)
    }
}