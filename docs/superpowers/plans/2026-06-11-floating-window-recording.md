# 全局悬浮窗 · 计划 C：录音按钮 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 点悬浮窗面板的【录音】按钮立即开始录音（QuickSave 自录），再次点击（或通知里「停止」）停止；录音期间切 App / 锁屏不中断；录音文件存到 `Music/QuickSave/QS_yyyyMMdd_HHmmss.m4a`。录音中收起态把手变红 + 红点，展开态录音按钮变红显示计时。

**Architecture:** 新增独立 `RecorderService`（`microphone` 前台类型 + `RECORD_AUDIO` 运行时权限），用 `MediaRecorder` 录制并经 MediaStore 写入公共 `Music/QuickSave/`。进程内单例 `RecordingController`（`StateFlow<RecordingUiState>`）桥接 `RecorderService`（更新状态）与 `OverlayService`（订阅状态以更新把手/按钮 UI）。悬浮窗无法直接弹系统权限框，未授权时由透明 `RecordPermissionActivity` 申请。纯逻辑（文件名生成）抽到可单测的 `RecordingFileNamer`。

**Tech Stack:** Kotlin、MediaRecorder、MediaStore（API 29+ scoped storage + IS_PENDING）、microphone 前台服务、Kotlin coroutines/StateFlow、JUnit4。

**关联：** 设计文档 `docs/features/QS-0003/design-floating-window.md` §4.3 / §5 / §6 / §7；前置已合并的计划 A（悬浮窗基础设施）与计划 B（文字输入）。本计划完成后，悬浮窗两个预定义按钮全部具备真实功能。

---

## 文件结构

**新建：**
- `app/src/main/java/com/ylib/quicksave/recorder/RecordingUiState.kt` — 录音 UI 状态数据类
- `app/src/main/java/com/ylib/quicksave/recorder/RecordingController.kt` — 进程内单例状态桥
- `app/src/main/java/com/ylib/quicksave/recorder/RecordingFileNamer.kt` — 文件名生成（纯逻辑，单测）
- `app/src/main/java/com/ylib/quicksave/recorder/RecorderService.kt` — microphone 前台录音服务
- `app/src/main/java/com/ylib/quicksave/ui/RecordPermissionActivity.kt` — 透明权限申请 Activity
- `app/src/test/java/com/ylib/quicksave/recorder/RecordingFileNamerTest.kt` — 单测

**修改：**
- `app/src/main/AndroidManifest.xml` — 新增 `RECORD_AUDIO`、`FOREGROUND_SERVICE_MICROPHONE` 权限；声明 `RecorderService`、`RecordPermissionActivity`
- `app/src/main/java/com/ylib/quicksave/util/PermissionHelper.kt` — 新增 `hasRecordAudioPermission`
- `app/src/main/java/com/ylib/quicksave/overlay/OverlayService.kt` — 【录音】按钮接线（开始/停止/申请权限）+ 订阅 `RecordingController` 更新把手/按钮录音态

---

## Task C1：权限声明 + RecordingController + RecordingFileNamer（含 TDD）

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/ylib/quicksave/util/PermissionHelper.kt`
- Create: `app/src/main/java/com/ylib/quicksave/recorder/RecordingUiState.kt`
- Create: `app/src/main/java/com/ylib/quicksave/recorder/RecordingController.kt`
- Create: `app/src/main/java/com/ylib/quicksave/recorder/RecordingFileNamer.kt`
- Test: `app/src/test/java/com/ylib/quicksave/recorder/RecordingFileNamerTest.kt`

- [ ] **Step 1：Manifest 新增权限**

在 `AndroidManifest.xml` 的 `<uses-permission>` 区追加：
```xml
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
```

- [ ] **Step 2：PermissionHelper 新增麦克风权限判断**

在 `PermissionHelper` 对象内（`canDrawOverlays` 之后）新增：
```kotlin
    /** 是否已授予录音（RECORD_AUDIO）权限。 */
    fun hasRecordAudioPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
```
（`Manifest`、`ContextCompat`、`PackageManager` 已在该文件 import。）

- [ ] **Step 3：RecordingUiState**

`RecordingUiState.kt`：
```kotlin
package com.ylib.quicksave.recorder

/** 录音 UI 状态：由 RecorderService 更新、OverlayService 订阅。 */
data class RecordingUiState(
    val isRecording: Boolean = false,
    val elapsedSeconds: Int = 0
)
```

- [ ] **Step 4：RecordingController（进程内单例状态桥）**

`RecordingController.kt`：
```kotlin
package com.ylib.quicksave.recorder

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 进程内单例：桥接 RecorderService（写入）与 OverlayService（订阅）的录音状态。
 * 两个服务同进程，直接共享一个 StateFlow。
 */
object RecordingController {
    private val _state = MutableStateFlow(RecordingUiState())
    val state: StateFlow<RecordingUiState> = _state.asStateFlow()

    fun update(isRecording: Boolean, elapsedSeconds: Int) {
        _state.value = RecordingUiState(isRecording, elapsedSeconds)
    }

    fun reset() {
        _state.value = RecordingUiState()
    }
}
```

- [ ] **Step 5：写失败的测试** `RecordingFileNamerTest.kt`：
```kotlin
package com.ylib.quicksave.recorder

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class RecordingFileNamerTest {

    @Test
    fun `name matches QS timestamp m4a pattern`() {
        val result = RecordingFileNamer.name(Date(0L))
        assertTrue(
            "应形如 QS_yyyyMMdd_HHmmss.m4a，实际：$result",
            Regex("""QS_\d{8}_\d{6}\.m4a""").matches(result)
        )
    }

    @Test
    fun `name starts with QS_ and ends with m4a`() {
        val result = RecordingFileNamer.name(Date(1_700_000_000_000L))
        assertTrue(result.startsWith("QS_"))
        assertTrue(result.endsWith(".m4a"))
    }

    @Test
    fun `different times produce timestamped names of fixed length`() {
        // "QS_" (3) + 8 + "_" (1) + 6 + ".m4a" (4) = 22
        assertTrue(RecordingFileNamer.name(Date(0L)).length == 22)
    }
}
```

- [ ] **Step 6：运行测试确认失败**

Run: `./gradlew testDebugUnitTest --tests "com.ylib.quicksave.recorder.RecordingFileNamerTest"`
Expected: FAIL，"Unresolved reference: RecordingFileNamer"。

- [ ] **Step 7：写实现** `RecordingFileNamer.kt`：
```kotlin
package com.ylib.quicksave.recorder

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 录音文件名生成：QS_yyyyMMdd_HHmmss.m4a（本地时区）。纯逻辑，便于单测。 */
object RecordingFileNamer {
    private const val PREFIX = "QS_"
    private const val EXTENSION = ".m4a"

    fun name(date: Date): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(date)
        return "$PREFIX$stamp$EXTENSION"
    }
}
```

- [ ] **Step 8：运行测试确认通过**

Run: `./gradlew testDebugUnitTest --tests "com.ylib.quicksave.recorder.RecordingFileNamerTest"`
Expected: PASS（3 个测试）。

- [ ] **Step 9：编译验证**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 10：提交**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/com/ylib/quicksave/util/PermissionHelper.kt app/src/main/java/com/ylib/quicksave/recorder/ app/src/test/java/com/ylib/quicksave/recorder/
git commit -m "feat(recorder): add permissions, RecordingController and RecordingFileNamer"
```
追加（空行后）：
```
Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

## Task C2：RecorderService（MediaRecorder + MediaStore + 计时 + 通知）

无法 JVM 单测，提供完整代码，构建验证 + C5 设备验证。

**Files:**
- Create: `app/src/main/java/com/ylib/quicksave/recorder/RecorderService.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1：声明服务**

在 `AndroidManifest.xml` 的 `<application>` 内（建议放在 `OverlayService` 之后）追加：
```xml
        <service
            android:name=".recorder.RecorderService"
            android:exported="false"
            android:foregroundServiceType="microphone" />
```

- [ ] **Step 2：编写 RecorderService**

`RecorderService.kt`（完整内容）：
```kotlin
package com.ylib.quicksave.recorder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.ylib.quicksave.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Date

/**
 * microphone 前台录音服务。ACTION_START 开始、ACTION_STOP 停止（toggle 由 OverlayService 控制）。
 * 录音文件经 MediaStore 写入公共 Music/QuickSave/，文件名 QS_yyyyMMdd_HHmmss.m4a。
 * 前台服务保证切 App / 锁屏不中断；MediaRecorder 出错时停止并保留已录片段。
 */
class RecorderService : Service() {

    companion object {
        const val ACTION_START = "com.ylib.quicksave.recorder.START"
        const val ACTION_STOP = "com.ylib.quicksave.recorder.STOP"
        private const val CHANNEL_ID = "quicksave_recorder_channel"
        private const val NOTIFICATION_ID = 1003
        private const val SUBDIR = "Music/QuickSave"
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private var recorder: MediaRecorder? = null
    private var pfd: ParcelFileDescriptor? = null
    private var outputUri: Uri? = null
    private var timerJob: Job? = null
    private var elapsed = 0
    private var recording = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> if (!recording) startRecording()
            ACTION_STOP -> stopRecording(success = true)
            else -> {}
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // 进程被杀/服务销毁：安全停止并保留已录片段
        if (recording) stopRecording(success = true)
        scope.cancel()
        super.onDestroy()
    }

    // --- 录音开始 ---
    private fun startRecording() {
        val uri = createPendingOutput()
        if (uri == null) {
            toast("无法创建录音文件")
            stopSelf()
            return
        }
        val descriptor = runCatching { contentResolver.openFileDescriptor(uri, "w") }.getOrNull()
        if (descriptor == null) {
            toast("无法打开录音文件")
            deletePending(uri)
            stopSelf()
            return
        }
        val rec = newRecorder()
        try {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioEncodingBitRate(128_000)
            rec.setAudioSamplingRate(44_100)
            rec.setOutputFile(descriptor.fileDescriptor)
            rec.setOnErrorListener { _, _, _ -> stopRecording(success = true) }
            rec.prepare()
            rec.start()
        } catch (e: Exception) {
            runCatching { rec.release() }
            runCatching { descriptor.close() }
            deletePending(uri)
            toast("录音启动失败：${e.message}")
            stopSelf()
            return
        }

        recorder = rec
        pfd = descriptor
        outputUri = uri
        recording = true
        elapsed = 0

        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification(0))
        RecordingController.update(isRecording = true, elapsedSeconds = 0)

        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                elapsed += 1
                RecordingController.update(isRecording = true, elapsedSeconds = elapsed)
                notifyElapsed(elapsed)
            }
        }
    }

    // --- 录音停止 ---
    private fun stopRecording(success: Boolean) {
        if (!recording) {
            stopSelf()
            return
        }
        recording = false
        timerJob?.cancel()
        timerJob = null

        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        runCatching { pfd?.close() }
        pfd = null

        outputUri?.let { finalizePending(it) }
        outputUri = null

        RecordingController.reset()
        toast(if (success) "录音已保存" else "录音已停止")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // --- MediaStore 输出 ---
    private fun createPendingOutput(): Uri? {
        val name = RecordingFileNamer.name(Date())
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, SUBDIR)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return runCatching { contentResolver.insert(collection, values) }.getOrNull()
    }

    private fun finalizePending(uri: Uri) {
        runCatching {
            val values = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            contentResolver.update(uri, values, null, null)
        }
    }

    private fun deletePending(uri: Uri) {
        runCatching { contentResolver.delete(uri, null, null) }
    }

    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    // --- 通知 ---
    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "QuickSave 录音", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "录音进行中通知" }
            )
        }
    }

    private fun buildNotification(seconds: Int): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this, 3,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 4,
            Intent(this, RecorderService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("正在录音")
            .setContentText(formatElapsed(seconds))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "停止", stopIntent)
            .build()
    }

    private fun notifyElapsed(seconds: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(seconds))
    }

    private fun formatElapsed(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }

    private fun toast(msg: String) {
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
    }
}
```

- [ ] **Step 3：编译验证**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4：构建 Debug**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL（manifest 合并通过）。

- [ ] **Step 5：提交**

```bash
git add app/src/main/java/com/ylib/quicksave/recorder/RecorderService.kt app/src/main/AndroidManifest.xml
git commit -m "feat(recorder): add microphone foreground RecorderService with MediaStore output"
```
追加（空行后）：
```
Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

## Task C3：RecordPermissionActivity（透明，申请 RECORD_AUDIO）

**Files:**
- Create: `app/src/main/java/com/ylib/quicksave/ui/RecordPermissionActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1：编写 Activity**

`RecordPermissionActivity.kt`（完整内容）：
```kotlin
package com.ylib.quicksave.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.ylib.quicksave.recorder.RecorderService

/**
 * 透明、无 UI 的权限申请 Activity：从悬浮窗【录音】按钮在无 RECORD_AUDIO 权限时拉起。
 * 授予后立即以 ACTION_START 启动 RecorderService；拒绝则 Toast 提示。完成即 finish。
 */
class RecordPermissionActivity : ComponentActivity() {

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, RecorderService::class.java).apply {
                        action = RecorderService.ACTION_START
                    }
                )
            } else {
                Toast.makeText(this, "需要麦克风权限才能录音", Toast.LENGTH_SHORT).show()
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            ContextCompat.startForegroundService(
                this,
                Intent(this, RecorderService::class.java).apply {
                    action = RecorderService.ACTION_START
                }
            )
            finish()
        } else {
            requestPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
```

- [ ] **Step 2：声明 Activity**

在 `AndroidManifest.xml` 的 `<application>` 内（建议放在 `InputActivity` 之后）追加：
```xml
        <activity
            android:name=".ui.RecordPermissionActivity"
            android:exported="false"
            android:excludeFromRecents="true"
            android:theme="@style/Theme.QuickSave.Transparent" />
```

- [ ] **Step 3：编译验证**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4：构建 Debug**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5：提交**

```bash
git add app/src/main/java/com/ylib/quicksave/ui/RecordPermissionActivity.kt app/src/main/AndroidManifest.xml
git commit -m "feat(recorder): add transparent RecordPermissionActivity for RECORD_AUDIO"
```
追加（空行后）：
```
Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

## Task C4：OverlayService 接线录音按钮 + 录音态可视化

**Files:**
- Modify: `app/src/main/java/com/ylib/quicksave/overlay/OverlayService.kt`

> 背景：当前 `OverlayService` 中【录音】按钮为 Toast 占位；`buildPanelView()` 内联创建两个按钮；`scope`（`CoroutineScope(SupervisorJob() + Dispatchers.Main)`）已存在；`rootView: FrameLayout?`、`handleView: View?`、`dp()`、`HANDLE_W_DP`、`HANDLE_H_DP` 已存在。

- [ ] **Step 1：新增 import**

在 `OverlayService.kt` import 区补充以下 5 个：
```kotlin
import com.ylib.quicksave.recorder.RecorderService
import com.ylib.quicksave.recorder.RecordingController
import com.ylib.quicksave.ui.RecordPermissionActivity
import com.ylib.quicksave.util.PermissionHelper
import kotlinx.coroutines.flow.collectLatest
```
（说明：`Color`、`GradientDrawable`、`Gravity`、`View`、`FrameLayout`、`Button`、`Intent`、`kotlinx.coroutines.launch` 等在计划 A 已 import，本任务无需重复。`ContextCompat` 在下文以全限定名 `androidx.core.content.ContextCompat` 引用，无需 import。）

- [ ] **Step 2：新增字段**

在 `OverlayService` 类的字段区（如 `private var panelView: LinearLayout? = null` 之后）新增：
```kotlin
    private var recordButton: Button? = null
    private var redDot: View? = null
```

- [ ] **Step 3：录音按钮引用化 + 行为接线**

将 `buildPanelView()` 中创建【录音】按钮的那一句：
```kotlin
        addView(buildPanelButton("录音") {
            Toast.makeText(this@OverlayService, "录音（待计划 C 实现）", Toast.LENGTH_SHORT).show()
            collapse()
        })
```
替换为：
```kotlin
        val recBtn = buildPanelButton("录音") {
            collapse()
            onRecordClicked()
        }
        recordButton = recBtn
        addView(recBtn)
```
（【文字输入】按钮保持计划 B 的现状不变。）

- [ ] **Step 4：录音点击处理**

在 `OverlayService` 类内新增：
```kotlin
    private fun onRecordClicked() {
        if (RecordingController.state.value.isRecording) {
            // 停止：服务已在前台运行，startService 即可送达 ACTION_STOP
            startService(
                Intent(this, RecorderService::class.java).apply { action = RecorderService.ACTION_STOP }
            )
        } else if (PermissionHelper.hasRecordAudioPermission(this)) {
            androidx.core.content.ContextCompat.startForegroundService(
                this,
                Intent(this, RecorderService::class.java).apply { action = RecorderService.ACTION_START }
            )
        } else {
            // 悬浮窗无法直接弹权限框，拉起透明权限 Activity（持有 SYSTEM_ALERT_WINDOW，享后台启动豁免）
            startActivity(
                Intent(this, RecordPermissionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
```

- [ ] **Step 5：在 addOverlay 中加入红点视图并启动状态订阅**

在 `addOverlay(pos)` 方法内，`root.addView(panel)` 之后、`rootView = root` 之前，加入红点视图（叠加在把手上，默认隐藏）：
```kotlin
        val dot = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(8), dp(8)).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dp(4)
                marginEnd = dp(3)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.RED)
            }
            visibility = View.GONE
        }
        root.addView(dot)
        redDot = dot
```
并在 `windowManager.addView(root, params)` 之后追加订阅：
```kotlin
        observeRecordingState()
```

- [ ] **Step 6：状态订阅方法**

在 `OverlayService` 类内新增：
```kotlin
    private fun observeRecordingState() {
        scope.launch {
            RecordingController.state.collectLatest { st ->
                applyRecordingVisual(st.isRecording, st.elapsedSeconds)
            }
        }
    }

    private fun applyRecordingVisual(isRecording: Boolean, seconds: Int) {
        // 把手颜色：录音中红色，否则蓝色（与初始一致）
        (handleView?.background as? GradientDrawable)?.setColor(
            if (isRecording) Color.argb(170, 255, 70, 70) else Color.argb(140, 80, 140, 255)
        )
        redDot?.visibility = if (isRecording) View.VISIBLE else View.GONE
        recordButton?.text = if (isRecording) "录音中 ${formatElapsed(seconds)}" else "录音"
    }

    private fun formatElapsed(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }
```

- [ ] **Step 7：编译验证**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL。
（`GradientDrawable` 在计划 A 已 import，`applyRecordingVisual`/红点视图直接使用即可。）

- [ ] **Step 8：整包构建 + 全部单测**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL；既有单测 + C1 的 `RecordingFileNamerTest` 全过。

- [ ] **Step 9：提交**

```bash
git add app/src/main/java/com/ylib/quicksave/overlay/OverlayService.kt
git commit -m "feat(recorder): wire record button and recording-state visuals into overlay"
```
追加（空行后）：
```
Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

## Task C5：设备手动验证

需真机/模拟器（API 29+），悬浮窗已开启。

- [ ] **Step 1：安装**

Run: `./gradlew installDebug`
Expected: 安装成功。

- [ ] **Step 2：首次录音 → 申请权限**

操作：展开面板 → 点【录音】。
Expected：弹出系统麦克风权限申请；授予后立即开始录音——收起态把手变红 + 右上红点；通知栏出现「正在录音 00:0x」带「停止」操作。

- [ ] **Step 3：录音中状态与防中断**

操作：录音中切到其他 App、再锁屏、再解锁。
Expected：录音不中断（通知计时持续增长）；回到任意界面点把手展开，【录音】按钮显示「录音中 mm:ss」红色。

- [ ] **Step 4：停止 → 落盘**

操作：展开面板再次点【录音】（或点通知「停止」）。
Expected：录音停止，Toast「录音已保存」，把手恢复蓝色、红点消失、通知消失；在文件管理器/音乐 App 的 `Music/QuickSave/` 下出现 `QS_yyyyMMdd_HHmmss.m4a`，可播放。

- [ ] **Step 5：拒绝权限路径**

操作：在系统设置撤销麦克风权限后，点【录音】→ 在弹出的申请框选「拒绝」。
Expected：Toast「需要麦克风权限才能录音」，不进入录音态，把手保持蓝色。

- [ ] **Step 6：通知「停止」路径**

操作：开始录音后下拉通知栏，点通知里的「停止」。
Expected：录音停止并落盘，悬浮窗录音态复位。

---

## 自检备注（计划作者完成）

- **Spec 覆盖**：覆盖 §4.3（toggle 录音、权限流、前台防中断）、§5（Music/QuickSave/*.m4a、AAC/MPEG-4、文件名）、§6（RECORD_AUDIO + FOREGROUND_SERVICE_MICROPHONE）、§7（启动失败/进程被杀/抢占 → 停止并保留片段）、录音态可视化（把手红+红点 / 按钮红+计时）。
- **跨服务状态**：`RecordingController` 单例 `StateFlow` 桥接 `RecorderService`（写）与 `OverlayService`（订阅），同进程安全。
- **类型一致性**：`RecorderService.ACTION_START/ACTION_STOP`、`RecordingController.state/update/reset`、`RecordingUiState(isRecording, elapsedSeconds)`、`PermissionHelper.hasRecordAudioPermission`、`RecordingFileNamer.name(Date)` 在各任务间签名一致。
- **占位符**：无 TBD/TODO 代码占位。计划 C 完成后，悬浮窗两个预定义按钮均具备真实功能。
- **后台启动豁免**：从 Service 拉起 `RecordPermissionActivity` 用 `FLAG_ACTIVITY_NEW_TASK` + 应用持有 `SYSTEM_ALERT_WINDOW`。
- **已知边界（YAGNI，留待后续）**：未做音频焦点主动管理（来电硬抢占依赖 `MediaRecorder.OnErrorListener` 停止并保留片段）；未做暂停/继续、波形、最大时长限制。
