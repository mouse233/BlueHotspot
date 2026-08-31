# BlueHotspot BLE Protocol v1

## MVP scope

The iPhone is the BLE central and Android is the BLE peripheral/GATT server.
The protocol controls the Android device's system-configured Wi-Fi hotspot.

Hotspot SSID and passphrase are deliberately outside this protocol. BlueHotspot
does not read, create, modify, transmit, or persist them.

## Characteristics

- Device Info: Android → iPhone, read-only.
- Command: iPhone → Android, write.
- Event: Android → iPhone, notify.
- Pairing: bidirectional, used during first trust establishment.

The Android GATT server requires an encrypted bonded Bluetooth link for all
control characteristics. The platform pairing prompt is the first-trust step;
implementations must not use a Bluetooth MAC address as an identity decision.

## MVP messages

```text
HELLO HELLO_ACK GET_STATUS STATUS START_HOTSPOT HOTSPOT_STARTING
HOTSPOT_READY HOTSPOT_FAILED STOP_HOTSPOT HOTSPOT_STOPPED PING PONG ERROR
```

Every request has a request ID. Implementations must reject malformed frames,
oversized messages, unauthenticated commands, and replayed commands.

## Binary framing

Characteristic writes and notifications carry a stream of frames. A frame is
prefixed by a two-byte unsigned big-endian body length. The body is:

| Offset | Size | Meaning |
| --- | ---: | --- |
| 0 | 1 | Protocol version (`1`) |
| 1 | 1 | Message type code |
| 2 | 16 | Request ID, UUID bytes in network order |
| 18 | N | UTF-8 payload, at most 512 bytes |

The length prefix is part of the stream, not part of each BLE write. Receivers
buffer incomplete frames and may return multiple frames from one write. A body
larger than 530 bytes, an unknown type, or invalid UTF-8 payload is rejected
with `INVALID_MESSAGE`.

| Code | Message |
| ---: | --- |
| 1 | `HELLO` |
| 2 | `HELLO_ACK` |
| 3 | `GET_STATUS` |
| 4 | `STATUS` |
| 5 | `START_HOTSPOT` |
| 6 | `HOTSPOT_STARTING` |
| 7 | `HOTSPOT_READY` |
| 8 | `HOTSPOT_FAILED` |
| 9 | `STOP_HOTSPOT` |
| 10 | `HOTSPOT_STOPPED` |
| 11 | `PING` |
| 12 | `PONG` |
| 13 | `ERROR` |

`HELLO` and `GET_STATUS` have an empty payload. `STATUS` and hotspot events
use `IDLE`, `STARTING`, `ACTIVE`, `STOPPING`, `FAILED`, or `UNSUPPORTED`.
Error payloads use the stable names in `ERROR_CODES.md`.

`PAIR_REQUEST`, `PAIR_APPROVED`, and `PAIR_REJECTED` are reserved for a future
app-level trust flow; BLE link encryption is the v1 authentication boundary.
