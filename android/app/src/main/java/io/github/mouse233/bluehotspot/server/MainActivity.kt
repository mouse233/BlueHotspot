package io.github.mouse233.bluehotspot.server

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.mouse233.bluehotspot.server.ble.BleControlForegroundService
import io.github.mouse233.bluehotspot.server.ui.AppViewModel
import io.github.mouse233.bluehotspot.server.ui.HomeScreen

class MainActivity : ComponentActivity() {
    private val app: BlueHotspotApplication
        get() = application as BlueHotspotApplication

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        startBleIfPermitted()
    }

    private val viewModel by viewModels<AppViewModel> {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AppViewModel(
                    app.tetheringController,
                    app.bleGattServer.connectedDevices,
                ) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startBleIfPermitted()
        setContent {
            val state by viewModel.state.collectAsState()
            val connectedDevices by viewModel.connectedDevices.collectAsState()
            HomeScreen(
                state = state,
                connectedDevices = connectedDevices,
                onStart = viewModel::start,
                onStop = viewModel::stop,
            )
        }
    }

    private fun startBleIfPermitted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val required = buildList {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            val missing = required.filter {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                permissionLauncher.launch(missing.toTypedArray())
                return
            }
        }
        BleControlForegroundService.start(this)
    }
}
