package com.sanchit.contestpilot.di

import android.content.Context
import com.sanchit.contestpilot.data.local.AppDatabase
import com.sanchit.contestpilot.data.provider.CodeChefContestProvider
import com.sanchit.contestpilot.data.provider.CodeforcesContestProvider
import com.sanchit.contestpilot.data.provider.ContestProvider
import com.sanchit.contestpilot.data.remote.NetworkModule
import com.sanchit.contestpilot.data.repository.AutomationStatusRepository
import com.sanchit.contestpilot.data.repository.ContestRepository
import com.sanchit.contestpilot.data.settings.SettingsRepository
import com.sanchit.contestpilot.domain.logic.TimeProvider
import com.sanchit.contestpilot.notification.ContestAlarmScheduler

/**
 * Hand-rolled dependency graph.
 *
 * The object graph is small and entirely singleton-shaped, so a service locator keeps the
 * build free of an annotation processor that would earn its keep only in a larger app.
 * Constructors still take their dependencies explicitly, which is what makes the
 * repositories testable with fakes.
 */
class ServiceLocator private constructor(context: Context) {

    private val appContext: Context = context.applicationContext

    val timeProvider: TimeProvider = TimeProvider.system

    private val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    private val providers: List<ContestProvider> by lazy {
        listOf(
            CodeforcesContestProvider(NetworkModule.codeforcesApi),
            CodeChefContestProvider(NetworkModule.codeChefApi)
        )
    }

    val contestRepository: ContestRepository by lazy {
        ContestRepository(
            providers = providers,
            contestDao = database.contestDao(),
            registrationStateDao = database.registrationStateDao(),
            syncStateDao = database.syncStateDao(),
            settingsProvider = settingsRepository,
            timeProvider = timeProvider
        )
    }

    val automationStatusRepository: AutomationStatusRepository by lazy {
        AutomationStatusRepository(
            api = NetworkModule.automationStatusApi,
            automationStatusDao = database.automationStatusDao(),
            registrationStateDao = database.registrationStateDao(),
            syncStateDao = database.syncStateDao(),
            settingsProvider = settingsRepository,
            timeProvider = timeProvider
        )
    }

    val alarmScheduler: ContestAlarmScheduler by lazy {
        ContestAlarmScheduler(
            context = appContext,
            scheduleDao = database.notificationScheduleDao(),
            timeProvider = timeProvider
        )
    }

    companion object {
        @Volatile
        private var instance: ServiceLocator? = null

        fun from(context: Context): ServiceLocator = instance ?: synchronized(this) {
            instance ?: ServiceLocator(context).also { instance = it }
        }
    }
}
