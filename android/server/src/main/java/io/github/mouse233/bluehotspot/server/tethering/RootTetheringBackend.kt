package io.github.mouse233.bluehotspot.server.tethering

import io.github.mouse233.bluehotspot.server.BlueHotspotApplication

internal class RootTetheringBackend(
    private val application: BlueHotspotApplication,
) : TetheringBackend {
    override val name: String = "root"

    override suspend fun requestAuthorization(): TetheringBackendResult =
        checkAvailability()

    override suspend fun checkAvailability(): TetheringBackendResult =
        application.rootSession.use { session ->
            session.execute(RootTetheringCommands.Check()).toBackendResult()
        }

    override suspend fun start(): TetheringBackendResult =
        application.rootSession.use { session ->
            session.execute(RootTetheringCommands.Start()).toBackendResult()
        }

    override suspend fun stop(): TetheringBackendResult =
        application.rootSession.use { session ->
            session.execute(RootTetheringCommands.Stop()).toBackendResult()
        }

    private fun RootTetheringResult.toBackendResult(): TetheringBackendResult =
        TetheringBackendResult(
            errorCode = errorCode,
            uid = uid,
            detail = "root uid=$uid",
        )
}
