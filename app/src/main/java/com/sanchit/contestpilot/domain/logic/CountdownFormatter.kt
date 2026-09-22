package com.sanchit.contestpilot.domain.logic

import com.sanchit.contestpilot.domain.model.Contest
import java.time.Duration
import java.time.Instant

/**
 * Renders "time until start" labels from two absolute [Instant] values.
 *
 * The formatter is pure: it never reads the system clock and never decrements a counter,
 * so a stale or resumed UI always shows the truth rather than a drifted tally.
 */
object CountdownFormatter {

    private const val SECONDS_PER_MINUTE = 60L
    private const val MINUTES_PER_HOUR = 60L
    private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

    /** Below this remaining time the exact minute count stops being useful. */
    private const val STARTING_SOON_THRESHOLD_MINUTES = 1L

    /** While a contest is this close, the label can change every second. */
    private const val FINE_GRAINED_WINDOW_SECONDS = 2 * SECONDS_PER_MINUTE

    const val FINE_TICK_MILLIS = 1_000L
    const val COARSE_TICK_MILLIS = 15_000L

    fun format(startTimeSeconds: Long?, now: Instant): String {
        val startTime = startTimeSeconds?.let(Instant::ofEpochSecond) ?: return "Start time unknown"
        return format(Duration.between(now, startTime))
    }

    fun format(remaining: Duration): String {
        if (remaining.isNegative) return "In progress"

        val totalMinutes = remaining.toMinutes()
        if (totalMinutes < STARTING_SOON_THRESHOLD_MINUTES) return "Starting soon"

        val days = totalMinutes / MINUTES_PER_DAY
        val hours = (totalMinutes % MINUTES_PER_DAY) / MINUTES_PER_HOUR
        val minutes = totalMinutes % MINUTES_PER_HOUR

        return when {
            days > 0 -> "Starts in ${days}d ${hours}h ${minutes}m"
            hours > 0 -> "Starts in ${hours}h ${minutes}m"
            else -> "Starts in ${minutes}m"
        }
    }

    /**
     * Label for a contest that may already have started or finished, so the card never
     * shows a countdown for something that is over.
     */
    fun formatForContest(contest: Contest, now: Instant): String {
        val start = contest.startInstant ?: return "Start time unknown"
        val end = contest.endInstant

        return when {
            now.isBefore(start) -> format(Duration.between(now, start))
            end != null && now.isBefore(end) -> "In progress"
            else -> "Finished"
        }
    }

    /**
     * How often the shared ticker should emit, given the contests currently on screen.
     *
     * Returns `null` when nothing needs a ticking clock at all, which lets the ViewModel
     * stop the ticker instead of burning a coroutine on an idle screen.
     */
    fun tickIntervalMillis(contests: List<Contest>, now: Instant): Long? {
        val nearestStart = contests
            .mapNotNull { it.startInstant }
            .filter { it.isAfter(now) }
            .minOrNull() ?: return null

        val secondsAway = Duration.between(now, nearestStart).seconds
        return if (secondsAway <= FINE_GRAINED_WINDOW_SECONDS) FINE_TICK_MILLIS else COARSE_TICK_MILLIS
    }
}
