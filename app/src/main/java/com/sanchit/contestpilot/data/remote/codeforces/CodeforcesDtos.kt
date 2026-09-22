package com.sanchit.contestpilot.data.remote.codeforces

/**
 * Envelope shared by every Codeforces API method: `status` is `"OK"` or `"FAILED"`,
 * and `comment` carries the failure reason.
 */
data class CodeforcesContestListResponse(
    val status: String?,
    val comment: String?,
    val result: List<CodeforcesContestDto>?
)

/**
 * Subset of the documented `Contest` object that ContestPilot needs.
 *
 * Fields the API marks "can be absent" are nullable here. There is intentionally no
 * `rated` field: the Codeforces `Contest` object does not define one.
 */
data class CodeforcesContestDto(
    val id: Long?,
    val name: String?,
    val phase: String?,
    val durationSeconds: Long?,
    val startTimeSeconds: Long?
)
