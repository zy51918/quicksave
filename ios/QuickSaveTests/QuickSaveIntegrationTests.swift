import XCTest
@testable import QuickSave

/// QS-0005 集成测试：覆盖单测够不到、但验收标准明确要求的跨层语义。
///
/// 对应 PRD §8 边界表与 §11 验收标准：
/// - L3 待办提示只在失败时打扰用户（成功已由控件态承载）
/// - 连续快速点击写入串行、无重复记录、无文件损坏
/// - 分类在触发前被删除时，按无分类格式保存
/// - 保存格式与 Android / QS-0004 完全一致
final class QuickSaveIntegrationTests: XCTestCase {
    private var dir: URL!
    private var fileURL: URL!
    private var suiteName: String!
    private var defaults: UserDefaults!
    private var preferences: UserDefaultsPreferencesStore!
    private var repository: ClipRepositoryImpl!
    private var resultStore: QuickSaveResultStore!

    override func setUpWithError() throws {
        try super.setUpWithError()
        dir = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        fileURL = dir.appendingPathComponent("quicksave.txt")
        try Data().write(to: fileURL)

        suiteName = "QuickSaveIntegrationTests.\(UUID().uuidString)"
        defaults = UserDefaults(suiteName: suiteName)
        preferences = UserDefaultsPreferencesStore(defaults: defaults)
        preferences.targetFileBookmark = try BookmarkFileDataSource.makeBookmark(for: fileURL)
        repository = ClipRepositoryImpl(preferences: preferences, files: BookmarkFileDataSource())
        resultStore = QuickSaveResultStore(defaults: defaults)
    }

    override func tearDownWithError() throws {
        defaults.removePersistentDomain(forName: suiteName)
        try? FileManager.default.removeItem(at: dir)
        try super.tearDownWithError()
    }

    private struct FixedClipboard: ClipboardReading {
        let text: String?
        func readString() -> String? { text }
    }

    private func service(_ clipboardText: String?) -> QuickSaveService {
        QuickSaveService(
            repository: repository,
            clipboard: FixedClipboard(text: clipboardText),
            results: resultStore
        )
    }

    private func fileContent() throws -> String {
        try String(contentsOf: fileURL, encoding: .utf8)
    }

    // MARK: - L3 待办提示（HomeViewModel.consumeQuickSaveResult）

    @MainActor
    private func makeHomeViewModel() -> HomeViewModel {
        HomeViewModel(
            repository: repository,
            clipboard: FixedClipboard(text: nil),
            payloads: SharedPayloadStore(defaults: defaults),
            quickSaveResults: resultStore
        )
    }

    @MainActor
    func testConsumeQuickSaveResultSurfacesFailure() async {
        resultStore.write(.failure(message: "文件无写入权限，请重新选择"))
        let model = makeHomeViewModel()

        model.consumeQuickSaveResult()

        XCTAssertEqual(model.feedback?.message, "文件无写入权限，请重新选择")
        XCTAssertEqual(model.feedback?.isError, true)
    }

    @MainActor
    func testConsumeQuickSaveResultStaysSilentOnSuccess() async {
        resultStore.write(.success())
        let model = makeHomeViewModel()

        model.consumeQuickSaveResult()

        XCTAssertNil(model.feedback, "成功不该再弹提示：用户已通过控件态得知，重复打扰是噪音")
    }

    @MainActor
    func testConsumeQuickSaveResultIsIdempotent() async {
        resultStore.write(.failure(message: "保存失败"))
        let model = makeHomeViewModel()

        model.consumeQuickSaveResult()
        model.feedback = nil
        model.consumeQuickSaveResult()

        XCTAssertNil(model.feedback, "同一结果不应在每次回到前台时重复提示")
    }

    @MainActor
    func testConsumeQuickSaveResultWithoutResultDoesNothing() async {
        let model = makeHomeViewModel()

        model.consumeQuickSaveResult()

        XCTAssertNil(model.feedback)
    }

    // MARK: - 并发点击（PRD §8「连续快速点击控件」）

    func testConcurrentSavesProduceNoLostOrDuplicateRecords() async throws {
        let count = 20
        let lines = try await withThrowingTaskGroup(of: Void.self) { group in
            for i in 0..<count {
                group.addTask { [service] in
                    _ = await service("并发记录\(i)").saveClipboard()
                }
            }
            try await group.waitForAll()
            return try self.fileContent()
        }

        let written = lines.split(separator: "\n").filter { !$0.isEmpty }
        XCTAssertEqual(written.count, count, "并发写入不得丢失或重复记录")

        // 每条记录都必须是完整的一行，不能被另一次写入拦腰截断
        let linePattern = #"^\[\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\] 并发记录\d+$"#
        for line in written {
            XCTAssertNotNil(
                line.range(of: linePattern, options: .regularExpression),
                "记录格式被破坏：\(line)"
            )
        }

        let distinct = Set(written)
        XCTAssertEqual(distinct.count, count, "出现了重复记录")
    }

    // MARK: - 分类语义（PRD §8「分类在控件触发前被删除」）

    func testSaveAfterCategoryDeletedFallsBackToUncategorized() async throws {
        preferences.categories = ["工作"]
        preferences.selectedCategory = "工作"

        // 模拟用户在 App 里删掉了该分类，控件此时触发
        preferences.categories = []
        preferences.selectedCategory = nil

        let result = await service("分类已失效的文字").saveClipboard()

        XCTAssertTrue(result.succeeded)
        let content = try fileContent()
        XCTAssertFalse(content.contains("[工作]"), "不应写入已删除的分类名：\(content)")
        XCTAssertTrue(content.hasPrefix("["), "应退化为无分类格式：\(content)")
        XCTAssertTrue(content.hasSuffix("] 分类已失效的文字\n"))
    }

    // MARK: - 格式一致性（验收标准：与 Android / QS-0004 完全一致）

    func testSavedFormatMatchesAndroidConvention() async throws {
        preferences.categories = ["灵感"]
        preferences.selectedCategory = "灵感"

        _ = await service("跨平台一致").saveClipboard()

        let content = try fileContent().trimmingCharacters(in: .whitespacesAndNewlines)
        // Android 约定：[分类][yyyy-MM-dd HH:mm:ss] 内容
        let pattern = #"^\[灵感\]\[\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\] 跨平台一致$"#
        XCTAssertNotNil(
            content.range(of: pattern, options: .regularExpression),
            "格式与 Android 不一致：\(content)"
        )
    }

    // MARK: - 与主 App 的存储契约（验收标准：不引入第二套存储）

    func testControlAndAppWriteToSameFileWithSameFormat() async throws {
        preferences.categories = ["共享"]
        preferences.selectedCategory = "共享"

        // 控件路径
        _ = await service("控件写入").saveClipboard()
        // 主 App 路径
        _ = await repository.saveEntry(text: "主页写入", category: "共享")

        let written = try fileContent().split(separator: "\n").filter { !$0.isEmpty }
        XCTAssertEqual(written.count, 2, "两条路径必须写同一文件")
        XCTAssertTrue(written[0].hasSuffix("] 控件写入"), "格式不一致：\(written[0])")
        XCTAssertTrue(written[1].hasSuffix("] 主页写入"), "格式不一致：\(written[1])")
    }

    // MARK: - 失败结果的跨进程可见性（L3 之所以必要）

    func testFailureResultWrittenInExtensionIsVisibleToApp() async {
        // 扩展进程写入（这里用同一 store 模拟 App Group 的共享语义）
        _ = await service(nil).saveClipboard()

        let consumed = resultStore.consume()
        XCTAssertEqual(consumed?.succeeded, false)
        XCTAssertEqual(consumed?.message, ClipError.emptyClipboard.errorDescription)
    }
}