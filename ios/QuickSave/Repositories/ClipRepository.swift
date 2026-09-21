import Foundation

protocol ClipRepository {
    var targetFileBookmark: Data? { get }
    var categories: [String] { get }
    var selectedCategory: String? { get }

    func setTargetFile(bookmark: Data)
    func clearTargetFile()
    func saveEntry(text: String, category: String?) async -> Result<Void, ClipError>
    func clearSavedFile() async -> Result<Void, ClipError>
    func setCategories(_ categories: [String])
    func setSelectedCategory(_ category: String?)
}

final class ClipRepositoryImpl: ClipRepository {
    private let preferences: PreferencesStoring
    private let files: any FileDataSource
    private let formatter: EntryFormatter

    init(
        preferences: PreferencesStoring,
        files: any FileDataSource,
        formatter: EntryFormatter = EntryFormatter()
    ) {
        self.preferences = preferences
        self.files = files
        self.formatter = formatter
    }

    var targetFileBookmark: Data? { preferences.targetFileBookmark }
    var categories: [String] { preferences.categories }
    var selectedCategory: String? {
        let selected = preferences.selectedCategory
        return selected.flatMap { preferences.categories.contains($0) ? $0 : nil }
    }

    func setTargetFile(bookmark: Data) {
        preferences.targetFileBookmark = bookmark
    }

    func clearTargetFile() {
        preferences.targetFileBookmark = nil
    }

    func saveEntry(text: String, category: String?) async -> Result<Void, ClipError> {
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return .failure(.emptyClipboard)
        }
        guard let bookmark = preferences.targetFileBookmark else {
            return .failure(.targetFileNotConfigured)
        }
        let validCategory = category.flatMap { preferences.categories.contains($0) ? $0 : nil }
        let line = formatter.format(text: text, category: validCategory)
        do {
            try await files.appendLine(line, to: bookmark)
            return .success(())
        } catch {
            return .failure(map(error))
        }
    }

    func clearSavedFile() async -> Result<Void, ClipError> {
        guard let bookmark = preferences.targetFileBookmark else {
            return .failure(.targetFileNotConfigured)
        }
        do {
            try await files.clearFile(bookmark: bookmark)
            return .success(())
        } catch {
            return .failure(map(error))
        }
    }

    func setCategories(_ categories: [String]) {
        let normalized = categories
            .map { $0.replacingOccurrences(of: "\n", with: "").trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        preferences.categories = normalized
        if let selected = preferences.selectedCategory, !normalized.contains(selected) {
            preferences.selectedCategory = nil
        }
    }

    func setSelectedCategory(_ category: String?) {
        preferences.selectedCategory = category.flatMap { preferences.categories.contains($0) ? $0 : nil }
    }

    private func map(_ error: Error) -> ClipError {
        if let error = error as? ClipError { return error }
        if let error = error as? FileDataSourceError {
            switch error {
            case .missing, .stale, .inaccessible:
                return .targetFileUnavailable
            }
        }
        return .io(error.localizedDescription)
    }
}
