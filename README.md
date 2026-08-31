# BlueHotspot

BlueHotspot is a native Android + iOS utility that lets a trusted iPhone
control an Android device's already-configured Wi-Fi hotspot over encrypted
Bluetooth Low Energy (BLE).

The project is split into a hotspot server and controller clients:

- Android server — advertises the BLE GATT service and controls the Android
  hotspot through the existing root-powered tethering integration.
- iOS client — scans for BlueHotspot Android devices, lets the user choose a
  device, connects over BLE, and sends hotspot commands.
- Android client — scans for the Android server and controls its configured hotspot over encrypted BLE.

## Current status

The MVP control loop is implemented:

- Android starts and stops the existing Wi-Fi hotspot through root commands.
- Android exposes authenticated BLE GATT start, stop, and status operations.
- BLE command and event characteristics require an encrypted link.
- Android keeps the BLE endpoint alive in a connected-device foreground service.
- iOS displays discovered Android devices, signal strength, connection state,
  and hotspot state.
- Android displays connected iPhone devices in its local UI.
- Android client displays nearby BlueHotspot servers, pairs through the system Bluetooth flow, and controls the server hotspot.
- The iOS home screen uses Liquid Glass on iOS 26 and a material fallback on
  older supported iOS versions.
- Protocol framing, fragmentation, and Android unit tests are included.

The MVP deliberately does not read, generate, transmit, or store the hotspot
SSID or passphrase. It only controls the hotspot already configured on the
Android device and reports the result.

## Package identifiers

- Android server: `io.github.mouse233.bluehotspot.server`
- Android client: `io.github.mouse233.bluehotspot.client`
- iOS client: `io.github.mouse233.bluehotspot.client.ios`

## Requirements

### Android server

- Android API 36 or newer.
- A rooted Android device with the required tethering capability.
- Bluetooth permissions and an enabled Bluetooth adapter.
- The Android app must remain installed and its BLE foreground service must be
  allowed to run.

### iOS client

- iOS 17 or newer.
- Bluetooth permission.
- iOS 26 or newer to display the native Liquid Glass appearance.

## Build and test

Android can be validated on the development machine:

```powershell
cd android
.\gradlew.bat test lint assembleDebug
```

The iOS project is generated with XcodeGen and built by the macOS GitHub
Actions workflow. It produces an unsigned `BlueHotspot-unsigned-ipa` artifact
for compile/package validation. The artifact is not installable on a device
until Apple signing certificates, provisioning profiles, and a separate
signing workflow are configured.

## Repository layout

- `android/` — Android server (`:app`) and Android controller client (`:client`) applications.
- `ios/` — SwiftUI iOS controller and XcodeGen specification.
- `protocol/` — platform-neutral BLE protocol and framing documentation.
- `docs/` — architecture and project scope documentation.

## Security and scope notes

Version 1 uses an encrypted, bonded BLE link and encrypted GATT
characteristic permissions as its authentication boundary. Full app-level
identity management, trusted-peer revocation, challenge-response, and replay
protection are reserved for a later iteration.

The complete product vision may eventually include Internet connectivity
verification, hotspot credential management, and broader tethering workflows.
Those features are intentionally outside the current MVP.
