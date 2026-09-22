package com.sanchit.contestpilot.data

import com.sanchit.contestpilot.REFERENCE_EPOCH_SECONDS
import com.sanchit.contestpilot.data.provider.ContestProvider
import com.sanchit.contestpilot.data.provider.ProviderError
import com.sanchit.contestpilot.data.provider.ProviderResult
import com.sanchit.contestpilot.data.repository.ContestRepository
import com.sanchit.contestpilot.data.repository.PlatformRefreshResult
import com.sanchit.contestpilot.domain.logic.TimeProvider
import com.sanchit.contestpilot.domain.model.AppSettings
import com.sanchit.contestpilot.domain.model.Contest
import com.sanchit.contestpilot.domain.model.Platform
import com.sanchit.contestpilot.domain.model.SyncOutcome
import com.sanchit.contestpilot.domain.model.SyncSource
import com.sanchit.contestpilot.testContest
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class StubProvider(
    override val platform: Platform,
    var result: ProviderResult
) : ContestProvider {
    var callCount = 0
    override suspend fun fetchContests(): ProviderResult {
        callCount++
        return result
    }
}

class ContestRepositoryTest {

    private val now = Instant.ofEpochSecond(REFERENCE_EPOCH_SECONDS)

    private val contestDao = FakeContestDao()
    private val registrationDao = FakeRegistrationStateDao(contestDao)
    private val syncDao = FakeSyncStateDao()

    private fun success(platform: Platform, vararg contests: Contest) =
        ProviderResult.Success(platform, contests.toList())

    private fun failure(platform: Platform) = ProviderResult.Failure(
        platform,
        ProviderError.NETWORK_UNAVAILABLE,
        ProviderError.NETWORK_UNAVAILABLE.userMessage
    )

    private fun repository(
        providers: List<ContestProvider>,
        settings: AppSettings = AppSettings()
    ) = ContestRepository(
        providers = providers,
        contestDao = contestDao,
        registrationStateDao = registrationDao,
        syncStateDao = syncDao,
        settingsProvider = FakeSettingsProvider(settings),
        timeProvider = TimeProvider.fixed(now)
    )

    @Test
    fun `a refresh caches contests from every enabled platform`() = runTest {
        val repo = repository(
            listOf(
                StubProvider(
                    Platform.CODEFORCES,
                    success(
                        Platform.CODEFORCES,
                        testContest(id = "1", startTimeSeconds = REFERENCE_EPOCH_SECONDS + 100)
                    )
                ),
                StubProvider(
                    Platform.CODECHEF,
                    success(
                        Platform.CODECHEF,
                        testContest(
                            id = "START1",
                            platform = Platform.CODECHEF,
                            startTimeSeconds = REFERENCE_EPOCH_SECONDS + 200
                        )
                    )
                )
            )
        )

        repo.refresh()

        assertEquals(
            listOf("CODEFORCES:1", "CODECHEF:START1"),
            repo.observeContests().first().map { it.id }
        )
    }

    @Test
    fun `one platform failing leaves the other platform's cache intact`() = runTest {
        val codeforces = StubProvider(
            Platform.CODEFORCES,
            success(
                Platform.CODEFORCES,
                testContest(id = "1", startTimeSeconds = REFERENCE_EPOCH_SECONDS + 100)
            )
        )
        val codechef = StubProvider(
            Platform.CODECHEF,
            success(
                Platform.CODECHEF,
                testContest(
                    id = "START1",
                    platform = Platform.CODECHEF,
                    startTimeSeconds = REFERENCE_EPOCH_SECONDS + 200
                )
            )
        )
        val repo = repository(listOf(codeforces, codechef))
        repo.refresh()

        codechef.result = failure(Platform.CODECHEF)
        val report = repo.refresh()

        assertTrue(report.anySucceeded)
        assertEquals(1, report.failures.size)
        assertEquals(
            listOf("CODEFORCES:1", "CODECHEF:START1"),
            repo.observeContests().first().map { it.id }
        )
    }

    @Test
    fun `a successful refresh removes contests the platform no longer lists`() = runTest {
        val provider = StubProvider(
            Platform.CODEFORCES,
            success(
                Platform.CODEFORCES,
                testContest(id = "1", startTimeSeconds = REFERENCE_EPOCH_SECONDS + 100),
                testContest(id = "2", startTimeSeconds = REFERENCE_EPOCH_SECONDS + 200)
            )
        )
        val repo = repository(listOf(provider))
        repo.refresh()

        provider.result = success(
            Platform.CODEFORCES,
            testContest(id = "2", startTimeSeconds = REFERENCE_EPOCH_SECONDS + 200)
        )
        repo.refresh()

        assertEquals(listOf("CODEFORCES:2"), repo.observeContests().first().map { it.id })
    }

    @Test
    fun `a disabled platform is not queried and its cache is cleared`() = runTest {
        val codechef = StubProvider(
            Platform.CODECHEF,
            success(
                Platform.CODECHEF,
                testContest(
                    id = "START1",
                    platform = Platform.CODECHEF,
                    startTimeSeconds = REFERENCE_EPOCH_SECONDS + 200
                )
            )
        )
        val repo = repository(listOf(codechef))
        repo.refresh()
        assertEquals(1, codechef.callCount)

        val disabled = repository(
            providers = listOf(codechef),
            settings = AppSettings(enabledPlatforms = setOf(Platform.CODEFORCES))
        )
        val report = disabled.refresh()

        assertEquals(1, codechef.callCount)
        assertEquals(
            PlatformRefreshResult.Disabled,
            report.platformResults[Platform.CODECHEF]
        )
        assertTrue(disabled.observeContests().first().isEmpty())
    }

    @Test
    fun `sync state records the last success separately from the last attempt`() = runTest {
        val provider = StubProvider(
            Platform.CODEFORCES,
            success(
                Platform.CODEFORCES,
                testContest(id = "1", startTimeSeconds = REFERENCE_EPOCH_SECONDS + 100)
            )
        )
        val repo = repository(listOf(provider))
        repo.refresh()

        provider.result = failure(Platform.CODEFORCES)
        repo.refresh()

        val state = repo.observeSyncStates().first().getValue(SyncSource.CODEFORCES)
        assertEquals(SyncOutcome.FAILED, state.outcome)
        assertEquals(REFERENCE_EPOCH_SECONDS, state.lastSuccessEpochSeconds)
        assertEquals(REFERENCE_EPOCH_SECONDS, state.lastAttemptEpochSeconds)
        assertEquals(ProviderError.NETWORK_UNAVAILABLE.userMessage, state.message)
    }

    @Test
    fun `registration states for vanished contests are dropped`() = runTest {
        val provider = StubProvider(
            Platform.CODEFORCES,
            success(
                Platform.CODEFORCES,
                testContest(id = "1", startTimeSeconds = REFERENCE_EPOCH_SECONDS + 100)
            )
        )
        val repo = repository(listOf(provider))
        repo.refresh()

        registrationDao.upsertAll(
            listOf(
                com.sanchit.contestpilot.data.local.entity.RegistrationStateEntity(
                    contestId = "CODEFORCES:1",
                    status = "REGISTERED",
                    message = null,
                    updatedAtEpochSeconds = REFERENCE_EPOCH_SECONDS
                ),
                com.sanchit.contestpilot.data.local.entity.RegistrationStateEntity(
                    contestId = "CODEFORCES:999",
                    status = "REGISTERED",
                    message = null,
                    updatedAtEpochSeconds = REFERENCE_EPOCH_SECONDS
                )
            )
        )

        repo.refresh()

        val states = repo.observeRegistrationStates().first()
        assertEquals(setOf("CODEFORCES:1"), states.keys)
        assertNull(states["CODEFORCES:999"])
    }
}
