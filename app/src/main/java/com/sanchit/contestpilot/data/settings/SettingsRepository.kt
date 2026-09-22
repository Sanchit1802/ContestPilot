package com.sanchit.contestpilot.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sanchit.contestpilot.domain.model.AppSettings
import com.sanchit.contestpilot.domain.model.ContestDivision
import com.sanchit.contestpilot.domain.model.Platform
import com.sanchit.contestpilot.domain.model.SettingsProvider
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "contestpilot_settings"
)

/**
 * Persists user preferences with Jetpack DataStore.
 *
 * Nothing stored here is sensitive: the Codeforces handle is public, and the sign-in
 * material that drives auto-registration lives only in GitHub Actions secrets.
 */
class SettingsRepository(context: Context) : SettingsProvider {

    private val dataStore = context.applicationContext.settingsDataStore

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { throwable ->
            // A corrupt or unreadable preferences file must not crash the app.
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { it.toAppSettings() }

    override suspend fun current(): AppSettings = settings.first()

    suspend fun setPlatformEnabled(platform: Platform, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.ENABLED_PLATFORMS] ?: defaultPlatformNames
            prefs[Keys.ENABLED_PLATFORMS] =
                if (enabled) current + platform.name else current - platform.name
        }
    }

    suspend fun setDivisionEnabled(division: ContestDivision, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.AUTO_REGISTER_DIVISIONS] ?: defaultDivisionNames
            prefs[Keys.AUTO_REGISTER_DIVISIONS] =
                if (enabled) current + division.name else current - division.name
        }
    }

    suspend fun setAutoRegistrationEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_REGISTRATION_ENABLED] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setNotificationLeadMinutes(minutes: Int) {
        dataStore.edit { it[Keys.NOTIFICATION_LEAD_MINUTES] = minutes.coerceIn(1, 24 * 60) }
    }

    suspend fun setContestWindowDays(days: Int) {
        dataStore.edit { it[Keys.CONTEST_WINDOW_DAYS] = days.coerceIn(1, 60) }
    }

    suspend fun setAutomationStatusUrl(url: String) {
        dataStore.edit { it[Keys.AUTOMATION_STATUS_URL] = url.trim() }
    }

    suspend fun setCodeforcesHandle(handle: String) {
        dataStore.edit { it[Keys.CODEFORCES_HANDLE] = handle.trim() }
    }

    private fun Preferences.toAppSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            enabledPlatforms = (this[Keys.ENABLED_PLATFORMS] ?: defaultPlatformNames)
                .mapNotNull(Platform::fromStorageValue)
                .toSet(),
            autoRegisterDivisions = (this[Keys.AUTO_REGISTER_DIVISIONS] ?: defaultDivisionNames)
                .mapNotNull(ContestDivision::fromStorageValue)
                .toSet(),
            autoRegistrationEnabled = this[Keys.AUTO_REGISTRATION_ENABLED]
                ?: defaults.autoRegistrationEnabled,
            notificationsEnabled = this[Keys.NOTIFICATIONS_ENABLED]
                ?: defaults.notificationsEnabled,
            notificationLeadMinutes = this[Keys.NOTIFICATION_LEAD_MINUTES]
                ?: defaults.notificationLeadMinutes,
            contestWindowDays = this[Keys.CONTEST_WINDOW_DAYS] ?: defaults.contestWindowDays,
            automationStatusUrl = this[Keys.AUTOMATION_STATUS_URL] ?: defaults.automationStatusUrl,
            codeforcesHandle = this[Keys.CODEFORCES_HANDLE] ?: defaults.codeforcesHandle
        )
    }

    private val defaultPlatformNames: Set<String> =
        AppSettings().enabledPlatforms.map { it.name }.toSet()

    private val defaultDivisionNames: Set<String> =
        AppSettings().autoRegisterDivisions.map { it.name }.toSet()

    private object Keys {
        val ENABLED_PLATFORMS = stringSetPreferencesKey("enabled_platforms")
        val AUTO_REGISTER_DIVISIONS = stringSetPreferencesKey("auto_register_divisions")
        val AUTO_REGISTRATION_ENABLED = booleanPreferencesKey("auto_registration_enabled")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val NOTIFICATION_LEAD_MINUTES = intPreferencesKey("notification_lead_minutes")
        val CONTEST_WINDOW_DAYS = intPreferencesKey("contest_window_days")
        val AUTOMATION_STATUS_URL = stringPreferencesKey("automation_status_url")
        val CODEFORCES_HANDLE = stringPreferencesKey("codeforces_handle")
    }
}
