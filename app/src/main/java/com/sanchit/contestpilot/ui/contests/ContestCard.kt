package com.sanchit.contestpilot.ui.contests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sanchit.contestpilot.domain.logic.CountdownFormatter
import com.sanchit.contestpilot.domain.logic.DateTimeUtils
import com.sanchit.contestpilot.domain.model.RegistrationStatus
import com.sanchit.contestpilot.ui.components.DivisionBadge
import com.sanchit.contestpilot.ui.components.PlatformBadge
import com.sanchit.contestpilot.ui.components.RegistrationBadge
import java.time.Instant

/**
 * One contest. Every value shown comes from the [ContestListItem]; the card performs no
 * filtering or eligibility logic of its own.
 */
@Composable
fun ContestCard(
    item: ContestListItem,
    now: Instant,
    onOpenContest: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val contest = item.contest
    val countdown = CountdownFormatter.formatForContest(contest, now)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlatformBadge(contest.platform)
                DivisionBadge(contest.division)
                RegistrationBadge(item.registrationStatus)
            }

            Text(
                text = contest.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = countdown,
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .semantics { contentDescription = "Time until start: $countdown" },
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DetailColumn(
                    label = "DATE",
                    value = DateTimeUtils.formatIndianDate(contest.startTimeSeconds),
                    modifier = Modifier.weight(1f)
                )
                DetailColumn(
                    label = "TIME (IST)",
                    value = DateTimeUtils.formatIndianClockTime(contest.startTimeSeconds),
                    modifier = Modifier.weight(1f)
                )
                DetailColumn(
                    label = "LENGTH",
                    value = DateTimeUtils.formatDuration(contest.durationSeconds),
                    modifier = Modifier.weight(1f)
                )
            }

            item.registrationMessage?.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.registrationStatus == RegistrationStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val autoRegistrationNote = when {
                        item.isAutoRegistrationCandidate -> "Queued for auto-registration"
                        else -> item.ineligibilityExplanation
                    }

                    if (autoRegistrationNote != null) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = autoRegistrationNote,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                TextButton(onClick = { onOpenContest(contest.url) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open")
                }
            }
        }
    }
}

@Composable
private fun DetailColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
