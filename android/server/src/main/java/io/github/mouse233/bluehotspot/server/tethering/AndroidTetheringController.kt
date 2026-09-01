package io.github.mouse233.bluehotspot.server.tethering

import android.net.TetheringInterface
import android.net.TetheringManager
import android.os.Build
import android.util.Log
import io.github.mouse233.bluehotspot.server.BlueHotspotApplication
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Controls the device's existing Internet hotspot through a KernelSU root
 * app_process. The app never reads or changes the SSID/password.
 */
class AndroidTetheringController(
    private val context: android.content.Context,
) : TetheringController {
    private companion object {
        const val TAG = "BlueHotspotTether"
    }
    private val _state = MutableStateFlow<TetheringState>(initialState())
    override val state: StateFlow<TetheringState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tetheringManager = context.getSystemService(TetheringManager::class.java)
    private val tetheringExecutor = Executor { command -> command.run() }
    private var ownsHotspot = false
    private var externalActive = false

    private val tetheringEventCallback = object : TetheringManager.TetheringEventCallback {
        override fun onTetheredInterfacesChanged(interfaces: Set<TetheringInterface>) {
            val wifiActive = interfaces.any { it.getType() == TetheringManager.TETHERING_WIFI }
            Log.i(TAG, "Tethering interfaces changed: wifiActive=" + wifiActive + ", count=" + interfaces.size)
            externalActive = wifiActive
            if (ownsHotspot) return

            when {
                wifiActive &&
                    _state.value != TetheringState.Starting &&
                    _state.value != TetheringState.Stopping -> {
                    _state.value = TetheringState.ExternalActive
                }
                !wifiActive && _state.value == TetheringState.ExternalActive -> {
                    _state.value = TetheringState.Idle
                }
            }
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= 36) {
            runCatching {
                tetheringManager?.registerTetheringEventCallback(
                    tetheringExecutor,
                    tetheringEventCallback,
                )
            }.onFailure { error ->
                Log.w(TAG, "Unable to register tethering callback", error)
            }
        }
    }

    override fun start() {
        if (Build.VERSION.SDK_INT < 36) {
            _state.value = TetheringState.Unsupported
            return
        }
        if (
            _state.value == TetheringState.Starting ||
            _state.value == TetheringState.Active ||
            _state.value == TetheringState.Stopping
        ) {
            return
        }
        if (externalActive) {
            _state.value = TetheringState.ExternalActive
            return
        }

        _state.value = TetheringState.Starting
        scope.launch {
            val result = runCatching {
                val application = context.applicationContext as BlueHotspotApplication
                application.rootSession.use { it.execute(RootTetheringCommands.Start()) }
            }.getOrElse { error ->
                _state.value = TetheringState.Failed(
                    "root unavailable: ${error.message ?: error.javaClass.simpleName}",
                )
                return@launch
            }
            Log.i(TAG, "Root tethering result: error=" + result.errorCode + ", uid=" + result.uid)
            _state.value = when {
                result.errorCode == 0 -> {
                    ownsHotspot = true
                    TetheringState.Active
                }
                result.errorCode == TetheringManager.TETHER_ERROR_DUPLICATE_REQUEST -> {
                    externalActive = true
                    TetheringState.ExternalActive
                }
                else -> TetheringState.Failed(
                    "root start error=${result.errorCode}, uid=${result.uid}",
                )
            }
        }
    }

    override fun stop() {
        val current = _state.value
        if (
            current == TetheringState.Idle ||
            current == TetheringState.Unsupported ||
            current == TetheringState.Starting ||
            current == TetheringState.Stopping ||
            current is TetheringState.Failed ||
            (!ownsHotspot && current != TetheringState.ExternalActive)
        ) {
            return
        }

        _state.value = TetheringState.Stopping
        scope.launch {
            val result = runCatching {
                val application = context.applicationContext as BlueHotspotApplication
                application.rootSession.use { it.execute(RootTetheringCommands.Stop()) }
            }.getOrElse { error ->
                _state.value = TetheringState.Failed(
                    "root unavailable: ${error.message ?: error.javaClass.simpleName}",
                )
                return@launch
            }
            Log.i(TAG, "Root tethering result: error=" + result.errorCode + ", uid=" + result.uid)
            _state.value = when {
                result.errorCode == 0 -> {
                    ownsHotspot = false
                    externalActive = false
                    TetheringState.Idle
                }
                result.errorCode == TetheringManager.TETHER_ERROR_UNKNOWN_REQUEST &&
                    externalActive -> {
                    TetheringState.ExternalActive
                }
                else -> TetheringState.Failed(
                    "root stop error=${result.errorCode}, uid=${result.uid}",
                )
            }
        }
    }

    private fun initialState(): TetheringState =
        if (Build.VERSION.SDK_INT >= 36) TetheringState.Idle else TetheringState.Unsupported
}
