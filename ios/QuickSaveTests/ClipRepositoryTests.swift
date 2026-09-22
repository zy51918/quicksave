import XCTest
@testable import QuickSave

final class ClipModelsTests: XCTestCase {
    func testFormatterMatchesAndroidFormatWithoutCategory() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let formatter = EntryFormatter(calendar: calendar)
        let date = Date(timeIntervalSince1970: 0)

        XCTAssertEqual(
            formatter.format(text: "hello", category: nil, date: date),
            "[1970-01-01 00:00:00] hello\n"
        )
    }

    func testFormatterAddsCategoryPrefix() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let formatter = EntryFormatter(calendar: calendar)

        XCTAssertEqual(
            formatter.format(text: "摘录", category: "学习", date: Date(timeIntervalSince1970: 0)),
            "[学习][1970-01-01 00:00:00] 摘录\n"
        )
    }
}

final class ClipRepositoryTests: XCTestCase {
    func testSaveWithoutTargetFileReturnsConfigurationError() async {
        let repository = makeRepository()
        let result = await repository.saveEntry(text: "text", category: nil)

        assertFailure(result, .targetFileNotConfigured)
    }

    func testSaveAppendsFormattedLineAndValidatesCategory() async throws {
        let preferences = MemoryPreferencesStore()
        preferences.targetFileBookmark = Data([1])
        preferences.categories = ["学习"]
        let files = FakeFileDataSource()
        let repository = ClipRepositoryImpl(
            preferences: preferences,
            files: files,
            formatter: EntryFormatter(calendar: fixedCalendar)
        )

        let result = await repository.saveEntry(
            text: "hello",
            category: "不存在"
        )

        assertSuccess(result)
        let lines = await files.appendedLines
        // repository 内部使用当前时间，无法注入；断言时间戳格式、分类被丢弃、正文正确
        XCTAssertEqual(lines.count, 1)
        let line = try XCTUnwrap(lines.first)
        XCTAssertTrue(
            line.hasSuffix("] hello\n"),
            "正文应为 hello，实际：\(line)"
        )
        XCTAssertFalse(
            line.hasPrefix("[不存在]"),
            "无效分类不应出现在前缀中，实际：\(line)"
        )
        let timestamp = line.dropFirst().prefix(while: { $0 != "]" })
        XCTAssertNotNil(
            timestamp.range(of: #"^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$"#, options: .regularExpression),
            "时间戳格式应为 yyyy-MM-dd HH:mm:ss，实际：\(timestamp)"
        )
    }

    func testClearDelegatesToFileDataSource() async {
        let preferences = MemoryPreferencesStore()
        preferences.targetFileBookmark = Data([1])
        let files = FakeFileDataSource()
        let repository = ClipRepositoryImpl(preferences: preferences, files: files)

        let result = await repository.clearSavedFile()

        assertSuccess(result)
        let clearCount = await files.clearCount
        XCTAssertEqual(clearCount, 1)
    }

    func testUnavailableBookmarkMapsToPermissionError() async {
        let preferences = MemoryPreferencesStore()
        preferences.targetFileBookmark = Data([1])
        let files = FakeFileDataSource()
        await files.setFailure(.inaccessible)
        let repository = ClipRepositoryImpl(preferences: preferences, files: files)

        let result = await repository.saveEntry(text: "text", category: nil)

        assertFailure(result, .targetFileUnavailable)
    }

    private func makeRepository() -> ClipRepositoryImpl {
        ClipRepositoryImpl(preferences: MemoryPreferencesStore(), files: FakeFileDataSource())
    }

    private var fixedCalendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }
}

// Void 在新版 Swift 中不再满足 Equatable，改用模式匹配断言 Result
private func assertSuccess(
    _ result: Result<Void, ClipError>,
    file: StaticString = #filePath,
    line: UInt = #line
) {
    if case .failure(let error) = result {
        XCTFail("期望成功，实际失败：\(error)", file: file, line: line)
    }
}

private func assertFailure(
    _ result: Result<Void, ClipError>,
    _ expected: ClipError,
    file: StaticString = #filePath,
    line: UInt = #line
) {
    switch result {
    case .success:
        XCTFail("期望失败 \(expected)，实际成功", file: file, line: line)
    case .failure(let error):
        XCTAssertEqual(error, expected, file: file, line: line)
    }
}

private final class MemoryPreferencesStore: PreferencesStoring {
    var targetFileBookmark: Data?
    var categories: [String] = []
    var selectedCategory: String?
}

private actor FakeFileDataSource: FileDataSource {
    var appendedLines: [String] = []
    var clearCount = 0
    var failure: Error?

    func setFailure(_ failure: FileDataSourceError) {
        self.failure = failure
    }

    func appendLine(_ line: String, to bookmark: Data) throws {
        if let failure { throw failure }
        appendedLines.append(line)
    }

    func clearFile(bookmark: Data) throws {
        if let failure { throw failure }
        clearCount += 1
    }

    func isAccessible(bookmark: Data?) async -> Bool {
        failure == nil && bookmark != nil
    }
}
