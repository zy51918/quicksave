# 跨应用文本分享自动保存功能实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 QuickSave 增加 Android `ACTION_SEND` 纯文本接收入口，分享后自动按当前分类保存，并在成功或失败后返回原 App。

**Architecture:** 新增独立的 `ShareReceiverActivity` 作为系统分享入口；新增纯 Kotlin `ShareContentParser` 负责校验分享载荷；新增 `ShareSaveCoordinator` 复用 `ClipRepository` 读取当前分类并执行保存。接收 Activity 只负责 Intent、Toast 和生命周期，不重复实现文件写入逻辑。

**Tech Stack:** Kotlin 2.1.20、Android Activity、Android Intent、Kotlin Coroutines/Flow、AndroidX Lifecycle、JUnit 4、Mockito-Kotlin、现有 Compose/Material 主题。

**Spec:** `docs/features/QS-0004/design-share-text.md`

## Global Constraints

- Android 最低支持 API 29（Android 9），`applicationId` 和 `namespace` 必须保持 `com.ylib.quicksave`。
- 首版只接收单条 `ACTION_SEND` + `text/plain` + `Intent.EXTRA_TEXT`，不处理 `ACTION_SEND_MULTIPLE`、图片、文件 Uri 或仅 HTML 内容。
- 分享保存直接使用 DataStore 中当前选中的分类；没有选中分类时向 `saveEntry` 传入 `null`。
- 分享成功或失败只显示短暂提示并结束接收 Activity，不打开 QuickSave 主页面。
- 不新增数据库、网络同步、后台服务或运行时权限；保存格式继续由 `ClipRepository.saveEntry` 负责。
- 分享文本通过 `isBlank()` 校验，但通过校验后必须保留原始空格、换行、网址和代码格式，不执行 `trim()`。
- 所有新增用户可见文案放入 `app/src/main/res/values/strings.xml`，不在 Activity 中硬编码。

---

## 文件与职责映射

- Create: `app/src/main/java/com/ylib/quicksave/share/ShareContentParser.kt` — 纯文本分享载荷的校验和结果类型。
- Create: `app/src/main/java/com/ylib/quicksave/share/ShareSaveCoordinator.kt` — 读取当前分类并调用 `ClipRepository.saveEntry`。
- Create: `app/src/main/java/com/ylib/quicksave/share/ShareReceiverActivity.kt` — Android 分享入口、Toast 和 `finish()`。
- Modify: `app/src/main/AndroidManifest.xml` — 注册导出的 `ACTION_SEND`/`text/plain` Activity。
- Modify: `app/src/main/res/values/strings.xml` — 分享成功和失败提示。
- Create: `app/src/test/java/com/ylib/quicksave/share/ShareContentParserTest.kt` — 解析器单元测试。
- Create: `app/src/test/java/com/ylib/quicksave/share/ShareSaveCoordinatorTest.kt` — 分类传递和仓库失败单元测试。
- Create: `app/src/androidTest/java/com/ylib/quicksave/share/ShareReceiverActivityTest.kt` — 使用现有 Instrumentation API 验证不支持的分享 Intent 会结束接收 Activity。

---

### Task 1: 为分享载荷定义纯 Kotlin 解析器并编写失败测试

**Files:**
- Create: `app/src/main/java/com/ylib/quicksave/share/ShareContentParser.kt`
- Test: `app/src/test/java/com/ylib/quicksave/share/ShareContentParserTest.kt`

**Interfaces:**
- Produces `ShareParseResult`：`Success(text: String)`、`Unsupported`、`Empty`。
- Produces `ShareContentParser.parse(action: String?, mimeType: String?, text: CharSequence?): ShareParseResult`。

- [ ] **Step 1: 写解析器失败测试**

创建 `ShareContentParserTest`，使用 JUnit 4 验证以下行为：

```kotlin
class ShareContentParserTest {

    @Test
    fun `accepts a non blank ACTION_SEND plain text`() {
        val result = ShareContentParser.parse(
            action = Intent.ACTION_SEND,
            mimeType = "text/plain",
            text = "  https://example.com  "
        )

        assertEquals(
            ShareParseResult.Success("  https://example.com  "),
            result
        )
    }

    @Test
    fun `preserves line breaks and spaces`() {
        val text = "标题\n\n  code = true  "

        assertEquals(
            ShareParseResult.Success(text),
            ShareContentParser.parse(Intent.ACTION_SEND, "text/plain", text)
        )
    }

    @Test
    fun `rejects wrong action`() {
        assertEquals(
            ShareParseResult.Unsupported,
            ShareContentParser.parse(Intent.ACTION_SEND_MULTIPLE, "text/plain", "text")
        )
    }

    @Test
    fun `rejects wrong mime type`() {
        assertEquals(
            ShareParseResult.Unsupported,
            ShareContentParser.parse(Intent.ACTION_SEND, "text/html", "text")
        )
    }

    @Test
    fun `rejects missing or blank text`() {
        assertEquals(
            ShareParseResult.Empty,
            ShareContentParser.parse(Intent.ACTION_SEND, "text/plain", null)
        )
        assertEquals(
            ShareParseResult.Empty,
            ShareContentParser.parse(Intent.ACTION_SEND, "text/plain", " \n\t")
        )
    }
}
```

测试文件使用已有的 `android.content.Intent` 常量、`org.junit.Assert.assertEquals`，不引入新依赖。

- [ ] **Step 2: 运行测试确认先失败**

运行：

```bash
./gradlew test --tests com.ylib.quicksave.share.ShareContentParserTest
```

预期：由于 `ShareContentParser` 和 `ShareParseResult` 尚未创建，编译失败。

- [ ] **Step 3: 实现最小解析器**

在 `ShareContentParser.kt` 中实现以下契约：

```kotlin
package com.ylib.quicksave.share

import android.content.Intent

sealed interface ShareParseResult {
    data class Success(val text: String) : ShareParseResult
    data object Unsupported : ShareParseResult
    data object Empty : ShareParseResult
}

object ShareContentParser {
    fun parse(
        action: String?,
        mimeType: String?,
        text: CharSequence?
    ): ShareParseResult {
        if (action != Intent.ACTION_SEND || mimeType != "text/plain") {
            return ShareParseResult.Unsupported
        }
        val rawText = text?.toString() ?: return ShareParseResult.Empty
        if (rawText.isBlank()) return ShareParseResult.Empty
        return ShareParseResult.Success(rawText)
    }
}
```

不要在解析器中调用 `trim()`，也不要读取 `EXTRA_TITLE` 或其他字段。

- [ ] **Step 4: 运行测试确认通过**

运行：

```bash
./gradlew test --tests com.ylib.quicksave.share.ShareContentParserTest
```

预期：全部解析器测试通过。

- [ ] **Step 5: 提交解析器变更**

```bash
git add app/src/main/java/com/ylib/quicksave/share/ShareContentParser.kt app/src/test/java/com/ylib/quicksave/share/ShareContentParserTest.kt
git commit -m "增加分享文本解析器"
```

---

### Task 2: 抽取分享保存协调器并验证分类传递

**Files:**
- Create: `app/src/main/java/com/ylib/quicksave/share/ShareSaveCoordinator.kt`
- Test: `app/src/test/java/com/ylib/quicksave/share/ShareSaveCoordinatorTest.kt`
- Reference: `app/src/main/java/com/ylib/quicksave/data/repository/ClipRepository.kt`

**Interfaces:**
- Consumes `ClipRepository.getSelectedCategory(): Flow<String?>` 和 `ClipRepository.saveEntry(text: String, category: String?): Result<Unit>`。
- Produces `class ShareSaveCoordinator(private val repository: ClipRepository)`。
- Produces `suspend fun save(text: String): Result<Unit>`，先读取当前分类，再调用 `saveEntry(text, category)`。

- [ ] **Step 1: 写分类传递和失败传播测试**

```kotlin
class ShareSaveCoordinatorTest {

    private val repository = mock<ClipRepository>()
    private val coordinator = ShareSaveCoordinator(repository)

    @Test
    fun `save uses currently selected category`() = runTest {
        whenever(repository.getSelectedCategory()).thenReturn(flowOf("工作"))
        whenever(repository.saveEntry("正文", "工作"))
            .thenReturn(Result.success(Unit))

        val result = coordinator.save("正文")

        assertTrue(result.isSuccess)
        verify(repository).saveEntry("正文", "工作")
    }

    @Test
    fun `save passes null when no category is selected`() = runTest {
        whenever(repository.getSelectedCategory()).thenReturn(flowOf(null))
        whenever(repository.saveEntry("正文", null))
            .thenReturn(Result.success(Unit))

        val result = coordinator.save("正文")

        assertTrue(result.isSuccess)
        verify(repository).saveEntry("正文", null)
    }

    @Test
    fun `save propagates repository failure`() = runTest {
        val failure = IllegalStateException("未设置目标文件")
        whenever(repository.getSelectedCategory()).thenReturn(flowOf(null))
        whenever(repository.saveEntry("正文", null))
            .thenReturn(Result.failure(failure))

        val result = coordinator.save("正文")

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
    }
}
```

- [ ] **Step 2: 运行测试确认先失败**

运行：

```bash
./gradlew test --tests com.ylib.quicksave.share.ShareSaveCoordinatorTest
```

预期：由于 `ShareSaveCoordinator` 尚未创建，编译失败。

- [ ] **Step 3: 实现最小协调器**

```kotlin
package com.ylib.quicksave.share

import com.ylib.quicksave.data.repository.ClipRepository
import kotlinx.coroutines.flow.first

class ShareSaveCoordinator(
    private val repository: ClipRepository
) {
    suspend fun save(text: String): Result<Unit> {
        val category = repository.getSelectedCategory().first()
        return repository.saveEntry(text, category)
    }
}
```

协调器不捕获或改写异常，保留仓库返回的 `Result`，由 Activity 统一决定用户可见文案。

- [ ] **Step 4: 运行测试确认通过**

运行：

```bash
./gradlew test --tests com.ylib.quicksave.share.ShareSaveCoordinatorTest
```

预期：分类传递、无分类和失败传播测试全部通过。

- [ ] **Step 5: 提交协调器变更**

```bash
git add app/src/main/java/com/ylib/quicksave/share/ShareSaveCoordinator.kt app/src/test/java/com/ylib/quicksave/share/ShareSaveCoordinatorTest.kt
git commit -m "增加分享保存协调器"
```

---

### Task 3: 注册分享接收 Activity 并增加提示文案

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces Android 可发现的 `ACTION_SEND` + `text/plain` 分享入口。
- Consumes现有 `Theme.QuickSave.Transparent` 和 `com.ylib.quicksave.ui.InputActivity` 的 Manifest 配置模式。

- [ ] **Step 1: 增加用户可见文案**

在 `<resources>` 中增加：

```xml
<string name="share_saved">已保存</string>
<string name="share_error_no_target_file">尚未设置保存文件</string>
<string name="share_error_unsupported_content">不支持的分享内容</string>
<string name="share_error_empty_content">分享内容为空</string>
<string name="share_error_generic">保存失败</string>
```

目标文件不可写时直接复用仓库已有中文异常信息“目标文件无写入权限，请重新选择”，不重复创建不同文案。

- [ ] **Step 2: 在 Manifest 注册接收 Activity**

在现有 Activity 声明区域新增：

```xml
<activity
    android:name=".share.ShareReceiverActivity"
    android:exported="true"
    android:excludeFromRecents="true"
    android:label="@string/app_name"
    android:theme="@style/Theme.QuickSave.Transparent">
    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
    </intent-filter>
</activity>
```

不要增加 `SEND_MULTIPLE`、图片 MIME、文件 Uri 或任何权限。保留 `applicationId` 和 `namespace` 为 `com.ylib.quicksave`。

- [ ] **Step 3: 验证资源和 Manifest 编译**

运行：

```bash
./gradlew :app:processDebugResources
```

预期：资源和 Manifest 配置处理成功。完整 APK 构建放在 Task 4 完成后执行，因为此时接收 Activity 尚未实现。

- [ ] **Step 4: 提交 Manifest 和文案变更**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml
git commit -m "注册纯文本分享入口"
```

---

### Task 4: 实现分享接收 Activity 的自动保存流程

**Files:**
- Create: `app/src/main/java/com/ylib/quicksave/share/ShareReceiverActivity.kt`
- Reference: `app/src/main/java/com/ylib/quicksave/app/QuickSaveApplication.kt`
- Reference: `app/src/main/java/com/ylib/quicksave/MainActivity.kt`

**Interfaces:**
- Consumes `ShareContentParser.parse(action, mimeType, text)`。
- Consumes `ShareSaveCoordinator.save(text)`。
- Produces一次性分享处理：解析失败或保存完成后显示 Toast 并 `finish()`。

- [ ] **Step 1: 写 Android 生命周期测试并运行确认先失败**

Activity 依赖 Android 生命周期和 Toast，不把 Toast 作为断言。使用项目已有的 `InstrumentationRegistry` 和 `AndroidJUnit4`，先验证不支持的 MIME 会结束分享接收 Activity：

```kotlin
@RunWith(AndroidJUnit4::class)
class ShareReceiverActivityTest {

    @Test
    fun unsupportedMime_finishesShareReceiverWithoutOpeningHome() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, ShareReceiverActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/html"
            putExtra(Intent.EXTRA_TEXT, "<p>不支持</p>")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val activity = InstrumentationRegistry.getInstrumentation()
            .startActivitySync(intent)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertTrue(activity.isFinishing || activity.isDestroyed)
        assertFalse(activity.javaClass.name.contains("MainActivity"))
    }
}
```

运行：

```bash
./gradlew connectedAndroidTest --tests com.ylib.quicksave.share.ShareReceiverActivityTest
```

预期：在 Activity 尚未实现前测试编译或运行失败。设备测试环境不可用时，该命令会因无设备失败；Task 5 仍必须完成 `test`、`lint` 和 `assembleDebug`。

- [ ] **Step 2: 实现一次性接收和解析分支**

Activity 的关键结构应为：

```kotlin
class ShareReceiverActivity : ComponentActivity() {

    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (handled) return
        handled = true

        when (
            val parsed = ShareContentParser.parse(
                action = intent.action,
                mimeType = intent.type,
                text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            )
        ) {
            is ShareParseResult.Success -> save(parsed.text)
            ShareParseResult.Empty -> finishWithToast(R.string.share_error_empty_content)
            ShareParseResult.Unsupported -> finishWithToast(R.string.share_error_unsupported_content)
        }
    }

    private fun save(text: String) {
        val repository = (application as QuickSaveApplication).clipRepository
        lifecycleScope.launch {
            val result = runCatching {
                ShareSaveCoordinator(repository).save(text)
            }.getOrElse { Result.failure(it) }
            if (result.isSuccess) {
                finishWithToast(R.string.share_saved)
            } else {
                finishWithToast(errorMessageResOrText(result.exceptionOrNull()))
            }
        }
    }
}
```

实现时需补齐以下具体行为：

- `finishWithToast` 使用 `Toast.makeText(this, message, Toast.LENGTH_SHORT).show()`，然后调用 `finish()`；
- 不调用 `startActivity(Intent(this, MainActivity::class.java))`；
- 处理中的异常必须通过 `runCatching` 转为失败结果，不能让分享入口崩溃；
- 失败消息映射：
  - 异常消息为 `未设置目标文件` 时使用 `R.string.share_error_no_target_file`；
  - 异常消息为空时使用 `R.string.share_error_generic`；
  - 其他异常消息直接显示仓库已有中文消息，例如 `目标文件无写入权限，请重新选择`；
- `onNewIntent` 若被触发且 `handled` 已为 `true`，只忽略，不重复保存；
- Activity 不设置 Compose 内容，不显示手动输入页面。

为支持“资源文案或异常文本”两种提示，建议定义：

```kotlin
private sealed interface ShareMessage {
    data class Resource(@StringRes val id: Int) : ShareMessage
    data class Text(val value: String) : ShareMessage
}
```

`finishWithToast` 根据 `ShareMessage` 选择 `getString(id)` 或直接使用文本，避免把资源 ID 和异常消息混在一起。

- [ ] **Step 3: 运行单元测试和 Debug 构建**

运行：

```bash
./gradlew test --tests com.ylib.quicksave.share.ShareContentParserTest --tests com.ylib.quicksave.share.ShareSaveCoordinatorTest
./gradlew :app:assembleDebug
```

预期：新增测试通过，Debug APK 构建成功。

- [ ] **Step 4: 提交 Activity 实现**

```bash
git add app/src/main/java/com/ylib/quicksave/share/ShareReceiverActivity.kt
git commit -m "实现分享文本自动保存"
```

---

### Task 5: 运行全量验证并检查分享入口边界

**Files:**
- Verify: `app/src/main/AndroidManifest.xml`
- Verify: `app/src/main/java/com/ylib/quicksave/share/ShareReceiverActivity.kt`
- Verify: `app/src/main/java/com/ylib/quicksave/share/ShareContentParser.kt`
- Verify: `app/src/main/java/com/ylib/quicksave/share/ShareSaveCoordinator.kt`

- [ ] **Step 1: 运行全量 JVM 测试**

```bash
./gradlew test
```

预期：现有仓库、ViewModel、悬浮窗、录音和新增分享测试全部通过。

- [ ] **Step 2: 运行 Lint 和 Debug 构建**

```bash
./gradlew lint assembleDebug
```

预期：Lint 无新增错误，Debug APK 生成成功。

- [ ] **Step 3: 在设备或模拟器验证真实分享链路**

使用浏览器或聊天 App 执行：

1. 配置一个可写目标文件，并分别测试有分类、无分类。
2. 分享普通文本、网址、包含换行的代码片段。
3. 确认 QuickSave 出现在分享面板。
4. 确认目标文件追加已有格式的记录。
5. 确认成功提示后回到原 App。
6. 清除目标文件配置或撤销写权限，确认失败提示后仍回到原 App，不打开主页。
7. 分享图片或文件，确认 QuickSave 不作为对应 MIME 类型的处理目标，或进入后提示不支持并返回。

如有连接设备，再运行：

```bash
./gradlew connectedAndroidTest
```

- [ ] **Step 4: 检查 Git 状态和提交范围**

```bash
git status --short
git log --oneline -8
```

确认没有生成未预期的构建产物或本地配置变更，且提交只包含 QS-0004 分享功能相关文件。

- [ ] **Step 5: 提交最终验证结果**

如果全量测试和构建通过，创建最终小提交记录验证或文档更新：

```bash
git add docs/features/QS-0004/design-share-text.md
git commit -m "完善分享功能验证记录"
```

只有确实修改了验证记录或设计文档时才执行该提交；没有文件变化时不创建空提交。


