package com.sanchit.contestpilot.data.provider

import com.sanchit.contestpilot.data.remote.codechef.CodeChefApi
import com.sanchit.contestpilot.data.remote.codechef.CodeChefContestDto
import com.sanchit.contestpilot.domain.model.Contest
import com.sanchit.contestpilot.domain.model.ContestDivision
import com.sanchit.contestpilot.domain.model.ContestPhase
import com.sanchit.contestpilot.domain.model.Platform
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlinx.coroutines.CancellationException

/**
 * Reads upcoming and running contests from CodeChef's contest-listing endpoint.
 *
 * CodeChef exposes no division concept, so every CodeChef contest is
 * [ContestDivision.OTHER]; ContestPilot does not invent one from the contest name.
 */
class CodeChefContestProvider(
    private val api: CodeChefApi
) : ContestProvider {

    override val platform: Platform = Platform.CODECHEF

    override suspend fun fetchContests(): ProviderResult = try {
        val response = api.getContests()

        if (response.status != null && response.status != "success") {
            ProviderResult.Failure(
                platform = platform,
                error = ProviderError.SERVICE_UNAVAILABLE,
                message = response.message ?: ProviderError.SERVICE_UNAVAILABLE.userMessage
            )
        } else {
            val future = response.futureContests.orEmpty()
                .mapNotNull { it.toContestOrNull(ContestPhase.BEFORE) }
            val present = response.presentContests.orEmpty()
                .mapNotNull { it.toContestOrNull(ContestPhase.CODING) }

            ProviderResult.Success(platform, future + present)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        val error = ProviderErrorMapper.classify(throwable)
        ProviderResult.Failure(platform, error, error.userMessage)
    }

    private fun CodeChefContestDto.toContestOrNull(phase: ContestPhase): Contest? {
        val code = contestCode?.takeIf { it.isNotBlank() } ?: return null
        val name = contestName?.takeIf { it.isNotBlank() } ?: return null
        val start = parseOffsetDateTime(startDateIso) ?: return null

        return Contest(
            id = Contest.buildId(Platform.CODECHEF, code),
            platform = Platform.CODECHEF,
            platformContestId = code,
            name = name,
            phase = phase,
            startTimeSeconds = start.toEpochSecond(),
            durationSeconds = resolveDurationSeconds(start),
            division = ContestDivision.OTHER,
            url = CodeChefApi.contestUrl(code)
        )
    }

    /**
     * Prefers the explicit end timestamp and falls back to the reported minute count;
     * returns 0 when neither is usable rather than guessing a length.
     */
    private fun CodeChefContestDto.resolveDurationSeconds(start: OffsetDateTime): Long {
        parseOffsetDateTime(endDateIso)?.let { end ->
            val seconds = Duration.between(start, end).seconds
            if (seconds > 0) return seconds
        }
        return durationMinutes?.trim()?.toLongOrNull()?.takeIf { it > 0 }?.times(60L) ?: 0L
    }

    private fun parseOffsetDateTime(value: String?): OffsetDateTime? {
        val text = value?.takeIf { it.isNotBlank() } ?: return null
        return try {
            OffsetDateTime.parse(text)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
