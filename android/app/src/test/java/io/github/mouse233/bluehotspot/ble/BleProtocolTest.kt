package io.github.mouse233.bluehotspot.ble

import java.util.UUID
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BleProtocolTest {
    @Test
    fun `round trips fragmented frame`() {
        val id = UUID.randomUUID()
        val encoded = BleFrameCodec.encode(BleFrame(BleMessage.START_HOTSPOT, id, ""))
        val decoder = BleFrameDecoder()
        val frames = encoded.toList().chunked(3).flatMap { decoder.append(it.toByteArray()) }

        assertEquals(listOf(BleFrame(BleMessage.START_HOTSPOT, id, "")), frames)
    }

    @Test
    fun `decodes multiple frames from one stream`() {
        val first = BleFrameCodec.encode(BleFrame(BleMessage.PING, UUID.randomUUID(), ""))
        val second = BleFrameCodec.encode(BleFrame(BleMessage.STATUS, UUID.randomUUID(), "ACTIVE"))
        val decoder = BleFrameDecoder()

        val frames = decoder.append(first + second)

        assertEquals(2, frames.size)
        assertEquals(BleMessage.PING, frames[0].type)
        assertEquals(BleMessage.STATUS, frames[1].type)
        assertEquals("ACTIVE", frames[1].payload)
    }

    @Test
    fun `rejects oversized payload`() {
        assertFailsWith<IllegalArgumentException> {
            BleFrameCodec.encode(BleFrame(BleMessage.STATUS, UUID.randomUUID(), "x".repeat(513)))
        }
    }
}


