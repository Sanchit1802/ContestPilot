package com.sanchit.contestpilot.data.remote.codeforces

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * The public, documented Codeforces API.
 *
 * Only `contest.list` is used. The API has no registration method at all — see
 * https://codeforces.com/apiHelp/methods — so nothing here pretends otherwise.
 */
interface CodeforcesApi {

    @GET("contest.list")
    suspend fun getContests(
        @Query("gym") gym: Boolean = false
    ): CodeforcesContestListResponse

    companion object {
        const val BASE_URL = "https://codeforces.com/api/"

        fun contestUrl(contestId: String): String = "https://codeforces.com/contest/$contestId"
    }
}
