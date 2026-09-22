package com.sanchit.contestpilot.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sanchit.contestpilot.domain.model.ContestDivision
import com.sanchit.contestpilot.domain.model.Platform
import com.sanchit.contestpilot.domain.model.RegistrationStatus
import com.sanchit.contestpilot.ui.theme.ContestPilotPalette

/**
 * Small pill used for platform, division and registration indicators.
 *
 * [contentDescription] replaces the visible text for screen readers so a terse label like
 * "Div. 2" is announced as something meaningful.
 */
@Composable
fun StatusBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.clearAndSetSemantics {
            this.contentDescription = contentDescription
        }
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PlatformBadge(platform: Platform, modifier: Modifier = Modifier) {
    val colors = ContestPilotPalette.forPlatform(platform)
    StatusBadge(
        text = platform.displayName,
        containerColor = colors.container,
        contentColor = colors.onContainer,
        contentDescription = "Platform: ${platform.displayName}",
        modifier = modifier
    )
}

@Composable
fun DivisionBadge(division: ContestDivision, modifier: Modifier = Modifier) {
    val colors = ContestPilotPalette.forDivision(division)
    StatusBadge(
        text = division.displayName,
        containerColor = colors.container,
        contentColor = colors.onContainer,
        contentDescription = "Division: ${division.displayName}",
        modifier = modifier
    )
}

@Composable
fun RegistrationBadge(status: RegistrationStatus, modifier: Modifier = Modifier) {
    val colors = ContestPilotPalette.forRegistration(status)
    StatusBadge(
        text = status.displayName,
        containerColor = colors.container,
        contentColor = colors.onContainer,
        contentDescription = "Registration status: ${status.displayName}",
        modifier = modifier
    )
}
