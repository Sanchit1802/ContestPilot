package com.sanchit.contestpilot.domain

import com.sanchit.contestpilot.REFERENCE_EPOCH_SECONDS
import com.sanchit.contestpilot.SEVEN_DAYS_SECONDS
import com.sanchit.contestpilot.domain.logic.ContestFilter
import com.sanchit.contestpilot.domain.model.ContestPhase
import com.sanchit.contestpilot.domain.model.Platform
import com.sanchit.contestpilot.testContest
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContestFilterTest {

    private val now: Instant = Instant.ofEpochSecond(REFERENCE_EPOCH_SECONDS)

    @Test
    fun `a contest starting one second from now is included`() {
        val result = ContestFilter.upcomingWithinWindow(
            contests = listOf(testContest(startTimeSeconds = REFERENCE_EPOCH_SECONDS + 1)),
            referenceTime = now
        )

        assertEquals(1, result.size)
    }

    @Test
    fun `a contest starting exactly now is excluded`() {
        val result = ContestFilter.upcomingWithinWindow(
            contests = listOf(testContest(startTimeSeconds = REFERENCE_EPOCH_SECONDS)),
            referenceTime = now
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `a contest that already started is excluded`() {
        val result = ContestFilter.upcomingWithinWindow(
            contests = listOf(testContest(startTimeSeconds = REFERENCE_EPOCH_SECONDS - 1)),
            referenceTime = now
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `the exact seven day boundary is included`() {
        val result = ContestFilter.upcomingWithinWindow(
            contests = listOf(
                testContest(startTimeSeconds = REFERENCE_EPOCH_SECONDS + SEVEN_DAYS_SECONDS)
            ),
            referenceTime = now
        )

        assertEquals(1, result.size)
    }

    @Test
    fun `one second past the seven day boundary is excluded`() {
        val result = ContestFilter.upcomingWithinWindow(
            contests = listOf(
                testContest(startTimeSeconds = REFERENCE_EPOCH_SECONDS + SEVEN_DAYS_SECONDS + 1)
            ),
            referenceTime = now
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `the window length is configurable`() {
        val contests = listOf(
            testContest(id = "1", startTimeSeconds = REFERENCE_EPOCH_SECONDS + 2 * 86_400)
        )

        assertTrue(
            ContestFilter.upcomingWithinWindow(contests, now, windowDays = 1).isEmpty()
        )
        assertEquals(
            1,
            ContestFilter.upcomingWithinWindow(contests, now, windowDays = 3).size
        )
    }

    @Test
    fun `a contest with no announced start time is excluded`() {
        val result = ContestFilter.upcomingWithinWindow(
            contests = listOf(testContest(startTimeSeconds = null)),
            referenceTime = now
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `contests that are not in the BEFORE phase are excluded`() {
        val contests = listOf(
            testContest(
                id = "1",
                phase = ContestPhase.CODING,
                startTimeSeconds = REFERENCE_EPOCH_SECONDS + 100
            ),
            testContest(
                id = "2",
                phase = ContestPhase.FINISHED,
                startTimeSeconds = REFERENCE_EPOCH_SECONDS + 200
            ),
            testContest(
                id = "3",
                phase = ContestPhase.UNKNOWN,
                startTimeSeconds = REFERENCE_EPOCH_SECONDS + 300
            )
        )

        assertTrue(ContestFilter.upcomingWithinWindow(contests, now).isEmpty())
    }

    @Test
    fun `results are sorted chronologically`() {
        val contests = listOf(
            testContest(id = "c", startTimeSeconds = REFERENCE_EPOCH_SECONDS + 300),
            testContest(id = "a", startTimeSeconds = REFERENCE_EPOCH_SECONDS + 100),
            testContest(id = "b", startTimeSeconds = REFERENCE_EPOCH_SECONDS + 200)
        )

        assertEquals(
            listOf("a", "b", "c"),
            ContestFilter.upcomingWithinWindow(contests, now).map { it.platformContestId }
        )
    }

    @Test
    fun `contests starting at the same instant are ordered deterministically`() {
        val start = REFERENCE_EPOCH_SECONDS + 100
        val contests = listOf(
            testContest(id = "z", platform = Platform.CODECHEF, startTimeSeconds = start),
            testContest(id = "a", platform = Platform.CODEFORCES, startTimeSeconds = start)
        )

        val firstPass = ContestFilter.upcomingWithinWindow(contests, now).map { it.id }
        val secondPass = ContestFilter.upcomingWithinWindow(contests.reversed(), now).map { it.id }

        assertEquals(firstPass, secondPass)
    }

    @Test
    fun `disabled platforms are filtered out`() {
        val contests = listOf(
            testContest(
                id = "1",
                platform = Platform.CODEFORCES,
                startTimeSeconds = REFERENCE_EPOCH_SECONDS + 100
            ),
            testContest(
                id = "2",
                platform = Platform.CODECHEF,
                startTimeSeconds = REFERENCE_EPOCH_SECONDS + 200
            )
        )

        val result = ContestFilter.upcomingWithinWindow(
            contests = contests,
            referenceTime = now,
            enabledPlatforms = setOf(Platform.CODECHEF)
        )

        assertEquals(listOf(Platform.CODECHEF), result.map { it.platform })
    }
}
