package com.sanchit.contestpilot.ui.contests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.sanchit.contestpilot.data.repository.AutomationStatusRepository
import com.sanchit.contestpilot.data.repository.AutomationSyncResult
import com.sanchit.contestpilot.data.repository.ContestRepository
import com.sanchit.contestpilot.data.repository.PlatformRefreshResult
import com.sanchit.contestpilot.data.settings.SettingsRepository
import com.sanchit.contestpilot.di.ServiceLocator
import com.sanchit.contestpilot.domain.logic.ContestFilter
import com.sanchit.contestpilot.domain.logic.CountdownFormatter
import com.sanchit.contestpilot.domain.logic.RegistrationEligibility
import com.sanchit.contestpilot.domain.logic.TimeProvider
import com.sanchit.contestpilot.domain.model.AppSettings
import com.sanchit.contestpilot.domain.model.AutomationStatus
import com.sanchit.contestpilot.domain.model.Contest
import com.sanchit.contestpilot.domain.model.RegistrationState
import com.sanchit.contestpilot.domain.model.RegistrationStatus
import com.sanchit.contestpilot.domain.model.SyncSource
import com.sanchit.contestpilot.domain.model.SyncState
import com.sanchit.contestpilot.notification.ContestAlarmScheduler
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the contest list screen.
 *
 * All filtering, eligibility and scheduling decisions happen here (or in `domain.logic`),
 * never inside a composable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContestsViewModel(
    private val contestRepository: ContestRepository,
    private val automationStatusRepository: AutomationStatusRepository,
    private val settingsRepository: SettingsRepository,
    private val alarmScheduler: ContestAlarmScheduler,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val initialLoading = MutableStateFlow(true)
    private val problems = MutableStateFlow<List<SourceProblem>>(emptyList())
    private val alarmWarning = MutableStateFlow<String?>(null)
    private val notificationPermissionGranted = MutableStateFlow(true)

    private val contestsFlow = contestRepository.observeContests()

    /**
     * Shared clock for every countdown on screen.
     *
     * One coroutine serves the whole list, its cadence follows the nearest contest, and it
     * stops entirely when nothing is counting down. Because the state flow is shared with
     * [SharingStarted.WhileSubscribed], it also stops while the screen is not collecting.
     */
    private val tickerFlow = contestsFlow.flatMapLatest { contests ->
        flow {
            while (true) {
                val now = timeProvider.now()
                emit(now)
                val interval = CountdownFormatter.tickIntervalMillis(contests, now) ?: break
                delay(interval)
            }
        }
    }

    val uiState: StateFlow<ContestsUiState> = combine(
        listOf(
            contestsFlow,
            settingsRepository.settings,
            contestRepository.observeRegistrationStates(),
            contestRepository.observeSyncStates(),
            automationStatusRepository.observeStatus(),
            tickerFlow,
            refreshing,
            initialLoading,
            problems,
            alarmWarning,
            notificationPermissionGranted
        )
    ) { values -> buildState(values) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ContestsUiState(now = timeProvider.now())
        )

    init {
        refresh()
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildState(values: Array<Any?>): ContestsUiState {
        val contests = values[0] as List<Contest>
        val settings = values[1] as AppSettings
        val registrations = values[2] as Map<String, RegistrationState>
        val syncStates = values[3] as Map<SyncSource, SyncState>
        val automationStatus = values[4] as AutomationStatus
        val now = values[5] as Instant
        val isRefreshing = values[6] as Boolean
        val isInitialLoading = values[7] as Boolean
        val currentProblems = values[8] as List<SourceProblem>
        val warning = values[9] as String?
        val permissionGranted = values[10] as Boolean

        val visible = ContestFilter.upcomingWithinWindow(
            contests = contests,
            referenceTime = now,
            windowDays = settings.contestWindowDays.toLong(),
            enabledPlatforms = settings.enabledPlatforms
        )

        val items = visible.map { contest ->
            val state = registrations[contest.id]
            val status = state?.status ?: defaultStatusFor(contest)

            ContestListItem(
                contest = contest,
                registrationStatus = status,
                registrationMessage = state?.message,
                eligibility = RegistrationEligibility.evaluate(
                    contest = contest,
                    settings = settings,
                    currentStatus = status,
                    referenceTime = now
                )
            )
        }

        return ContestsUiState(
            isInitialLoading = isInitialLoading && contests.isEmpty(),
            isRefreshing = isRefreshing,
            contests = items,
            problems = currentProblems,
            settings = settings,
            automationStatus = automationStatus,
            syncStates = syncStates,
            now = now,
            exactAlarmsAllowed = alarmScheduler.canScheduleExactAlarms(),
            notificationPermissionGranted = permissionGranted,
            alarmWarning = warning
        )
    }

    private fun defaultStatusFor(contest: Contest): RegistrationStatus =
        if (contest.platform in RegistrationEligibility.AUTOMATED_PLATFORMS) {
            RegistrationStatus.UNKNOWN
        } else {
            RegistrationStatus.NOT_APPLICABLE
        }

    fun refresh() {
        if (refreshing.value) return

        viewModelScope.launch {
            refreshing.value = true
            try {
                val report = contestRepository.refresh()
                val collected = report.platformResults.values
                    .filterIsInstance<PlatformRefreshResult.Failed>()
                    .map { SourceProblem(it.platform.displayName, it.message) }
                    .toMutableList()

                when (val automation = automationStatusRepository.refresh()) {
                    is AutomationSyncResult.Failed ->
                        collected += SourceProblem("Automation status", automation.message)

                    AutomationSyncResult.NotConfigured,
                    is AutomationSyncResult.Succeeded -> Unit
                }

                problems.value = collected
                synchronizeReminders()
            } finally {
                refreshing.value = false
                initialLoading.value = false
            }
        }
    }

    /**
     * Re-points AlarmManager at the current contest list. Called after every refresh and
     * whenever settings that affect reminders change.
     */
    fun synchronizeReminders() {
        viewModelScope.launch {
            val contests = contestRepository.observeContests().first()
            val settings = settingsRepository.current()
            val report = alarmScheduler.synchronize(contests, settings)

            alarmWarning.value = when {
                report.failureMessage != null -> report.failureMessage
                !report.exactAlarmsAllowed && settings.notificationsEnabled ->
                    "Exact alarms are not permitted, so reminders may arrive a few " +
                        "minutes late. Allow alarms and reminders in system settings."

                else -> null
            }
        }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        notificationPermissionGranted.value = granted
        if (granted) synchronizeReminders()
    }

    fun dismissProblems() {
        problems.value = emptyList()
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val application = checkNotNull(
                    extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                ) { "ContestsViewModel requires an Application context" }

                val locator = ServiceLocator.from(application)
                return ContestsViewModel(
                    contestRepository = locator.contestRepository,
                    automationStatusRepository = locator.automationStatusRepository,
                    settingsRepository = locator.settingsRepository,
                    alarmScheduler = locator.alarmScheduler,
                    timeProvider = locator.timeProvider
                ) as T
            }
        }
    }
}
