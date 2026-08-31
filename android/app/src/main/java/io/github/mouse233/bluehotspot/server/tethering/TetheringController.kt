package io.github.mouse233.bluehotspot.server.tethering

import kotlinx.coroutines.flow.StateFlow

interface TetheringController {
    val state: StateFlow<TetheringState>

    fun start()

    fun stop()
}

sealed interface TetheringState {
    data object Unsupported : TetheringState
    data object Idle : TetheringState
    data object Starting : TetheringState
    data object Active : TetheringState
    data object Stopping : TetheringState
    data class Failed(val reason: String) : TetheringState
}

