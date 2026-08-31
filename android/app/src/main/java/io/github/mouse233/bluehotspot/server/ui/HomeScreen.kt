package io.github.mouse233.bluehotspot.server.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mouse233.bluehotspot.server.ble.BleConnectedDevice
import io.github.mouse233.bluehotspot.server.tethering.TetheringState

@Composable
fun HomeScreen(
    state: TetheringState,
    connectedDevices: List<BleConnectedDevice>,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("BlueHotspot", style = MaterialTheme.typography.headlineMedium)
        Text("System-configured Wi-Fi hotspot")
        Text("State: ${state.label()}")

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Connected devices", style = MaterialTheme.typography.titleMedium)
                if (connectedDevices.isEmpty()) {
                    Text(
                        "No iPhone connected",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    connectedDevices.forEach { device ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(device.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "BLE connected · ${device.address}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = onStart,
            enabled = state !is TetheringState.Starting && state !is TetheringState.Active,
        ) {
            Text("Start hotspot")
        }
        OutlinedButton(
            onClick = onStop,
            enabled = state is TetheringState.Active,
        ) {
            Text("Stop hotspot")
        }
    }
}

private fun TetheringState.label(): String = when (this) {
    TetheringState.Unsupported -> "Unsupported on this Android version"
    TetheringState.Idle -> "Idle"
    TetheringState.Starting -> "Starting"
    TetheringState.Active -> "Active"
    TetheringState.Stopping -> "Stopping"
    is TetheringState.Failed -> "Failed: $reason"
}
