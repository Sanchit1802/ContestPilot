package com.sanchit.contestpilot.ui.contests

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanchit.contestpilot.ui.components.openUrl

/**
 * The contest list: the application's home screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContestsScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ContestsViewModel = viewModel(factory = ContestsViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onNotificationPermissionResult(granted) }

    // Android 13+ will not show a reminder until the user grants the runtime permission,
    // so it is requested once when the screen first appears.
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "ContestPilot",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Next ${state.settings.contestWindowDays} days",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::refresh,
                        enabled = !state.isRefreshing
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh contests"
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Open settings"
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
        ) {
            if (state.isRefreshing && !state.isInitialLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Refreshing contests" }
                )
            }

            when {
                state.isInitialLoading -> LoadingState()

                state.blockingError != null -> ErrorState(
                    message = requireNotNull(state.blockingError),
                    onRetry = viewModel::refresh
                )

                state.isEmpty -> EmptyState(
                    windowDays = state.settings.contestWindowDays,
                    platformsEnabled = state.enabledPlatformCount,
                    onOpenSettings = onOpenSettings,
                    onRetry = viewModel::refresh
                )

                else -> ContestList(
                    state = state,
                    onOpenContest = { url -> openUrl(context, url) },
                    onOpenSettings = onOpenSettings,
                    onDismissProblems = viewModel::dismissProblems
                )
            }
        }
    }
}

@Composable
private fun ContestList(
    state: ContestsUiState,
    onOpenContest: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onDismissProblems: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "status-panel") {
            StatusPanel(
                state = state,
                onOpenSettings = onOpenSettings,
                onDismissProblems = onDismissProblems
            )
        }

        items(items = state.contests, key = { it.contest.id }) { item ->
            ContestCard(
                item = item,
                now = state.now,
                onOpenContest = onOpenContest
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = "Loading contests",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Could not load contests",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onRetry) {
                Text("Try again")
            }
        }
    }
}

@Composable
private fun EmptyState(
    windowDays: Int,
    platformsEnabled: Int,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "No contests scheduled",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = if (platformsEnabled == 0) {
                    "Every platform is switched off. Enable one in Settings to see contests."
                } else {
                    "Nothing is starting in the next $windowDays days on the platforms " +
                        "you follow."
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRetry) { Text("Refresh") }
                Button(onClick = onOpenSettings) { Text("Settings") }
            }
        }
    }
}

@Composable
private fun StatusPanel(
    state: ContestsUiState,
    onOpenSettings: () -> Unit,
    onDismissProblems: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SyncSummaryCard(state = state, onOpenSettings = onOpenSettings)

        state.alarmWarning?.let { warning ->
            NoticeCard(
                text = warning,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                onContainer = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }

        if (!state.notificationPermissionGranted) {
            NoticeCard(
                text = "Notifications are blocked, so contest reminders will not appear. " +
                    "Allow notifications for ContestPilot in system settings.",
                container = MaterialTheme.colorScheme.tertiaryContainer,
                onContainer = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }

        if (state.problems.isNotEmpty()) {
            ProblemCard(problems = state.problems, onDismiss = onDismissProblems)
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun NoticeCard(
    text: String,
    container: androidx.compose.ui.graphics.Color,
    onContainer: androidx.compose.ui.graphics.Color
) {
    Surface(
        color = container,
        contentColor = onContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ProblemCard(
    problems: List<SourceProblem>,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Showing cached data",
                style = MaterialTheme.typography.titleSmall
            )
            problems.forEach { problem ->
                Text(
                    text = "${problem.sourceName}: ${problem.message}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}
