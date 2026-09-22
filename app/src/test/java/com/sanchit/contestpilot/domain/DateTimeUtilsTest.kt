package com.sanchit.contestpilot.domain

import com.sanchit.contestpilot.domain.logic.DateTimeUtils
import java.time.Instant
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DateTimeUtilsTest {

    /** 23 Sep 2026, 20:00 IST — a typical CodeChef Starters slot. */
    private val startersStart = OffsetDateTime.parse("2026-09-23T20:00:00+05:30").toEpochSecond()

    @Test
    fun `timestamps are rendered in Indian Standard Time`() {
        assertEquals("23 Sep 2026", DateTimeUtils.formatIndianDate(startersStart))
        assertEquals("08:00 PM", DateTimeUtils.formatIndianClockTime(startersStart))
    }

    @Test
    fun `a UTC instant is converted rather than displayed raw`() {
        // 14:30 UTC is 20:00 IST on the same day.
        val utcInstant = Instant.parse("2026-09-23T14:30:00Z").epochSecond

        assertEquals("08:00 PM", DateTimeUtils.formatIndianClockTime(utcInstant))
    }

    @Test
    fun `missing timestamps produce an explicit placeholder`() {
        assertEquals("Date unavailable", DateTimeUtils.formatIndianDate(null))
        assertEquals("Time unavailable", DateTimeUtils.formatIndianClockTime(null))
        assertEquals("Never", DateTimeUtils.formatShortIndianTime(null))
    }

    @Test
    fun `durations are rendered compactly`() {
        assertEquals("2h", DateTimeUtils.formatDuration(7_200))
        assertEquals("2h 30m", DateTimeUtils.formatDuration(9_000))
        assertEquals("45m", DateTimeUtils.formatDuration(2_700))
        assertEquals("Unknown", DateTimeUtils.formatDuration(0))
    }

    @Test
    fun `the upcoming window excludes the reference instant and includes its end`() {
        val now = Instant.ofEpochSecond(1_000)
        val sevenDays = 7L * 24 * 60 * 60

        assertFalse(DateTimeUtils.isWithinUpcomingWindow(1_000, now))
        assertTrue(DateTimeUtils.isWithinUpcomingWindow(1_001, now))
        assertTrue(DateTimeUtils.isWithinUpcomingWindow(1_000 + sevenDays, now))
        assertFalse(DateTimeUtils.isWithinUpcomingWindow(1_000 + sevenDays + 1, now))
        assertFalse(DateTimeUtils.isWithinUpcomingWindow(null, now))
    }
}
