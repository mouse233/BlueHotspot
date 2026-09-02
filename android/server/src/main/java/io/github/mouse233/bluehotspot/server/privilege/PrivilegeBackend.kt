package io.github.mouse233.bluehotspot.server.privilege

enum class PrivilegeBackend {
    SHIZUKU,
    ROOT,
}

sealed interface PrivilegeState {
    data object NotChecked : PrivilegeState
    data object ShizukuNotRunning : PrivilegeState
    data object RootUnavailable : PrivilegeState
    data object NotAuthorized : PrivilegeState
    data object Requesting : PrivilegeState
    data object Checking : PrivilegeState
    data object Available : PrivilegeState
    data object Denied : PrivilegeState
    data class Failed(val reason: String) : PrivilegeState
}

data class PrivilegeUiState(
    val selectedBackend: PrivilegeBackend = PrivilegeBackend.SHIZUKU,
    val shizukuState: PrivilegeState = PrivilegeState.NotChecked,
    val rootState: PrivilegeState = PrivilegeState.NotChecked,
) {
    val canContinue: Boolean
        get() = when (selectedBackend) {
            PrivilegeBackend.SHIZUKU -> shizukuState == PrivilegeState.Available
            PrivilegeBackend.ROOT -> rootState == PrivilegeState.Available
        }
}
