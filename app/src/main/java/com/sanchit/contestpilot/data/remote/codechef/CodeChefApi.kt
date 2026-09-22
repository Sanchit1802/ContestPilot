package com.sanchit.contestpilot.data.remote.codechef

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * CodeChef's contest listing endpoint.
 *
 * CodeChef retired its documented developer API and publishes no replacement, so this is
 * the JSON endpoint that powers codechef.com/contests itself. It is unofficial and may
 * change without notice; [com.sanchit.contestpilot.data.provider.CodeChefContestProvider]
 * therefore treats every failure as recoverable and keeps showing cached data.
 */
interface CodeChefApi {

    @GET("api/list/contests/all")
    suspend fun getContests(
        @Query("sort_by") sortBy: String = "START",
        @Query("sorting_order") sortingOrder: String = "asc",
        @Query("offset") offset: Int = 0,
        @Query("mode") mode: String = "all"
    ): CodeChefContestListResponse

    companion object {
        const val BASE_URL = "https://www.codechef.com/"

        fun contestUrl(contestCode: String): String =
            "https://www.codechef.com/$contestCode"
    }
}
