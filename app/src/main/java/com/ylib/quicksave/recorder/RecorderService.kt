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
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

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
        if (recording) stopRecording(success = true)
        scope.cancel()
        super.onDestroy()
    }

    private fun startRecording() {
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification(0))

        val uri = createPendingOutput()
        if (uri == null) {
            toast("无法创建录音文件")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val descriptor = runCatching { contentResolver.openFileDescriptor(uri, "w") }.getOrNull()
        if (descriptor == null) {
            toast("无法打开录音文件")
            deletePending(uri)
            stopForeground(STOP_FOREGROUND_REMOVE)
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
            rec.prepare()
            rec.start()
        } catch (e: Exception) {
            runCatching { rec.release() }
            runCatching { descriptor.close() }
            deletePending(uri)
            toast("录音启动失败：${e.message}")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        recorder = rec
        pfd = descriptor
        outputUri = uri
        recording = true
        elapsed = 0
        rec.setOnErrorListener { _, _, _ -> stopRecording(success = true) }

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

    private fun ensureChannel() {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            notificationManager.createNotificationChannel(
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
        notificationManager.notify(NOTIFICATION_ID, buildNotification(seconds))
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
