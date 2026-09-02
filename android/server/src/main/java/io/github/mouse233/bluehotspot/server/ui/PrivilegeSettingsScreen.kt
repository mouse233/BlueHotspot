package io.github.mouse233.bluehotspot.server.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mouse233.bluehotspot.server.privilege.PrivilegeBackend
import io.github.mouse233.bluehotspot.server.privilege.PrivilegeState
import io.github.mouse233.bluehotspot.server.privilege.PrivilegeUiState

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

@Composable
fun PrivilegeSettingsScreen(
    state: PrivilegeUiState,
    onSelectBackend: (PrivilegeBackend) -> Unit,
    onRequestAuthorization: (PrivilegeBackend) -> Unit,
    onRefresh: (PrivilegeBackend) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permission settings") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            modifier = Modifier.rotate(180f),
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(onClick = onContinue, enabled = state.canContinue) {
                    Text("Continue")
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Choose how BlueHotspot gets the advanced system access it needs.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Select one method and complete its availability check to continue.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(listOf(PrivilegeBackend.SHIZUKU, PrivilegeBackend.ROOT)) { backend ->
                val backendState = when (backend) {
                    PrivilegeBackend.SHIZUKU -> state.shizukuState
                    PrivilegeBackend.ROOT -> state.rootState
                }
                PrivilegeCard(
                    backend = backend,
                    selected = state.selectedBackend == backend,
                    state = backendState,
                    onSelect = { onSelectBackend(backend) },
                    onRequest = { onRequestAuthorization(backend) },
                    onRefresh = { onRefresh(backend) },
                )
            }
        }
    }
}

@Composable
private fun PrivilegeCard(
    backend: PrivilegeBackend,
    selected: Boolean,
    state: PrivilegeState,
    onSelect: () -> Unit,
    onRequest: () -> Unit,
    onRefresh: () -> Unit,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val action = state.action()

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            ),
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(modifier = Modifier.size(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = backend.title(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (backend == PrivilegeBackend.SHIZUKU) {
                        Text(
                            text = "Recommended",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = backend.description(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = state.label(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = state.statusColor(),
                    fontWeight = FontWeight.Medium,
                )
                if (action != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.isBusy()) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            androidx.compose.material3.TextButton(
                                onClick = if (action.isRequest) onRequest else onRefresh,
                            ) {
                                Text(action.label)
                            }
                        }
                    }
                } else if (state.isBusy()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

private data class PrivilegeAction(val label: String, val isRequest: Boolean)

private fun PrivilegeState.action(): PrivilegeAction? = when (this) {
    PrivilegeState.Requesting,
    PrivilegeState.Checking,
    -> null
    PrivilegeState.NotAuthorized,
    PrivilegeState.Denied,
    PrivilegeState.NotChecked,
    -> PrivilegeAction("Request authorization", isRequest = true)
    PrivilegeState.ShizukuNotRunning,
    PrivilegeState.RootUnavailable,
    PrivilegeState.Available,
    is PrivilegeState.Failed,
    -> PrivilegeAction("Test again", isRequest = false)
}

private fun PrivilegeState.isBusy(): Boolean =
    this == PrivilegeState.Requesting || this == PrivilegeState.Checking

private fun PrivilegeState.label(): String = when (this) {
    PrivilegeState.NotChecked -> "Not checked"
    PrivilegeState.ShizukuNotRunning -> "Shizuku is not running"
    PrivilegeState.RootUnavailable -> "No usable root access"
    PrivilegeState.NotAuthorized -> "Authorization required"
    PrivilegeState.Requesting -> "Requesting authorization"
    PrivilegeState.Checking -> "Checking availability"
    PrivilegeState.Available -> "Authorized and available"
    PrivilegeState.Denied -> "Authorization denied"
    is PrivilegeState.Failed -> "Check failed: $reason"
}

@Composable
private fun PrivilegeState.statusColor(): Color = when (this) {
    PrivilegeState.Available -> MaterialTheme.colorScheme.primary
    PrivilegeState.Denied,
    is PrivilegeState.Failed,
    -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun PrivilegeBackend.title(): String = when (this) {
    PrivilegeBackend.SHIZUKU -> "Shizuku"
    PrivilegeBackend.ROOT -> "Root"
}

private fun PrivilegeBackend.description(): String = when (this) {
    PrivilegeBackend.SHIZUKU -> "Get the required system access through Shizuku without granting the app direct root access."
    PrivilegeBackend.ROOT -> "Use su to get superuser access directly on devices that are already rooted."
}
