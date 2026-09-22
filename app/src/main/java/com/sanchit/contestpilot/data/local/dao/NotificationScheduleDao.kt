package com.sanchit.contestpilot.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sanchit.contestpilot.data.local.entity.NotificationScheduleEntity

@Dao
interface NotificationScheduleDao {

    @Query("SELECT * FROM notification_schedules")
    suspend fun getAll(): List<NotificationScheduleEntity>

    @Query("SELECT * FROM notification_schedules WHERE delivered = 0")
    suspend fun getPending(): List<NotificationScheduleEntity>

    @Upsert
    suspend fun upsert(schedule: NotificationScheduleEntity)

    @Query("UPDATE notification_schedules SET delivered = 1 WHERE contestId = :contestId")
    suspend fun markDelivered(contestId: String)

    @Query("DELETE FROM notification_schedules WHERE contestId = :contestId")
    suspend fun deleteByContestId(contestId: String)

    @Query("DELETE FROM notification_schedules")
    suspend fun deleteAll()
}
