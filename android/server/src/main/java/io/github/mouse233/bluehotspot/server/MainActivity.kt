package io.github.mouse233.bluehotspot.server

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.mouse233.bluehotspot.server.ble.BleControlForegroundService
import io.github.mouse233.bluehotspot.server.ui.AppViewModel
import io.github.mouse233.bluehotspot.server.ui.HomeScreen
import io.github.mouse233.bluehotspot.server.ui.PrivilegeSettingsScreen
import io.github.mouse233.bluehotspot.server.ui.theme.BlueHotspotTheme

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
                    app.privilegeController,
                    app.bleGattServer.connectedDevices,
                ) as T
        }
    }

    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlueHotspotTheme {
                val state by viewModel.state.collectAsState()
                val privilegeState by viewModel.privilegeState.collectAsState()
                val connectedDevices by viewModel.connectedDevices.collectAsState()
                var showPrivilegeSettings by rememberSaveable { mutableStateOf(false) }
                AnimatedContent(
                    targetState = showPrivilegeSettings,
                    label = "screen transition",
                    transitionSpec = {
                        if (targetState) {
                            (slideInHorizontally { it } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it / 4 } + fadeOut())
                        } else {
                            (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
                                (slideOutHorizontally { it } + fadeOut())
                        }
                    },
                ) { settingsVisible ->
                    if (settingsVisible) {
                        PrivilegeSettingsScreen(
                            state = privilegeState,
                            onSelectBackend = viewModel::selectBackend,
                            onRequestAuthorization = viewModel::requestAuthorization,
                            onRefresh = viewModel::refreshPrivilege,
                            onContinue = {
                                showPrivilegeSettings = false
                                startBleIfPermitted()
                            },
                        )
                    } else {
                        HomeScreen(
                            state = state,
                            privilegeState = privilegeState,
                            connectedDevices = connectedDevices,
                            onStart = viewModel::start,
                            onStop = viewModel::stop,
                            onOpenPrivilegeSettings = { showPrivilegeSettings = true },
                        )
                    }
                }
            }
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
