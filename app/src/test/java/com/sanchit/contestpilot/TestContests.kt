package com.sanchit.contestpilot

import com.sanchit.contestpilot.domain.model.Contest
import com.sanchit.contestpilot.domain.model.ContestDivision
import com.sanchit.contestpilot.domain.model.ContestPhase
import com.sanchit.contestpilot.domain.model.Platform

/** Fixed reference instant used throughout the suite: no test reads the system clock. */
const val REFERENCE_EPOCH_SECONDS = 1_800_000_000L

const val SEVEN_DAYS_SECONDS = 7L * 24 * 60 * 60

fun testContest(
    id: String = "1",
    platform: Platform = Platform.CODEFORCES,
    name: String = "Codeforces Round $id",
    phase: ContestPhase = ContestPhase.BEFORE,
    startTimeSeconds: Long?,
    durationSeconds: Long = 7_200,
    division: ContestDivision = ContestDivision.OTHER
): Contest = Contest(
    id = Contest.buildId(platform, id),
    platform = platform,
    platformContestId = id,
    name = name,
    phase = phase,
    startTimeSeconds = startTimeSeconds,
    durationSeconds = durationSeconds,
    division = division,
    url = "https://example.invalid/$id"
)
