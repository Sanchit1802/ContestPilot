package com.sanchit.contestpilot.domain

import com.sanchit.contestpilot.REFERENCE_EPOCH_SECONDS
import com.sanchit.contestpilot.SEVEN_DAYS_SECONDS
import com.sanchit.contestpilot.domain.logic.EligibilityDecision
import com.sanchit.contestpilot.domain.logic.RegistrationEligibility
import com.sanchit.contestpilot.domain.model.AppSettings
import com.sanchit.contestpilot.domain.model.ContestDivision
import com.sanchit.contestpilot.domain.model.ContestPhase
import com.sanchit.contestpilot.domain.model.Platform
import com.sanchit.contestpilot.domain.model.RegistrationStatus
import com.sanchit.contestpilot.testContest
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class RegistrationEligibilityTest {

    private val now: Instant = Instant.ofEpochSecond(REFERENCE_EPOCH_SECONDS)

    private val settings = AppSettings(
        autoRegistrationEnabled = true,
        autoRegisterDivisions = setOf(ContestDivision.DIV_2, ContestDivision.DIV_3)
    )

    private fun evaluate(
        contest: com.sanchit.contestpilot.domain.model.Contest,
        settings: AppSettings = this.settings,
        status: RegistrationStatus = RegistrationStatus.UNKNOWN
    ) = RegistrationEligibility.evaluate(contest, settings, status, now)

    private fun reasonOf(decision: EligibilityDecision) =
        (decision as EligibilityDecision.NotEligible).reason

    private val div2Contest = testContest(
        startTimeSeconds = REFERENCE_EPOCH_SECONDS + 86_400,
        division = ContestDivision.DIV_2
    )

    @Test
    fun `an upcoming contest in a selected division is eligible`() {
        assertEquals(EligibilityDecision.Eligible, evaluate(div2Contest))
    }

    @Test
    fun `nothing is eligible while auto-registration is off`() {
        assertEquals(
            EligibilityDecision.Reason.AUTO_REGISTRATION_DISABLED,
            reasonOf(evaluate(div2Contest, settings.copy(autoRegistrationEnabled = false)))
        )
    }

    @Test
    fun `CodeChef is reported as not automated rather than silently skipped`() {
        val contest = testContest(
            id = "START257",
            platform = Platform.CODECHEF,
            startTimeSeconds = REFERENCE_EPOCH_SECONDS + 86_400,
            division = ContestDivision.DIV_2
        )

        assertEquals(
            EligibilityDecision.Reason.PLATFORM_NOT_AUTOMATED,
            reasonOf(evaluate(contest))
        )
    }

    @Test
    fun `a disabled platform is not registered for`() {
        assertEquals(
            EligibilityDecision.Reason.PLATFORM_DISABLED,
            reasonOf(
                evaluate(
                    div2Contest,
                    settings.copy(enabledPlatforms = setOf(Platform.CODECHEF))
                )
            )
        )
    }

    @Test
    fun `a contest that already started is not registered for`() {
        val started = testContest(
            phase = ContestPhase.CODING,
            startTimeSeconds = REFERENCE_EPOCH_SECONDS + 86_400,
            division = ContestDivision.DIV_2
        )

        assertEquals(EligibilityDecision.Reason.NOT_UPCOMING, reasonOf(evaluate(started)))
    }

    @Test
    fun `a contest beyond the configured window is not registered for`() {
        val distant = testContest(
            startTimeSeconds = REFERENCE_EPOCH_SECONDS + SEVEN_DAYS_SECONDS + 1,
            division = ContestDivision.DIV_2
        )

        assertEquals(EligibilityDecision.Reason.OUTSIDE_WINDOW, reasonOf(evaluate(distant)))
    }

    @Test
    fun `a division the user did not select is not registered for`() {
        val div1 = testContest(
            startTimeSeconds = REFERENCE_EPOCH_SECONDS + 86_400,
            division = ContestDivision.DIV_1
        )

        assertEquals(
            EligibilityDecision.Reason.DIVISION_NOT_SELECTED,
            reasonOf(evaluate(div1))
        )
    }

    @Test
    fun `an unclassified contest is not registered for`() {
        val other = testContest(
            startTimeSeconds = REFERENCE_EPOCH_SECONDS + 86_400,
            division = ContestDivision.OTHER
        )

        assertEquals(
            EligibilityDecision.Reason.DIVISION_NOT_SELECTED,
            reasonOf(evaluate(other))
        )
    }

    @Test
    fun `an already registered contest is never attempted again`() {
        listOf(RegistrationStatus.REGISTERED, RegistrationStatus.ALREADY_REGISTERED)
            .forEach { status ->
                assertEquals(
                    EligibilityDecision.Reason.ALREADY_HANDLED,
                    reasonOf(evaluate(div2Contest, status = status))
                )
            }
    }

    @Test
    fun `a previous failure does not block a later retry`() {
        assertEquals(
            EligibilityDecision.Eligible,
            evaluate(div2Contest, status = RegistrationStatus.FAILED)
        )
    }
}
