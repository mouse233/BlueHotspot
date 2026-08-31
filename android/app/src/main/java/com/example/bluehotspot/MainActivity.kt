package com.example.bluehotspot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.bluehotspot.tethering.AndroidTetheringController
import com.example.bluehotspot.ui.AppViewModel
import com.example.bluehotspot.ui.HomeScreen

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<AppViewModel> {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppViewModel(
                    AndroidTetheringController(applicationContext)
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsState()
            HomeScreen(
                state = state,
                onStart = viewModel::start,
                onStop = viewModel::stop
            )
        }
    }
}
