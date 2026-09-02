package io.github.mouse233.bluehotspot.server.tethering

internal data class TetheringBackendResult(
    val errorCode: Int,
    val uid: Int,
    val detail: String,
) {
    companion object {
        const val ERROR_OPERATION_UNCERTAIN = -10_000
    }
}

internal interface TetheringBackend {
    val name: String

    suspend fun start(): TetheringBackendResult

    suspend fun stop(): TetheringBackendResult
}
