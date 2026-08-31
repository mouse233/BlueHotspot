package io.github.mouse233.bluehotspot.client.ble

import java.util.UUID

internal object BleUuids {
    val SERVICE: UUID = UUID.fromString("8b5f0001-7d3e-4e4a-9f1c-4b20f4e9a001")
    val COMMAND: UUID = UUID.fromString("8b5f0003-7d3e-4e4a-9f1c-4b20f4e9a001")
    val EVENT: UUID = UUID.fromString("8b5f0004-7d3e-4e4a-9f1c-4b20f4e9a001")
    val CLIENT_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
