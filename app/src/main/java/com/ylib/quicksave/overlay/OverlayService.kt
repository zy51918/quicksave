package com.ylib.quicksave.overlay

import android.app.Service
import android.content.ClipboardManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.ylib.quicksave.app.QuickSaveApplication
import com.ylib.quicksave.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.ylib.quicksave.recorder.RecorderService
import com.ylib.quicksave.recorder.RecordingController
import com.ylib.quicksave.ui.RecordPermissionActivity
import com.ylib.quicksave.util.PermissionHelper
import kotlin.math.abs
import kotlin.math.roundToInt

class OverlayService : Service() {

    companion object {
        const val ACTION_STOP = "com.ylib.quicksave.overlay.STOP"
        private const val HANDLE_W_DP = 8
        private const val HANDLE_H_DP = 64
        private const val HANDLE_TOUCH_W_DP = 48
        private const val ACTION_W_DP = 92
        private const val ACTION_H_DP = 76
        private const val PANEL_ALPHA = 82 // #173942 at 32%
        private const val PANEL_STROKE_ALPHA = 46 // #D7E3E5 at 18%
        private const val ACTION_ALPHA = 199 // #087F7B at 78%
        private const val TAG = "OverlayService"
        // 悬浮窗必须绕过系统栏 inset-fit，否则 gravity=TOP|LEFT, x=0 的窗口会被系统按
        // fitTypes=STATUS_BARS/NAVIGATION_BARS 收进"安全区"里定位。安全区的偏移量在竖屏
        // 只体现为顶部 inset，但横屏时状态栏/摄像头挖孔仍锚定在设备物理顶边，旋转后会变成
        // 左侧 inset——导致贴左边的悬浮窗每次旋转后 x 从 0 漂移到 ~161px，看起来"没贴边"。
        // LAYOUT_IN_SCREEN + LAYOUT_NO_LIMITS 让窗口坐标系始终是整块屏幕的原始像素范围，
        // 与 overlayBounds 用的 currentWindowMetrics 口径一致，两个方向都不再漂移。
        private const val BASE_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    }

    private lateinit var windowManager: WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var rootView: FrameLayout? = null
    private var handleView: View? = null
    private var handleVisualView: View? = null
    private var panelView: LinearLayout? = null
    private var recordButton: FrameLayout? = null
    private var recordButtonLabel: TextView? = null
    private var redDot: View? = null
    private lateinit var params: WindowManager.LayoutParams

    private var expanded = false
    private var currentEdge = OverlayEdge.RIGHT
    private var clipboardSaving = false
    // 防止 Service.onConfigurationChanged 与 ComponentCallbacks2 两条路径同时触发导致重复 remove+add
    @Volatile private var relayouting = false

    // Service.onConfigurationChanged 对屏幕旋转投递不可靠（尤其 targetSdk 30+），
    // 注册进程级 ComponentCallbacks2 作为更稳的补充监听，确保旋转后能重定位悬浮窗。
    private val configCallback = object : ComponentCallbacks2 {
        override fun onConfigurationChanged(newConfig: Configuration) {
            this@OverlayService.onConfigurationChanged(newConfig)
        }
        override fun onLowMemory() {}
        override fun onTrimMemory(level: Int) {}
    }

    private val density get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).roundToInt()

    /**
     * 通过 WindowManager 实时获取 overlay 窗口可用区域尺寸，避免 resources.displayMetrics
     * 在 Service.onConfigurationChanged 被调用时尚未刷新成新方向的问题。
     */
    private val overlayBounds: Rect
        get() {
            // 优先用平台 WindowMetrics（API 30+），反映系统栏与 cutout 后的真实可用区域
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return windowManager.currentWindowMetrics.bounds
            }
            // fallback：直接量 Display 实时像素，比 resources.displayMetrics 更贴近当下方向
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(dm)
            return Rect(0, 0, dm.widthPixels, dm.heightPixels)
        }

    private val screenWidth get() = overlayBounds.width()
    private val screenHeight get() = overlayBounds.height()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        registerComponentCallbacks(configCallback)
        scope.launch {
            val pos = overlayRepo().getPosition().first()
            Log.d(TAG, "onCreate: initial position edge=${pos.edge}, yRatio=${pos.yRatio}")
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

    /**
     * 旋转屏幕时 Service 不会重建，WindowManager 中悬浮窗的 params.y 仍是旧屏高的像素值，
     * 会导致按钮被定位到屏幕外/没贴边。这里移除并按新屏高重新添加窗口，
     * 彻底重算布局区域（比 updateViewLayout 更稳，规避某些 WindowManager 实现缓存旧布局）。
     * 同时被 registerComponentCallbacks 的回调复用，弥补 Service.onConfigurationChanged 投递不可靠。
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "onConfigurationChanged: orientation=${newConfig.orientation}, relayouting=$relayouting, rootView=${rootView != null}")
        val root = rootView ?: run {
            Log.w(TAG, "onConfigurationChanged: rootView is null, skip relayout")
            return
        }
        if (relayouting) {
            Log.d(TAG, "onConfigurationChanged: already relayouting, skip (duplicate callback from Service+ComponentCallbacks2)")
            return
        }
        relayouting = true
        // 横竖屏切换时展开态的面板布局已不适用，先折叠回把手
        if (expanded) collapse()
        try {
            windowManager.removeView(root)
            Log.d(TAG, "onConfigurationChanged: removeView OK")
        } catch (e: Exception) {
            Log.e(TAG, "onConfigurationChanged: removeView FAILED", e)
        }
        scope.launch {
            try {
                val pos = overlayRepo().getPosition().first()
                val newScreenHeight = screenHeight
                currentEdge = pos.edge
                params.gravity = Gravity.TOP or edgeGravity(currentEdge)
                params.x = 0
                params.y = OverlayPositionCalculator.ratioToY(pos.yRatio, newScreenHeight)
                Log.d(TAG, "onConfigurationChanged: relayout with edge=$currentEdge, yRatio=${pos.yRatio}, " +
                    "newScreenHeight=$newScreenHeight, params.y=${params.y}")
                try {
                    windowManager.addView(root, params)
                    Log.d(TAG, "onConfigurationChanged: addView OK")
                } catch (e: Exception) {
                    Log.e(TAG, "onConfigurationChanged: addView FAILED, overlay is now GONE from screen", e)
                }
            } finally {
                relayouting = false
            }
        }
    }

    override fun onDestroy() {
        unregisterComponentCallbacks(configCallback)
        removeOverlay()
        scope.cancel()
        super.onDestroy()
    }

    private fun overlayRepo() =
        (application as QuickSaveApplication).overlayRepository

    // --- 叠加层视图 ---
    private fun addOverlay(pos: OverlayPosition) {
        currentEdge = pos.edge
        val root = FrameLayout(this)
        val handle = buildHandleView()
        val panel = buildPanelView()
        // 必须用 GONE 而非 INVISIBLE：WRAP_CONTENT 窗口按可见子视图测量，收起态窗口才能缩到把手大小
        panel.visibility = View.GONE
        root.addView(handle)
        root.addView(panel)
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
        rootView = root
        handleView = handle
        panelView = panel

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            BASE_FLAGS,
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
        Log.d(TAG, "addOverlay: edge=$currentEdge, y=${params.y}, screenWidth=$screenWidth, screenHeight=$screenHeight")
        windowManager.addView(root, params)
        observeRecordingState()
    }

    private fun removeOverlay() {
        rootView?.let { runCatching { windowManager.removeView(it) } }
        rootView = null
    }

    private fun edgeGravity(edge: OverlayEdge) =
        // 用绝对 LEFT/RIGHT 而非 START/END，避免 RTL（supportsRtl=true）在横屏下
        // 把贴边方向映射反，导致按钮看起来“没贴边”。
        if (edge == OverlayEdge.LEFT) Gravity.LEFT else Gravity.RIGHT

    private fun buildHandleView(): View {
        val visual = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(HANDLE_W_DP), dp(HANDLE_H_DP)).apply {
                gravity = Gravity.CENTER_VERTICAL or handleVisualGravity()
            }
            background = buildHandleBackground()
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        handleVisualView = visual
        return FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(HANDLE_TOUCH_W_DP), dp(HANDLE_H_DP))
            isClickable = true
            isFocusable = true
            contentDescription = "展开悬浮窗"
            setOnClickListener { toggle() }
            addView(visual)
        }
    }

    private fun buildPanelView(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(8), dp(8), dp(8), dp(8))
        background = buildPanelBackground()
        addView(buildPanelAction(R.drawable.ic_content_paste, "保存剪切板") {
            onClipboardSaveClicked()
        }.container)
        addView(buildPanelAction(R.drawable.ic_edit, "文字输入") {
            collapse()
            launchInputActivity()
        }.container)
        val recAction = buildPanelAction(R.drawable.ic_mic, "录音") {
            collapse()
            onRecordClicked()
        }
        recordButton = recAction.container
        recordButtonLabel = recAction.label
        addView(recAction.container)
    }

    private fun launchInputActivity() {
        val intent = Intent(this, com.ylib.quicksave.ui.InputActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private data class PanelAction(
        val container: FrameLayout,
        val label: TextView
    )

    private fun buildPanelAction(
        iconRes: Int,
        label: String,
        onClick: () -> Unit
    ): PanelAction {
        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                bottomMargin = dp(4)
            }
        }
        val labelView = TextView(this).apply {
            text = label
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 1
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(icon)
            addView(labelView)
        }
        val container = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(ACTION_W_DP), dp(ACTION_H_DP)).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            }
            background = buildActionBackground()
            isClickable = true
            isFocusable = true
            contentDescription = label
            setOnClickListener { onClick() }
            addView(
                content,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        return PanelAction(container, labelView)
    }

    private fun buildPanelBackground(): Drawable = GradientDrawable().apply {
        cornerRadius = dp(20).toFloat()
        setColor(Color.argb(PANEL_ALPHA, 23, 57, 66))
        setStroke(dp(1), Color.argb(PANEL_STROKE_ALPHA, 215, 227, 229))
    }

    private fun buildActionBackground(color: Int = Color.argb(ACTION_ALPHA, 8, 127, 123)): Drawable {
        val content = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(color)
        }
        val mask = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(Color.WHITE)
        }
        return RippleDrawable(
            ColorStateList.valueOf(Color.argb(52, 255, 255, 255)),
            content,
            mask
        )
    }

    private fun buildHandleBackground(isRecording: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(HANDLE_W_DP / 2).toFloat()
            setColor(
                if (isRecording) Color.argb(170, 255, 70, 70)
                else Color.argb(PANEL_ALPHA, 23, 57, 66)
            )
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
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        handle.performClick()
                    } else {
                        snapToNearestEdge(event.rawX)
                        persistPosition(handle)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    // 系统取消触摸序列：不切换、不吸附、不持久化，把手留在当前位置
                    moved = false
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToNearestEdge(rawX: Float) {
        // 把手在 x 方向始终贴边、仅纵向拖动；用手指 rawX 判断用户想吸附到哪一边，符合预期。
        currentEdge = OverlayPositionCalculator.nearestEdge(rawX.roundToInt(), screenWidth)
        params.gravity = Gravity.TOP or edgeGravity(currentEdge)
        params.x = 0
        alignHandleVisual()
        windowManager.updateViewLayout(rootView, params)
    }

    private fun handleVisualGravity() =
        if (currentEdge == OverlayEdge.LEFT) Gravity.START else Gravity.END

    private fun alignHandleVisual() {
        val visual = handleVisualView ?: return
        val lp = visual.layoutParams as? FrameLayout.LayoutParams ?: return
        lp.gravity = Gravity.CENTER_VERTICAL or handleVisualGravity()
        visual.layoutParams = lp
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
        params.flags = BASE_FLAGS or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        runCatching { windowManager.updateViewLayout(rootView, params) }
            .onFailure { Log.e(TAG, "expand: updateViewLayout FAILED", it) }
    }

    private fun collapse() {
        expanded = false
        panelView?.visibility = View.GONE
        handleView?.visibility = View.VISIBLE
        params.flags = BASE_FLAGS
        runCatching { windowManager.updateViewLayout(rootView, params) }
            .onFailure { Log.e(TAG, "collapse: updateViewLayout FAILED", it) }
    }

    private fun onRecordClicked() {
        if (RecordingController.state.value.isRecording) {
            startService(
                Intent(this, RecorderService::class.java).apply { action = RecorderService.ACTION_STOP }
            )
        } else if (PermissionHelper.hasRecordAudioPermission(this)) {
            androidx.core.content.ContextCompat.startForegroundService(
                this,
                Intent(this, RecorderService::class.java).apply { action = RecorderService.ACTION_START }
            )
        } else {
            startActivity(
                Intent(this, RecordPermissionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun onClipboardSaveClicked() {
        if (clipboardSaving) return

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = runCatching {
            clipboard.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.text
                ?.toString()
        }.getOrNull()?.takeIf { it.isNotBlank() }

        if (text == null) {
            Toast.makeText(this, "剪切板为空，请先复制文字", Toast.LENGTH_SHORT).show()
            return
        }

        clipboardSaving = true
        collapse()
        scope.launch {
            val result = runCatching {
                val category = clipRepository().getSelectedCategory().first()
                clipRepository().saveEntry(text, category).getOrThrow()
            }
            clipboardSaving = false
            val exception = result.exceptionOrNull()
            val message = when {
                result.isSuccess -> "已保存剪切板内容"
                exception is IllegalStateException -> "请先在设置中选择保存文件"
                exception is SecurityException -> "文件无写入权限，请重新选择"
                else -> "保存失败：${exception?.message ?: "未知错误"}"
            }
            Toast.makeText(
                this@OverlayService,
                message,
                if (result.isSuccess) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun clipRepository() =
        (application as QuickSaveApplication).clipRepository

    private fun observeRecordingState() {
        scope.launch {
            RecordingController.state.collectLatest { st ->
                applyRecordingVisual(st.isRecording, st.elapsedSeconds)
            }
        }
    }

    private fun applyRecordingVisual(isRecording: Boolean, seconds: Int) {
        (handleVisualView?.background as? GradientDrawable)?.setColor(
            if (isRecording) Color.argb(170, 255, 70, 70)
            else Color.argb(PANEL_ALPHA, 23, 57, 66)
        )
        redDot?.visibility = if (isRecording) View.VISIBLE else View.GONE
        recordButton?.background = buildActionBackground(
            if (isRecording) Color.argb(170, 255, 70, 70)
            else Color.argb(ACTION_ALPHA, 8, 127, 123)
        )
        recordButtonLabel?.text = if (isRecording) "录音中 ${formatElapsed(seconds)}" else "录音"
    }

    private fun formatElapsed(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }
}
