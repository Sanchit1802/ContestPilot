package com.sanchit.contestpilot.ui.contests

import com.sanchit.contestpilot.domain.logic.EligibilityDecision
import com.sanchit.contestpilot.domain.model.AppSettings
import com.sanchit.contestpilot.domain.model.AutomationStatus
import com.sanchit.contestpilot.domain.model.Contest
import com.sanchit.contestpilot.domain.model.RegistrationStatus
import com.sanchit.contestpilot.domain.model.SyncSource
import com.sanchit.contestpilot.domain.model.SyncState
import java.time.Instant

/**
 * A contest as the list renders it: the contest itself plus everything ContestPilot knows
 * about what the automation did — or will do — with it.
 */
data class ContestListItem(
    val contest: Contest,
    val registrationStatus: RegistrationStatus,
    val registrationMessage: String?,
    val eligibility: EligibilityDecision
) {
    val isAutoRegistrationCandidate: Boolean
        get() = eligibility is EligibilityDecision.Eligible

    /**
     * Why the cloud automation will leave this contest alone, or `null` when it will not.
     * Reasons that are not about this contest — auto-registration being off altogether,
     * or a platform ContestPilot does not automate — are left unexplained per card,
     * because repeating them on every row would be noise.
     */
    val ineligibilityExplanation: String?
        get() = (eligibility as? EligibilityDecision.NotEligible)
            ?.reason
            ?.takeIf { it in EXPLAINED_REASONS }
            ?.displayName

    private companion object {
        val EXPLAINED_REASONS = setOf(
            EligibilityDecision.Reason.DIVISION_NOT_SELECTED,
            EligibilityDecision.Reason.OUTSIDE_WINDOW,
            EligibilityDecision.Reason.ALREADY_HANDLED
        )
    }
}

/**
 * A data source that failed while cached data is still being shown.
 */
data class SourceProblem(
    val sourceName: String,
    val message: String
)

data class ContestsUiState(
    /** True only for the very first load, when there is nothing cached to show. */
    val isInitialLoading: Boolean = true,
    /** True while a refresh runs over content that is already on screen. */
    val isRefreshing: Boolean = false,
    val contests: List<ContestListItem> = emptyList(),
    val problems: List<SourceProblem> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val automationStatus: AutomationStatus = AutomationStatus.notConfigured,
    val syncStates: Map<SyncSource, SyncState> = emptyMap(),
    val now: Instant = Instant.EPOCH,
    val exactAlarmsAllowed: Boolean = true,
    val notificationPermissionGranted: Boolean = true,
    val alarmWarning: String? = null
) {
    val isEmpty: Boolean
        get() = contests.isEmpty()

    /** Shown as a full-screen error only when there is nothing at all to display. */
    val blockingError: String?
        get() = if (isEmpty && problems.isNotEmpty()) problems.first().message else null

    val enabledPlatformCount: Int
        get() = settings.enabledPlatforms.size
}
