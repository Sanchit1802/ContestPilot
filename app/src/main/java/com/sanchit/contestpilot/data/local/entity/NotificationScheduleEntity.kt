package com.sanchit.contestpilot.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A reminder that has been handed to `AlarmManager`.
 *
 * Persisting it is what makes reminders survive a process death or a reboot: the boot
 * receiver replays this table, and [delivered] stops an already-fired reminder from
 * being scheduled a second time.
 */
@Entity(tableName = "notification_schedules")
data class NotificationScheduleEntity(
    @PrimaryKey val contestId: String,
    val contestName: String,
    val platformName: String,
    val requestCode: Int,
    val triggerAtEpochMillis: Long,
    val contestStartEpochMillis: Long,
    val leadMinutes: Int,
    val delivered: Boolean,
    /** False when the OS refused an exact alarm and an inexact one was used instead. */
    val exact: Boolean,
    val scheduledAtEpochMillis: Long
)
