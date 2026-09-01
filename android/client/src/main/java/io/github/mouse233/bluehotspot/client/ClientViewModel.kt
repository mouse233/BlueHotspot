package io.github.mouse233.bluehotspot.client

import android.content.Context
import androidx.lifecycle.ViewModel
import io.github.mouse233.bluehotspot.client.ble.BluetoothCentral
import io.github.mouse233.bluehotspot.client.ble.BluetoothState
import io.github.mouse233.bluehotspot.client.ble.ConnectionState
import kotlinx.coroutines.flow.StateFlow

internal class ClientViewModel(context: Context) : ViewModel() {
    private val bluetooth = BluetoothCentral(context)

    val bluetoothState: StateFlow<BluetoothState> = bluetooth.bluetoothState
    val connectionState: StateFlow<ConnectionState> = bluetooth.connectionState
    val devices = bluetooth.devices
    val hotspotState = bluetooth.hotspotState
    val deviceName = bluetooth.deviceName
    val lastError = bluetooth.lastError
    val autoConnectEnabled = bluetooth.autoConnectEnabled

    fun refreshBluetoothState() = bluetooth.refreshBluetoothState()
    fun startScanning() = bluetooth.startScanning()
    fun connect(device: io.github.mouse233.bluehotspot.client.ble.DiscoveredDevice) = bluetooth.connect(device)
    fun disconnect() = bluetooth.disconnect()
    fun setAutoConnectEnabled(enabled: Boolean) = bluetooth.setAutoConnectEnabled(enabled)
    fun startHotspot() = bluetooth.startHotspot()
    fun stopHotspot() = bluetooth.stopHotspot()

    override fun onCleared() {
        bluetooth.close()
    }
}
