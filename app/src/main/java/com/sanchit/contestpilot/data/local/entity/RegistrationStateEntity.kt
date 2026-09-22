package com.sanchit.contestpilot.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sanchit.contestpilot.domain.model.RegistrationState
import com.sanchit.contestpilot.domain.model.RegistrationStatus

/**
 * Registration outcome for one contest, mirrored from the cloud automation report.
 * Stored separately from [ContestEntity] so a contest refresh never discards it.
 */
@Entity(tableName = "registration_states")
data class RegistrationStateEntity(
    @PrimaryKey val contestId: String,
    val status: String,
    val message: String?,
    val updatedAtEpochSeconds: Long?
) {
    fun toDomain(): RegistrationState = RegistrationState(
        contestId = contestId,
        status = RegistrationStatus.fromStorageValue(status),
        message = message,
        updatedAtEpochSeconds = updatedAtEpochSeconds
    )
}
