package com.sanchit.contestpilot.domain

import com.sanchit.contestpilot.REFERENCE_EPOCH_SECONDS
import com.sanchit.contestpilot.domain.logic.NotificationScheduleCalculator
import com.sanchit.contestpilot.domain.model.AppSettings
import com.sanchit.contestpilot.domain.model.ContestPhase
import com.sanchit.contestpilot.domain.model.Platform
import com.sanchit.contestpilot.testContest
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationScheduleCalculatorTest {

    private val now: Instant = Instant.ofEpochSecond(REFERENCE_EPOCH_SECONDS)
    private val settings = AppSettings()

    private fun inSeconds(seconds: Long) = REFERENCE_EPOCH_SECONDS + seconds

    @Test
    fun `a reminder fires exactly the configured lead time before the start`() {
        val start = inSeconds(3_600)
        val plan = NotificationScheduleCalculator.plan(
            contests = listOf(testContest(startTimeSeconds = start)),
            settings = settings,
            referenceTime = now
        )

        assertEquals(1, plan.size)
        assertEquals((start - 10 * 60) * 1_000L, plan.single().triggerAtEpochMillis)
        assertEquals(start * 1_000L, plan.single().contestStartEpochMillis)
    }

    @Test
    fun `changing the lead time moves the reminder`() {
        val start = inSeconds(3_600)
        val plan = NotificationScheduleCalculator.plan(
            contests = listOf(testContest(startTimeSeconds = start)),
            settings = settings.copy(notificationLeadMinutes = 30),
            referenceTime = now
        )

        assertEquals((start - 30 * 60) * 1_000L, plan.single().triggerAtEpochMillis)
    }

    @Test
    fun `a contest whose reminder moment has already passed is skipped`() {
        // Starts in five minutes, which is inside the ten-minute lead time.
        val plan = NotificationScheduleCalculator.plan(
            contests = listOf(testContest(startTimeSeconds = inSeconds(5 * 60))),
            settings = settings,
            referenceTime = now
        )

        assertTrue(plan.isEmpty())
    }

    @Test
    fun `a reminder exactly at the reference instant is not scheduled`() {
        val plan = NotificationScheduleCalculator.plan(
            contests = listOf(testContest(startTimeSeconds = inSeconds(10 * 60))),
            settings = settings,
            referenceTime = now
        )

        assertTrue(plan.isEmpty())
    }

    @Test
    fun `nothing is planned when notifications are switched off`() {
        val plan = NotificationScheduleCalculator.plan(
            contests = listOf(testContest(startTimeSeconds = inSeconds(3_600))),
            settings = settings.copy(notificationsEnabled = false),
            referenceTime = now
        )

        assertTrue(plan.isEmpty())
    }

    @Test
    fun `contests on disabled platforms get no reminder`() {
        val plan = NotificationScheduleCalculator.plan(
            contests = listOf(
                testContest(
                    id = "cc",
                    platform = Platform.CODECHEF,
                    startTimeSeconds = inSeconds(3_600)
                )
            ),
            settings = settings.copy(enabledPlatforms = setOf(Platform.CODEFORCES)),
            referenceTime = now
        )

        assertTrue(plan.isEmpty())
    }

    @Test
    fun `contests outside the configured window get no reminder`() {
        val plan = NotificationScheduleCalculator.plan(
            contests = listOf(testContest(startTimeSeconds = inSeconds(3 * 86_400))),
            settings = settings.copy(contestWindowDays = 1),
            referenceTime = now
        )

        assertTrue(plan.isEmpty())
    }

    @Test
    fun `contests that are not upcoming get no reminder`() {
        val plan = NotificationScheduleCalculator.plan(
            contests = listOf(
                testContest(
                    phase = ContestPhase.CODING,
                    startTimeSeconds = inSeconds(3_600)
                ),
                testContest(id = "2", startTimeSeconds = null)
            ),
            settings = settings,
            referenceTime = now
        )

        assertTrue(plan.isEmpty())
    }

    @Test
    fun `reminders are ordered by when they fire`() {
        val plan = NotificationScheduleCalculator.plan(
            contests = listOf(
                testContest(id = "late", startTimeSeconds = inSeconds(7_200)),
                testContest(id = "early", startTimeSeconds = inSeconds(3_600))
            ),
            settings = settings,
            referenceTime = now
        )

        assertEquals(
            listOf("CODEFORCES:early", "CODEFORCES:late"),
            plan.map { it.contestId }
        )
    }

    @Test
    fun `the notification text matches the required wording`() {
        assertEquals(
            "Codeforces Round 999 starts in 10 minutes.",
            NotificationScheduleCalculator.notificationText("Codeforces Round 999", 10)
        )
        assertEquals(
            "Starters 257 starts in 1 minute.",
            NotificationScheduleCalculator.notificationText("Starters 257", 1)
        )
    }

    @Test
    fun `request codes are stable per contest and positive`() {
        val first = NotificationScheduleCalculator.requestCode("CODEFORCES:1234")
        val second = NotificationScheduleCalculator.requestCode("CODEFORCES:1234")

        assertEquals(first, second)
        assertTrue(first >= 0)
        assertNotEquals(first, NotificationScheduleCalculator.requestCode("CODEFORCES:1235"))
    }
}
