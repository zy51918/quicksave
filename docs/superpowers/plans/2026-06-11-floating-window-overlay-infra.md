# 全局悬浮窗 · 计划 A：悬浮窗基础设施 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现一个可由设置页总开关控制的常驻悬浮窗：贴边显示半透明把手，点击展开为横排 2 按钮面板（按钮本期仅 Toast 占位），可拖拽改位置并自动吸附边缘，位置与开关状态持久化。

**Architecture:** 新增独立前台服务 `OverlayService`（`specialUse` 类型），用经典 Android View + `WindowManager` 绘制 `TYPE_APPLICATION_OVERLAY` 叠加层（Compose 不适合做悬浮窗）。悬浮窗位置/开关经新增的 `OverlayRepository`→`AppDataStore` 持久化。贴边吸附等纯逻辑抽到可单测的 `OverlayPositionCalculator`。设置页新增总开关，负责申请 `SYSTEM_ALERT_WINDOW` 权限并启停服务。

**Tech Stack:** Kotlin、Android View（非 Compose 叠加层）、WindowManager、DataStore Preferences、前台服务、JUnit4 + Mockito + coroutines-test。

**关联：** 设计文档 `docs/features/QS-0003/design-floating-window.md`。本计划只覆盖"基础设施"，文字输入（计划 B）与录音（计划 C）按钮的真实功能不在本计划内——本计划中两个按钮点击仅弹 Toast 占位。

---

## 文件结构

**新建：**
- `app/src/main/java/com/ylib/quicksave/overlay/OverlayEdge.kt` — 贴边方向枚举
- `app/src/main/java/com/ylib/quicksave/overlay/OverlayPosition.kt` — 位置数据类
- `app/src/main/java/com/ylib/quicksave/overlay/OverlayPositionCalculator.kt` — 纯逻辑：最近边/Y 钳制/比例换算（单测目标）
- `app/src/main/java/com/ylib/quicksave/data/repository/OverlayRepository.kt` — 悬浮窗偏好仓库接口
- `app/src/main/java/com/ylib/quicksave/data/repository/OverlayRepositoryImpl.kt` — 实现
- `app/src/main/java/com/ylib/quicksave/overlay/OverlayService.kt` — 前台服务 + WindowManager 叠加层
- `app/src/test/java/com/ylib/quicksave/overlay/OverlayPositionCalculatorTest.kt` — 单测
- `app/src/test/java/com/ylib/quicksave/data/repository/OverlayRepositoryImplTest.kt` — 单测

**修改：**
- `app/src/main/java/com/ylib/quicksave/data/source/AppDataStore.kt` — 新增悬浮窗偏好读写
- `app/src/main/java/com/ylib/quicksave/data/source/AppDataStoreImpl.kt` — 实现 + 新增 Preference Key
- `app/src/main/java/com/ylib/quicksave/util/PermissionHelper.kt` — 新增 `canDrawOverlays`
- `app/src/main/java/com/ylib/quicksave/app/QuickSaveApplication.kt` — 装配 `overlayRepository`
- `app/src/main/java/com/ylib/quicksave/ui/viewmodel/SettingsViewModel.kt` — 新增开关状态与操作
- `app/src/main/java/com/ylib/quicksave/ui/screens/SettingsScreen.kt` — 新增"全局悬浮窗"区块
- `app/src/main/AndroidManifest.xml` — 新增权限与服务声明

---

## Task 1：贴边方向枚举与位置数据类

**Files:**
- Create: `app/src/main/java/com/ylib/quicksave/overlay/OverlayEdge.kt`
- Create: `app/src/main/java/com/ylib/quicksave/overlay/OverlayPosition.kt`

- [ ] **Step 1：创建 OverlayEdge 枚举**

`OverlayEdge.kt`：
```kotlin
package com.ylib.quicksave.overlay

/** 悬浮窗贴靠的屏幕边缘。 */
enum class OverlayEdge {
    LEFT,
    RIGHT;

    companion object {
        /** 从持久化字符串还原，无法识别时回退到 RIGHT。 */
        fun fromStorage(value: String?): OverlayEdge =
            entries.firstOrNull { it.name == value } ?: RIGHT
    }
}
```

- [ ] **Step 2：创建 OverlayPosition 数据类**

`OverlayPosition.kt`：
```kotlin
package com.ylib.quicksave.overlay

/**
 * 悬浮窗位置。
 * @param edge 贴靠的边
 * @param yRatio 把手顶部相对屏幕高度的比例 [0f, 1f]，跨分辨率/旋转更稳健
 */
data class OverlayPosition(
    val edge: OverlayEdge,
    val yRatio: Float
) {
    companion object {
        /** 默认贴右、约屏幕 40% 高度处。 */
        val DEFAULT = OverlayPosition(OverlayEdge.RIGHT, 0.4f)
    }
}
```

- [ ] **Step 3：编译验证**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL（无 main 源新增报错）

- [ ] **Step 4：提交**

```bash
git add app/src/main/java/com/ylib/quicksave/overlay/OverlayEdge.kt app/src/main/java/com/ylib/quicksave/overlay/OverlayPosition.kt
git commit -m "feat(overlay): add OverlayEdge and OverlayPosition models"
```

---

## Task 2：贴边位置计算器（TDD）

纯逻辑，无 Android 依赖，完整单测。

**Files:**
- Create: `app/src/main/java/com/ylib/quicksave/overlay/OverlayPositionCalculator.kt`
- Test: `app/src/test/java/com/ylib/quicksave/overlay/OverlayPositionCalculatorTest.kt`

- [ ] **Step 1：写失败的测试**

`OverlayPositionCalculatorTest.kt`：
```kotlin
package com.ylib.quicksave.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayPositionCalculatorTest {

    @Test
    fun `nearestEdge centerX in left half returns LEFT`() {
        assertEquals(OverlayEdge.LEFT, OverlayPositionCalculator.nearestEdge(100, 1080))
    }

    @Test
    fun `nearestEdge centerX in right half returns RIGHT`() {
        assertEquals(OverlayEdge.RIGHT, OverlayPositionCalculator.nearestEdge(900, 1080))
    }

    @Test
    fun `nearestEdge at exact midpoint returns RIGHT`() {
        assertEquals(OverlayEdge.RIGHT, OverlayPositionCalculator.nearestEdge(540, 1080))
    }

    @Test
    fun `clampY below zero returns zero`() {
        assertEquals(0, OverlayPositionCalculator.clampY(-50, viewHeight = 100, screenHeight = 1920))
    }

    @Test
    fun `clampY above max returns screenHeight minus viewHeight`() {
        assertEquals(1820, OverlayPositionCalculator.clampY(5000, viewHeight = 100, screenHeight = 1920))
    }

    @Test
    fun `clampY within range is unchanged`() {
        assertEquals(500, OverlayPositionCalculator.clampY(500, viewHeight = 100, screenHeight = 1920))
    }

    @Test
    fun `yToRatio converts pixel to fraction`() {
        assertEquals(0.25f, OverlayPositionCalculator.yToRatio(480, 1920), 0.001f)
    }

    @Test
    fun `ratioToY converts fraction to pixel`() {
        assertEquals(480, OverlayPositionCalculator.ratioToY(0.25f, 1920))
    }

    @Test
    fun `yToRatio clamps ratio into 0_1`() {
        assertEquals(1f, OverlayPositionCalculator.yToRatio(5000, 1920), 0.001f)
        assertEquals(0f, OverlayPositionCalculator.yToRatio(-100, 1920), 0.001f)
    }
}
```

- [ ] **Step 2：运行测试确认失败**

Run: `./gradlew testDebugUnitTest --tests "com.ylib.quicksave.overlay.OverlayPositionCalculatorTest"`
Expected: FAIL，编译错误 "Unresolved reference: OverlayPositionCalculator"

- [ ] **Step 3：写最小实现**

`OverlayPositionCalculator.kt`：
```kotlin
package com.ylib.quicksave.overlay

/** 悬浮窗位置相关的纯计算，无 Android 依赖，便于单测。 */
object OverlayPositionCalculator {

    /** 根据视图中心 X 与屏幕宽度返回最近的边；正中点归到 RIGHT。 */
    fun nearestEdge(centerX: Int, screenWidth: Int): OverlayEdge =
        if (centerX < screenWidth / 2) OverlayEdge.LEFT else OverlayEdge.RIGHT

    /** 将顶部 Y 钳制到 [0, screenHeight - viewHeight]，避免拖出屏幕。 */
    fun clampY(y: Int, viewHeight: Int, screenHeight: Int): Int {
        val max = (screenHeight - viewHeight).coerceAtLeast(0)
        return y.coerceIn(0, max)
    }

    /** 像素 Y → 屏高比例，结果钳制到 [0f, 1f]。 */
    fun yToRatio(y: Int, screenHeight: Int): Float {
        if (screenHeight <= 0) return 0f
        return (y.toFloat() / screenHeight).coerceIn(0f, 1f)
    }

    /** 屏高比例 → 像素 Y。 */
    fun ratioToY(ratio: Float, screenHeight: Int): Int =
        (ratio.coerceIn(0f, 1f) * screenHeight).toInt()
}
```

- [ ] **Step 4：运行测试确认通过**

Run: `./gradlew testDebugUnitTest --tests "com.ylib.quicksave.overlay.OverlayPositionCalculatorTest"`
Expected: PASS（9 个测试全过）

- [ ] **Step 5：提交**

```bash
git add app/src/main/java/com/ylib/quicksave/overlay/OverlayPositionCalculator.kt app/src/test/java/com/ylib/quicksave/overlay/OverlayPositionCalculatorTest.kt
git commit -m "feat(overlay): add OverlayPositionCalculator with unit tests"
```

---

## Task 3：AppDataStore 新增悬浮窗偏好

**Files:**
- Modify: `app/src/main/java/com/ylib/quicksave/data/source/AppDataStore.kt`
- Modify: `app/src/main/java/com/ylib/quicksave/data/source/AppDataStoreImpl.kt`

- [ ] **Step 1：扩展 AppDataStore 接口**

在 `AppDataStore.kt` 接口尾部（`getSelectedCategory` 之后）追加：
```kotlin
    suspend fun saveOverlayEnabled(enabled: Boolean)
    fun getOverlayEnabled(): Flow<Boolean>
    suspend fun saveOverlayPosition(edge: String, yRatio: Float)
    fun getOverlayPosition(): Flow<Pair<String, Float>>
```

- [ ] **Step 2：实现 AppDataStoreImpl**

在 `AppDataStoreImpl.kt` 顶部 import 区补充：
```kotlin
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
```
在 `companion object` 内新增 Key：
```kotlin
        private val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        private val OVERLAY_EDGE = stringPreferencesKey("overlay_edge")
        private val OVERLAY_Y_RATIO = floatPreferencesKey("overlay_y_ratio")
```
在类尾部新增实现：
```kotlin
    override suspend fun saveOverlayEnabled(enabled: Boolean) {
        context.dataStore.edit { it[OVERLAY_ENABLED] = enabled }
    }

    override fun getOverlayEnabled(): Flow<Boolean> =
        context.dataStore.data.map { it[OVERLAY_ENABLED] ?: false }

    override suspend fun saveOverlayPosition(edge: String, yRatio: Float) {
        context.dataStore.edit {
            it[OVERLAY_EDGE] = edge
            it[OVERLAY_Y_RATIO] = yRatio
        }
    }

    override fun getOverlayPosition(): Flow<Pair<String, Float>> =
        context.dataStore.data.map {
            (it[OVERLAY_EDGE] ?: "RIGHT") to (it[OVERLAY_Y_RATIO] ?: 0.4f)
        }
```

- [ ] **Step 3：编译验证**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4：提交**

```bash
git add app/src/main/java/com/ylib/quicksave/data/source/AppDataStore.kt app/src/main/java/com/ylib/quicksave/data/source/AppDataStoreImpl.kt
git commit -m "feat(overlay): persist overlay enabled flag and position in DataStore"
```

---

## Task 4：OverlayRepository（TDD passthrough）

**Files:**
- Create: `app/src/main/java/com/ylib/quicksave/data/repository/OverlayRepository.kt`
- Create: `app/src/main/java/com/ylib/quicksave/data/repository/OverlayRepositoryImpl.kt`
- Test: `app/src/test/java/com/ylib/quicksave/data/repository/OverlayRepositoryImplTest.kt`

- [ ] **Step 1：写接口**

`OverlayRepository.kt`：
```kotlin
package com.ylib.quicksave.data.repository

import com.ylib.quicksave.overlay.OverlayPosition
import kotlinx.coroutines.flow.Flow

interface OverlayRepository {
    fun isEnabled(): Flow<Boolean>
    suspend fun setEnabled(enabled: Boolean)
    fun getPosition(): Flow<OverlayPosition>
    suspend fun setPosition(position: OverlayPosition)
}
```

- [ ] **Step 2：写失败的测试**

`OverlayRepositoryImplTest.kt`：
```kotlin
package com.ylib.quicksave.data.repository

import com.ylib.quicksave.data.source.AppDataStore
import com.ylib.quicksave.overlay.OverlayEdge
import com.ylib.quicksave.overlay.OverlayPosition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OverlayRepositoryImplTest {

    private val dataStore = mock<AppDataStore>()
    private val repo = OverlayRepositoryImpl(dataStore)

    @Test
    fun `isEnabled passes through dataStore flow`() = runTest {
        whenever(dataStore.getOverlayEnabled()).thenReturn(flowOf(true))
        assertEquals(true, repo.isEnabled().first())
    }

    @Test
    fun `setEnabled delegates to dataStore`() = runTest {
        repo.setEnabled(true)
        verify(dataStore).saveOverlayEnabled(eq(true))
    }

    @Test
    fun `getPosition maps storage pair to OverlayPosition`() = runTest {
        whenever(dataStore.getOverlayPosition()).thenReturn(flowOf("LEFT" to 0.6f))
        val pos = repo.getPosition().first()
        assertEquals(OverlayEdge.LEFT, pos.edge)
        assertEquals(0.6f, pos.yRatio, 0.001f)
    }

    @Test
    fun `getPosition falls back to RIGHT for unknown edge string`() = runTest {
        whenever(dataStore.getOverlayPosition()).thenReturn(flowOf("garbage" to 0.1f))
        assertEquals(OverlayEdge.RIGHT, repo.getPosition().first().edge)
    }

    @Test
    fun `setPosition delegates edge name and ratio to dataStore`() = runTest {
        repo.setPosition(OverlayPosition(OverlayEdge.LEFT, 0.3f))
        verify(dataStore).saveOverlayPosition(eq("LEFT"), eq(0.3f))
    }
}
```

- [ ] **Step 3：运行测试确认失败**

Run: `./gradlew testDebugUnitTest --tests "com.ylib.quicksave.data.repository.OverlayRepositoryImplTest"`
Expected: FAIL，"Unresolved reference: OverlayRepositoryImpl"

- [ ] **Step 4：写实现**

`OverlayRepositoryImpl.kt`：
```kotlin
package com.ylib.quicksave.data.repository

import com.ylib.quicksave.data.source.AppDataStore
import com.ylib.quicksave.overlay.OverlayEdge
import com.ylib.quicksave.overlay.OverlayPosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OverlayRepositoryImpl(
    private val dataStore: AppDataStore
) : OverlayRepository {

    override fun isEnabled(): Flow<Boolean> = dataStore.getOverlayEnabled()

    override suspend fun setEnabled(enabled: Boolean) = dataStore.saveOverlayEnabled(enabled)

    override fun getPosition(): Flow<OverlayPosition> =
        dataStore.getOverlayPosition().map { (edge, ratio) ->
            OverlayPosition(OverlayEdge.fromStorage(edge), ratio)
        }

    override suspend fun setPosition(position: OverlayPosition) =
        dataStore.saveOverlayPosition(position.edge.name, position.yRatio)
}
```

- [ ] **Step 5：运行测试确认通过**

Run: `./gradlew testDebugUnitTest --tests "com.ylib.quicksave.data.repository.OverlayRepositoryImplTest"`
Expected: PASS（5 个测试全过）

- [ ] **Step 6：提交**

```bash
git add app/src/main/java/com/ylib/quicksave/data/repository/OverlayRepository.kt app/src/main/java/com/ylib/quicksave/data/repository/OverlayRepositoryImpl.kt app/src/test/java/com/ylib/quicksave/data/repository/OverlayRepositoryImplTest.kt
git commit -m "feat(overlay): add OverlayRepository with unit tests"
```

---

## Task 5：在 Application 装配 OverlayRepository

**Files:**
- Modify: `app/src/main/java/com/ylib/quicksave/app/QuickSaveApplication.kt`

- [ ] **Step 1：新增依赖**

在 `QuickSaveApplication.kt` import 区补充：
```kotlin
import com.ylib.quicksave.data.repository.OverlayRepository
import com.ylib.quicksave.data.repository.OverlayRepositoryImpl
```
在 `clipRepository` 之后新增：
```kotlin
    val overlayRepository: OverlayRepository by lazy { OverlayRepositoryImpl(dataStore) }
```

- [ ] **Step 2：编译验证**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3：提交**

```bash
git add app/src/main/java/com/ylib/quicksave/app/QuickSaveApplication.kt
git commit -m "feat(overlay): wire OverlayRepository into Application"
```

---

## Task 6：PermissionHelper 新增 canDrawOverlays

**Files:**
- Modify: `app/src/main/java/com/ylib/quicksave/util/PermissionHelper.kt`

- [ ] **Step 1：新增方法**

在 `PermissionHelper` 对象内（`hasNotificationPermission` 之后）新增：
```kotlin
    /** 是否已授予悬浮窗（SYSTEM_ALERT_WINDOW）权限。 */
    fun canDrawOverlays(context: Context): Boolean =
        android.provider.Settings.canDrawOverlays(context)
```

- [ ] **Step 2：编译验证**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

> 说明：`Settings.canDrawOverlays` 是静态调用，单测需 `mockStatic`，价值有限；本方法留待设备手动验证（Task 9）。不强制单测。

- [ ] **Step 3：提交**

```bash
git add app/src/main/java/com/ylib/quicksave/util/PermissionHelper.kt
git commit -m "feat(overlay): add canDrawOverlays helper"
```

---

## Task 7：OverlayService（前台服务 + WindowManager 叠加层）

无法 JVM 单测，提供完整代码，构建验证 + Task 9 设备验证。

**Files:**
- Create: `app/src/main/java/com/ylib/quicksave/overlay/OverlayService.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1：声明权限与服务**

在 `AndroidManifest.xml` 现有 `<uses-permission>` 区追加：
```xml
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
```
在 `<application>` 内、现有 `ClipboardMonitorService` 之后追加：
```xml
        <service
            android:name=".overlay.OverlayService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="floatingWindow" />
        </service>
```

- [ ] **Step 2：编写 OverlayService**

`OverlayService.kt`（完整内容）：
```kotlin
package com.ylib.quicksave.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.ylib.quicksave.MainActivity
import com.ylib.quicksave.app.QuickSaveApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class OverlayService : Service() {

    companion object {
        const val ACTION_STOP = "com.ylib.quicksave.overlay.STOP"
        private const val CHANNEL_ID = "quicksave_overlay_channel"
        private const val NOTIFICATION_ID = 1002
        private const val HANDLE_W_DP = 14
        private const val HANDLE_H_DP = 54
    }

    private lateinit var windowManager: WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var rootView: FrameLayout? = null
    private var handleView: View? = null
    private var panelView: LinearLayout? = null
    private lateinit var params: WindowManager.LayoutParams

    private var expanded = false
    private var currentEdge = OverlayEdge.RIGHT

    private val density get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).roundToInt()
    private val screenWidth get() = resources.displayMetrics.widthPixels
    private val screenHeight get() = resources.displayMetrics.heightPixels

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createChannelAndForeground()
        scope.launch {
            val pos = overlayRepo().getPosition().first()
            addOverlay(pos)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeOverlay()
        scope.cancel()
        super.onDestroy()
    }

    private fun overlayRepo() =
        (application as QuickSaveApplication).overlayRepository

    // --- 通知 / 前台 ---
    private fun createChannelAndForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "QuickSave 悬浮窗", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "悬浮窗常驻通知" }
            )
        }
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("QuickSave 悬浮窗")
            .setContentText("点击打开应用")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openIntent)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    // --- 叠加层视图 ---
    private fun addOverlay(pos: OverlayPosition) {
        currentEdge = pos.edge
        val root = FrameLayout(this)
        val handle = buildHandleView()
        val panel = buildPanelView()
        panel.visibility = View.GONE
        root.addView(handle)
        root.addView(panel)
        rootView = root
        handleView = handle
        panelView = panel

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or edgeGravity(currentEdge)
            x = 0
            y = OverlayPositionCalculator.ratioToY(pos.yRatio, screenHeight)
        }

        attachHandleTouch(handle)
        root.setOnTouchListener { _, event ->
            if (expanded && event.action == MotionEvent.ACTION_OUTSIDE) {
                collapse()
                true
            } else false
        }
        windowManager.addView(root, params)
    }

    private fun removeOverlay() {
        rootView?.let { runCatching { windowManager.removeView(it) } }
        rootView = null
    }

    private fun edgeGravity(edge: OverlayEdge) =
        if (edge == OverlayEdge.LEFT) Gravity.START else Gravity.END

    private fun buildHandleView(): View = View(this).apply {
        layoutParams = FrameLayout.LayoutParams(dp(HANDLE_W_DP), dp(HANDLE_H_DP))
        background = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(Color.argb(140, 80, 140, 255))
        }
    }

    private fun buildPanelView(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(8), dp(8), dp(8), dp(8))
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(Color.argb(245, 28, 34, 46))
        }
        addView(buildPanelButton("文字输入") {
            Toast.makeText(this@OverlayService, "文字输入（待计划 B 实现）", Toast.LENGTH_SHORT).show()
            collapse()
        })
        addView(buildPanelButton("录音") {
            Toast.makeText(this@OverlayService, "录音（待计划 C 实现）", Toast.LENGTH_SHORT).show()
            collapse()
        })
    }

    private fun buildPanelButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
            (layoutParams as? LinearLayout.LayoutParams
                ?: LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )).also {
                it.marginStart = dp(4); it.marginEnd = dp(4); layoutParams = it
            }
        }

    // --- 触摸：拖拽 vs 点击 ---
    private fun attachHandleTouch(handle: View) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var downY = 0
        var moved = false

        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX; downRawY = event.rawY
                    downY = params.y; moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!moved && (abs(dx) > slop || abs(dy) > slop)) moved = true
                    if (moved) {
                        val handleH = handle.height.takeIf { it > 0 } ?: dp(HANDLE_H_DP)
                        params.y = OverlayPositionCalculator.clampY(
                            (downY + dy).roundToInt(), handleH, screenHeight
                        )
                        windowManager.updateViewLayout(rootView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!moved) {
                        toggle()
                    } else {
                        snapToNearestEdge(event.rawX)
                        persistPosition(handle)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToNearestEdge(rawX: Float) {
        currentEdge = OverlayPositionCalculator.nearestEdge(rawX.roundToInt(), screenWidth)
        params.gravity = Gravity.TOP or edgeGravity(currentEdge)
        params.x = 0
        windowManager.updateViewLayout(rootView, params)
    }

    private fun persistPosition(handle: View) {
        val handleH = handle.height.takeIf { it > 0 } ?: dp(HANDLE_H_DP)
        val clampedY = OverlayPositionCalculator.clampY(params.y, handleH, screenHeight)
        val ratio = OverlayPositionCalculator.yToRatio(clampedY, screenHeight)
        scope.launch { overlayRepo().setPosition(OverlayPosition(currentEdge, ratio)) }
    }

    // --- 展开 / 折叠 ---
    private fun toggle() = if (expanded) collapse() else expand()

    private fun expand() {
        expanded = true
        handleView?.visibility = View.GONE
        panelView?.visibility = View.VISIBLE
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        runCatching { windowManager.updateViewLayout(rootView, params) }
    }

    private fun collapse() {
        expanded = false
        panelView?.visibility = View.GONE
        handleView?.visibility = View.VISIBLE
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        runCatching { windowManager.updateViewLayout(rootView, params) }
    }
}
```

- [ ] **Step 3：编译验证**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4：提交**

```bash
git add app/src/main/java/com/ylib/quicksave/overlay/OverlayService.kt app/src/main/AndroidManifest.xml
git commit -m "feat(overlay): add OverlayService with draggable edge handle and panel"
```

---

## Task 8：设置页总开关（ViewModel + UI + 权限流 + 启停服务）

**Files:**
- Modify: `app/src/main/java/com/ylib/quicksave/ui/viewmodel/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/ylib/quicksave/ui/screens/SettingsScreen.kt`

- [ ] **Step 1：SettingsViewModel 新增开关状态与操作**

在 `SettingsViewModel.kt` import 区补充：
```kotlin
import com.ylib.quicksave.data.repository.OverlayRepository
```
在 `private val repo = ...` 之后新增字段：
```kotlin
    private val overlayRepo: OverlayRepository = (app as QuickSaveApplication).overlayRepository

    val overlayEnabled: StateFlow<Boolean> = overlayRepo.isEnabled()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
```
在类尾部新增方法：
```kotlin
    /** 仅持久化开关状态；服务启停由 UI 层根据权限结果触发。 */
    fun setOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch { overlayRepo.setEnabled(enabled) }
    }
```

- [ ] **Step 2：SettingsScreen 新增"全局悬浮窗"区块**

在 `SettingsScreen.kt` import 区补充：
```kotlin
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.Switch
import androidx.core.content.ContextCompat
import com.ylib.quicksave.overlay.OverlayService
import com.ylib.quicksave.util.PermissionHelper
```
在 `@Composable fun SettingsScreen` 顶部、`val context = LocalContext.current` 之后新增：
```kotlin
    val overlayEnabled by viewModel.overlayEnabled.collectAsState()

    // 申请悬浮窗权限后回到本页：若已授权则开启并启动服务
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (PermissionHelper.canDrawOverlays(context)) {
            viewModel.setOverlayEnabled(true)
            ContextCompat.startForegroundService(
                context, Intent(context, OverlayService::class.java)
            )
        }
    }

    fun onToggleOverlay(enable: Boolean) {
        if (enable) {
            if (PermissionHelper.canDrawOverlays(context)) {
                viewModel.setOverlayEnabled(true)
                ContextCompat.startForegroundService(
                    context, Intent(context, OverlayService::class.java)
                )
            } else {
                overlayPermissionLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
        } else {
            viewModel.setOverlayEnabled(false)
            context.startService(
                Intent(context, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_STOP
                }
            )
        }
    }
```
在 `Column { ... }` 内部、"分类管理"区块**之前**（即 `Spacer(Modifier.height(24.dp))` 那段之前）插入新区块：
```kotlin
            Spacer(Modifier.height(24.dp))
            Text(
                "全局悬浮窗",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "启用全局悬浮窗",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = overlayEnabled,
                    onCheckedChange = { onToggleOverlay(it) }
                )
            }
            Text(
                "开启后将在所有应用之上显示一个贴边悬浮窗，点击展开快捷操作。需要授予“显示在其他应用上层”权限。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
```

- [ ] **Step 3：编译验证**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4：整包构建 + 全部单测**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL；既有测试 + 新增 Task 2/Task 4 测试全过

- [ ] **Step 5：提交**

```bash
git add app/src/main/java/com/ylib/quicksave/ui/viewmodel/SettingsViewModel.kt app/src/main/java/com/ylib/quicksave/ui/screens/SettingsScreen.kt
git commit -m "feat(overlay): add settings toggle with overlay permission flow"
```

---

## Task 9：设备手动验证（无法自动化的部分）

需真机或模拟器（API 29+）。

- [ ] **Step 1：安装**

Run: `./gradlew installDebug`
Expected: 安装成功

- [ ] **Step 2：开关与权限流**

操作：打开 App → 设置 → 打开"启用全局悬浮窗"开关。
Expected：跳转系统"显示在其他应用上层"授权页；授予后返回，屏幕右侧出现半透明竖条把手；开关保持开启。

- [ ] **Step 3：拖拽与吸附**

操作：按住把手上下拖动；再拖到屏幕左半边松手。
Expected：把手跟随上下移动且不超出屏幕；松手后吸附到最近边（拖到左半边则贴左）。

- [ ] **Step 4：展开 / 折叠 / 占位按钮**

操作：点击把手；点击"文字输入"；再次点把手后点面板外空白处。
Expected：点击把手展开横排面板（文字输入 / 录音）；点"文字输入"弹 Toast"文字输入（待计划 B 实现）"并收回；点面板外空白处收回贴边。

- [ ] **Step 5：跨 App 与持久化**

操作：回到桌面或切换到其他 App；杀掉并重开 QuickSave。
Expected：悬浮窗在其他 App 之上依然可见；重开 App 后把手位置（边 + 高度）与开启状态被还原。

- [ ] **Step 6：关闭开关**

操作：设置里关闭开关。
Expected：悬浮窗立即消失；其常驻通知消失。

- [ ] **Step 7：提交验证记录（可选）**

若需要，将验证结果记到 `docs/features/QS-0003/` 或在 PR 描述中说明。无代码变更则跳过。

---

## 自检备注（计划作者完成）

- **Spec 覆盖**：本计划覆盖设计文档中"悬浮窗框架/总开关/权限/贴边把手/展开折叠/拖拽吸附/位置持久化/两条服务中的 OverlayService 通知"。文字输入（设计§4.2）→ 计划 B；录音（§4.3、§5、§六录音权限）→ 计划 C。两个按钮在本计划为 Toast 占位，已在 Goal 与 Task 7 注明。
- **类型一致性**：`OverlayEdge`/`OverlayPosition`/`OverlayPositionCalculator`/`OverlayRepository` 的方法签名在 Task 1/2/4/7/8 间一致；DataStore 的 `getOverlayPosition(): Flow<Pair<String, Float>>` 与 Repository 映射一致。
- **占位符**：无 TBD/TODO 代码占位（"待计划 B/C 实现"为面向用户的 Toast 文案，非代码缺口）。
