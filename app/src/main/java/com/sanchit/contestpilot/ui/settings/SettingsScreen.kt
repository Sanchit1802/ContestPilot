package com.sanchit.contestpilot.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanchit.contestpilot.domain.model.AppSettings
import com.sanchit.contestpilot.domain.model.ContestDivision
import com.sanchit.contestpilot.domain.model.Platform

/**
 * User configuration. Nothing here is a credential: Codeforces sign-in material belongs
 * in GitHub Actions secrets, and the app is built so it can never read it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = state.settings

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to contests"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SettingsSection(
                title = "Platforms",
                description = "Which sites ContestPilot tracks."
            ) {
                Platform.entries.forEach { platform ->
                    SwitchRow(
                        label = platform.displayName,
                        checked = platform in settings.enabledPlatforms,
                        onCheckedChange = { viewModel.setPlatformEnabled(platform, it) }
                    )
                }
            }

            SettingsSection(
                title = "Contest window",
                description = "How far ahead the list looks."
            ) {
                ChoiceChips(
                    options = AppSettings.CONTEST_WINDOW_CHOICES,
                    selected = settings.contestWindowDays,
                    label = { days -> if (days == 1) "1 day" else "$days days" },
                    onSelect = viewModel::setContestWindowDays
                )
            }

            SettingsSection(
                title = "Notifications",
                description = "A reminder is delivered by an exact alarm, so it arrives " +
                    "even when ContestPilot is closed."
            ) {
                SwitchRow(
                    label = "Contest reminders",
                    checked = settings.notificationsEnabled,
                    onCheckedChange = viewModel::setNotificationsEnabled
                )
                Text(
                    text = "Remind me before the start",
                    style = MaterialTheme.typography.bodyMedium
                )
                ChoiceChips(
                    options = AppSettings.NOTIFICATION_LEAD_CHOICES,
                    selected = settings.notificationLeadMinutes,
                    label = { minutes -> "$minutes min" },
                    onSelect = viewModel::setNotificationLeadMinutes,
                    enabled = settings.notificationsEnabled
                )
            }

            SettingsSection(
                title = "Auto-registration",
                description = "Registration runs in GitHub Actions, not on this device " +
                    "and not on your PC. Codeforces publishes no registration API, so the " +
                    "automation drives a real browser session instead."
            ) {
                SwitchRow(
                    label = "Register me automatically",
                    checked = settings.autoRegistrationEnabled,
                    onCheckedChange = viewModel::setAutoRegistrationEnabled
                )
                Text(
                    text = "Codeforces divisions",
                    style = MaterialTheme.typography.bodyMedium
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ContestDivision.selectable.forEach { division ->
                        FilterChip(
                            selected = division in settings.autoRegisterDivisions,
                            onClick = {
                                viewModel.setDivisionEnabled(
                                    division,
                                    division !in settings.autoRegisterDivisions
                                )
                            },
                            enabled = settings.autoRegistrationEnabled,
                            label = { Text(division.displayName) }
                        )
                    }
                }
                Text(
                    text = "Codeforces does not publish whether a round is rated, so " +
                        "ContestPilot matches on division only and never claims to know " +
                        "the rated status.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingsSection(
                title = "Cloud status feed",
                description = "The public JSON your GitHub Actions workflow publishes. " +
                    "It carries registration results only - never credentials."
            ) {
                TextFieldRow(
                    label = "Status JSON URL",
                    initialValue = settings.automationStatusUrl,
                    placeholder = "https://raw.githubusercontent.com/<you>/<repo>/status/automation-status.json",
                    errorMessage = state.statusUrlError,
                    onCommit = viewModel::setAutomationStatusUrl
                )
                TextFieldRow(
                    label = "Codeforces handle (for display)",
                    initialValue = settings.codeforcesHandle,
                    placeholder = "tourist",
                    errorMessage = null,
                    onCommit = viewModel::setCodeforcesHandle
                )
                Text(
                    text = "Never enter your Codeforces password here. ContestPilot has " +
                        "no field for it because the app must never hold one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean = true
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                enabled = enabled,
                label = { Text(label(option)) }
            )
        }
    }
}

/**
 * Text is committed when the field loses focus or the user presses Done, so a partially
 * typed URL is never written to storage.
 */
@Composable
private fun TextFieldRow(
    label: String,
    initialValue: String,
    placeholder: String,
    errorMessage: String?,
    onCommit: (String) -> Unit
) {
    var text by remember(initialValue) { mutableStateOf(initialValue) }
    var wasFocused by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        isError = errorMessage != null,
        supportingText = errorMessage?.let { message -> { Text(message) } },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit(text) }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (wasFocused && !focusState.isFocused) onCommit(text)
                wasFocused = focusState.isFocused
            }
    )
}
