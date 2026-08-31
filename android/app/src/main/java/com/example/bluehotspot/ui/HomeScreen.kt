package com.example.bluehotspot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bluehotspot.tethering.TetheringState

@Composable
fun HomeScreen(
    state: TetheringState,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("BlueHotspot", style = MaterialTheme.typography.headlineMedium)
        Text("System-configured Wi-Fi hotspot")
        Text("State: ${state.label()}")

        Button(onClick = onStart, enabled = state !is TetheringState.Starting && state !is TetheringState.Active) {
            Text("Start hotspot")
        }
        OutlinedButton(onClick = onStop, enabled = state is TetheringState.Active) {
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
