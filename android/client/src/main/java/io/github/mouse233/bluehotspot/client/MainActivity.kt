package io.github.mouse233.bluehotspot.client

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.mouse233.bluehotspot.client.ui.HomeScreen
import io.github.mouse233.bluehotspot.client.ui.theme.BlueHotspotTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<ClientViewModel> {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ClientViewModel(applicationContext) as T
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refreshBluetoothState()
        if (hasBluetoothPermissions()) viewModel.startScanning()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlueHotspotTheme {
                HomeScreen(
                    bluetoothState = viewModel.bluetoothState.collectAsState().value,
                    connectionState = viewModel.connectionState.collectAsState().value,
                    devices = viewModel.devices.collectAsState().value,
                    hotspotState = viewModel.hotspotState.collectAsState().value,
                    deviceName = viewModel.deviceName.collectAsState().value,
                    lastError = viewModel.lastError.collectAsState().value,
                    onScan = ::requestPermissionsAndScan,
                    onConnect = viewModel::connect,
                    onDisconnect = viewModel::disconnect,
                    onStart = viewModel::startHotspot,
                    onStop = viewModel::stopHotspot,
                )
            }
        }
        requestPermissionsAndScan()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshBluetoothState()
    }

    private fun requestPermissionsAndScan() {
        val missing = bluetoothPermissions().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            viewModel.startScanning()
        }
    }

    private fun hasBluetoothPermissions(): Boolean = bluetoothPermissions().all {
        checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    private fun bluetoothPermissions(): List<String> = listOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
    )
}
