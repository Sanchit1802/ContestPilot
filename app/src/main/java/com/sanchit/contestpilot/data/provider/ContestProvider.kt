package com.sanchit.contestpilot.data.provider

import com.sanchit.contestpilot.domain.model.Contest
import com.sanchit.contestpilot.domain.model.Platform

/**
 * Outcome of asking one platform for its contests.
 *
 * A provider never throws at the repository; a failed source must not take the other
 * platforms down with it.
 */
sealed interface ProviderResult {
    val platform: Platform

    data class Success(
        override val platform: Platform,
        val contests: List<Contest>
    ) : ProviderResult

    data class Failure(
        override val platform: Platform,
        val error: ProviderError,
        val message: String
    ) : ProviderResult
}

/**
 * Classified failure reasons, so the UI can say something more useful than "error".
 */
enum class ProviderError(val userMessage: String) {
    NETWORK_UNAVAILABLE("No network connection."),
    TIMEOUT("The platform did not respond in time."),
    SERVICE_UNAVAILABLE("The platform is currently unavailable."),
    MALFORMED_RESPONSE("The platform returned data ContestPilot could not read."),
    UNKNOWN("Something went wrong while loading contests.")
}

/**
 * Source of contests for a single platform. The UI depends on this abstraction rather
 * than on Codeforces specifically.
 */
interface ContestProvider {
    val platform: Platform

    suspend fun fetchContests(): ProviderResult
}
