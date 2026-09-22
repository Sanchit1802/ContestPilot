package com.sanchit.contestpilot.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanchit.contestpilot.data.settings.SettingsRepository
import com.sanchit.contestpilot.domain.model.AppSettings
import com.sanchit.contestpilot.domain.model.ContestDivision
import com.sanchit.contestpilot.domain.model.Platform
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {

    private val repository =
        SettingsRepository(ApplicationProvider.getApplicationContext())

    @Test
    fun togglingAPlatformPersists() = runTest {
        repository.setPlatformEnabled(Platform.CODECHEF, false)
        assertFalse(Platform.CODECHEF in repository.current().enabledPlatforms)

        repository.setPlatformEnabled(Platform.CODECHEF, true)
        assertTrue(Platform.CODECHEF in repository.current().enabledPlatforms)
    }

    @Test
    fun togglingADivisionPersists() = runTest {
        repository.setDivisionEnabled(ContestDivision.DIV_1, true)
        assertTrue(ContestDivision.DIV_1 in repository.current().autoRegisterDivisions)

        repository.setDivisionEnabled(ContestDivision.DIV_1, false)
        assertFalse(ContestDivision.DIV_1 in repository.current().autoRegisterDivisions)
    }

    @Test
    fun theNotificationLeadTimeIsClampedToAUsableRange() = runTest {
        repository.setNotificationLeadMinutes(0)
        assertEquals(1, repository.current().notificationLeadMinutes)

        repository.setNotificationLeadMinutes(10_000)
        assertEquals(24 * 60, repository.current().notificationLeadMinutes)

        repository.setNotificationLeadMinutes(AppSettings.DEFAULT_NOTIFICATION_LEAD_MINUTES)
        assertEquals(10, repository.current().notificationLeadMinutes)
    }

    @Test
    fun theContestWindowIsClampedToAUsableRange() = runTest {
        repository.setContestWindowDays(0)
        assertEquals(1, repository.current().contestWindowDays)

        repository.setContestWindowDays(1_000)
        assertEquals(60, repository.current().contestWindowDays)

        repository.setContestWindowDays(AppSettings.DEFAULT_CONTEST_WINDOW_DAYS)
        assertEquals(7, repository.current().contestWindowDays)
    }

    @Test
    fun theStatusUrlIsTrimmedAndReportedAsConfigured() = runTest {
        repository.setAutomationStatusUrl("  https://example.invalid/status.json  ")

        val settings = repository.current()
        assertEquals("https://example.invalid/status.json", settings.automationStatusUrl)
        assertTrue(settings.isAutomationStatusConfigured)

        repository.setAutomationStatusUrl("")
        assertFalse(repository.current().isAutomationStatusConfigured)
    }

    @Test
    fun theCodeforcesHandleIsStoredAsPlainPublicText() = runTest {
        repository.setCodeforcesHandle(" tourist ")

        assertEquals("tourist", repository.current().codeforcesHandle)
    }
}
