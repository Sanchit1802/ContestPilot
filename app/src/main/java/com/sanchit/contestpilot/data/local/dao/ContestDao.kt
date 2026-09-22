package com.sanchit.contestpilot.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.sanchit.contestpilot.data.local.entity.ContestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContestDao {

    @Query("SELECT * FROM contests ORDER BY startTimeSeconds IS NULL, startTimeSeconds ASC")
    fun observeAll(): Flow<List<ContestEntity>>

    @Upsert
    suspend fun upsertAll(contests: List<ContestEntity>)

    @Query("DELETE FROM contests WHERE platform = :platform AND id NOT IN (:keepIds)")
    suspend fun deleteStaleForPlatform(platform: String, keepIds: List<String>)

    @Query("DELETE FROM contests WHERE platform = :platform")
    suspend fun deleteAllForPlatform(platform: String)

    /**
     * Replaces one platform's cache atomically, so a refresh never leaves the UI showing
     * a half-updated mixture of old and new rows.
     */
    @Transaction
    suspend fun replacePlatformContests(platform: String, contests: List<ContestEntity>) {
        if (contests.isEmpty()) {
            deleteAllForPlatform(platform)
            return
        }
        upsertAll(contests)
        deleteStaleForPlatform(platform, contests.map { it.id })
    }
}
