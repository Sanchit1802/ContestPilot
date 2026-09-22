package com.sanchit.contestpilot.data.repository

import com.sanchit.contestpilot.data.local.dao.AutomationStatusDao
import com.sanchit.contestpilot.data.local.dao.RegistrationStateDao
import com.sanchit.contestpilot.data.local.dao.SyncStateDao
import com.sanchit.contestpilot.data.local.entity.AutomationStatusEntity
import com.sanchit.contestpilot.data.local.entity.RegistrationStateEntity
import com.sanchit.contestpilot.data.local.entity.SyncStateEntity
import com.sanchit.contestpilot.data.provider.ProviderErrorMapper
import com.sanchit.contestpilot.data.remote.status.AutomationStatusApi
import com.sanchit.contestpilot.data.remote.status.AutomationStatusDto
import com.sanchit.contestpilot.domain.model.SettingsProvider
import com.sanchit.contestpilot.domain.logic.TimeProvider
import com.sanchit.contestpilot.domain.model.AutomationRunStatus
import com.sanchit.contestpilot.domain.model.AutomationStatus
import com.sanchit.contestpilot.domain.model.Contest
import com.sanchit.contestpilot.domain.model.Platform
import com.sanchit.contestpilot.domain.model.RegistrationStatus
import com.sanchit.contestpilot.domain.model.SyncOutcome
import com.sanchit.contestpilot.domain.model.SyncSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Outcome of reading the cloud automation status feed.
 */
sealed interface AutomationSyncResult {
    data object NotConfigured : AutomationSyncResult
    data class Succeeded(val registrationsReported: Int) : AutomationSyncResult
    data class Failed(val message: String) : AutomationSyncResult
}

/**
 * Mirrors the GitHub Actions automation report into the local database.
 *
 * ContestPilot is a read-only consumer of that document: the app never registers for a
 * contest itself and never holds a credential that could.
 */
class AutomationStatusRepository(
    private val api: AutomationStatusApi,
    private val automationStatusDao: AutomationStatusDao,
    private val registrationStateDao: RegistrationStateDao,
    private val syncStateDao: SyncStateDao,
    private val settingsProvider: SettingsProvider,
    private val timeProvider: TimeProvider = TimeProvider.system
) {

    companion object {
        /** Schema the app understands; a newer document is reported rather than guessed at. */
        const val SUPPORTED_SCHEMA_VERSION = 1
    }

    fun observeStatus(): Flow<AutomationStatus> =
        automationStatusDao.observe().map { it?.toDomain() ?: AutomationStatus.notConfigured }

    suspend fun refresh(): AutomationSyncResult {
        val settings = settingsProvider.current()
        val nowSeconds = timeProvider.now().epochSecond

        if (!settings.isAutomationStatusConfigured) {
            automationStatusDao.upsert(
                AutomationStatusEntity.fromDomain(AutomationStatus.notConfigured)
            )
            recordSync(SyncOutcome.DISABLED, nowSeconds, null)
            return AutomationSyncResult.NotConfigured
        }

        return try {
            val dto = api.getStatus(settings.automationStatusUrl)

            if (dto.schemaVersion != null && dto.schemaVersion > SUPPORTED_SCHEMA_VERSION) {
                val message =
                    "The automation report uses a newer format (v${dto.schemaVersion}). " +
                        "Update ContestPilot to read it."
                storeStatus(dto, AutomationRunStatus.FAILED, message, nowSeconds)
                recordSync(SyncOutcome.FAILED, nowSeconds, message)
                return AutomationSyncResult.Failed(message)
            }

            val runStatus = AutomationRunStatus.fromStorageValue(dto.run?.status)
            storeStatus(dto, runStatus, dto.run?.message, nowSeconds)
            storeRegistrations(dto)
            recordSync(SyncOutcome.SUCCESS, nowSeconds, dto.run?.message)

            AutomationSyncResult.Succeeded(dto.registrations.orEmpty().size)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            val message = ProviderErrorMapper.classify(throwable).userMessage
            recordSync(SyncOutcome.FAILED, nowSeconds, message)
            AutomationSyncResult.Failed(message)
        }
    }

    private suspend fun storeStatus(
        dto: AutomationStatusDto,
        runStatus: AutomationRunStatus,
        message: String?,
        nowSeconds: Long
    ) {
        val registrations = dto.registrations.orEmpty()
        val statuses = registrations.map { RegistrationStatus.fromStorageValue(it.status) }

        automationStatusDao.upsert(
            AutomationStatusEntity(
                runStatus = runStatus.name,
                message = message,
                generatedAtEpochSeconds = dto.generatedAtEpochSeconds ?: nowSeconds,
                workflowRunUrl = dto.run?.workflowRunUrl,
                codeforcesHandle = dto.codeforces?.handle,
                registrationsAttempted = registrations.size,
                registrationsSucceeded = statuses.count {
                    it == RegistrationStatus.REGISTERED ||
                        it == RegistrationStatus.ALREADY_REGISTERED
                },
                registrationsFailed = statuses.count { it == RegistrationStatus.FAILED }
            )
        )
    }

    private suspend fun storeRegistrations(dto: AutomationStatusDto) {
        val rows = dto.registrations.orEmpty().mapNotNull { registration ->
            val platform = registration.platform?.let(Platform::fromStorageValue) ?: return@mapNotNull null
            val contestId = registration.contestId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null

            RegistrationStateEntity(
                contestId = Contest.buildId(platform, contestId),
                status = RegistrationStatus.fromStorageValue(registration.status).name,
                message = registration.message,
                updatedAtEpochSeconds = registration.updatedAtEpochSeconds
            )
        }

        if (rows.isNotEmpty()) registrationStateDao.upsertAll(rows)
    }

    private suspend fun recordSync(outcome: SyncOutcome, nowSeconds: Long, message: String?) {
        val previous = syncStateDao.find(SyncSource.AUTOMATION_STATUS.name)
        syncStateDao.upsert(
            SyncStateEntity(
                source = SyncSource.AUTOMATION_STATUS.name,
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
