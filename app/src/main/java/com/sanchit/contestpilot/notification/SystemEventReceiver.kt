package com.sanchit.contestpilot.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms reminders after events that clear the system alarm table or invalidate the
 * moments it holds: a reboot, an app update, a manual clock change or a time-zone change.
 *
 * Alarms are stored as absolute epoch milliseconds, so a time-zone change never moves a
 * reminder; the reschedule exists because a user-set clock change does.
 */
class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return

        // Broadcast receivers get a few seconds at most, so the actual rescheduling runs
        // in WorkManager where it can outlive this call.
        ReminderMaintenanceWorker.enqueueRestore(context)
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        )
    }
}
