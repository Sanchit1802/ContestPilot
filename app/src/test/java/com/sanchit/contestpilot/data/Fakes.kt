package com.sanchit.contestpilot.data

import com.sanchit.contestpilot.data.local.dao.AutomationStatusDao
import com.sanchit.contestpilot.data.local.dao.ContestDao
import com.sanchit.contestpilot.data.local.dao.NotificationScheduleDao
import com.sanchit.contestpilot.data.local.dao.RegistrationStateDao
import com.sanchit.contestpilot.data.local.dao.SyncStateDao
import com.sanchit.contestpilot.data.local.entity.AutomationStatusEntity
import com.sanchit.contestpilot.data.local.entity.ContestEntity
import com.sanchit.contestpilot.data.local.entity.NotificationScheduleEntity
import com.sanchit.contestpilot.data.local.entity.RegistrationStateEntity
import com.sanchit.contestpilot.data.local.entity.SyncStateEntity
import com.sanchit.contestpilot.domain.model.AppSettings
import com.sanchit.contestpilot.domain.model.SettingsProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory stand-ins for the Room DAOs.
 *
 * They reproduce the behaviour the repositories rely on — upsert semantics, the
 * per-platform replace, orphan cleanup — so repository tests exercise real logic without
 * an Android device.
 */
class FakeContestDao : ContestDao {
    val rows = MutableStateFlow<Map<String, ContestEntity>>(emptyMap())

    override fun observeAll(): Flow<List<ContestEntity>> = rows.map { map ->
        map.values.sortedWith(
            compareBy({ it.startTimeSeconds == null }, { it.startTimeSeconds })
        )
    }

    override suspend fun upsertAll(contests: List<ContestEntity>) {
        rows.value = rows.value + contests.associateBy { it.id }
    }

    override suspend fun deleteStaleForPlatform(platform: String, keepIds: List<String>) {
        rows.value = rows.value.filterValues { it.platform != platform || it.id in keepIds }
    }

    override suspend fun deleteAllForPlatform(platform: String) {
        rows.value = rows.value.filterValues { it.platform != platform }
    }
}

class FakeRegistrationStateDao(
    private val contestDao: FakeContestDao
) : RegistrationStateDao {
    val rows = MutableStateFlow<Map<String, RegistrationStateEntity>>(emptyMap())

    override fun observeAll(): Flow<List<RegistrationStateEntity>> =
        rows.map { it.values.toList() }

    override suspend fun upsertAll(states: List<RegistrationStateEntity>) {
        rows.value = rows.value + states.associateBy { it.contestId }
    }

    override suspend fun deleteOrphans() {
        val known = contestDao.rows.value.keys
        rows.value = rows.value.filterKeys { it in known }
    }
}

class FakeSyncStateDao : SyncStateDao {
    val rows = MutableStateFlow<Map<String, SyncStateEntity>>(emptyMap())

    override fun observeAll(): Flow<List<SyncStateEntity>> = rows.map { it.values.toList() }

    override suspend fun find(source: String): SyncStateEntity? = rows.value[source]

    override suspend fun upsert(state: SyncStateEntity) {
        rows.value = rows.value + (state.source to state)
    }
}

class FakeAutomationStatusDao : AutomationStatusDao {
    val row = MutableStateFlow<AutomationStatusEntity?>(null)

    override fun observe(): Flow<AutomationStatusEntity?> = row

    override suspend fun upsert(status: AutomationStatusEntity) {
        row.value = status
    }

}

class FakeNotificationScheduleDao : NotificationScheduleDao {
    val rows = MutableStateFlow<Map<String, NotificationScheduleEntity>>(emptyMap())

    override suspend fun getAll(): List<NotificationScheduleEntity> = rows.value.values.toList()

    override suspend fun getPending(): List<NotificationScheduleEntity> =
        rows.value.values.filterNot { it.delivered }

    override suspend fun upsert(schedule: NotificationScheduleEntity) {
        rows.value = rows.value + (schedule.contestId to schedule)
    }

    override suspend fun markDelivered(contestId: String) {
        rows.value[contestId]?.let { rows.value = rows.value + (contestId to it.copy(delivered = true)) }
    }

    override suspend fun deleteByContestId(contestId: String) {
        rows.value = rows.value - contestId
    }

    override suspend fun deleteAll() {
        rows.value = emptyMap()
    }
}

class FakeSettingsProvider(initial: AppSettings = AppSettings()) : SettingsProvider {
    private val state = MutableStateFlow(initial)

    override val settings: Flow<AppSettings> = state

    override suspend fun current(): AppSettings = state.value

    fun update(settings: AppSettings) {
        state.value = settings
    }
}
