package com.sanchit.contestpilot.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import com.sanchit.contestpilot.data.local.dao.NotificationScheduleDao
import com.sanchit.contestpilot.data.local.entity.NotificationScheduleEntity
import com.sanchit.contestpilot.domain.logic.NotificationScheduleCalculator
import com.sanchit.contestpilot.domain.logic.PlannedReminder
import com.sanchit.contestpilot.domain.logic.TimeProvider
import com.sanchit.contestpilot.domain.model.AppSettings
import com.sanchit.contestpilot.domain.model.Contest

/**
 * How the last synchronisation of reminders went, so the UI can warn the user when the
 * operating system would not grant exact alarms.
 */
data class AlarmSyncReport(
    val scheduled: Int,
    val cancelled: Int,
    val exactAlarmsAllowed: Boolean,
    val failureMessage: String? = null
)

/**
 * Keeps AlarmManager in step with the contests ContestPilot knows about.
 *
 * Exact alarms are used deliberately: a periodic background job cannot guarantee firing
 * at "start minus ten minutes". Every scheduled alarm is mirrored into Room so it can be
 * replayed after a reboot and so a reminder is never scheduled twice.
 */
class ContestAlarmScheduler(
    private val context: Context,
    private val scheduleDao: NotificationScheduleDao,
    private val timeProvider: TimeProvider = TimeProvider.system
) {

    private val alarmManager: AlarmManager?
        get() = context.getSystemService()

    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return alarmManager?.canScheduleExactAlarms() == true
    }

    /**
     * Makes the pending alarms exactly match [contests] under [settings]: new reminders
     * are scheduled, changed ones are replaced, and obsolete ones are cancelled.
     */
    suspend fun synchronize(contests: List<Contest>, settings: AppSettings): AlarmSyncReport {
        val now = timeProvider.now()
        val planned = NotificationScheduleCalculator.plan(contests, settings, now)
        val plannedById = planned.associateBy { it.contestId }
        val existing = scheduleDao.getAll().associateBy { it.contestId }

        var cancelled = 0
        for ((contestId, entity) in existing) {
            val stillPlanned = plannedById[contestId]
            val unchanged = stillPlanned != null &&
                stillPlanned.triggerAtEpochMillis == entity.triggerAtEpochMillis &&
                stillPlanned.contestName == entity.contestName &&
                !entity.delivered

            if (!unchanged) {
                cancelAlarm(entity.requestCode, contestId)
                scheduleDao.deleteByContestId(contestId)
                if (stillPlanned == null) cancelled++
            }
        }

        val exactAllowed = canScheduleExactAlarms()
        var scheduled = 0
        var failure: String? = null

        for (reminder in planned) {
            val existingEntity = existing[reminder.contestId]
            val alreadyCurrent = existingEntity != null &&
                !existingEntity.delivered &&
                existingEntity.triggerAtEpochMillis == reminder.triggerAtEpochMillis &&
                existingEntity.contestName == reminder.contestName

            if (alreadyCurrent) continue

            when (val result = scheduleAlarm(reminder, exactAllowed)) {
                is ScheduleOutcome.Scheduled -> {
                    scheduleDao.upsert(
                        NotificationScheduleEntity(
                            contestId = reminder.contestId,
                            contestName = reminder.contestName,
                            platformName = reminder.platformName,
                            requestCode = NotificationScheduleCalculator.requestCode(
                                reminder.contestId
                            ),
                            triggerAtEpochMillis = reminder.triggerAtEpochMillis,
                            contestStartEpochMillis = reminder.contestStartEpochMillis,
                            leadMinutes = reminder.leadMinutes,
                            delivered = false,
                            exact = result.exact,
                            scheduledAtEpochMillis = now.toEpochMilli()
                        )
                    )
                    scheduled++
                }

                is ScheduleOutcome.Failed -> failure = result.message
            }
        }

        return AlarmSyncReport(
            scheduled = scheduled,
            cancelled = cancelled,
            exactAlarmsAllowed = exactAllowed,
            failureMessage = failure
        )
    }

    /** Re-arms everything still pending in the database, after a reboot or an app update. */
    suspend fun restorePendingAlarms(): AlarmSyncReport {
        val now = timeProvider.now().toEpochMilli()
        val exactAllowed = canScheduleExactAlarms()
        var scheduled = 0
        var cancelled = 0
        var failure: String? = null

        for (entity in scheduleDao.getPending()) {
            if (entity.triggerAtEpochMillis <= now) {
                // The moment passed while the device was off; firing now would be noise.
                scheduleDao.deleteByContestId(entity.contestId)
                cancelled++
                continue
            }

            val reminder = PlannedReminder(
                contestId = entity.contestId,
                contestName = entity.contestName,
                platformName = entity.platformName,
                triggerAtEpochMillis = entity.triggerAtEpochMillis,
                contestStartEpochMillis = entity.contestStartEpochMillis,
                leadMinutes = entity.leadMinutes
            )

            when (val result = scheduleAlarm(reminder, exactAllowed)) {
                is ScheduleOutcome.Scheduled -> {
                    scheduleDao.upsert(entity.copy(exact = result.exact))
                    scheduled++
                }

                is ScheduleOutcome.Failed -> failure = result.message
            }
        }

        return AlarmSyncReport(scheduled, cancelled, exactAllowed, failure)
    }

    suspend fun cancelAll() {
        for (entity in scheduleDao.getAll()) {
            cancelAlarm(entity.requestCode, entity.contestId)
        }
        scheduleDao.deleteAll()
    }

    suspend fun markDelivered(contestId: String) {
        scheduleDao.markDelivered(contestId)
    }

    private fun scheduleAlarm(reminder: PlannedReminder, exactAllowed: Boolean): ScheduleOutcome {
        val manager = alarmManager
            ?: return ScheduleOutcome.Failed("Alarm service unavailable on this device.")

        val pendingIntent = buildPendingIntent(reminder)
            ?: return ScheduleOutcome.Failed("Could not create the reminder alarm.")

        return try {
            if (exactAllowed) {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.triggerAtEpochMillis,
                    pendingIntent
                )
                ScheduleOutcome.Scheduled(exact = true)
            } else {
                // Without the exact-alarm permission the reminder still fires, just not
                // to the second. The UI tells the user how to grant it.
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.triggerAtEpochMillis,
                    pendingIntent
                )
                ScheduleOutcome.Scheduled(exact = false)
            }
        } catch (_: SecurityException) {
            ScheduleOutcome.Failed(
                "Android refused to schedule an exact alarm. Allow alarms and reminders " +
                    "for ContestPilot in system settings."
            )
        }
    }

    private fun cancelAlarm(requestCode: Int, contestId: String) {
        val intent = Intent(context, ContestAlarmReceiver::class.java).apply {
            action = ContestAlarmReceiver.ACTION_CONTEST_REMINDER
            data = ContestAlarmReceiver.buildUri(contestId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            alarmManager?.cancel(it)
            it.cancel()
        }
    }

    private fun buildPendingIntent(reminder: PlannedReminder): PendingIntent? {
        val intent = Intent(context, ContestAlarmReceiver::class.java).apply {
            action = ContestAlarmReceiver.ACTION_CONTEST_REMINDER
            // A unique data URI keeps otherwise-identical intents from being coalesced.
            data = ContestAlarmReceiver.buildUri(reminder.contestId)
            putExtra(ContestAlarmReceiver.EXTRA_CONTEST_ID, reminder.contestId)
            putExtra(ContestAlarmReceiver.EXTRA_CONTEST_NAME, reminder.contestName)
            putExtra(ContestAlarmReceiver.EXTRA_PLATFORM_NAME, reminder.platformName)
            putExtra(ContestAlarmReceiver.EXTRA_LEAD_MINUTES, reminder.leadMinutes)
        }

        return PendingIntent.getBroadcast(
            context,
            NotificationScheduleCalculator.requestCode(reminder.contestId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private sealed interface ScheduleOutcome {
        data class Scheduled(val exact: Boolean) : ScheduleOutcome
        data class Failed(val message: String) : ScheduleOutcome
    }
}
