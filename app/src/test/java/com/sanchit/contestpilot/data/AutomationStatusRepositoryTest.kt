package com.sanchit.contestpilot.data

import com.sanchit.contestpilot.REFERENCE_EPOCH_SECONDS
import com.sanchit.contestpilot.data.remote.status.AutomationAccountDto
import com.sanchit.contestpilot.data.remote.status.AutomationRegistrationDto
import com.sanchit.contestpilot.data.remote.status.AutomationRunDto
import com.sanchit.contestpilot.data.remote.status.AutomationStatusApi
import com.sanchit.contestpilot.data.remote.status.AutomationStatusDto
import com.sanchit.contestpilot.data.repository.AutomationStatusRepository
import com.sanchit.contestpilot.data.repository.AutomationSyncResult
import com.sanchit.contestpilot.domain.logic.TimeProvider
import com.sanchit.contestpilot.domain.model.AppSettings
import com.sanchit.contestpilot.domain.model.AutomationRunStatus
import com.sanchit.contestpilot.domain.model.RegistrationStatus
import com.sanchit.contestpilot.domain.model.SyncOutcome
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeStatusApi(private val response: () -> AutomationStatusDto) :
    AutomationStatusApi {
    var lastUrl: String? = null
    var callCount = 0

    override suspend fun getStatus(url: String): AutomationStatusDto {
        lastUrl = url
        callCount++
        return response()
    }
}

class AutomationStatusRepositoryTest {

    private val now = Instant.ofEpochSecond(REFERENCE_EPOCH_SECONDS)
    private val statusUrl = "https://raw.githubusercontent.com/me/repo/status/automation-status.json"

    private val contestDao = FakeContestDao()
    private val registrationDao = FakeRegistrationStateDao(contestDao)
    private val automationDao = FakeAutomationStatusDao()
    private val syncDao = FakeSyncStateDao()

    private fun repository(
        api: AutomationStatusApi,
        settings: AppSettings = AppSettings(automationStatusUrl = statusUrl)
    ) = AutomationStatusRepository(
        api = api,
        automationStatusDao = automationDao,
        registrationStateDao = registrationDao,
        syncStateDao = syncDao,
        settingsProvider = FakeSettingsProvider(settings),
        timeProvider = TimeProvider.fixed(now)
    )

    private fun statusDto(
        schemaVersion: Int? = 1,
        runStatus: String? = "SUCCESS",
        registrations: List<AutomationRegistrationDto> = emptyList()
    ) = AutomationStatusDto(
        schemaVersion = schemaVersion,
        generatedAt = "2027-01-15T10:00:00Z",
        generatedAtEpochSeconds = REFERENCE_EPOCH_SECONDS - 60,
        run = AutomationRunDto(runStatus, "All good", "https://github.com/me/repo/actions/runs/1"),
        codeforces = AutomationAccountDto("tourist", "OK"),
        registrations = registrations
    )

    @Test
    fun `nothing is fetched until the user configures a status url`() = runTest {
        val api = FakeStatusApi { statusDto() }
        val repo = repository(api, AppSettings(automationStatusUrl = ""))

        assertEquals(AutomationSyncResult.NotConfigured, repo.refresh())
        assertEquals(0, api.callCount)
        assertEquals(
            AutomationRunStatus.NOT_CONFIGURED,
            repo.observeStatus().first().runStatus
        )
    }

    @Test
    fun `the configured url is requested verbatim`() = runTest {
        val api = FakeStatusApi { statusDto() }
        repository(api).refresh()

        assertEquals(statusUrl, api.lastUrl)
    }

    @Test
    fun `a run report is mirrored into local state`() = runTest {
        val api = FakeStatusApi {
            statusDto(
                registrations = listOf(
                    AutomationRegistrationDto(
                        "CODEFORCES", "1234", "Round 1234", "REGISTERED", null,
                        REFERENCE_EPOCH_SECONDS
                    ),
                    AutomationRegistrationDto(
                        "CODEFORCES", "1235", "Round 1235", "ALREADY_REGISTERED", null,
                        REFERENCE_EPOCH_SECONDS
                    ),
                    AutomationRegistrationDto(
                        "CODEFORCES", "1236", "Round 1236", "FAILED", "Login rejected",
                        REFERENCE_EPOCH_SECONDS
                    )
                )
            )
        }
        val repo = repository(api)

        assertEquals(AutomationSyncResult.Succeeded(3), repo.refresh())

        val status = repo.observeStatus().first()
        assertEquals(AutomationRunStatus.SUCCESS, status.runStatus)
        assertEquals("tourist", status.codeforcesHandle)
        assertEquals(3, status.registrationsAttempted)
        assertEquals(2, status.registrationsSucceeded)
        assertEquals(1, status.registrationsFailed)
        assertEquals(REFERENCE_EPOCH_SECONDS - 60, status.generatedAtEpochSeconds)
    }

    @Test
    fun `registration ids are namespaced so they match cached contests`() = runTest {
        val api = FakeStatusApi {
            statusDto(
                registrations = listOf(
                    AutomationRegistrationDto(
                        "CODEFORCES", "1234", "Round", "REGISTERED", null, REFERENCE_EPOCH_SECONDS
                    )
                )
            )
        }
        repository(api).refresh()

        assertEquals(setOf("CODEFORCES:1234"), registrationDao.rows.value.keys)
        assertEquals(
            RegistrationStatus.REGISTERED,
            registrationDao.rows.value.getValue("CODEFORCES:1234").toDomain().status
        )
    }

    @Test
    fun `registration rows with an unknown platform or missing id are ignored`() = runTest {
        val api = FakeStatusApi {
            statusDto(
                registrations = listOf(
                    AutomationRegistrationDto(
                        "TOPCODER", "1", "x", "REGISTERED", null, REFERENCE_EPOCH_SECONDS
                    ),
                    AutomationRegistrationDto(
                        "CODEFORCES", null, "x", "REGISTERED", null, REFERENCE_EPOCH_SECONDS
                    ),
                    AutomationRegistrationDto(
                        "CODEFORCES", "  ", "x", "REGISTERED", null, REFERENCE_EPOCH_SECONDS
                    )
                )
            )
        }
        repository(api).refresh()

        assertTrue(registrationDao.rows.value.isEmpty())
    }

    @Test
    fun `an unrecognised registration status is stored as unknown rather than guessed`() =
        runTest {
            val api = FakeStatusApi {
                statusDto(
                    registrations = listOf(
                        AutomationRegistrationDto(
                            "CODEFORCES", "1", "x", "SOMETHING_ELSE", null,
                            REFERENCE_EPOCH_SECONDS
                        )
                    )
                )
            }
            repository(api).refresh()

            assertEquals(
                RegistrationStatus.UNKNOWN,
                registrationDao.rows.value.getValue("CODEFORCES:1").toDomain().status
            )
        }

    @Test
    fun `a blocked run is surfaced instead of being reported as a plain failure`() = runTest {
        val api = FakeStatusApi { statusDto(runStatus = "BLOCKED") }
        val repo = repository(api)
        repo.refresh()

        assertEquals(AutomationRunStatus.BLOCKED, repo.observeStatus().first().runStatus)
    }

    @Test
    fun `a newer schema version is reported rather than mis-parsed`() = runTest {
        val api = FakeStatusApi { statusDto(schemaVersion = 99) }
        val repo = repository(api)

        val result = repo.refresh()

        assertTrue(result is AutomationSyncResult.Failed)
        assertTrue((result as AutomationSyncResult.Failed).message.contains("v99"))
        assertTrue(registrationDao.rows.value.isEmpty())
    }

    @Test
    fun `a network failure keeps the previous report and records the failed sync`() = runTest {
        repository(FakeStatusApi { statusDto() }).refresh()

        val failing = repository(FakeStatusApi { throw IOException("offline") })
        val result = failing.refresh()

        assertTrue(result is AutomationSyncResult.Failed)
        assertEquals(AutomationRunStatus.SUCCESS, failing.observeStatus().first().runStatus)
        assertEquals(
            SyncOutcome.FAILED,
            syncDao.rows.value.getValue("AUTOMATION_STATUS").toDomain()?.outcome
        )
    }
}
