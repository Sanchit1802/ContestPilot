package com.sanchit.contestpilot.ui.contests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sanchit.contestpilot.domain.logic.DateTimeUtils
import com.sanchit.contestpilot.domain.model.AutomationRunStatus
import com.sanchit.contestpilot.domain.model.SyncOutcome
import com.sanchit.contestpilot.domain.model.SyncState

/**
 * Tells the user how fresh the data is and what the cloud automation last did.
 *
 * This is where the "PC not required" part of the system becomes visible: the app reports
 * the outcome of a GitHub Actions run it had no part in.
 */
@Composable
fun SyncSummaryCard(
    state: ContestsUiState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Synchronisation",
                style = MaterialTheme.typography.titleSmall
            )

            state.settings.enabledPlatforms
                .map { platform ->
                    platform.displayName to state.syncStates[platform.syncSource]
                }
                .sortedBy { it.first }
                .forEach { (label, syncState) ->
                    SyncRow(label = label, syncState = syncState)
                }

            HorizontalDivider()

            val automation = state.automationStatus

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Cloud auto-registration",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = automation.runStatus.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = automationSummary(state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (automation.runStatus != AutomationRunStatus.NOT_CONFIGURED) {
                Text(
                    text = "Last report: " +
                        DateTimeUtils.formatShortIndianTime(automation.generatedAtEpochSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                automation.message?.takeIf { it.isNotBlank() }?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                TextButton(onClick = onOpenSettings) {
                    Text("Set up the automation status feed")
                }
            }
        }
    }
}

@Composable
private fun SyncRow(label: String, syncState: SyncState?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = when (syncState?.outcome) {
                null, SyncOutcome.NEVER_SYNCED -> "Not synced yet"
                SyncOutcome.DISABLED -> "Disabled"
                SyncOutcome.SUCCESS -> DateTimeUtils.formatShortIndianTime(
                    syncState.lastSuccessEpochSeconds
                )

                SyncOutcome.FAILED -> "Failed - showing cached data"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun automationSummary(state: ContestsUiState): String {
    val automation = state.automationStatus

    return when (automation.runStatus) {
        AutomationRunStatus.NOT_CONFIGURED ->
            "No status feed configured, so ContestPilot cannot tell whether the cloud " +
                "automation has registered you for anything."

        AutomationRunStatus.NEVER_RUN ->
            "The status feed is configured but has not reported a run yet."

        AutomationRunStatus.BLOCKED ->
            "Codeforces asked for a CAPTCHA or a second factor. The automation stopped " +
                "without attempting to get around it - register manually for now."

        AutomationRunStatus.FAILED ->
            "The last automation run failed. ${automation.registrationsFailed} " +
                "registration attempt(s) did not succeed."

        AutomationRunStatus.PARTIAL,
        AutomationRunStatus.SUCCESS -> {
            val handle = automation.codeforcesHandle?.takeIf { it.isNotBlank() }
            val who = handle?.let { " as $it" } ?: ""
            "${automation.registrationsSucceeded} of ${automation.registrationsAttempted} " +
                "contest(s) registered$who."
        }
    }
}
