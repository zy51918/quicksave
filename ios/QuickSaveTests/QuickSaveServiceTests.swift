import XCTest
@testable import QuickSave

/// 覆盖快捷保存链路（QS-0005）：剪切板守卫、分类传递、结果记录与一次性消费。
final class QuickSaveServiceTests: XCTestCase {
    private var fileURL: URL!
    private var preferences: UserDefaultsPreferencesStore!
    private var repository: ClipRepositoryImpl!

    override func setUpWithError() throws {
        try super.setUpWithError()
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        fileURL = dir.appendingPathComponent("quicksave.txt")
        try Data().write(to: fileURL)

        let defaults = UserDefaults(suiteName: "QuickSaveServiceTests.\(UUID().uuidString)")!
        preferences = UserDefaultsPreferencesStore(defaults: defaults)
        let bookmark = try BookmarkFileDataSource.makeBookmark(for: fileURL)
        preferences.targetFileBookmark = bookmark
        repository = ClipRepositoryImpl(preferences: preferences, files: BookmarkFileDataSource())
    }

    override func tearDownWithError() throws {
        try? FileManager.default.removeItem(at: fileURL.deletingLastPathComponent())
        try super.tearDownWithError()
    }

    // MARK: - Helpers

    private struct StubClipboard: ClipboardReading {
        let text: String?
        func readString() -> String? { text }
    }

    private final class InMemoryResultStore: QuickSaveResultStoring {
        private(set) var stored: QuickSaveResult?
        var writeCount = 0

        func write(_ result: QuickSaveResult) {
            stored = result
            writeCount += 1
        }

        func consume() -> QuickSaveResult? {
            defer { stored = nil }
            return stored
        }
    }

    private func makeService(
        clipboardText: String?,
        store: InMemoryResultStore
    ) -> QuickSaveService {
        QuickSaveService(
            repository: repository,
            clipboard: StubClipboard(text: clipboardText),
            results: store
        )
    }

    private func readTargetFile() throws -> String {
        try String(contentsOf: fileURL, encoding: .utf8)
    }

    private func assertSuccess(_ result: QuickSaveResult, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(result.succeeded, "期望成功，实际：\(result.message ?? "nil")", file: file, line: line)
    }

    private func assertFailure(_ result: QuickSaveResult, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertFalse(result.succeeded, "期望失败，实际成功", file: file, line: line)
        XCTAssertNotNil(result.message, "失败必须带原因文案", file: file, line: line)
    }

    // MARK: - 保存

    func testSaveClipboardAppendsFormattedLine() async throws {
        preferences.categories = ["工作"]
        preferences.selectedCategory = "工作"
        let store = InMemoryResultStore()

        let result = await makeService(clipboardText: "要归档的文字", store: store).saveClipboard()

        assertSuccess(result)
        let content = try readTargetFile()
        XCTAssertTrue(content.hasPrefix("[工作]["), "应有分类前缀：\(content)")
        XCTAssertTrue(content.hasSuffix("] 要归档的文字\n"), "应以内容与换行结尾：\(content)")
    }

    func testSaveClipboardWithoutSelectedCategoryOmitsPrefix() async throws {
        let store = InMemoryResultStore()

        let result = await makeService(clipboardText: "无分类文字", store: store).saveClipboard()

        assertSuccess(result)
        let content = try readTargetFile()
        XCTAssertTrue(content.hasPrefix("["), "不应有分类前缀：\(content)")
        XCTAssertFalse(content.contains("[工作]"))
        XCTAssertTrue(content.hasSuffix("] 无分类文字\n"))
    }

    // MARK: - 守卫

    func testEmptyClipboardFailsWithoutWriting() async throws {
        let store = InMemoryResultStore()

        let result = await makeService(clipboardText: "", store: store).saveClipboard()

        assertFailure(result)
        XCTAssertEqual(result.message, ClipError.emptyClipboard.errorDescription)
        XCTAssertEqual(try readTargetFile(), "", "空剪切板不应写入任何内容")
    }

    func testWhitespaceOnlyClipboardFails() async throws {
        let store = InMemoryResultStore()

        let result = await makeService(clipboardText: "   \n  ", store: store).saveClipboard()

        assertFailure(result)
        XCTAssertEqual(try readTargetFile(), "")
    }

    func testNilClipboardFails() async throws {
        let store = InMemoryResultStore()

        let result = await makeService(clipboardText: nil, store: store).saveClipboard()

        assertFailure(result)
        XCTAssertEqual(try readTargetFile(), "")
    }

    func testMissingTargetFileReportsNotConfigured() async {
        preferences.targetFileBookmark = nil
        let store = InMemoryResultStore()

        let result = await makeService(clipboardText: "文字", store: store).saveClipboard()

        assertFailure(result)
        XCTAssertEqual(result.message, ClipError.targetFileNotConfigured.errorDescription)
    }

    // MARK: - 结果记录

    func testSuccessIsRecorded() async {
        let store = InMemoryResultStore()

        _ = await makeService(clipboardText: "文字", store: store).saveClipboard()

        XCTAssertEqual(store.writeCount, 1, "每次保存都应记录结果")
        XCTAssertEqual(store.stored?.succeeded, true)
    }

    func testFailureIsRecordedWithReason() async {
        let store = InMemoryResultStore()

        _ = await makeService(clipboardText: nil, store: store).saveClipboard()

        XCTAssertEqual(store.writeCount, 1)
        XCTAssertEqual(store.stored?.succeeded, false)
        XCTAssertEqual(store.stored?.message, ClipError.emptyClipboard.errorDescription)
    }

    func testConsumeClearsResult() async {
        let store = InMemoryResultStore()
        _ = await makeService(clipboardText: "文字", store: store).saveClipboard()

        XCTAssertNotNil(store.consume())
        XCTAssertNil(store.consume(), "消费后应清除，避免重复提示")
    }
}

/// 覆盖结果模型在 App Group 中的编解码往返。
final class QuickSaveResultStoreTests: XCTestCase {
    private var defaults: UserDefaults!
    private var suiteName: String!

    override func setUpWithError() throws {
        try super.setUpWithError()
        suiteName = "QuickSaveResultStoreTests.\(UUID().uuidString)"
        defaults = UserDefaults(suiteName: suiteName)
    }

    override func tearDownWithError() throws {
        defaults.removePersistentDomain(forName: suiteName)
        try super.tearDownWithError()
    }

    func testWriteThenConsumeReturnsEqualResult() {
        let store = QuickSaveResultStore(defaults: defaults)
        let original = QuickSaveResult.failure(message: "文件无写入权限，请重新选择")

        store.write(original)
        let consumed = store.consume()

        XCTAssertEqual(consumed, original)
    }

    func testConsumeIsOneShot() {
        let store = QuickSaveResultStore(defaults: defaults)
        store.write(.success())

        XCTAssertNotNil(store.consume())
        XCTAssertNil(store.consume(), "结果是一次性的，消费即清")
    }

    func testConsumeWithoutWriteReturnsNil() {
        let store = QuickSaveResultStore(defaults: defaults)

        XCTAssertNil(store.consume())
    }

    func testSuccessHasNoMessage() {
        let result = QuickSaveResult.success()

        XCTAssertTrue(result.succeeded)
        XCTAssertNil(result.message)
    }
}