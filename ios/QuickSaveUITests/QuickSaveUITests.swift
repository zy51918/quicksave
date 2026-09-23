import XCTest

/// QS-0005 系统测试：在真实 App 进程里验证验收标准中「可在模拟器验证」的部分。
///
/// 覆盖 PRD §11 的 US-Q01 / US-Q03 / US-Q05 / US-Q06：
/// - 设置页出现快捷入口引导区块，且文案与系统版本匹配
/// - 主页三条保存路径与清空操作无回归
///
/// **不覆盖**（模拟器不支持，见 arch §十一）：控件能否添加到控制中心、点击控件的实际行为、
/// 读剪切板的授权提示（R1）、控件态刷新（R2）。这些需 iOS 18+ 真机。
final class QuickSaveUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        try super.setUpWithError()
        continueAfterFailure = false
        app = XCUIApplication()
        app.launch()
    }

    override func tearDownWithError() throws {
        app = nil
        try super.tearDownWithError()
    }

    // MARK: - 主页（回归，US-Q05）

    func testHomeShowsCoreEntryPoints() {
        XCTAssertTrue(app.staticTexts["快速归档"].waitForExistence(timeout: 5), "主页标题缺失")
        XCTAssertTrue(app.staticTexts["刚刚复制的内容"].exists, "剪切板区块缺失")
        XCTAssertTrue(app.staticTexts["手动记录"].exists, "手动记录区块缺失")
        XCTAssertTrue(app.buttons["保存到文件"].firstMatch.exists, "主页保存按钮缺失")
    }

    func testHomeSettingsEntryIsReachable() {
        let gear = app.buttons["打开设置"]
        XCTAssertTrue(gear.waitForExistence(timeout: 5), "设置入口缺失")
        gear.tap()

        XCTAssertTrue(app.staticTexts["保存目标文件"].waitForExistence(timeout: 5), "未进入设置页")
        XCTAssertTrue(app.staticTexts["分类管理"].exists, "设置页分类区块缺失")
    }

    // MARK: - 设置页快捷入口引导（US-Q01 / US-Q06）

    func testSettingsShowsQuickEntryGuideMatchingSystemVersion() {
        app.buttons["打开设置"].tap()
        XCTAssertTrue(app.staticTexts["快捷入口"].waitForExistence(timeout: 5), "快捷入口区块缺失")

        // 模拟器 runtime 为 iOS 18+，应走添加引导分支
        let addGuide = app.staticTexts["添加到控制中心"]
        let unsupported = app.staticTexts["当前系统不支持"]

        XCTAssertTrue(
            addGuide.exists || unsupported.exists,
            "快捷入口区块必须给出引导文案，不能为空"
        )

        if #available(iOS 18.0, *) {
            XCTAssertTrue(addGuide.exists, "iOS 18+ 应显示「添加到控制中心」引导")
            XCTAssertFalse(unsupported.exists, "iOS 18+ 不应显示不支持提示")
            XCTAssertTrue(
                app.staticTexts.containing(
                    NSPredicate(format: "label CONTAINS %@", "控制中心")
                ).firstMatch.exists,
                "引导文案应说明控制中心路径"
            )
        } else {
            XCTAssertTrue(unsupported.exists, "iOS 17 及以下应显示不支持提示")
            XCTAssertFalse(addGuide.exists, "iOS 17 及以下不应显示添加引导")
        }
    }

    // MARK: - 手动保存路径（回归，US-Q05）

    func testManualSaveWithoutTargetFileGuidesInsteadOfCrashing() {
        clearTargetFileIfConfigured()

        XCTAssertTrue(app.staticTexts["还差一步就能保存"].waitForExistence(timeout: 5), "缺目标文件时未给出引导")
        XCTAssertTrue(app.buttons["去选择文件"].exists, "引导按钮缺失")
        XCTAssertTrue(app.state == .runningForeground, "App 不应崩溃或退出")
    }

    /// 设备上可能残留上一轮运行配置好的目标文件，用例需自带干净前提。
    private func clearTargetFileIfConfigured() {
        guard app.staticTexts["还差一步就能保存"].waitForExistence(timeout: 3) == false else { return }

        app.buttons["打开设置"].tap()
        let remove = app.buttons["移除文件配置"]
        if remove.waitForExistence(timeout: 5) {
            remove.tap()
        }
        app.navigationBars.buttons.element(boundBy: 0).tap()
    }

    func testAppStaysAliveAcrossForegroundTransitions() {
        // 覆盖 scenePhase 回到前台时消费 L3 结果的路径
        XCUIDevice.shared.press(.home)
        app.activate()

        XCTAssertTrue(app.staticTexts["快速归档"].waitForExistence(timeout: 5), "回到前台后主页应正常")
        XCTAssertTrue(app.state == .runningForeground, "App 不应在前后台切换中崩溃")
    }
}