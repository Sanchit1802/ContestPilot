package com.sanchit.contestpilot.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

object NotificationChannels {

    const val CONTEST_REMINDERS_ID = "contest_reminders"

    /**
     * Created eagerly at application start so the channel exists before the first alarm
     * fires, and so it is visible in system settings even before any reminder is posted.
     */
    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService<NotificationManager>() ?: return
        val channel = NotificationChannel(
            CONTEST_REMINDERS_ID,
            "Contest reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts shortly before a contest you follow begins."
            enableVibration(true)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }
}
