package io.github.mouse233.bluehotspot.client.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
    onConnect: (DiscoveredDevice) -> Unit,
    onDisconnect: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("BlueHotspot") },
                actions = {
                    IconButton(
                        onClick = onScan,
                        modifier = Modifier.semantics {
                            contentDescription = "Scan for Android devices"
                        },
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
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
                Section(
                    title = "Android devices",
                    icon = Icons.Outlined.SettingsInputAntenna,
                    count = devices.size,
                ) {
                    SectionCard {
                        if (devices.isEmpty()) {
                            EmptyRow(
                                icon = Icons.Outlined.Search,
                                text = "No Android devices found",
                            )
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
                                if (index != devices.lastIndex) RowDivider()
                            }
                        }
                        RowDivider()
                        ActionRow(
                            icon = Icons.Outlined.Refresh,
                            title = "Scan again",
                            onClick = onScan,
                        )
                    }
                }
            }

            item {
                Section(title = "Connection", icon = Icons.Outlined.Link) {
                    SectionCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = if (connectionState == ConnectionState.Connected) {
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
                            Column {
                                Text(
                                    text = if (connectionState == ConnectionState.Connected) {
                                        deviceName ?: "Android device"
                                    } else {
                                        "No Android device"
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = connectionState.label(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        RowDivider()
                        StatusRow("Bluetooth", bluetoothState.label())
                        RowDivider()
                        StatusRow("Hotspot", hotspotState.label())
                    }
                }
            }

            item {
                Section(title = "Controls", icon = Icons.Outlined.PowerSettingsNew) {
                    SectionCard {
                        if (connectionState == ConnectionState.Connected) {
                            ActionRow(
                                icon = Icons.Outlined.PowerSettingsNew,
                                title = "Start hotspot",
                                enabled = hotspotState !in setOf("STARTING", "ACTIVE"),
                                onClick = onStart,
                            )
                            RowDivider()
                            ActionRow(
                                icon = Icons.Outlined.Stop,
                                title = "Stop hotspot",
                                enabled = hotspotState == "ACTIVE",
                                onClick = onStop,
                            )
                            RowDivider()
                            ActionRow(
                                icon = Icons.Outlined.Close,
                                title = "Disconnect",
                                onClick = onDisconnect,
                                destructive = true,
                            )
                        } else {
                            ActionRow(
                                icon = Icons.Outlined.Search,
                                title = "Scan for Android device",
                                enabled = bluetoothState == BluetoothState.Ready,
                                onClick = onScan,
                            )
                        }
                    }
                }
            }

            if (lastError != null) {
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = lastError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
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
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = title,
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (count != null) {
                Text(
                    text = count.toString(),
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        content()
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Outlined.SettingsInputAntenna,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (device.rssi == 0) "Signal unavailable" else "${device.rssi} dBm",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            isConnecting -> Text(
                "Connecting",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            isConnected -> Text(
                "Connected",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF2E7D32),
                fontWeight = FontWeight.SemiBold,
            )
            else -> Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = "Connect",
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun EmptyRow(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val activeColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val contentColor = if (enabled) activeColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = contentColor)
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = contentColor,
            style = MaterialTheme.typography.bodyLarge,
        )
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.outline else contentColor,
        )
    }
}

@Composable
private fun StatusRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun RowDivider() {
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
