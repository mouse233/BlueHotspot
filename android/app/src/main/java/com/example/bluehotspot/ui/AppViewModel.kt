package com.example.bluehotspot.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import com.example.bluehotspot.tethering.TetheringController
import com.example.bluehotspot.tethering.TetheringState

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
