package com.sanchit.contestpilot.notification

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sanchit.contestpilot.di.ServiceLocator
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Keeps the cached contest list and the automation status fresh in the background, and
 * re-synchronises reminders whenever the data changes.
 *
 * This is refresh work only. The reminder itself is delivered by an exact alarm, because
 * a periodic job carries no timing guarantee.
 */
class ContestSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val locator = ServiceLocator.from(applicationContext)

        return try {
            val report = locator.contestRepository.refresh()
            locator.automationStatusRepository.refresh()

            val contests = locator.contestRepository.observeContests().first()
            val settings = locator.settingsRepository.current()
            locator.alarmScheduler.synchronize(contests, settings)

            // Cached data is still on screen, so a failed source is worth retrying but
            // is not an error the user must act on.
            if (report.anySucceeded || report.failures.isEmpty()) Result.success() else Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "contestpilot-periodic-sync"
        private const val SYNC_INTERVAL_HOURS = 6L

        /**
         * Six hours keeps contests current without waking the radio often; Codeforces
         * announces rounds days ahead, so a tighter interval buys nothing.
         */
        fun enqueuePeriodicSync(context: Context) {
            val request = PeriodicWorkRequestBuilder<ContestSyncWorker>(
                SYNC_INTERVAL_HOURS,
                TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
