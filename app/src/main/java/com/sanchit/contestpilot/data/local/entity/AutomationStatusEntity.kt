package com.sanchit.contestpilot.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sanchit.contestpilot.domain.model.AutomationRunStatus
import com.sanchit.contestpilot.domain.model.AutomationStatus

/**
 * Last known cloud automation state. A single row (`id = 0`) so the UI can observe it
 * without dealing with an empty list.
 */
@Entity(tableName = "automation_status")
data class AutomationStatusEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val runStatus: String,
    val message: String?,
    val generatedAtEpochSeconds: Long?,
    val workflowRunUrl: String?,
    val codeforcesHandle: String?,
    val registrationsAttempted: Int,
    val registrationsSucceeded: Int,
    val registrationsFailed: Int
) {
    fun toDomain(): AutomationStatus = AutomationStatus(
        runStatus = AutomationRunStatus.fromStorageValue(runStatus),
        message = message,
        generatedAtEpochSeconds = generatedAtEpochSeconds,
        workflowRunUrl = workflowRunUrl,
        codeforcesHandle = codeforcesHandle,
        registrationsAttempted = registrationsAttempted,
        registrationsSucceeded = registrationsSucceeded,
        registrationsFailed = registrationsFailed
    )

    companion object {
        const val SINGLETON_ID = 0

        fun fromDomain(status: AutomationStatus) = AutomationStatusEntity(
            runStatus = status.runStatus.name,
            message = status.message,
            generatedAtEpochSeconds = status.generatedAtEpochSeconds,
            workflowRunUrl = status.workflowRunUrl,
            codeforcesHandle = status.codeforcesHandle,
            registrationsAttempted = status.registrationsAttempted,
            registrationsSucceeded = status.registrationsSucceeded,
            registrationsFailed = status.registrationsFailed
        )
    }
}
