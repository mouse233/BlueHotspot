package io.github.mouse233.bluehotspot.server.privilege

import android.content.Context
import android.net.TetheringManager
import io.github.mouse233.bluehotspot.server.BlueHotspotApplication
import io.github.mouse233.bluehotspot.server.settings.PrivilegeSettingsRepository
import io.github.mouse233.bluehotspot.server.tethering.RootTetheringBackend
import io.github.mouse233.bluehotspot.server.tethering.ShizukuTetheringBackend
import io.github.mouse233.bluehotspot.server.tethering.TetheringBackend
import io.github.mouse233.bluehotspot.server.tethering.TetheringBackendResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PrivilegeController(
    context: Context,
    application: BlueHotspotApplication,
    private val settings: PrivilegeSettingsRepository,
) {
    private val shizukuBackend = ShizukuTetheringBackend(context)
    private val rootBackend = RootTetheringBackend(application)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val operationMutex = Mutex()
    private val _uiState = MutableStateFlow(
        PrivilegeUiState(selectedBackend = settings.selectedBackend.value),
    )

    val uiState: StateFlow<PrivilegeUiState> = _uiState.asStateFlow()

    val selectedBackend: PrivilegeBackend
        get() = settings.selectedBackend.value

    init {
        refresh(settings.selectedBackend.value)
    }

    fun selectBackend(backend: PrivilegeBackend) {
        settings.setSelectedBackend(backend)
        _uiState.update { it.copy(selectedBackend = backend) }
    }

    fun requestAuthorization(backend: PrivilegeBackend) {
        runOperation(backend, requestAuthorization = true)
    }

    fun refresh(backend: PrivilegeBackend) {
        runOperation(backend, requestAuthorization = false)
    }

    internal fun backendFor(backend: PrivilegeBackend): TetheringBackend = when (backend) {
        PrivilegeBackend.SHIZUKU -> shizukuBackend
        PrivilegeBackend.ROOT -> rootBackend
    }

    private fun runOperation(backend: PrivilegeBackend, requestAuthorization: Boolean) {
        val current = stateFor(backend)
        if (current == PrivilegeState.Requesting || current == PrivilegeState.Checking) return

        setState(backend, if (requestAuthorization) PrivilegeState.Requesting else PrivilegeState.Checking)
        scope.launch {
            operationMutex.withLock {
                runCatching {
                    val selected = backendFor(backend)
                    if (requestAuthorization) {
                        val authorization = selected.requestAuthorization()
                        if (authorization.errorCode != TetheringManager.TETHER_ERROR_NO_ERROR) {
                            setState(backend, stateFromResult(backend, authorization))
                            return@withLock
                        }
                    }
                    setState(backend, PrivilegeState.Checking)
                    val check = selected.checkAvailability()
                    if (check.errorCode == TetheringManager.TETHER_ERROR_NO_ERROR) {
                        setState(backend, PrivilegeState.Available)
                    } else {
                        setState(backend, stateFromResult(backend, check))
                    }
                }.onFailure { error ->
                    setState(backend, stateFromFailure(backend, error))
                }
            }
        }
    }

    private fun stateFor(backend: PrivilegeBackend): PrivilegeState = when (backend) {
        PrivilegeBackend.SHIZUKU -> _uiState.value.shizukuState
        PrivilegeBackend.ROOT -> _uiState.value.rootState
    }

    private fun setState(backend: PrivilegeBackend, state: PrivilegeState) {
        _uiState.update {
            when (backend) {
                PrivilegeBackend.SHIZUKU -> it.copy(shizukuState = state)
                PrivilegeBackend.ROOT -> it.copy(rootState = state)
            }
        }
    }

    private fun stateFromResult(backend: PrivilegeBackend, result: TetheringBackendResult): PrivilegeState {
        if (backend == PrivilegeBackend.ROOT && result.errorCode == TetheringBackendResult.ERROR_NOT_ROOT) {
            return PrivilegeState.RootUnavailable
        }
        return PrivilegeState.Failed(result.detail.ifBlank { "Capability test failed (${result.errorCode})" })
    }

    private fun stateFromFailure(backend: PrivilegeBackend, error: Throwable): PrivilegeState {
        val message = (error.message ?: error.javaClass.simpleName).trim()
        val normalized = message.lowercase()
        return when {
            backend == PrivilegeBackend.SHIZUKU && normalized.contains("not running") -> {
                PrivilegeState.ShizukuNotRunning
            }
            backend == PrivilegeBackend.SHIZUKU && normalized.contains("not granted") -> {
                PrivilegeState.NotAuthorized
            }
            normalized.contains("permission") &&
                (normalized.contains("denied") || normalized.contains("reject")) -> {
                PrivilegeState.Denied
            }
            backend == PrivilegeBackend.ROOT &&
                (normalized.contains("root missing") || normalized.contains("no root") ||
                    normalized.contains("uid=0") || normalized.contains("not root")) -> {
                PrivilegeState.RootUnavailable
            }
            else -> PrivilegeState.Failed(message)
        }
    }

}
