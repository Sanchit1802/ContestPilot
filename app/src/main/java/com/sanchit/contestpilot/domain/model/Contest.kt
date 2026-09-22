package com.sanchit.contestpilot.domain.model

import java.time.Instant

/**
 * Platform-independent contest used by the whole application.
 *
 * Only fields that ContestPilot actually needs are modelled; nothing here is invented.
 * Every value originates from a provider response.
 */
data class Contest(
    /** Stable, globally unique key: `"CODEFORCES:1234"`. Used as the Room primary key. */
    val id: String,
    val platform: Platform,
    /** Identifier as the platform itself uses it (Codeforces contest id, CodeChef contest code). */
    val platformContestId: String,
    val name: String,
    val phase: ContestPhase,
    /** Absent when the platform has not announced a start time yet. */
    val startTimeSeconds: Long?,
    val durationSeconds: Long,
    val division: ContestDivision,
    /** Public contest page, for the "open contest" action. */
    val url: String
) {
    val startInstant: Instant?
        get() = startTimeSeconds?.let(Instant::ofEpochSecond)

    val endInstant: Instant?
        get() = startTimeSeconds?.let { Instant.ofEpochSecond(it + durationSeconds) }

    /**
     * True when the contest has not started yet, judged against absolute [Instant] values.
     * A contest whose start instant equals [referenceTime] is no longer upcoming.
     */
    fun isUpcoming(referenceTime: Instant): Boolean {
        val start = startInstant ?: return false
        return phase == ContestPhase.BEFORE && start.isAfter(referenceTime)
    }

    companion object {
        fun buildId(platform: Platform, platformContestId: String): String =
            "${platform.name}:$platformContestId"
    }
}
