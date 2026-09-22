package com.sanchit.contestpilot.domain.logic

import com.sanchit.contestpilot.domain.model.AppSettings
import com.sanchit.contestpilot.domain.model.Contest
import com.sanchit.contestpilot.domain.model.Platform
import com.sanchit.contestpilot.domain.model.RegistrationStatus
import java.time.Instant

/**
 * Why a contest is or is not a candidate for cloud auto-registration.
 *
 * The Android app never performs registration itself; it evaluates the same rules the
 * GitHub Actions automation uses so the user can see, before a run happens, what the
 * automation is going to do.
 */
sealed interface EligibilityDecision {
    data object Eligible : EligibilityDecision

    data class NotEligible(val reason: Reason) : EligibilityDecision

    enum class Reason(val displayName: String) {
        AUTO_REGISTRATION_DISABLED("Auto-registration is off"),
        PLATFORM_NOT_AUTOMATED("Registration is not automated for this platform"),
        PLATFORM_DISABLED("Platform disabled in settings"),
        NOT_UPCOMING("Contest is not upcoming"),
        OUTSIDE_WINDOW("Outside the configured contest window"),
        DIVISION_NOT_SELECTED("Division not selected for auto-registration"),
        ALREADY_HANDLED("Already registered")
    }
}

object RegistrationEligibility {

    /** Platforms whose registration ContestPilot's automation can actually drive. */
    val AUTOMATED_PLATFORMS: Set<Platform> = setOf(Platform.CODEFORCES)

    fun evaluate(
        contest: Contest,
        settings: AppSettings,
        currentStatus: RegistrationStatus,
        referenceTime: Instant
    ): EligibilityDecision {
        if (!settings.autoRegistrationEnabled) {
            return EligibilityDecision.NotEligible(
                EligibilityDecision.Reason.AUTO_REGISTRATION_DISABLED
            )
        }
        if (contest.platform !in AUTOMATED_PLATFORMS) {
            return EligibilityDecision.NotEligible(
                EligibilityDecision.Reason.PLATFORM_NOT_AUTOMATED
            )
        }
        if (contest.platform !in settings.enabledPlatforms) {
            return EligibilityDecision.NotEligible(EligibilityDecision.Reason.PLATFORM_DISABLED)
        }
        if (!contest.isUpcoming(referenceTime)) {
            return EligibilityDecision.NotEligible(EligibilityDecision.Reason.NOT_UPCOMING)
        }
        if (!DateTimeUtils.isWithinUpcomingWindow(
                timestampSeconds = contest.startTimeSeconds,
                referenceTime = referenceTime,
                windowDays = settings.contestWindowDays.toLong()
            )
        ) {
            return EligibilityDecision.NotEligible(EligibilityDecision.Reason.OUTSIDE_WINDOW)
        }
        if (contest.division !in settings.autoRegisterDivisions) {
            return EligibilityDecision.NotEligible(
                EligibilityDecision.Reason.DIVISION_NOT_SELECTED
            )
        }
        if (currentStatus == RegistrationStatus.REGISTERED ||
            currentStatus == RegistrationStatus.ALREADY_REGISTERED
        ) {
            return EligibilityDecision.NotEligible(EligibilityDecision.Reason.ALREADY_HANDLED)
        }

        return EligibilityDecision.Eligible
    }
}
