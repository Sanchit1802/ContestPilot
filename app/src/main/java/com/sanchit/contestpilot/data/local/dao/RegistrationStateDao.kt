package com.sanchit.contestpilot.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sanchit.contestpilot.data.local.entity.RegistrationStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RegistrationStateDao {

    @Query("SELECT * FROM registration_states")
    fun observeAll(): Flow<List<RegistrationStateEntity>>

    @Upsert
    suspend fun upsertAll(states: List<RegistrationStateEntity>)

    /** Drops rows for contests that are no longer cached, keeping the table bounded. */
    @Query("DELETE FROM registration_states WHERE contestId NOT IN (SELECT id FROM contests)")
    suspend fun deleteOrphans()
}
