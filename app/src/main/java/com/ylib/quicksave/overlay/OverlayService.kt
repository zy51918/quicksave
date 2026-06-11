package com.ylib.quicksave.overlay

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
import com.ylib.quicksave.app.QuickSaveApplication
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
        private const val HANDLE_W_DP = 14
        private const val HANDLE_H_DP = 54
    }

    private lateinit var windowManager: WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var rootView: FrameLayout? = null
    private var handleView: View? = null
    private var panelView: LinearLayout? = null
    private var recordButton: Button? = null
    private var redDot: View? = null
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
        observeRecordingState()
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
            collapse()
            launchInputActivity()
        })
        val recBtn = buildPanelButton("录音") {
            collapse()
            onRecordClicked()
        }
        recBtn.apply {
            maxLines = 1
            fontFeatureSettings = "tnum" // 等宽数字，计时更新不改变字宽
            // 固定宽度槽位：按最长态实测，避免计时引起面板重排（抖动）
            val longest = "录音中 00:00"
            val contentWidth = paint.measureText(longest).toInt()
            val lp = layoutParams as LinearLayout.LayoutParams
            lp.width = contentWidth + dp(28)
            layoutParams = lp
        }
        recordButton = recBtn
        addView(recBtn)
    }

    private fun launchInputActivity() {
        val intent = Intent(this, com.ylib.quicksave.ui.InputActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun buildPanelButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.marginStart = dp(4)
                it.marginEnd = dp(4)
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
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        toggle()
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

    private fun observeRecordingState() {
        scope.launch {
            RecordingController.state.collectLatest { st ->
                applyRecordingVisual(st.isRecording, st.elapsedSeconds)
            }
        }
    }

    private fun applyRecordingVisual(isRecording: Boolean, seconds: Int) {
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
}
