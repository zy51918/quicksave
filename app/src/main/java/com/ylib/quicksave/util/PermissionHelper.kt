package com.ylib.quicksave.util

import android.Manifest
import android.content.Context
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

object PermissionHelper {

    /** Injection point for SDK_INT in unit tests. */
    var sdkIntProvider: () -> Int = { android.os.Build.VERSION.SDK_INT }

    fun hasNotificationPermission(context: Context): Boolean {
        if (sdkIntProvider() < 33) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** 是否已授予悬浮窗（SYSTEM_ALERT_WINDOW）权限。 */
    fun canDrawOverlays(context: Context): Boolean =
        android.provider.Settings.canDrawOverlays(context)
}
