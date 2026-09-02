package io.github.mouse233.bluehotspot.server.ui

import androidx.lifecycle.ViewModel
import io.github.mouse233.bluehotspot.server.ble.BleConnectedDevice
import io.github.mouse233.bluehotspot.server.privilege.PrivilegeBackend
import io.github.mouse233.bluehotspot.server.privilege.PrivilegeController
import io.github.mouse233.bluehotspot.server.privilege.PrivilegeUiState
import io.github.mouse233.bluehotspot.server.tethering.TetheringController
import io.github.mouse233.bluehotspot.server.tethering.TetheringState
import kotlinx.coroutines.flow.StateFlow

class AppViewModel(
    private val controller: TetheringController,
    private val privileges: PrivilegeController,
    connectedDevices: StateFlow<List<BleConnectedDevice>>,
) : ViewModel() {
    val state: StateFlow<TetheringState> = controller.state
    val privilegeState: StateFlow<PrivilegeUiState> = privileges.uiState
    val connectedDevices: StateFlow<List<BleConnectedDevice>> = connectedDevices

    fun selectBackend(backend: PrivilegeBackend) {
        privileges.selectBackend(backend)
    }

    fun requestAuthorization(backend: PrivilegeBackend) {
        privileges.requestAuthorization(backend)
    }

    fun refreshPrivilege(backend: PrivilegeBackend) {
        privileges.refresh(backend)
    }

    fun start() {
        controller.start()
    }

    fun stop() {
        controller.stop()
    }
}
