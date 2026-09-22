package com.sanchit.contestpilot.domain.model

/**
 * A competitive-programming site ContestPilot can track.
 *
 * Every [Contest] carries its platform so the UI, the notification scheduler and the
 * cloud automation can all reason about contests without knowing which provider
 * produced them.
 */
enum class Platform(val displayName: String) {
    CODEFORCES("Codeforces"),
    CODECHEF("CodeChef");

    /** Freshness bucket this platform reports into. */
    val syncSource: SyncSource
        get() = when (this) {
            CODEFORCES -> SyncSource.CODEFORCES
            CODECHEF -> SyncSource.CODECHEF
        }

    companion object {
        fun fromStorageValue(value: String): Platform? =
            entries.firstOrNull { it.name == value }
    }
}
