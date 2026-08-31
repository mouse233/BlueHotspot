package io.github.mouse233.bluehotspot.client.ble

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test

class BleProtocolTest {
    @Test
    fun `round trips fragmented frame including request id`() {
        val id = UUID.randomUUID()
        val encoded = BleFrameCodec.encode(BleFrame(BleMessage.START_HOTSPOT, id))
        val decoder = BleFrameDecoder()

        val frames = encoded.toList().chunked(3).flatMap { decoder.append(it.toByteArray()) }

        assertEquals(listOf(BleFrame(BleMessage.START_HOTSPOT, id)), frames)
    }

    @Test
    fun `decodes multiple frames from one stream`() {
        val first = BleFrameCodec.encode(BleFrame(BleMessage.PING, UUID.randomUUID()))
        val secondId = UUID.randomUUID()
        val second = BleFrameCodec.encode(BleFrame(BleMessage.STATUS, secondId, "ACTIVE"))

        val frames = BleFrameDecoder().append(first + second)

        assertEquals(2, frames.size)
        assertEquals(BleMessage.PING, frames[0].type)
        assertEquals(secondId, frames[1].requestId)
        assertEquals("ACTIVE", frames[1].payload)
    }

    @Test
    fun `rejects oversized payload`() {
        assertFailsWith<IllegalArgumentException> {
            BleFrameCodec.encode(BleFrame(BleMessage.STATUS, UUID.randomUUID(), "x".repeat(513)))
        }
    }

    @Test
    fun `rejects invalid frame length`() {
        assertFailsWith<BleProtocolException> {
            BleFrameDecoder().append(byteArrayOf(0, 17))
        }
    }
}
