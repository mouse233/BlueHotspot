package com.example.bluehotspot.tethering

import android.net.TetheringManager`nimport androidx.core.content.ContextCompat
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executor

/**
 * Minimal controller for the Android system-configured Wi-Fi tethering profile.
 * It deliberately does not inspect or change the SSID/password.
 */
class AndroidTetheringController(
    context: android.content.Context
) : TetheringController {
    private val _state = MutableStateFlow<TetheringState>(initialState())
    override val state: StateFlow<TetheringState> = _state.asStateFlow()

    private val executor: Executor = ContextCompat.getMainExecutor(context)
    private val manager: TetheringManager? = if (Build.VERSION.SDK_INT >= 36) {
        context.getSystemService(TetheringManager::class.java)
    } else {
        null
    }

    private var ownedRequest: TetheringManager.TetheringRequest? = null

    override fun start() {
        if (Build.VERSION.SDK_INT < 36 || manager == null) {
            _state.value = TetheringState.Unsupported
            return
        }

        if (_state.value == TetheringState.Starting || _state.value == TetheringState.Active) {
            return
        }

        val request = TetheringManager.TetheringRequest.Builder(
            TetheringManager.TETHERING_WIFI
        ).build()
        // No SoftApConfiguration is supplied: the privileged system caller uses
        // the hotspot profile already configured in Android system settings.

        ownedRequest = request
        _state.value = TetheringState.Starting

        try {
            manager.startTethering(
                request,
                executor,
                object : TetheringManager.StartTetheringCallback {
                    override fun onTetheringStarted() {
                        _state.value = TetheringState.Active
                    }

                    override fun onTetheringFailed(error: Int) {
                        ownedRequest = null
                        _state.value = TetheringState.Failed("start error=$error")
                    }
                }
            )
        } catch (error: SecurityException) {
            ownedRequest = null
            _state.value = TetheringState.Failed("privileged tethering permission required")
        } catch (error: RuntimeException) {
            ownedRequest = null
            _state.value = TetheringState.Failed(error.message ?: "unable to start tethering")
        }
    }

    override fun stop() {
        val request = ownedRequest
        if (manager == null || request == null) {
            _state.value = TetheringState.Idle
            return
        }

        _state.value = TetheringState.Stopping
        try {
            manager.stopTethering(
                request,
                executor,
                object : TetheringManager.StopTetheringCallback {
                    override fun onStopTetheringSucceeded() {
                        ownedRequest = null
                        _state.value = TetheringState.Idle
                    }

                    override fun onStopTetheringFailed(error: Int) {
                        _state.value = TetheringState.Failed("stop error=$error")
                    }
                }
            )
        } catch (error: SecurityException) {
            _state.value = TetheringState.Failed("privileged tethering permission required")
        } catch (error: RuntimeException) {
            _state.value = TetheringState.Failed(error.message ?: "unable to stop tethering")
        }
    }

    private fun initialState(): TetheringState =
        if (Build.VERSION.SDK_INT >= 36) TetheringState.Idle else TetheringState.Unsupported
}
