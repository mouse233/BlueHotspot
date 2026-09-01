package io.github.mouse233.bluehotspot.server.ui

import androidx.lifecycle.ViewModel
import io.github.mouse233.bluehotspot.server.ble.BleConnectedDevice
import io.github.mouse233.bluehotspot.server.tethering.TetheringController
import io.github.mouse233.bluehotspot.server.tethering.TetheringState
import kotlinx.coroutines.flow.StateFlow

class AppViewModel(
    private val controller: TetheringController,
    connectedDevices: StateFlow<List<BleConnectedDevice>>,
) : ViewModel() {
    val state: StateFlow<TetheringState> = controller.state
    val connectedDevices: StateFlow<List<BleConnectedDevice>> = connectedDevices

    fun start() {
        controller.start()
    }

    fun stop() {
        controller.stop()
    }
}
