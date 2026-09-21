import SwiftUI

@main
struct QuickSaveApp: App {
    private let dependencies = AppDependencies.shared

    var body: some Scene {
        WindowGroup {
            HomeView(dependencies: dependencies)
        }
    }
}
