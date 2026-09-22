package com.sanchit.contestpilot.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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

@Database(
    entities = [
        ContestEntity::class,
        RegistrationStateEntity::class,
        NotificationScheduleEntity::class,
        SyncStateEntity::class,
        AutomationStatusEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun contestDao(): ContestDao
    abstract fun registrationStateDao(): RegistrationStateDao
    abstract fun notificationScheduleDao(): NotificationScheduleDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun automationStatusDao(): AutomationStatusDao

    companion object {
        private const val DATABASE_NAME = "contestpilot.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME).build()
    }
}
