package com.ylib.quicksave.notification

import androidx.annotation.DrawableRes
import com.ylib.quicksave.R

internal object NotificationIconSpec {
    @get:DrawableRes
    val smallIcon: Int = R.drawable.ic_notification_quicksave

    const val currentNotificationId: Int = 1004
    const val legacyNotificationId: Int = 1001
}

