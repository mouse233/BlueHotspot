package io.github.mouse233.bluehotspot.server.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mouse233.bluehotspot.server.ble.BleConnectedDevice
import io.github.mouse233.bluehotspot.server.tethering.TetheringState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: TetheringState,
    connectedDevices: List<BleConnectedDevice>,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState(),
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("BlueHotspot") },
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Section("Hotspot", Icons.Outlined.PowerSettingsNew) {
                    SectionCard {
                        ListItem(
                            leadingContent = {
                                Icon(
                                    if (state.isActive()) Icons.Outlined.CheckCircle else Icons.Outlined.Circle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            supportingContent = {
                                Text(
                                    "System-configured Wi-Fi hotspot",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            headlineContent = {
                                Text(state.label(), fontWeight = FontWeight.Medium)
                            },
                        )
                    }
                }
            }

            item {
                Section("Controls", Icons.Outlined.PowerSettingsNew) {
                    SectionCard {
                        ActionRow(
                            icon = Icons.Outlined.PowerSettingsNew,
                            title = "Start hotspot",
                            enabled = state.canStart(),
                            onClick = onStart,
                        )
                        ListDivider()
                        ActionRow(
                            icon = Icons.Outlined.Stop,
                            title = "Stop hotspot",
                            enabled = state.canStop(),
                            onClick = onStop,
                        )
                    }
                }
            }

            item {
                Section("Connected devices", Icons.Outlined.Devices, connectedDevices.size) {
                    SectionCard {
                        if (connectedDevices.isEmpty()) {
                            ListItem(
                                leadingContent = {
                                    Icon(
                                        Icons.Outlined.Bluetooth,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                headlineContent = {
                                    Text(
                                        "No controller connected",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                            )
                        } else {
                            connectedDevices.forEachIndexed { index, device ->
                                ListItem(
                                    leadingContent = {
                                        Icon(
                                            Icons.Outlined.Bluetooth,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    },
                                    headlineContent = {
                                        Text(device.name, fontWeight = FontWeight.Medium)
                                    },
                                    supportingContent = {
                                        Text(
                                            "BLE connected · ${device.address}",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                )
                                if (index != connectedDevices.lastIndex) ListDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(
    title: String,
    icon: ImageVector,
    count: Int? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ListItem(
            leadingContent = {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingContent = count?.let {
                { Text(it.toString(), color = MaterialTheme.colorScheme.outline) }
            },
            headlineContent = {
                Text(
                    title,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
        )
        content()
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        content()
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
        headlineContent = { Text(title) },
    )
}

@Composable
private fun ListDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
}

private fun TetheringState.isActive(): Boolean =
    this == TetheringState.Active || this == TetheringState.ExternalActive

private fun TetheringState.canStart(): Boolean =
    this != TetheringState.Starting && this != TetheringState.Active && this != TetheringState.ExternalActive

private fun TetheringState.canStop(): Boolean =
    this == TetheringState.Active || this == TetheringState.ExternalActive

private fun TetheringState.label(): String = when (this) {
    TetheringState.Unsupported -> "Unsupported on this Android version"
    TetheringState.Idle -> "Idle"
    TetheringState.Starting -> "Starting"
    TetheringState.Active -> "Active"
    TetheringState.ExternalActive -> "Active (system)"
    TetheringState.Stopping -> "Stopping"
    is TetheringState.Failed -> "Failed: $reason"
}
