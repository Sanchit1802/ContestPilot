package com.sanchit.contestpilot.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sanchit.contestpilot.domain.model.Contest
import com.sanchit.contestpilot.domain.model.ContestDivision
import com.sanchit.contestpilot.domain.model.ContestPhase
import com.sanchit.contestpilot.domain.model.Platform

/**
 * Cached contest. Only the fields the app renders or schedules against are stored —
 * raw provider payloads are never persisted.
 */
@Entity(
    tableName = "contests",
    indices = [Index("startTimeSeconds"), Index("platform")]
)
data class ContestEntity(
    @PrimaryKey val id: String,
    val platform: String,
    val platformContestId: String,
    val name: String,
    val phase: String,
    val startTimeSeconds: Long?,
    val durationSeconds: Long,
    val division: String,
    val url: String,
    @ColumnInfo(name = "fetched_at_epoch_seconds") val fetchedAtEpochSeconds: Long
) {
    fun toDomain(): Contest = Contest(
        id = id,
        platform = Platform.fromStorageValue(platform) ?: Platform.CODEFORCES,
        platformContestId = platformContestId,
        name = name,
        phase = ContestPhase.fromCodeforces(phase),
        startTimeSeconds = startTimeSeconds,
        durationSeconds = durationSeconds,
        division = ContestDivision.fromStorageValue(division) ?: ContestDivision.OTHER,
        url = url
    )

    companion object {
        fun fromDomain(contest: Contest, fetchedAtEpochSeconds: Long) = ContestEntity(
            id = contest.id,
            platform = contest.platform.name,
            platformContestId = contest.platformContestId,
            name = contest.name,
            phase = contest.phase.name,
            startTimeSeconds = contest.startTimeSeconds,
            durationSeconds = contest.durationSeconds,
            division = contest.division.name,
            url = contest.url,
            fetchedAtEpochSeconds = fetchedAtEpochSeconds
        )
    }
}
