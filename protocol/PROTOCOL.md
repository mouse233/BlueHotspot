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

## MVP messages

```text
HELLO
HELLO_ACK
PAIR_REQUEST
PAIR_APPROVED
PAIR_REJECTED
GET_STATUS
STATUS
START_HOTSPOT
HOTSPOT_STARTING
HOTSPOT_READY
HOTSPOT_FAILED
STOP_HOTSPOT
HOTSPOT_STOPPED
PING
PONG
ERROR
```

Every request has a request ID. Implementations must reject malformed frames,
oversized messages, unauthenticated commands, and replayed commands.

The transport must support fragmentation because a BLE characteristic write is
not guaranteed to carry a complete message.
