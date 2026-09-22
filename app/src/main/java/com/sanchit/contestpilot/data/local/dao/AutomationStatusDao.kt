package com.sanchit.contestpilot.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sanchit.contestpilot.data.local.entity.AutomationStatusEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationStatusDao {

    @Query("SELECT * FROM automation_status WHERE id = 0")
    fun observe(): Flow<AutomationStatusEntity?>

    @Upsert
    suspend fun upsert(status: AutomationStatusEntity)
}
