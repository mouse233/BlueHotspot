package io.github.mouse233.bluehotspot.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import io.github.mouse233.bluehotspot.tethering.TetheringController
import io.github.mouse233.bluehotspot.tethering.TetheringState

class AppViewModel(
    private val controller: TetheringController
) : ViewModel() {
    val state: StateFlow<TetheringState> = controller.state

    fun start() {
        controller.start()
    }

    fun stop() {
        controller.stop()
    }
}

