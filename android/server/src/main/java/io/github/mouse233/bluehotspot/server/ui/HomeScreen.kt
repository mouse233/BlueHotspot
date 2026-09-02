package io.github.mouse233.bluehotspot.server.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
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
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState(),
    )
    var menuExpanded by remember { mutableStateOf(false) }
    var aboutDialogVisible by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("BlueHotspot") },
                scrollBehavior = scrollBehavior,
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("About") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Info, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    aboutDialogVisible = true
                                },
                            )
                        }
                    }
                },
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
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Hotspot", Icons.Outlined.PowerSettingsNew)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Icon(
                                imageVector = if (state.isActive()) {
                                    Icons.Outlined.CheckCircle
                                } else {
                                    Icons.Outlined.Circle
                                },
                                contentDescription = null,
                                tint = if (state.isActive()) {
                                    Color(0xFF2E7D32)
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                            Column {
                                Text(
                                    text = state.label(),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "System-configured Wi-Fi hotspot",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Controls", Icons.Outlined.PowerSettingsNew)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        ActionRow(
                            icon = Icons.Outlined.PowerSettingsNew,
                            title = "Start hotspot",
                            enabled = state.canStart(),
                            onClick = onStart,
                        )
                        RowDivider()
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
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Connected devices", Icons.Outlined.Devices, connectedDevices.size)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        if (connectedDevices.isEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.Bluetooth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "No controller connected",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            connectedDevices.forEachIndexed { index, device ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Bluetooth,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            device.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            "BLE connected · ${device.address}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (index != connectedDevices.lastIndex) RowDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    if (aboutDialogVisible) {
        AlertDialog(
            onDismissRequest = { aboutDialogVisible = false },
            title = { Text("About BlueHotspot") },
            text = {
                Text("BlueHotspot lets an iPhone or Android client control this device's already-configured Wi-Fi hotspot over encrypted Bluetooth Low Energy (BLE).")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        aboutDialogVisible = false
                        uriHandler.openUri(REPOSITORY_URL)
                    },
                ) {
                    Text("GitHub")
                }
            },
            dismissButton = {
                TextButton(onClick = { aboutDialogVisible = false }) {
                    Text("Close")
                }
            },
        )
    }
}

private const val REPOSITORY_URL = "https://github.com/mouse233/BlueHotspot"

@Composable
private fun SectionHeader(title: String, icon: ImageVector, count: Int? = null) {
    Row(
        modifier = Modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            title,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (count != null) {
            Text(
                count.toString(),
                modifier = Modifier.padding(start = 8.dp),
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxWidth(),
    ) {
        androidx.compose.material3.TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(icon, contentDescription = null)
                Text(title, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronRight, contentDescription = null)
            }
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
}

private fun TetheringState.isActive(): Boolean =
    this == TetheringState.Active || this == TetheringState.ExternalActive

private fun TetheringState.canStart(): Boolean =
    this != TetheringState.Starting && this != TetheringState.Active && this != TetheringState.ExternalActive

private fun TetheringState.canStop(): Boolean =
    this == TetheringState.Active

private fun TetheringState.label(): String = when (this) {
    TetheringState.Unsupported -> "Unsupported on this Android version"
    TetheringState.Idle -> "Idle"
    TetheringState.Starting -> "Starting"
    TetheringState.Active -> "Active"
    TetheringState.ExternalActive -> "Active (system)"
    TetheringState.Stopping -> "Stopping"
    is TetheringState.Failed -> "Failed: $reason"
}
