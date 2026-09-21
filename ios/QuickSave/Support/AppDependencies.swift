import Foundation

final class AppDependencies {
    static let shared = AppDependencies()

    let preferences: UserDefaultsPreferencesStore
    let files: BookmarkFileDataSource
    let repository: ClipRepositoryImpl
    let sharedPayloads: SharedPayloadStore

    private init() {
        let preferences = AppGroupPreferencesStore(appGroupIdentifier: SharedPayloadStore.appGroupIdentifier)
        let files = BookmarkFileDataSource()
        self.preferences = preferences
        self.files = files
        self.repository = ClipRepositoryImpl(preferences: preferences, files: files)
        self.sharedPayloads = SharedPayloadStore()
    }
}
