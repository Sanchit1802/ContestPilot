package com.sanchit.contestpilot.domain.model

/**
 * Result of the most recent cloud automation run, as published by GitHub Actions.
 */
enum class AutomationRunStatus(val displayName: String) {
    /** The status feed has never been read successfully. */
    NEVER_RUN("Never run"),
    SUCCESS("Succeeded"),
    /** Some contests were handled, at least one was not. */
    PARTIAL("Partially succeeded"),
    FAILED("Failed"),
    /** Codeforces asked for a CAPTCHA or a second factor; automation stopped safely. */
    BLOCKED("Blocked by Codeforces"),
    /** The user has not configured a status feed URL yet. */
    NOT_CONFIGURED("Not configured");

    companion object {
        fun fromStorageValue(value: String?): AutomationRunStatus =
            entries.firstOrNull { it.name == value } ?: NEVER_RUN
    }
}

data class AutomationStatus(
    val runStatus: AutomationRunStatus,
    val message: String?,
    val generatedAtEpochSeconds: Long?,
    val workflowRunUrl: String?,
    val codeforcesHandle: String?,
    val registrationsAttempted: Int,
    val registrationsSucceeded: Int,
    val registrationsFailed: Int
) {
    companion object {
        val notConfigured = AutomationStatus(
            runStatus = AutomationRunStatus.NOT_CONFIGURED,
            message = null,
            generatedAtEpochSeconds = null,
            workflowRunUrl = null,
            codeforcesHandle = null,
            registrationsAttempted = 0,
            registrationsSucceeded = 0,
            registrationsFailed = 0
        )
    }
}
