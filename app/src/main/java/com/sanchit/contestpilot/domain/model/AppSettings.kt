package com.sanchit.contestpilot.domain.model

/**
 * Everything the user can configure. Deliberately contains no credentials: Codeforces
 * sign-in material lives only in GitHub Actions secrets, never on the device.
 */
data class AppSettings(
    val enabledPlatforms: Set<Platform> = setOf(Platform.CODEFORCES, Platform.CODECHEF),
    /** Codeforces divisions eligible for cloud auto-registration. */
    val autoRegisterDivisions: Set<ContestDivision> = setOf(
        ContestDivision.DIV_2,
        ContestDivision.DIV_3,
        ContestDivision.DIV_4,
        ContestDivision.DIV_1_2
    ),
    val autoRegistrationEnabled: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val notificationLeadMinutes: Int = DEFAULT_NOTIFICATION_LEAD_MINUTES,
    val contestWindowDays: Int = DEFAULT_CONTEST_WINDOW_DAYS,
    /**
     * HTTPS URL of the automation status JSON published by the user's GitHub Actions
     * workflow. Empty until the user configures it; the file contains no secrets.
     */
    val automationStatusUrl: String = "",
    /** Codeforces handle, shown for context only. Never a credential. */
    val codeforcesHandle: String = ""
) {
    val isAutomationStatusConfigured: Boolean
        get() = automationStatusUrl.isNotBlank()

    companion object {
        const val DEFAULT_NOTIFICATION_LEAD_MINUTES = 10
        const val DEFAULT_CONTEST_WINDOW_DAYS = 7

        val NOTIFICATION_LEAD_CHOICES = listOf(5, 10, 15, 30, 60)
        val CONTEST_WINDOW_CHOICES = listOf(1, 3, 7, 14, 30)
    }
}
