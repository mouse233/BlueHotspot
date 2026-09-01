package io.github.mouse233.bluehotspot.client.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mouse233.bluehotspot.client.ble.BluetoothState
import io.github.mouse233.bluehotspot.client.ble.ConnectionState
import io.github.mouse233.bluehotspot.client.ble.DiscoveredDevice

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
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("BlueHotspot", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Android controller for a configured hotspot",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Bluetooth", style = MaterialTheme.typography.titleMedium)
                    Text(bluetoothState.label())
                    Text("Connection: ${connectionState.label()}")
                    if (deviceName != null) Text("Device: $deviceName")
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Nearby Android servers", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = onScan) { Text("Scan") }
            }
        }

        if (devices.isEmpty()) {
            item {
                Text(
                    "No BlueHotspot server found. Keep the server app open and its BLE service running.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(devices, key = { it.id }) { device ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onConnect(device) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(device.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (device.rssi == 0) "Signal unavailable" else "${device.rssi} dBm",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("Connect", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Hotspot", style = MaterialTheme.typography.titleMedium)
                    Text(hotspotState)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onStart,
                            enabled = connectionState == ConnectionState.Connected &&
                                hotspotState !in setOf("STARTING", "ACTIVE"),
                        ) { Text("Start") }
                        OutlinedButton(
                            onClick = onStop,
                            enabled = connectionState == ConnectionState.Connected &&
                                hotspotState == "ACTIVE",
                        ) { Text("Stop") }
                    }
                    if (connectionState != ConnectionState.Disconnected) {
                        OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
                    }
                }
            }
        }

        if (lastError != null) {
            item {
                Text(
                    lastError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun BluetoothState.label(): String = when (this) {
    BluetoothState.Unsupported -> "Unavailable or permission required"
    BluetoothState.PoweredOff -> "Turn on Bluetooth"
    BluetoothState.Ready -> "Ready"
}

private fun ConnectionState.label(): String = when (this) {
    ConnectionState.Disconnected -> "Disconnected"
    ConnectionState.Scanning -> "Scanning"
    ConnectionState.Connecting -> "Connecting"
    ConnectionState.Pairing -> "Pairing — approve the Android system prompt"
    ConnectionState.Connected -> "Connected and encrypted"
}
