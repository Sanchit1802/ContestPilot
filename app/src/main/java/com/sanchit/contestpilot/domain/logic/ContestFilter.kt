package com.sanchit.contestpilot.domain.logic

import com.sanchit.contestpilot.domain.model.Contest
import com.sanchit.contestpilot.domain.model.Platform
import java.time.Instant

/**
 * Selects the contests the user actually wants to see: upcoming, inside the configured
 * window, on an enabled platform, sorted chronologically.
 */
object ContestFilter {

    fun upcomingWithinWindow(
        contests: List<Contest>,
        referenceTime: Instant,
        windowDays: Long = DateTimeUtils.DEFAULT_CONTEST_WINDOW_DAYS,
        enabledPlatforms: Set<Platform> = Platform.entries.toSet()
    ): List<Contest> = contests
        .filter { it.platform in enabledPlatforms }
        .filter { it.isUpcoming(referenceTime) }
        .filter {
            DateTimeUtils.isWithinUpcomingWindow(
                timestampSeconds = it.startTimeSeconds,
                referenceTime = referenceTime,
                windowDays = windowDays
            )
        }
        .sortedWith(compareBy({ it.startTimeSeconds }, { it.platform.name }, { it.id }))
}
