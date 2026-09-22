package com.sanchit.contestpilot.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sanchit.contestpilot.domain.model.SyncOutcome
import com.sanchit.contestpilot.domain.model.SyncSource
import com.sanchit.contestpilot.domain.model.SyncState

/** Freshness record for one data source, keyed by [SyncSource] name. */
@Entity(tableName = "sync_states")
data class SyncStateEntity(
    @PrimaryKey val source: String,
    val outcome: String,
    val lastSuccessEpochSeconds: Long?,
    val lastAttemptEpochSeconds: Long?,
    val message: String?
) {
    fun toDomain(): SyncState? {
        val syncSource = SyncSource.entries.firstOrNull { it.name == source } ?: return null
        return SyncState(
            source = syncSource,
            outcome = SyncOutcome.entries.firstOrNull { it.name == outcome }
                ?: SyncOutcome.NEVER_SYNCED,
            lastSuccessEpochSeconds = lastSuccessEpochSeconds,
            lastAttemptEpochSeconds = lastAttemptEpochSeconds,
            message = message
        )
    }
}
