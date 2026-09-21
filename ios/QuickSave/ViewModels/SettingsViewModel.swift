import Foundation

@MainActor
final class SettingsViewModel: ObservableObject {
    @Published private(set) var targetFileConfigured = false
    @Published private(set) var categories: [String] = []

    private let repository: ClipRepository
    private var preferenceObserver: NSObjectProtocol?

    init(repository: ClipRepository) {
        self.repository = repository
        reload()
        preferenceObserver = NotificationCenter.default.addObserver(
            forName: .quickSavePreferencesDidChange,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.reload()
            }
        }
    }

    deinit {
        if let preferenceObserver {
            NotificationCenter.default.removeObserver(preferenceObserver)
        }
    }

    func reload() {
        targetFileConfigured = repository.targetFileBookmark != nil
        categories = repository.categories
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

    func setTargetFile(bookmark: Data) {
        repository.setTargetFile(bookmark: bookmark)
        reload()
    }

    func clearTargetFile() {
        // Clearing is intentionally explicit and does not delete the user's file.
        repository.clearTargetFile()
        reload()
    }

    func addCategory(_ name: String) {
        let normalized = normalizeCategory(name)
        guard !normalized.isEmpty, !categories.contains(normalized) else { return }
        repository.setCategories(categories + [normalized])
        reload()
    }

    func renameCategory(_ oldName: String, to newName: String) {
        let normalized = normalizeCategory(newName)
        guard !normalized.isEmpty,
              oldName != normalized,
              !categories.contains(normalized)
        else { return }
        let wasSelected = repository.selectedCategory == oldName
        repository.setCategories(categories.map { $0 == oldName ? normalized : $0 })
        if wasSelected {
            repository.setSelectedCategory(normalized)
        }
        reload()
    }

    func deleteCategory(_ category: String) {
        repository.setCategories(categories.filter { $0 != category })
        reload()
    }

    func moveCategory(from source: IndexSet, to destination: Int) {
        var reordered = categories
        reordered.move(fromOffsets: source, toOffset: destination)
        repository.setCategories(reordered)
        reload()
    }

    private func normalizeCategory(_ name: String) -> String {
        name.replacingOccurrences(of: "\n", with: "").trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
