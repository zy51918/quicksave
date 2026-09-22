import XCTest
@testable import QuickSave

@MainActor
final class HomeViewModelTests: XCTestCase {
    func testBlankManualInputDoesNotCallRepository() {
        let repository = MockClipRepository()
        let viewModel = HomeViewModel(
            repository: repository,
            clipboard: StubClipboard(value: nil),
            payloads: MemoryPayloadStore()
        )
        viewModel.manualInputText = " \n\t"

        viewModel.saveManualInput()

        XCTAssertEqual(repository.saveCallCount, 0)
        XCTAssertFalse(viewModel.isManualSaving)
    }

    func testSuccessfulManualSaveClearsTextAndPreservesCategory() async {
        let repository = MockClipRepository()
        repository.categories = ["工作"]
        repository.selectedCategory = "工作"
        repository.saveResult = .success(())
        let viewModel = HomeViewModel(
            repository: repository,
            clipboard: StubClipboard(value: nil),
            payloads: MemoryPayloadStore()
        )
        viewModel.manualInputText = "记录内容"
        viewModel.selectCategory("工作")

        viewModel.saveManualInput()
        await waitUntil { !viewModel.isManualSaving && viewModel.feedback != nil }

        XCTAssertEqual(repository.lastText, "记录内容")
        XCTAssertEqual(repository.lastCategory, "工作")
        XCTAssertEqual(viewModel.manualInputText, "")
        XCTAssertEqual(viewModel.selectedCategory, "工作")
        XCTAssertEqual(viewModel.feedback?.message, "已保存")
    }

    func testFailedManualSaveKeepsText() async {
        let repository = MockClipRepository()
        repository.saveResult = .failure(.targetFileNotConfigured)
        let viewModel = HomeViewModel(
            repository: repository,
            clipboard: StubClipboard(value: nil),
            payloads: MemoryPayloadStore()
        )
        viewModel.manualInputText = "保留我"

        viewModel.saveManualInput()
        await waitUntil { !viewModel.isManualSaving && viewModel.feedback != nil }

        XCTAssertEqual(viewModel.manualInputText, "保留我")
        XCTAssertEqual(viewModel.feedback?.message, "请先在设置中选择保存文件")
    }

    func testClipboardRefreshReadsOnlyThroughReader() {
        let viewModel = HomeViewModel(
            repository: MockClipRepository(),
            clipboard: StubClipboard(value: "剪切板内容"),
            payloads: MemoryPayloadStore()
        )

        viewModel.refreshClipboard()

        XCTAssertEqual(viewModel.clipText, "剪切板内容")
    }
}

/// 轮询等待异步保存完成：ViewModel 内部起 Task，固定次数的 Task.yield() 不保证完成
@MainActor
private func waitUntil(
    timeout: TimeInterval = 2,
    _ condition: @MainActor () -> Bool
) async {
    let deadline = Date().addingTimeInterval(timeout)
    while !condition() {
        if Date() >= deadline {
            XCTFail("等待条件超时（\(timeout)s）")
            return
        }
        await Task.yield()
    }
}

private final class MockClipRepository: ClipRepository {
    var targetFileBookmark: Data?
    var categories: [String] = []
    var selectedCategory: String?
    var saveResult: Result<Void, ClipError> = .success(())
    var saveCallCount = 0
    var lastText: String?
    var lastCategory: String?

    func setTargetFile(bookmark: Data) { targetFileBookmark = bookmark }
    func clearTargetFile() { targetFileBookmark = nil }
    func isTargetFileAccessible() async -> Bool { targetFileBookmark != nil }
    func saveEntry(text: String, category: String?) async -> Result<Void, ClipError> {
        saveCallCount += 1
        lastText = text
        lastCategory = category
        return saveResult
    }
    func clearSavedFile() async -> Result<Void, ClipError> { .success(()) }
    func setCategories(_ categories: [String]) { self.categories = categories }
    func setSelectedCategory(_ category: String?) { selectedCategory = category }
}

private struct StubClipboard: ClipboardReading {
    let value: String?
    func readString() -> String? { value }
}

private final class MemoryPayloadStore: SharedPayloadStoring {
    var value: String?
    func write(_ text: String) throws { value = text }
    func consume() -> String? {
        defer { value = nil }
        return value
    }
}
