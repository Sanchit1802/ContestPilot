package com.sanchit.contestpilot.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.sanchit.contestpilot.data.repository.ContestRepository
import com.sanchit.contestpilot.data.settings.SettingsRepository
import com.sanchit.contestpilot.di.ServiceLocator
import com.sanchit.contestpilot.domain.model.AppSettings
import com.sanchit.contestpilot.domain.model.ContestDivision
import com.sanchit.contestpilot.domain.model.Platform
import com.sanchit.contestpilot.notification.ContestAlarmScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val statusUrlError: String? = null
)

/**
 * Edits user preferences and keeps the reminder schedule consistent with them.
 *
 * Changing the notification lead time, the contest window or the enabled platforms all
 * change which alarms should exist, so every such edit triggers a resynchronisation.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val contestRepository: ContestRepository,
    private val alarmScheduler: ContestAlarmScheduler
) : ViewModel() {

    private var statusUrlError: String? = null

    val uiState: StateFlow<SettingsUiState> = settingsRepository.settings
        .map { SettingsUiState(settings = it, statusUrlError = statusUrlError) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SettingsUiState()
        )

    fun setPlatformEnabled(platform: Platform, enabled: Boolean) = edit {
        settingsRepository.setPlatformEnabled(platform, enabled)
        contestRepository.refresh()
    }

    fun setDivisionEnabled(division: ContestDivision, enabled: Boolean) = edit {
        settingsRepository.setDivisionEnabled(division, enabled)
    }

    fun setAutoRegistrationEnabled(enabled: Boolean) = edit {
        settingsRepository.setAutoRegistrationEnabled(enabled)
    }

    fun setNotificationsEnabled(enabled: Boolean) = edit {
        settingsRepository.setNotificationsEnabled(enabled)
        if (!enabled) alarmScheduler.cancelAll()
    }

    fun setNotificationLeadMinutes(minutes: Int) = edit {
        settingsRepository.setNotificationLeadMinutes(minutes)
    }

    fun setContestWindowDays(days: Int) = edit {
        settingsRepository.setContestWindowDays(days)
    }

    fun setCodeforcesHandle(handle: String) = edit(resynchronizeAlarms = false) {
        settingsRepository.setCodeforcesHandle(handle)
    }

    /**
     * Accepts only an HTTPS URL: the status document is fetched without authentication,
     * so plain HTTP would expose it to tampering in transit.
     */
    fun setAutomationStatusUrl(url: String) {
        val trimmed = url.trim()
        statusUrlError = when {
            trimmed.isEmpty() -> null
            !trimmed.startsWith("https://") -> "The status URL must start with https://"
            else -> null
        }

        if (trimmed.isNotEmpty() && statusUrlError != null) return

        edit(resynchronizeAlarms = false) {
            settingsRepository.setAutomationStatusUrl(trimmed)
        }
    }

    private fun edit(
        resynchronizeAlarms: Boolean = true,
        block: suspend () -> Unit
    ) {
        viewModelScope.launch {
            block()
            if (resynchronizeAlarms) {
                val contests = contestRepository.observeContests().first()
                alarmScheduler.synchronize(contests, settingsRepository.current())
            }
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(
                    extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                ) { "SettingsViewModel requires an Application context" }

                val locator = ServiceLocator.from(application)
                return SettingsViewModel(
                    settingsRepository = locator.settingsRepository,
                    contestRepository = locator.contestRepository,
                    alarmScheduler = locator.alarmScheduler
                ) as T
            }
        }
    }
}
