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

        XCTAssertEqual(result, .failure(.targetFileNotConfigured))
    }

    func testSaveAppendsFormattedLineAndValidatesCategory() async {
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

        XCTAssertEqual(result, .success(()))
        let lines = await files.appendedLines
        XCTAssertEqual(lines, ["[1970-01-01 00:00:00] hello\n"])
    }

    func testClearDelegatesToFileDataSource() async {
        let preferences = MemoryPreferencesStore()
        preferences.targetFileBookmark = Data([1])
        let files = FakeFileDataSource()
        let repository = ClipRepositoryImpl(preferences: preferences, files: files)

        let result = await repository.clearSavedFile()

        XCTAssertEqual(result, .success(()))
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

        XCTAssertEqual(result, .failure(.targetFileUnavailable))
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
