import Foundation

protocol SharedPayloadStoring {
    func write(_ text: String) throws
    func consume() -> String?
}

final class SharedPayloadStore: SharedPayloadStoring {
    static let appGroupIdentifier = "group.com.ylib.quicksave"
    private let defaults: UserDefaults
    private let payloadKey = "pending_shared_text"

    init(defaults: UserDefaults? = nil) {
        self.defaults = defaults ?? UserDefaults(suiteName: Self.appGroupIdentifier) ?? .standard
    }

    func write(_ text: String) throws {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw SharedPayloadError.empty }
        defaults.set(text, forKey: payloadKey)
        defaults.synchronize()
    }

    func consume() -> String? {
        let text = defaults.string(forKey: payloadKey)
        defaults.removeObject(forKey: payloadKey)
        return text
    }
}

enum SharedPayloadError: LocalizedError {
    case empty

    var errorDescription: String? { "没有可导入的文字" }
}
