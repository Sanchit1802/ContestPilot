package com.sanchit.contestpilot.notification

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sanchit.contestpilot.MainActivity
import com.sanchit.contestpilot.R
import com.sanchit.contestpilot.di.ServiceLocator
import com.sanchit.contestpilot.domain.logic.NotificationScheduleCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Posts the "starts in N minutes" reminder when an alarm fires.
 *
 * The receiver runs whether or not the app is open, which is the whole point of using
 * AlarmManager rather than an in-app timer.
 */
class ContestAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CONTEST_REMINDER) return

        val contestId = intent.getStringExtra(EXTRA_CONTEST_ID) ?: return
        val contestName = intent.getStringExtra(EXTRA_CONTEST_NAME) ?: return
        val platformName = intent.getStringExtra(EXTRA_PLATFORM_NAME).orEmpty()
        val leadMinutes = intent.getIntExtra(EXTRA_LEAD_MINUTES, 10)

        showNotification(context, contestId, contestName, platformName, leadMinutes)

        // Marking the reminder delivered is what stops a later reschedule — or a reboot
        // replay — from posting the same notification twice.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ServiceLocator.from(context).alarmScheduler.markDelivered(contestId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(
        context: Context,
        contestId: String,
        contestName: String,
        platformName: String,
        leadMinutes: Int
    ) {
        if (!hasPostPermission(context)) return

        val contentIntent = PendingIntent.getActivity(
            context,
            NotificationScheduleCalculator.requestCode(contestId),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = NotificationScheduleCalculator.notificationText(contestName, leadMinutes)
        val title = if (platformName.isBlank()) "Contest starting soon" else platformName

        val notification = NotificationCompat.Builder(
            context,
            NotificationChannels.CONTEST_REMINDERS_ID
        )
            .setSmallIcon(R.drawable.ic_notification_contest)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(
            NotificationScheduleCalculator.requestCode(contestId),
            notification
        )
    }

    private fun hasPostPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val ACTION_CONTEST_REMINDER = "com.sanchit.contestpilot.CONTEST_REMINDER"
        const val EXTRA_CONTEST_ID = "contest_id"
        const val EXTRA_CONTEST_NAME = "contest_name"
        const val EXTRA_PLATFORM_NAME = "platform_name"
        const val EXTRA_LEAD_MINUTES = "lead_minutes"

        fun buildUri(contestId: String): Uri =
            Uri.parse("contestpilot://reminder/$contestId")
    }
}
