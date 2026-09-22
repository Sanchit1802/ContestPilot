package com.sanchit.contestpilot.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.sanchit.contestpilot.domain.model.ContestDivision
import com.sanchit.contestpilot.domain.model.Platform
import com.sanchit.contestpilot.domain.model.RegistrationStatus

// Seed scheme, used when the device does not provide a dynamic palette.
val Blue80 = Color(0xFFAAC7FF)
val BlueGrey80 = Color(0xFFBFC6DC)
val Teal80 = Color(0xFF7FD8C8)

val Blue40 = Color(0xFF2B5DA8)
val BlueGrey40 = Color(0xFF565E71)
val Teal40 = Color(0xFF00695C)

/**
 * A badge colour pair.
 */
data class BadgeColors(val container: Color, val onContainer: Color)

/**
 * Semantic colours for the indicators the contest list uses.
 *
 * These are read from the active [MaterialTheme] where a theme role fits, and only fall
 * back to fixed hues where the meaning (a platform's identity, a failure) is not one
 * Material expresses.
 */
object ContestPilotPalette {

    @Composable
    @ReadOnlyComposable
    fun forPlatform(platform: Platform): BadgeColors = when (platform) {
        Platform.CODEFORCES -> BadgeColors(
            container = MaterialTheme.colorScheme.primaryContainer,
            onContainer = MaterialTheme.colorScheme.onPrimaryContainer
        )

        Platform.CODECHEF -> BadgeColors(
            container = MaterialTheme.colorScheme.tertiaryContainer,
            onContainer = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }

    @Composable
    @ReadOnlyComposable
    fun forDivision(division: ContestDivision): BadgeColors = when (division) {
        ContestDivision.OTHER -> BadgeColors(
            container = MaterialTheme.colorScheme.surfaceContainerHighest,
            onContainer = MaterialTheme.colorScheme.onSurfaceVariant
        )

        else -> BadgeColors(
            container = MaterialTheme.colorScheme.secondaryContainer,
            onContainer = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }

    @Composable
    @ReadOnlyComposable
    fun forRegistration(status: RegistrationStatus): BadgeColors = when (status) {
        RegistrationStatus.REGISTERED,
        RegistrationStatus.ALREADY_REGISTERED -> BadgeColors(
            container = MaterialTheme.colorScheme.tertiaryContainer,
            onContainer = MaterialTheme.colorScheme.onTertiaryContainer
        )

        RegistrationStatus.FAILED -> BadgeColors(
            container = MaterialTheme.colorScheme.errorContainer,
            onContainer = MaterialTheme.colorScheme.onErrorContainer
        )

        RegistrationStatus.PENDING -> BadgeColors(
            container = MaterialTheme.colorScheme.secondaryContainer,
            onContainer = MaterialTheme.colorScheme.onSecondaryContainer
        )

        RegistrationStatus.UNKNOWN,
        RegistrationStatus.NOT_APPLICABLE,
        RegistrationStatus.SKIPPED -> BadgeColors(
            container = MaterialTheme.colorScheme.surfaceContainerHighest,
            onContainer = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
