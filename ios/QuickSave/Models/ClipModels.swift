import Foundation

/// A user-visible failure from the text archive path.
enum ClipError: LocalizedError, Equatable {
    case targetFileNotConfigured
    case targetFileUnavailable
    case emptyClipboard
    case io(String)

    var errorDescription: String? {
        switch self {
        case .targetFileNotConfigured:
            return "请先在设置中选择保存文件"
        case .targetFileUnavailable:
            return "文件无写入权限，请重新选择"
        case .emptyClipboard:
            return "剪切板为空，请先复制文字"
        case let .io(message):
            return "保存失败：\(message)"
        }
    }
}

struct EntryFormatter {
    var calendar: Calendar = .current

    func format(text: String, category: String?, date: Date = Date()) -> String {
        let formatter = DateFormatter()
        formatter.calendar = calendar
        formatter.timeZone = calendar.timeZone
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        let prefix = category.map { "[\($0)]" } ?? ""
        return "\(prefix)[\(formatter.string(from: date))] \(text)\n"
    }
}

struct Feedback: Identifiable {
    let id = UUID()
    let message: String
    let isError: Bool
}
