package com.sanchit.contestpilot.ui

import com.sanchit.contestpilot.REFERENCE_EPOCH_SECONDS
import com.sanchit.contestpilot.domain.logic.EligibilityDecision
import com.sanchit.contestpilot.domain.model.RegistrationStatus
import com.sanchit.contestpilot.testContest
import com.sanchit.contestpilot.ui.contests.ContestListItem
import com.sanchit.contestpilot.ui.contests.ContestsUiState
import com.sanchit.contestpilot.ui.contests.SourceProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContestListItemTest {

    private fun item(eligibility: EligibilityDecision) = ContestListItem(
        contest = testContest(startTimeSeconds = REFERENCE_EPOCH_SECONDS + 3_600),
        registrationStatus = RegistrationStatus.UNKNOWN,
        registrationMessage = null,
        eligibility = eligibility
    )

    @Test
    fun `an eligible contest is a candidate and needs no explanation`() {
        val listItem = item(EligibilityDecision.Eligible)

        assertTrue(listItem.isAutoRegistrationCandidate)
        assertNull(listItem.ineligibilityExplanation)
    }

    @Test
    fun `a contest-specific reason is explained on the card`() {
        val listItem = item(
            EligibilityDecision.NotEligible(
                EligibilityDecision.Reason.DIVISION_NOT_SELECTED
            )
        )

        assertFalse(listItem.isAutoRegistrationCandidate)
        assertEquals(
            "Division not selected for auto-registration",
            listItem.ineligibilityExplanation
        )
    }

    @Test
    fun `reasons that apply to every contest are not repeated per card`() {
        listOf(
            EligibilityDecision.Reason.AUTO_REGISTRATION_DISABLED,
            EligibilityDecision.Reason.PLATFORM_DISABLED,
            EligibilityDecision.Reason.PLATFORM_NOT_AUTOMATED,
            EligibilityDecision.Reason.NOT_UPCOMING
        ).forEach { reason ->
            assertNull(
                reason.name,
                item(EligibilityDecision.NotEligible(reason)).ineligibilityExplanation
            )
        }
    }
}

class ContestsUiStateTest {

    private val problem = SourceProblem("Codeforces", "The platform is currently unavailable.")

    private val listItem = ContestListItem(
        contest = testContest(startTimeSeconds = REFERENCE_EPOCH_SECONDS + 3_600),
        registrationStatus = RegistrationStatus.UNKNOWN,
        registrationMessage = null,
        eligibility = EligibilityDecision.Eligible
    )

    @Test
    fun `a failure with nothing cached blocks the whole screen`() {
        val state = ContestsUiState(contests = emptyList(), problems = listOf(problem))

        assertTrue(state.isEmpty)
        assertEquals(problem.message, state.blockingError)
    }

    @Test
    fun `a failure with cached contests does not block the screen`() {
        val state = ContestsUiState(contests = listOf(listItem), problems = listOf(problem))

        assertFalse(state.isEmpty)
        assertNull(state.blockingError)
    }

    @Test
    fun `an empty list without a failure is not an error`() {
        val state = ContestsUiState(contests = emptyList())

        assertTrue(state.isEmpty)
        assertNull(state.blockingError)
    }

    @Test
    fun `the enabled platform count comes from settings`() {
        assertEquals(2, ContestsUiState().enabledPlatformCount)
        assertEquals(
            0,
            ContestsUiState(
                settings = ContestsUiState().settings.copy(enabledPlatforms = emptySet())
            ).enabledPlatformCount
        )
    }
}
