package com.sanchit.contestpilot.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sanchit.contestpilot.data.local.entity.SyncStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncStateDao {

    @Query("SELECT * FROM sync_states")
    fun observeAll(): Flow<List<SyncStateEntity>>

    @Query("SELECT * FROM sync_states WHERE source = :source")
    suspend fun find(source: String): SyncStateEntity?

    @Upsert
    suspend fun upsert(state: SyncStateEntity)
}
