package com.sanchit.contestpilot.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanchit.contestpilot.domain.logic.EligibilityDecision
import com.sanchit.contestpilot.domain.model.Contest
import com.sanchit.contestpilot.domain.model.ContestDivision
import com.sanchit.contestpilot.domain.model.ContestPhase
import com.sanchit.contestpilot.domain.model.Platform
import com.sanchit.contestpilot.domain.model.RegistrationStatus
import com.sanchit.contestpilot.ui.contests.ContestCard
import com.sanchit.contestpilot.ui.contests.ContestListItem
import com.sanchit.contestpilot.ui.theme.ContestPilotTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContestCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now: Instant = Instant.ofEpochSecond(1_800_000_000)

    private fun item(
        name: String = "Codeforces Round 999 (Div. 2)",
        startOffsetSeconds: Long = 2 * 86_400 + 4 * 3_600 + 12 * 60,
        status: RegistrationStatus = RegistrationStatus.REGISTERED,
        message: String? = null,
        eligibility: EligibilityDecision = EligibilityDecision.NotEligible(
            EligibilityDecision.Reason.ALREADY_HANDLED
        )
    ) = ContestListItem(
        contest = Contest(
            id = "CODEFORCES:999",
            platform = Platform.CODEFORCES,
            platformContestId = "999",
            name = name,
            phase = ContestPhase.BEFORE,
            startTimeSeconds = now.epochSecond + startOffsetSeconds,
            durationSeconds = 7_200,
            division = ContestDivision.DIV_2,
            url = "https://codeforces.com/contest/999"
        ),
        registrationStatus = status,
        registrationMessage = message,
        eligibility = eligibility
    )

    @Test
    fun theCardShowsTheContestNameCountdownAndIndianStartTime() {
        composeRule.setContent {
            ContestPilotTheme(dynamicColor = false) {
                ContestCard(item = item(), now = now, onOpenContest = {})
            }
        }

        composeRule.onNodeWithText("Codeforces Round 999 (Div. 2)").assertIsDisplayed()
        composeRule.onNodeWithText("Starts in 2d 4h 12m").assertIsDisplayed()
        composeRule.onNodeWithText("TIME (IST)").assertIsDisplayed()
        composeRule.onNodeWithText("2h").assertIsDisplayed()
    }

    @Test
    fun badgesCarryAccessibleDescriptions() {
        composeRule.setContent {
            ContestPilotTheme(dynamicColor = false) {
                ContestCard(item = item(), now = now, onOpenContest = {})
            }
        }

        composeRule.onNodeWithContentDescription("Platform: Codeforces").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Division: Div. 2").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Registration status: Registered")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Time until start: Starts in 2d 4h 12m")
            .assertIsDisplayed()
    }

    @Test
    fun anEligibleContestIsMarkedAsQueued() {
        composeRule.setContent {
            ContestPilotTheme(dynamicColor = false) {
                ContestCard(
                    item = item(
                        status = RegistrationStatus.UNKNOWN,
                        eligibility = EligibilityDecision.Eligible
                    ),
                    now = now,
                    onOpenContest = {}
                )
            }
        }

        composeRule.onNodeWithText("Queued for auto-registration").assertIsDisplayed()
    }

    @Test
    fun aContestTheAutomationWillSkipExplainsWhy() {
        composeRule.setContent {
            ContestPilotTheme(dynamicColor = false) {
                ContestCard(
                    item = item(
                        status = RegistrationStatus.UNKNOWN,
                        eligibility = EligibilityDecision.NotEligible(
                            EligibilityDecision.Reason.DIVISION_NOT_SELECTED
                        )
                    ),
                    now = now,
                    onOpenContest = {}
                )
            }
        }

        composeRule.onNodeWithText("Division not selected for auto-registration")
            .assertIsDisplayed()
    }

    @Test
    fun aFailureMessageFromTheAutomationIsShown() {
        composeRule.setContent {
            ContestPilotTheme(dynamicColor = false) {
                ContestCard(
                    item = item(
                        status = RegistrationStatus.FAILED,
                        message = "Codeforces asked for a CAPTCHA."
                    ),
                    now = now,
                    onOpenContest = {}
                )
            }
        }

        composeRule.onNodeWithText("Codeforces asked for a CAPTCHA.").assertIsDisplayed()
    }

    @Test
    fun aVeryLongContestNameDoesNotBreakTheCard() {
        val longName = "Codeforces Round 999 " + "extremely verbose title ".repeat(20)

        composeRule.setContent {
            ContestPilotTheme(dynamicColor = false) {
                ContestCard(item = item(name = longName), now = now, onOpenContest = {})
            }
        }

        composeRule.onNodeWithText("Starts in 2d 4h 12m").assertIsDisplayed()
    }

    @Test
    fun openingAContestPassesItsUrl() {
        var opened: String? = null

        composeRule.setContent {
            ContestPilotTheme(dynamicColor = false) {
                ContestCard(item = item(), now = now, onOpenContest = { opened = it })
            }
        }

        composeRule.onNodeWithText("Open").performClick()

        assertEquals("https://codeforces.com/contest/999", opened)
    }
}
