package io.github.mouse233.bluehotspot.server.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import java.util.UUID

private const val PROTOCOL_VERSION = 1
private const val HEADER_SIZE = 18
private const val MAX_PAYLOAD_SIZE = 512
private const val MAX_BODY_SIZE = HEADER_SIZE + MAX_PAYLOAD_SIZE

internal enum class BleMessage(val code: Int) {
    HELLO(1), HELLO_ACK(2), GET_STATUS(3), STATUS(4), START_HOTSPOT(5),
    HOTSPOT_STARTING(6), HOTSPOT_READY(7), HOTSPOT_FAILED(8), STOP_HOTSPOT(9),
    HOTSPOT_STOPPED(10), PING(11), PONG(12), ERROR(13);

    companion object {
        fun fromCode(code: Int): BleMessage = entries.firstOrNull { it.code == code }
            ?: throw BleProtocolException("unknown message type: $code")
    }
}

internal data class BleFrame(
    val type: BleMessage,
    val requestId: UUID,
    val payload: String = "",
)

internal class BleProtocolException(message: String) : Exception(message)

internal object BleFrameCodec {
    fun encode(frame: BleFrame): ByteArray {
        val payload = frame.payload.toByteArray(Charsets.UTF_8)
        require(payload.size <= MAX_PAYLOAD_SIZE) { "payload is too large" }
        val body = ByteBuffer.allocate(HEADER_SIZE + payload.size).order(ByteOrder.BIG_ENDIAN)
            .put(PROTOCOL_VERSION.toByte())
            .put(frame.type.code.toByte())
            .putLong(frame.requestId.mostSignificantBits)
            .putLong(frame.requestId.leastSignificantBits)
            .put(payload)
            .array()
        return ByteBuffer.allocate(body.size + 2).order(ByteOrder.BIG_ENDIAN)
            .putShort(body.size.toShort())
            .put(body)
            .array()
    }

    fun decode(body: ByteArray): BleFrame {
        if (body.size !in HEADER_SIZE..MAX_BODY_SIZE) {
            throw BleProtocolException("invalid body length: ${body.size}")
        }
        val buffer = ByteBuffer.wrap(body).order(ByteOrder.BIG_ENDIAN)
        if (buffer.get().toInt() != PROTOCOL_VERSION) {
            throw BleProtocolException("unsupported protocol version")
        }
        val type = BleMessage.fromCode(buffer.get().toInt() and 0xff)
        val requestId = UUID(buffer.long, buffer.long)
        val payloadBytes = ByteArray(buffer.remaining())
        buffer.get(payloadBytes)
        val payload = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(payloadBytes))
                .toString()
        } catch (error: Exception) {
            throw BleProtocolException("payload is not valid UTF-8: ${error.message}")
        }
        return BleFrame(type, requestId, payload)
    }
}

/** Reassembles frames when a characteristic write contains only a fragment. */
internal class BleFrameDecoder {
    private var buffer = ByteArray(0)

    fun append(fragment: ByteArray): List<BleFrame> {
        if (fragment.isEmpty()) return emptyList()
        if (buffer.size + fragment.size > MAX_BODY_SIZE * 4) {
            buffer = ByteArray(0)
            throw BleProtocolException("frame stream is too large")
        }
        buffer += fragment
        val frames = mutableListOf<BleFrame>()
        while (buffer.size >= 2) {
            val bodyLength = ((buffer[0].toInt() and 0xff) shl 8) or
                (buffer[1].toInt() and 0xff)
            if (bodyLength !in HEADER_SIZE..MAX_BODY_SIZE) {
                buffer = ByteArray(0)
                throw BleProtocolException("invalid frame length: $bodyLength")
            }
            if (buffer.size < bodyLength + 2) break
            val body = buffer.copyOfRange(2, bodyLength + 2)
            buffer = buffer.copyOfRange(bodyLength + 2, buffer.size)
            frames += BleFrameCodec.decode(body)
        }
        return frames
    }
}

