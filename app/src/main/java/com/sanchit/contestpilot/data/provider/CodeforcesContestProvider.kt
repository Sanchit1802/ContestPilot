package com.sanchit.contestpilot.data.provider

import com.sanchit.contestpilot.data.remote.codeforces.CodeforcesApi
import com.sanchit.contestpilot.data.remote.codeforces.CodeforcesContestDto
import com.sanchit.contestpilot.domain.logic.ContestClassifier
import com.sanchit.contestpilot.domain.model.Contest
import com.sanchit.contestpilot.domain.model.ContestPhase
import com.sanchit.contestpilot.domain.model.Platform
import kotlinx.coroutines.CancellationException

class CodeforcesContestProvider(
    private val api: CodeforcesApi
) : ContestProvider {

    override val platform: Platform = Platform.CODEFORCES

    override suspend fun fetchContests(): ProviderResult = try {
        val response = api.getContests(gym = false)

        when {
            response.status != "OK" -> ProviderResult.Failure(
                platform = platform,
                error = ProviderError.SERVICE_UNAVAILABLE,
                message = response.comment ?: ProviderError.SERVICE_UNAVAILABLE.userMessage
            )

            response.result == null -> ProviderResult.Failure(
                platform = platform,
                error = ProviderError.MALFORMED_RESPONSE,
                message = ProviderError.MALFORMED_RESPONSE.userMessage
            )

            else -> ProviderResult.Success(
                platform = platform,
                contests = response.result.mapNotNull { it.toContestOrNull() }
            )
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        val error = ProviderErrorMapper.classify(throwable)
        ProviderResult.Failure(platform, error, error.userMessage)
    }

    /**
     * Drops entries missing an id or name rather than substituting placeholder values.
     * A contest with no announced `startTimeSeconds` is kept: the filter layer decides
     * what to do with it.
     */
    private fun CodeforcesContestDto.toContestOrNull(): Contest? {
        val contestId = id ?: return null
        val contestName = name?.takeIf { it.isNotBlank() } ?: return null

        return Contest(
            id = Contest.buildId(Platform.CODEFORCES, contestId.toString()),
            platform = Platform.CODEFORCES,
            platformContestId = contestId.toString(),
            name = contestName,
            phase = ContestPhase.fromCodeforces(phase),
            startTimeSeconds = startTimeSeconds,
            durationSeconds = durationSeconds ?: 0L,
            division = ContestClassifier.classify(contestName),
            url = CodeforcesApi.contestUrl(contestId.toString())
        )
    }
}
