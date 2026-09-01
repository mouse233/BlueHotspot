package io.github.mouse233.bluehotspot.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mouse233.bluehotspot.client.ble.BluetoothState
import io.github.mouse233.bluehotspot.client.ble.ConnectionState
import io.github.mouse233.bluehotspot.client.ble.DiscoveredDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    bluetoothState: BluetoothState,
    connectionState: ConnectionState,
    devices: List<DiscoveredDevice>,
    hotspotState: String,
    deviceName: String?,
    lastError: String?,
    onScan: () -> Unit,
    autoConnectEnabled: Boolean,
    onAutoConnectChange: (Boolean) -> Unit,
    onConnect: (DiscoveredDevice) -> Unit,
    onDisconnect: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState(),
    )
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("BlueHotspot") },
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
                                text = { Text("Automatic connection") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Bolt, contentDescription = null)
                                },
                                trailingIcon = {
                                    Switch(
                                        checked = autoConnectEnabled,
                                        onCheckedChange = {
                                            onAutoConnectChange(it)
                                            menuExpanded = false
                                        },
                                    )
                                },
                                onClick = {
                                    onAutoConnectChange(!autoConnectEnabled)
                                    menuExpanded = false
                                },
                            )
                        }
                    }
                },
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
                Section("Android devices", Icons.Outlined.SettingsInputAntenna, devices.size) {
                    SectionCard {
                        if (devices.isEmpty()) {
                            EmptyRow(Icons.Outlined.Search, "No Android devices found")
                        } else {
                            devices.forEachIndexed { index, device ->
                                DeviceRow(
                                    device = device,
                                    isConnecting = connectionState == ConnectionState.Connecting ||
                                        connectionState == ConnectionState.Pairing,
                                    isConnected = connectionState == ConnectionState.Connected &&
                                        deviceName == device.name,
                                    onClick = { onConnect(device) },
                                )
                                if (index != devices.lastIndex) ListDivider()
                            }
                        }
                        ListDivider()
                        ActionRow(Icons.Outlined.Search, "Scan again", onClick = onScan)
                    }
                }
            }

            item {
                Section("Connection", Icons.Outlined.Link) {
                    SectionCard {
                        ListItem(
                            leadingContent = {
                                Icon(
                                    if (connectionState == ConnectionState.Connected) {
                                        Icons.Outlined.CheckCircle
                                    } else {
                                        Icons.Outlined.Circle
                                    },
                                    contentDescription = null,
                                    tint = if (connectionState == ConnectionState.Connected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            },
                            supportingContent = {
                                Text(
                                    connectionState.label(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            headlineContent = {
                                Text(
                                    if (connectionState == ConnectionState.Connected) {
                                        deviceName ?: "Android device"
                                    } else {
                                        "No Android device"
                                    },
                                    fontWeight = FontWeight.Medium,
                                )
                            },
                        )
                        ListDivider()
                        StatusRow("Bluetooth", bluetoothState.label())
                        ListDivider()
                        StatusRow("Hotspot", hotspotState.label())
                    }
                }
            }

            item {
                Section("Controls", Icons.Outlined.PowerSettingsNew) {
                    SectionCard {
                        if (connectionState == ConnectionState.Connected) {
                            ActionRow(
                                Icons.Outlined.PowerSettingsNew,
                                "Start hotspot",
                                enabled = hotspotState !in setOf("STARTING", "ACTIVE"),
                                onClick = onStart,
                            )
                            ListDivider()
                            ActionRow(
                                Icons.Outlined.Stop,
                                "Stop hotspot",
                                enabled = hotspotState == "ACTIVE",
                                onClick = onStop,
                            )
                            ListDivider()
                            ActionRow(
                                Icons.Outlined.Close,
                                "Disconnect",
                                destructive = true,
                                onClick = onDisconnect,
                            )
                        } else {
                            ActionRow(
                                Icons.Outlined.Search,
                                "Scan for Android device",
                                enabled = bluetoothState == BluetoothState.Ready,
                                onClick = onScan,
                            )
                        }
                    }
                }
            }

            if (lastError != null) {
                item {
                    ListItem(
                        leadingContent = {
                            Icon(
                                Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        headlineContent = {
                            Text(
                                lastError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                    )
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
private fun DeviceRow(
    device: DiscoveredDevice,
    isConnecting: Boolean,
    isConnected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(
                Icons.Outlined.SettingsInputAntenna,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        supportingContent = {
            Text(
                "${device.id.uppercase()} · " +
                    if (device.rssi == 0) "Signal unavailable" else "${device.rssi} dBm",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            when {
                isConnecting -> Text(
                    "Connecting",
                    color = MaterialTheme.colorScheme.primary,
                )
                isConnected -> Text(
                    "Connected",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                else -> Text(">", color = MaterialTheme.colorScheme.outline)
            }
        },
        headlineContent = {
            Text(device.name, fontWeight = FontWeight.Medium)
        },
    )
}

@Composable
private fun EmptyRow(icon: ImageVector, text: String) {
    ListItem(
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        headlineContent = { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null, tint = contentColor) },
        trailingContent = { Text(">", color = contentColor) },
        headlineContent = { Text(title, color = contentColor) },
    )
}

@Composable
private fun StatusRow(title: String, value: String) {
    ListItem(
        trailingContent = {
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        headlineContent = { Text(title) },
    )
}

@Composable
private fun ListDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
}

private fun BluetoothState.label(): String = when (this) {
    BluetoothState.Unsupported -> "Permission required"
    BluetoothState.PoweredOff -> "Powered off"
    BluetoothState.Ready -> "Ready"
}

private fun ConnectionState.label(): String = when (this) {
    ConnectionState.Disconnected -> "Disconnected"
    ConnectionState.Scanning -> "Scanning"
    ConnectionState.Connecting -> "Connecting"
    ConnectionState.Pairing -> "Pairing — approve the system prompt"
    ConnectionState.Connected -> "Connected and encrypted"
}

private fun String.label(): String = when (this) {
    "IDLE" -> "Idle"
    "STARTING" -> "Starting"
    "ACTIVE" -> "Active"
    "STOPPING" -> "Stopping"
    "FAILED" -> "Failed"
    "UNSUPPORTED" -> "Unsupported"
    "Unknown" -> "Unknown"
    else -> this
}
