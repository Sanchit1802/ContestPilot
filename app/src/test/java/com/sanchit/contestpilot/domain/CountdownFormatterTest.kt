package com.sanchit.contestpilot.domain

import com.sanchit.contestpilot.REFERENCE_EPOCH_SECONDS
import com.sanchit.contestpilot.domain.logic.CountdownFormatter
import com.sanchit.contestpilot.domain.model.ContestPhase
import com.sanchit.contestpilot.testContest
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountdownFormatterTest {

    private val now: Instant = Instant.ofEpochSecond(REFERENCE_EPOCH_SECONDS)

    private fun inSeconds(seconds: Long) = REFERENCE_EPOCH_SECONDS + seconds

    @Test
    fun `more than a day away shows days hours and minutes`() {
        assertEquals(
            "Starts in 2d 4h 12m",
            CountdownFormatter.format(
                inSeconds(2 * 86_400 + 4 * 3_600 + 12 * 60),
                now
            )
        )
    }

    @Test
    fun `less than a day away shows hours and minutes`() {
        assertEquals(
            "Starts in 8h 32m",
            CountdownFormatter.format(inSeconds(8 * 3_600 + 32 * 60), now)
        )
    }

    @Test
    fun `less than an hour away shows minutes only`() {
        assertEquals("Starts in 42m", CountdownFormatter.format(inSeconds(42 * 60), now))
    }

    @Test
    fun `less than a minute away shows starting soon`() {
        assertEquals("Starting soon", CountdownFormatter.format(inSeconds(59), now))
    }

    @Test
    fun `the exact start instant shows starting soon`() {
        assertEquals("Starting soon", CountdownFormatter.format(inSeconds(0), now))
    }

    @Test
    fun `a past start shows in progress`() {
        assertEquals("In progress", CountdownFormatter.format(inSeconds(-1), now))
        assertEquals("In progress", CountdownFormatter.format(inSeconds(-5 * 60), now))
    }

    @Test
    fun `an unknown start time is reported rather than guessed`() {
        assertEquals("Start time unknown", CountdownFormatter.format(null, now))
    }

    @Test
    fun `formatting is pure and repeatable for the same reference instant`() {
        val target = inSeconds(8 * 3_600 + 32 * 60)

        assertEquals(
            CountdownFormatter.format(target, now),
            CountdownFormatter.format(target, now)
        )
    }

    @Test
    fun `a running contest is shown as in progress and a past one as finished`() {
        val contest = testContest(
            phase = ContestPhase.CODING,
            startTimeSeconds = inSeconds(-600),
            durationSeconds = 7_200
        )

        assertEquals("In progress", CountdownFormatter.formatForContest(contest, now))
        assertEquals(
            "Finished",
            CountdownFormatter.formatForContest(
                contest,
                Instant.ofEpochSecond(inSeconds(7_200))
            )
        )
    }

    @Test
    fun `the ticker stops when nothing is counting down`() {
        assertNull(CountdownFormatter.tickIntervalMillis(emptyList(), now))
        assertNull(
            CountdownFormatter.tickIntervalMillis(
                listOf(testContest(startTimeSeconds = inSeconds(-10))),
                now
            )
        )
    }

    @Test
    fun `the ticker slows down when the nearest contest is far away`() {
        assertEquals(
            CountdownFormatter.COARSE_TICK_MILLIS,
            CountdownFormatter.tickIntervalMillis(
                listOf(testContest(startTimeSeconds = inSeconds(3_600))),
                now
            )
        )
    }

    @Test
    fun `the ticker speeds up as a contest becomes imminent`() {
        assertEquals(
            CountdownFormatter.FINE_TICK_MILLIS,
            CountdownFormatter.tickIntervalMillis(
                listOf(
                    testContest(id = "far", startTimeSeconds = inSeconds(86_400)),
                    testContest(id = "near", startTimeSeconds = inSeconds(30))
                ),
                now
            )
        )
    }
}
