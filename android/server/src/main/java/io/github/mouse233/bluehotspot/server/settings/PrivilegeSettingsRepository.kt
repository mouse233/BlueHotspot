package io.github.mouse233.bluehotspot.server.settings

import android.content.Context
import io.github.mouse233.bluehotspot.server.privilege.PrivilegeBackend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persists the user's explicit privilege backend choice. */
class PrivilegeSettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val _selectedBackend = MutableStateFlow(readSelectedBackend())

    val selectedBackend: StateFlow<PrivilegeBackend> = _selectedBackend.asStateFlow()

    fun setSelectedBackend(backend: PrivilegeBackend) {
        preferences.edit()
            .putString(KEY_SELECTED_BACKEND, backend.name)
            .apply()
        _selectedBackend.value = backend
    }

    private fun readSelectedBackend(): PrivilegeBackend =
        preferences.getString(KEY_SELECTED_BACKEND, null)
            ?.let { value -> runCatching { PrivilegeBackend.valueOf(value) }.getOrNull() }
            ?: PrivilegeBackend.SHIZUKU

    private companion object {
        const val FILE_NAME = "bluehotspot_settings"
        const val KEY_SELECTED_BACKEND = "selected_privilege_backend"
    }
}
