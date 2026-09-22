package com.sanchit.contestpilot.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sanchit.contestpilot.di.ServiceLocator

/**
 * Replays persisted reminders into AlarmManager.
 *
 * This runs after a reboot or an app update, where the system has dropped every pending
 * alarm but the database still knows what should be pending.
 */
class ReminderMaintenanceWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        ServiceLocator.from(applicationContext).alarmScheduler.restorePendingAlarms()
        Result.success()
    } catch (_: Exception) {
        // Retrying costs nothing and the next attempt runs with a healthier system.
        Result.retry()
    }

    companion object {
        private const val UNIQUE_NAME = "contestpilot-restore-reminders"

        fun enqueueRestore(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ReminderMaintenanceWorker>().build()
            )
        }
    }
}
