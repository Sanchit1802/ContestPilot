package com.sanchit.contestpilot.data.repository

import com.sanchit.contestpilot.data.local.dao.ContestDao
import com.sanchit.contestpilot.data.local.dao.RegistrationStateDao
import com.sanchit.contestpilot.data.local.dao.SyncStateDao
import com.sanchit.contestpilot.data.local.entity.ContestEntity
import com.sanchit.contestpilot.data.local.entity.SyncStateEntity
import com.sanchit.contestpilot.data.provider.ContestProvider
import com.sanchit.contestpilot.data.provider.ProviderResult
import com.sanchit.contestpilot.domain.model.SettingsProvider
import com.sanchit.contestpilot.domain.logic.TimeProvider
import com.sanchit.contestpilot.domain.model.Contest
import com.sanchit.contestpilot.domain.model.Platform
import com.sanchit.contestpilot.domain.model.RegistrationState
import com.sanchit.contestpilot.domain.model.SyncOutcome
import com.sanchit.contestpilot.domain.model.SyncSource
import com.sanchit.contestpilot.domain.model.SyncState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Per-platform result of one refresh, reported to the UI so partial failures stay visible.
 */
data class RefreshReport(
    val platformResults: Map<Platform, PlatformRefreshResult>
) {
    val failures: List<PlatformRefreshResult.Failed>
        get() = platformResults.values.filterIsInstance<PlatformRefreshResult.Failed>()

    val anySucceeded: Boolean
        get() = platformResults.values.any { it is PlatformRefreshResult.Succeeded }
}

sealed interface PlatformRefreshResult {
    data class Succeeded(val contestCount: Int) : PlatformRefreshResult
    data class Failed(val platform: Platform, val message: String) : PlatformRefreshResult
    data object Disabled : PlatformRefreshResult
}

/**
 * Single source of truth for contest data.
 *
 * Providers are queried concurrently and each result is written to Room independently, so
 * an outage on one platform leaves the other platform's cache — and the UI — intact.
 */
class ContestRepository(
    private val providers: List<ContestProvider>,
    private val contestDao: ContestDao,
    private val registrationStateDao: RegistrationStateDao,
    private val syncStateDao: SyncStateDao,
    private val settingsProvider: SettingsProvider,
    private val timeProvider: TimeProvider = TimeProvider.system
) {

    /** Cached contests joined with whatever the automation last reported about them. */
    fun observeContests(): Flow<List<Contest>> =
        contestDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    fun observeRegistrationStates(): Flow<Map<String, RegistrationState>> =
        registrationStateDao.observeAll().map { states ->
            states.associate { it.contestId to it.toDomain() }
        }

    fun observeSyncStates(): Flow<Map<SyncSource, SyncState>> =
        syncStateDao.observeAll().map { rows ->
            rows.mapNotNull { it.toDomain() }.associateBy { it.source }
        }

    suspend fun refresh(): RefreshReport = coroutineScope {
        val settings = settingsProvider.current()
        val nowSeconds = timeProvider.now().epochSecond

        val results = providers.map { provider ->
            provider.platform to async {
                if (provider.platform !in settings.enabledPlatforms) {
                    null
                } else {
                    provider.fetchContests()
                }
            }
        }

        val report = results.associate { (platform, deferred) ->
            when (val result = deferred.await()) {
                null -> {
                    // A disabled platform's cache is cleared so it cannot resurface later.
                    contestDao.deleteAllForPlatform(platform.name)
                    recordSync(platform.syncSource, SyncOutcome.DISABLED, nowSeconds, null)
                    platform to PlatformRefreshResult.Disabled
                }

                is ProviderResult.Success -> {
                    contestDao.replacePlatformContests(
                        platform = platform.name,
                        contests = result.contests.map { ContestEntity.fromDomain(it, nowSeconds) }
                    )
                    recordSync(platform.syncSource, SyncOutcome.SUCCESS, nowSeconds, null)
                    platform to PlatformRefreshResult.Succeeded(result.contests.size)
                }

                is ProviderResult.Failure -> {
                    recordSync(
                        platform.syncSource,
                        SyncOutcome.FAILED,
                        nowSeconds,
                        result.message
                    )
                    platform to PlatformRefreshResult.Failed(platform, result.message)
                }
            }
        }

        registrationStateDao.deleteOrphans()
        RefreshReport(report)
    }

    private suspend fun recordSync(
        source: SyncSource,
        outcome: SyncOutcome,
        nowSeconds: Long,
        message: String?
    ) {
        val previous = syncStateDao.find(source.name)
        syncStateDao.upsert(
            SyncStateEntity(
                source = source.name,
                outcome = outcome.name,
                lastSuccessEpochSeconds = if (outcome == SyncOutcome.SUCCESS) {
                    nowSeconds
                } else {
                    previous?.lastSuccessEpochSeconds
                },
                lastAttemptEpochSeconds = nowSeconds,
                message = message
            )
        )
    }
}
