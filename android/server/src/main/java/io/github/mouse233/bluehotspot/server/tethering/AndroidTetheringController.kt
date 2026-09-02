package io.github.mouse233.bluehotspot.server.tethering

import android.net.TetheringInterface
import android.net.TetheringManager
import android.os.Build
import android.util.Log
import io.github.mouse233.bluehotspot.server.BlueHotspotApplication
import io.github.mouse233.bluehotspot.server.privilege.PrivilegeController
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Controls the device's existing Internet hotspot through the user-selected
 * Shizuku or Root backend. The app never reads or changes the SSID/password.
 */
class AndroidTetheringController(
    private val context: android.content.Context,
    private val privileges: PrivilegeController,
) : TetheringController {
    private companion object {
        const val TAG = "BlueHotspotTether"
    }
    private val _state = MutableStateFlow<TetheringState>(initialState())
    override val state: StateFlow<TetheringState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val application = context.applicationContext as BlueHotspotApplication
    private val tetheringManager = context.getSystemService(TetheringManager::class.java)
    private val tetheringExecutor = Executor { command -> command.run() }
    private var ownsHotspot = false
    private var externalActive = false
    private var activeBackend: TetheringBackend? = null

    private val tetheringEventCallback = object : TetheringManager.TetheringEventCallback {
        override fun onTetheredInterfacesChanged(interfaces: Set<TetheringInterface>) {
            val wifiActive = interfaces.any { it.getType() == TetheringManager.TETHERING_WIFI }
            Log.i(TAG, "Tethering interfaces changed: wifiActive=" + wifiActive + ", count=" + interfaces.size)
            externalActive = wifiActive
            if (ownsHotspot) {
                if (!wifiActive) {
                    ownsHotspot = false
                    activeBackend = null
                    _state.value = TetheringState.Idle
                }
                return
            }

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
            val backend = privileges.backendFor(privileges.selectedBackend)
            val attempt = runCatching { backend.start() }
            val result = attempt.getOrNull()
            if (result == null) {
                _state.value = TetheringState.Failed(
                    "${backend.name}: ${attempt.exceptionOrNull().describe()}",
                )
                return@launch
            }
            Log.i(
                TAG,
                "${backend.name} tethering start: error=${result.errorCode}, uid=${result.uid}",
            )
            when {
                result.errorCode == TetheringManager.TETHER_ERROR_NO_ERROR -> {
                    ownsHotspot = true
                    activeBackend = backend
                    _state.value = TetheringState.Active
                }
                result.errorCode == TetheringManager.TETHER_ERROR_DUPLICATE_REQUEST -> {
                    externalActive = true
                    activeBackend = null
                    _state.value = TetheringState.ExternalActive
                }
                result.errorCode == TetheringBackendResult.ERROR_OPERATION_UNCERTAIN -> {
                    _state.value = TetheringState.Failed(
                        "${backend.name}: ${result.detail}; check the hotspot before retrying",
                    )
                }
                else -> {
                    _state.value = TetheringState.Failed(
                        "${backend.name}: error=${result.errorCode} (${result.detail})",
                    )
                }
            }
        }
    }

    override fun stop() {
        val current = _state.value
        if (
            current != TetheringState.Active || !ownsHotspot
        ) {
            return
        }

        _state.value = TetheringState.Stopping
        scope.launch {
            val backend = activeBackend
            if (backend == null) {
                ownsHotspot = false
                _state.value = if (externalActive) TetheringState.ExternalActive else TetheringState.Idle
                return@launch
            }
            val attempt = runCatching { backend.stop() }
            val result = attempt.getOrNull()
            if (result == null) {
                Log.e(TAG, "${backend.name} tethering stop failed", attempt.exceptionOrNull())
                _state.value = TetheringState.Active
                return@launch
            }
            Log.i(
                TAG,
                "${backend.name} tethering stop: error=${result.errorCode}, uid=${result.uid}",
            )
            _state.value = when {
                result.errorCode == TetheringManager.TETHER_ERROR_NO_ERROR -> {
                    ownsHotspot = false
                    externalActive = false
                    activeBackend = null
                    TetheringState.Idle
                }
                result.errorCode == TetheringManager.TETHER_ERROR_UNKNOWN_REQUEST -> {
                    ownsHotspot = false
                    activeBackend = null
                    if (externalActive) TetheringState.ExternalActive else TetheringState.Idle
                }
                else -> {
                    Log.e(TAG, "${backend.name} stop error=${result.errorCode}: ${result.detail}")
                    TetheringState.Active
                }
            }
        }
    }

    private fun initialState(): TetheringState =
        if (Build.VERSION.SDK_INT >= 36) TetheringState.Idle else TetheringState.Unsupported

    private fun Throwable?.describe(): String =
        this?.let { "${it.javaClass.simpleName}: ${it.message ?: "no message"}" }
            ?: "unknown failure"
}
