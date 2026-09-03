# HarmonyOS BLE client notes

The HarmonyOS client uses the current `@kit.ConnectivityKit` BLE APIs:

```ts
import { ble } from '@kit.ConnectivityKit';
```

Relevant operations are:

- `ble.startBLEScan()` / `ble.stopBLEScan()`
- `ble.on('BLEDeviceFind', ...)`
- `ble.createGattClientDevice(deviceId)`
- `GattClientDevice.connect()` / `disconnect()` / `close()`
- `GattClientDevice.getServices()`
- `GattClientDevice.setCharacteristicChangeNotification()`
- `GattClientDevice.on('BLECharacteristicChange', ...)`
- `GattClientDevice.writeCharacteristicValue()`
- `GattClientDevice.setBLEMtu()` (API 26; fallback to ATT payload size 20 when negotiation fails)
- `GattClientDevice.getRssiValue()`

The project uses a Service UUID scan filter and the `deviceId` supplied by the
scan result. Service discovery must retain the actual characteristic objects
returned by the SDK, including their descriptors, instead of manufacturing
replacement objects from UUIDs alone.

For interoperability with the current Android server:

- request MTU 247 when supported;
- use at most 180 bytes per write chunk;
- fall back to 20 bytes when MTU negotiation is unavailable;
- use the response-producing `GattWriteType.WRITE` mode;
- serialize all writes;
- feed every notification fragment into the same stream decoder.

The Android server marks control characteristics as encrypted. A connected
state is therefore not proof that pairing has completed. If the SDK exposes no
public pairing operation, the app must surface a pairing-required error and
let the user retry after completing pairing in the system UI.

For protocol bytes, call the current `@kit.ArkTS` `TextEncoder` and
`TextDecoder.create('utf-8', { fatal: true }).decodeToString(...)` APIs, with
strict validation retained for runtimes that silently replace malformed UTF-8.
