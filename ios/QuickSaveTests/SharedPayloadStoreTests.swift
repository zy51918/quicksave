import XCTest
@testable import QuickSave

final class SharedPayloadStoreTests: XCTestCase {
    func testConsumeIsOneShot() throws {
        let defaults = UserDefaults(suiteName: "QuickSaveTests.\(UUID().uuidString)")!
        let store = SharedPayloadStore(defaults: defaults)

        try store.write("分享的文字")

        XCTAssertEqual(store.consume(), "分享的文字")
        XCTAssertNil(store.consume())
    }

    func testBlankPayloadIsRejected() {
        let defaults = UserDefaults(suiteName: "QuickSaveTests.\(UUID().uuidString)")!
        let store = SharedPayloadStore(defaults: defaults)

        XCTAssertThrowsError(try store.write(" \n"))
    }
}
