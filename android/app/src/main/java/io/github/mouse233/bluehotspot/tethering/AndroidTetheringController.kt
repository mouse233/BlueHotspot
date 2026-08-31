package io.github.mouse233.bluehotspot.tethering

import android.os.Build
import io.github.mouse233.bluehotspot.BlueHotspotApplication
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
    private val _state = MutableStateFlow<TetheringState>(initialState())
    override val state: StateFlow<TetheringState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ownsHotspot = false

    override fun start() {
        if (Build.VERSION.SDK_INT < 36) {
            _state.value = TetheringState.Unsupported
            return
        }
        if (_state.value == TetheringState.Starting || _state.value == TetheringState.Active) return

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
            _state.value = if (result.errorCode == 0) {
                ownsHotspot = true
                TetheringState.Active
            } else {
                TetheringState.Failed("root start error=${result.errorCode}, uid=${result.uid}")
            }
        }
    }

    override fun stop() {
        if (!ownsHotspot || _state.value == TetheringState.Idle) return
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
            _state.value = if (result.errorCode == 0) {
                ownsHotspot = false
                TetheringState.Idle
            } else {
                TetheringState.Failed("root stop error=${result.errorCode}, uid=${result.uid}")
            }
        }
    }

    private fun initialState(): TetheringState =
        if (Build.VERSION.SDK_INT >= 36) TetheringState.Idle else TetheringState.Unsupported
}


