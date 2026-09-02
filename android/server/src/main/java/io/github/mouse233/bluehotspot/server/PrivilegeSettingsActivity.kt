package io.github.mouse233.bluehotspot.server

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.mouse233.bluehotspot.server.ui.AppViewModel
import io.github.mouse233.bluehotspot.server.ui.PrivilegeSettingsScreen
import io.github.mouse233.bluehotspot.server.ui.theme.BlueHotspotTheme

/** Settings is a real Android screen so the system owns its enter/exit transition. */
class PrivilegeSettingsActivity : ComponentActivity() {
    private val app: BlueHotspotApplication
        get() = application as BlueHotspotApplication

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlueHotspotTheme {
                val privilegeState by viewModel.privilegeState.collectAsState()
                PrivilegeSettingsScreen(
                    state = privilegeState,
                    onSelectBackend = viewModel::selectBackend,
                    onRequestAuthorization = viewModel::requestAuthorization,
                    onRefresh = viewModel::refreshPrivilege,
                    onContinue = ::finish,
                )
            }
        }
    }
}
