package com.sanchit.contestpilot.domain.logic

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Time helpers. All comparisons use absolute [Instant] values; the Indian time zone is
 * applied only when rendering text for the user.
 */
object DateTimeUtils {

    const val DEFAULT_CONTEST_WINDOW_DAYS = 7L

    val DISPLAY_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

    private val dateFormatter =
        DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(DISPLAY_ZONE)
    private val clockFormatter =
        DateTimeFormatter.ofPattern("hh:mm a").withZone(DISPLAY_ZONE)
    private val relativeFormatter =
        DateTimeFormatter.ofPattern("dd MMM, hh:mm a").withZone(DISPLAY_ZONE)

    fun formatIndianDate(timestampSeconds: Long?): String =
        timestampSeconds?.let { dateFormatter.format(Instant.ofEpochSecond(it)) }
            ?: "Date unavailable"

    fun formatIndianClockTime(timestampSeconds: Long?): String =
        timestampSeconds?.let { clockFormatter.format(Instant.ofEpochSecond(it)) }
            ?: "Time unavailable"

    /** Compact "22 Sep, 08:00 PM" form used for sync/automation timestamps. */
    fun formatShortIndianTime(timestampSeconds: Long?): String =
        timestampSeconds?.let { relativeFormatter.format(Instant.ofEpochSecond(it)) } ?: "Never"

    fun formatDuration(durationSeconds: Long): String {
        if (durationSeconds <= 0) return "Unknown"

        val hours = durationSeconds / 3600
        val minutes = (durationSeconds % 3600) / 60

        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }

    /**
     * True when [timestampSeconds] falls strictly after [referenceTime] and no later than
     * [windowDays] afterwards. The exact boundary instant is included.
     */
    fun isWithinUpcomingWindow(
        timestampSeconds: Long?,
        referenceTime: Instant,
        windowDays: Long = DEFAULT_CONTEST_WINDOW_DAYS
    ): Boolean {
        val startTime = timestampSeconds?.let(Instant::ofEpochSecond) ?: return false
        val windowEnd = referenceTime.plus(windowDays, ChronoUnit.DAYS)

        return startTime.isAfter(referenceTime) && !startTime.isAfter(windowEnd)
    }
}
