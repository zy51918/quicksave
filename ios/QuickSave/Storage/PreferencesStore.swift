import Foundation

extension Notification.Name {
    static let quickSavePreferencesDidChange = Notification.Name("QuickSave.preferencesDidChange")
}

protocol PreferencesStoring: AnyObject {
    var targetFileBookmark: Data? { get set }
    var categories: [String] { get set }
    var selectedCategory: String? { get set }
}

class UserDefaultsPreferencesStore: PreferencesStoring {
    private enum Key {
        static let targetFileBookmark = "target_file_bookmark"
        static let categories = "categories"
        static let selectedCategory = "selected_category"
    }

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    var targetFileBookmark: Data? {
        get { defaults.data(forKey: Key.targetFileBookmark) }
        set {
            defaults.set(newValue, forKey: Key.targetFileBookmark)
            notifyChange()
        }
    }

    var categories: [String] {
        get { defaults.stringArray(forKey: Key.categories) ?? [] }
        set {
            defaults.set(newValue, forKey: Key.categories)
            notifyChange()
        }
    }

    var selectedCategory: String? {
        get { defaults.string(forKey: Key.selectedCategory) }
        set {
            defaults.set(newValue, forKey: Key.selectedCategory)
            notifyChange()
        }
    }

    private func notifyChange() {
        NotificationCenter.default.post(name: .quickSavePreferencesDidChange, object: nil)
    }
}

final class AppGroupPreferencesStore: UserDefaultsPreferencesStore {
    init(appGroupIdentifier: String) {
        super.init(defaults: UserDefaults(suiteName: appGroupIdentifier) ?? .standard)
    }
}
