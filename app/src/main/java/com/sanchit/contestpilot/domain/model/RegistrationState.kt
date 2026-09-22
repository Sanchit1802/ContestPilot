package com.sanchit.contestpilot.domain.model

/**
 * Outcome of the cloud auto-registration attempt for one contest.
 *
 * Codeforces has no registration API (see https://codeforces.com/apiHelp/methods), so
 * these values can only ever come from the GitHub Actions automation that drives a real
 * browser session. When no automation report mentions a contest, its state is [UNKNOWN] —
 * ContestPilot never guesses.
 */
enum class RegistrationStatus(val displayName: String) {
    /** No automation report covers this contest. */
    UNKNOWN("Unknown"),
    /** The platform does not support (or ContestPilot does not automate) registration. */
    NOT_APPLICABLE("Not automated"),
    /** Eligible and queued: the automation has seen it but registration is not open yet. */
    PENDING("Registration pending"),
    /** Registration succeeded in this automation run. */
    REGISTERED("Registered"),
    /** The account was already registered before the run started. */
    ALREADY_REGISTERED("Already registered"),
    /** Excluded by the user's auto-registration rules. */
    SKIPPED("Skipped"),
    /** The automation tried and failed; see the accompanying message. */
    FAILED("Registration failed");

    companion object {
        fun fromStorageValue(value: String?): RegistrationStatus =
            entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}

/**
 * Registration outcome plus the human-readable explanation the automation produced.
 */
data class RegistrationState(
    val contestId: String,
    val status: RegistrationStatus,
    val message: String?,
    val updatedAtEpochSeconds: Long?
)
