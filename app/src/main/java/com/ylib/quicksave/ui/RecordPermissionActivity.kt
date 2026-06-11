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
