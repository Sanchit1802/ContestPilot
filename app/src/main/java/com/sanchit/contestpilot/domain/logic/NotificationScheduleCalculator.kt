package com.sanchit.contestpilot.domain.logic

import com.sanchit.contestpilot.domain.model.AppSettings
import com.sanchit.contestpilot.domain.model.Contest
import java.time.Instant

/**
 * One reminder that should be pending with the system alarm service.
 */
data class PlannedReminder(
    val contestId: String,
    val contestName: String,
    val platformName: String,
    val triggerAtEpochMillis: Long,
    val contestStartEpochMillis: Long,
    val leadMinutes: Int
)

/**
 * Works out which reminders should exist for a set of contests.
 *
 * Kept free of Android types so the arithmetic — especially the "already too late to
 * schedule" boundary — can be tested against fixed instants.
 */
object NotificationScheduleCalculator {

    fun plan(
        contests: List<Contest>,
        settings: AppSettings,
        referenceTime: Instant
    ): List<PlannedReminder> {
        if (!settings.notificationsEnabled) return emptyList()

        val leadMillis = settings.notificationLeadMinutes * 60_000L

        return contests
            .asSequence()
            .filter { it.platform in settings.enabledPlatforms }
            .filter { it.isUpcoming(referenceTime) }
            .filter {
                DateTimeUtils.isWithinUpcomingWindow(
                    timestampSeconds = it.startTimeSeconds,
                    referenceTime = referenceTime,
                    windowDays = settings.contestWindowDays.toLong()
                )
            }
            .mapNotNull { contest ->
                val startMillis = (contest.startTimeSeconds ?: return@mapNotNull null) * 1_000L
                val triggerAt = startMillis - leadMillis

                // A reminder whose moment has passed is dropped rather than fired late.
                if (triggerAt <= referenceTime.toEpochMilli()) return@mapNotNull null

                PlannedReminder(
                    contestId = contest.id,
                    contestName = contest.name,
                    platformName = contest.platform.displayName,
                    triggerAtEpochMillis = triggerAt,
                    contestStartEpochMillis = startMillis,
                    leadMinutes = settings.notificationLeadMinutes
                )
            }
            .sortedBy { it.triggerAtEpochMillis }
            .toList()
    }

    /**
     * "Codeforces Round 999 starts in 10 minutes."
     */
    fun notificationText(contestName: String, leadMinutes: Int): String {
        val unit = if (leadMinutes == 1) "minute" else "minutes"
        return "$contestName starts in $leadMinutes $unit."
    }

    /**
     * Stable positive request code for a contest's alarm [android.app.PendingIntent].
     * Derived from the contest id so a reschedule always replaces the same alarm.
     */
    fun requestCode(contestId: String): Int = contestId.hashCode() and 0x7FFFFFFF
}
