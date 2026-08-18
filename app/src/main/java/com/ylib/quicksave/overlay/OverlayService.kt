package com.ylib.quicksave.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
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
import android.view.animation.DecelerateInterpolator
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
        private const val HANDLE_W_DP = OverlayHandleSpec.WIDTH_DP
        private const val HANDLE_H_DP = 64
        private const val HANDLE_TOUCH_W_DP = OverlayHandleSpec.TOUCH_WIDTH_DP
        private const val HANDLE_SETTLE_DURATION_MS = 220L
        private const val ACTION_W_DP = OverlayPanelSpec.ACTION_WIDTH_DP
        private const val ACTION_H_DP = OverlayPanelSpec.ACTION_HEIGHT_DP
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
    private var edgeAnimator: ValueAnimator? = null
    private var handlePressAnimator: ValueAnimator? = null
    private var draggingHandle = false
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
        cancelEdgeAnimation()
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
        cancelEdgeAnimation()
        handlePressAnimator?.cancel()
        handleVisualView?.animate()?.cancel()
        unregisterComponentCallbacks(configCallback)
        removeOverlay()
        scope.cancel()
        super.onDestroy()
    }

    private fun overlayRepo() =
        (application as QuickSaveApplication).overlayRepository

    private fun overlayPalette(): OverlayClipboardColors {
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return OverlayClipboardPalette.forNightMode(
            nightMode == Configuration.UI_MODE_NIGHT_YES
        )
    }

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
            imageTintList = ColorStateList.valueOf(overlayPalette().actionContentColor)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams(
                dp(OverlayPanelSpec.ICON_SIZE_DP),
                dp(OverlayPanelSpec.ICON_SIZE_DP)
            ).apply {
                bottomMargin = dp(OverlayPanelSpec.ICON_BOTTOM_MARGIN_DP)
            }
        }
        val labelView = TextView(this).apply {
            text = label
            textSize = 11f
            setTextColor(overlayPalette().actionContentColor)
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
        val palette = overlayPalette()
        cornerRadius = dp(20).toFloat()
        setColor(palette.panelColor)
    }

    private fun buildActionBackground(color: Int = overlayPalette().actionColor): Drawable {
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
            shape = if (OverlayHandleSpec.USE_ROUNDED_BACKGROUND) {
                GradientDrawable.RECTANGLE
            } else {
                GradientDrawable.OVAL
            }
            cornerRadius = OverlayHandleSpec.cornerRadiusPx(
                dp(HANDLE_W_DP),
                dp(HANDLE_H_DP)
            )
            setColor(
                if (isRecording) Color.argb(170, 255, 70, 70)
                else Color.argb(
                    OverlayHandleSpec.NORMAL_ALPHA,
                    OverlayHandleSpec.NORMAL_RED,
                    OverlayHandleSpec.NORMAL_GREEN,
                    OverlayHandleSpec.NORMAL_BLUE
                )
            )
        }

    // --- 触摸：拖拽 vs 点击 ---
    private fun attachHandleTouch(handle: View) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var downTouchOffsetX = 0
        var downY = 0
        var moved = false

        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    cancelEdgeAnimation()
                    animateHandlePress(pressed = true)
                    val root = rootView
                    val rootWidth = root?.width?.takeIf { it > 0 }
                        ?: root?.measuredWidth?.takeIf { it > 0 }
                        ?: dp(HANDLE_TOUCH_W_DP)
                    val startLeft = OverlayPositionCalculator.edgeWindowLeft(
                        currentEdge, screenWidth, rootWidth
                    )
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downTouchOffsetX = event.rawX.roundToInt() - startLeft
                    downY = params.y
                    moved = false
                    draggingHandle = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!moved && (abs(dx) > slop || abs(dy) > slop)) moved = true
                    if (moved) {
                        val root = rootView
                        val rootWidth = root?.width?.takeIf { it > 0 }
                            ?: root?.measuredWidth?.takeIf { it > 0 }
                            ?: dp(HANDLE_TOUCH_W_DP)
                        val handleH = handle.height.takeIf { it > 0 } ?: dp(HANDLE_H_DP)
                        if (!draggingHandle) {
                            params.gravity = Gravity.TOP or Gravity.LEFT
                            draggingHandle = true
                        }
                        params.x = OverlayPositionCalculator.windowLeftForPointer(
                            event.rawX.roundToInt(),
                            downTouchOffsetX,
                            screenWidth,
                            rootWidth
                        )
                        params.y = OverlayPositionCalculator.clampY(
                            (downY + dy).roundToInt(), handleH, screenHeight
                        )
                        runCatching { windowManager.updateViewLayout(rootView, params) }
                            .onFailure { Log.e(TAG, "drag handle: updateViewLayout FAILED", it) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    animateHandlePress(pressed = false)
                    if (!moved) {
                        draggingHandle = false
                        handle.performClick()
                    } else {
                        animateToNearestEdge(event.rawX, handle)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    animateHandlePress(pressed = false)
                    draggingHandle = false
                    params.gravity = Gravity.TOP or edgeGravity(currentEdge)
                    params.x = 0
                    runCatching { windowManager.updateViewLayout(rootView, params) }
                    moved = false
                    true
                }
                else -> false
            }
        }
    }

    private fun animateHandlePress(pressed: Boolean) {
        val visual = handleVisualView ?: return
        val baseWidth = dp(HANDLE_W_DP)
        val currentWidth = (visual.layoutParams as? FrameLayout.LayoutParams)?.width
            ?.takeIf { it > 0 }
            ?: baseWidth
        val targetWidth = if (pressed) {
            (baseWidth * OverlayAnimationSpec.PRESS_SCALE).roundToInt()
        } else {
            baseWidth
        }
        val handleHeight = visual.height.takeIf { it > 0 } ?: dp(HANDLE_H_DP)

        visual.scaleX = OverlayAnimationSpec.RELEASE_SCALE
        visual.animate().cancel()
        handlePressAnimator?.cancel()

        handlePressAnimator = ValueAnimator.ofInt(currentWidth, targetWidth).apply {
            duration = if (pressed) {
                OverlayAnimationSpec.PRESS_DURATION_MS
            } else {
                OverlayAnimationSpec.RELEASE_DURATION_MS
            }
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { animator ->
                val width = animator.animatedValue as Int
                val lp = visual.layoutParams as? FrameLayout.LayoutParams ?: return@addUpdateListener
                if (lp.width != width) {
                    lp.width = width
                    visual.layoutParams = lp
                }
                (visual.background as? GradientDrawable)?.cornerRadius =
                    OverlayHandleSpec.cornerRadiusPx(width, handleHeight)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (handlePressAnimator === animation) {
                        handlePressAnimator = null
                    }
                }
            })
        }.also { it.start() }
    }

    private fun animateToNearestEdge(rawX: Float, handle: View) {
        val root = rootView ?: return
        val rootWidth = root.width.takeIf { it > 0 }
            ?: root.measuredWidth.takeIf { it > 0 }
            ?: dp(HANDLE_TOUCH_W_DP)
        val targetEdge = OverlayPositionCalculator.nearestEdge(rawX.roundToInt(), screenWidth)
        val startX = if (draggingHandle) {
            params.x
        } else {
            OverlayPositionCalculator.edgeWindowLeft(currentEdge, screenWidth, rootWidth)
        }
        val targetX = OverlayPositionCalculator.edgeWindowLeft(targetEdge, screenWidth, rootWidth)

        params.gravity = Gravity.TOP or Gravity.LEFT
        params.x = startX
        runCatching { windowManager.updateViewLayout(root, params) }
            .onFailure { Log.e(TAG, "animateToNearestEdge: prepare FAILED", it) }

        if (startX == targetX) {
            draggingHandle = false
            currentEdge = targetEdge
            params.gravity = Gravity.TOP or edgeGravity(currentEdge)
            params.x = 0
            alignHandleVisual()
            runCatching { windowManager.updateViewLayout(root, params) }
                .onFailure { error -> Log.e(TAG, "animateToNearestEdge: finish FAILED", error) }
            persistPosition(handle)
            return
        }

        val animator = ValueAnimator.ofInt(startX, targetX).apply {
            duration = HANDLE_SETTLE_DURATION_MS
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                params.x = it.animatedValue as Int
                runCatching { windowManager.updateViewLayout(root, params) }
                    .onFailure { error -> Log.e(TAG, "animateToNearestEdge: update FAILED", error) }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (edgeAnimator !== animation || rootView !== root) return
                    edgeAnimator = null
                    draggingHandle = false
                    currentEdge = targetEdge
                    params.gravity = Gravity.TOP or edgeGravity(currentEdge)
                    params.x = 0
                    alignHandleVisual()
                    runCatching { windowManager.updateViewLayout(root, params) }
                        .onFailure { error -> Log.e(TAG, "animateToNearestEdge: finish FAILED", error) }
                    persistPosition(handle)
                }
            })
        }
        edgeAnimator = animator
        animator.start()
    }

    private fun cancelEdgeAnimation() {
        edgeAnimator?.let {
            edgeAnimator = null
            it.cancel()
        }
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
            else Color.argb(
                OverlayHandleSpec.NORMAL_ALPHA,
                OverlayHandleSpec.NORMAL_RED,
                OverlayHandleSpec.NORMAL_GREEN,
                OverlayHandleSpec.NORMAL_BLUE
            )
        )
        redDot?.visibility = if (isRecording) View.VISIBLE else View.GONE
        recordButton?.background = buildActionBackground(
            if (isRecording) Color.argb(170, 255, 70, 70)
            else overlayPalette().actionColor
        )
        recordButtonLabel?.text = if (isRecording) "录音中 ${formatElapsed(seconds)}" else "录音"
    }

    private fun formatElapsed(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }
}
