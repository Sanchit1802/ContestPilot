package com.sanchit.contestpilot.domain.model

/**
 * A data source whose freshness is tracked and shown to the user.
 */
enum class SyncSource {
    CODEFORCES,
    CODECHEF,
    AUTOMATION_STATUS
}

enum class SyncOutcome {
    NEVER_SYNCED,
    SUCCESS,
    /** The source failed but cached data is still being shown. */
    FAILED,
    /** The user has switched this source off. */
    DISABLED
}

data class SyncState(
    val source: SyncSource,
    val outcome: SyncOutcome,
    val lastSuccessEpochSeconds: Long?,
    val lastAttemptEpochSeconds: Long?,
    val message: String?
)
