package com.sanchit.contestpilot.data.remote.codechef

import com.google.gson.annotations.SerializedName

data class CodeChefContestListResponse(
    val status: String?,
    val message: String?,
    @SerializedName("future_contests") val futureContests: List<CodeChefContestDto>?,
    @SerializedName("present_contests") val presentContests: List<CodeChefContestDto>?
)

/**
 * A CodeChef contest as the listing endpoint returns it.
 *
 * `contest_start_date_iso` is an offset date-time such as `2026-09-23T20:00:00+05:30`,
 * and `contest_duration` is a whole number of minutes encoded as a string.
 */
data class CodeChefContestDto(
    @SerializedName("contest_code") val contestCode: String?,
    @SerializedName("contest_name") val contestName: String?,
    @SerializedName("contest_start_date_iso") val startDateIso: String?,
    @SerializedName("contest_end_date_iso") val endDateIso: String?,
    @SerializedName("contest_duration") val durationMinutes: String?
)
