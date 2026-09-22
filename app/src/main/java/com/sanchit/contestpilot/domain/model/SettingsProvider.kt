package com.sanchit.contestpilot.domain.model

import kotlinx.coroutines.flow.Flow

/**
 * Read-only view of user settings.
 *
 * Repositories depend on this rather than on the DataStore-backed implementation, which
 * keeps them free of Android types and testable with a plain in-memory fake.
 */
interface SettingsProvider {
    val settings: Flow<AppSettings>

    suspend fun current(): AppSettings
}
