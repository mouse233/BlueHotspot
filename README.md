# BlueHotspot


BlueHotspot is a native Android + iOS utility that lets an iPhone
control an Android device's already-configured Wi-Fi hotspot over encrypted
Bluetooth Low Energy (BLE). The Android server requires ROOT access.


<a href="https://github.com/mouse233/BlueHotspot/releases/latest"><img src="https://raw.githubusercontent.com/rubenpgrady/get-it-on-github/main/get-it-on-github.png" alt="Get it on GitHub" height="60"></a>

The project is split into a hotspot server and controller clients.

- Android server — runs on Android and controls the configured hotspot.
- iOS client — controls an Android server over BLE.
- Android client — controls an Android server over BLE.

## Requirements

### Android server

- Android 16 (API 36) or newer.
- A KernelSU-compatible ROOT-enabled Android device with the required tethering capability.
- Bluetooth permissions and an enabled Bluetooth adapter.
- The Android app must remain installed and its BLE foreground service must be
  allowed to run.

### Android client

- Android 16 (API 36) or newer.
- Bluetooth permissions and an enabled Bluetooth adapter.

### iOS client

- iOS 17 or newer.
- Bluetooth permission.

## Current status

The MVP control loop is implemented across the Android server, Android client,
and iOS client. Encrypted BLE is used to discover devices and start, stop, and
report the status of the Android device's already-configured Wi-Fi hotspot. The
MVP does not read, generate, transmit, or store the hotspot SSID or passphrase.

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

## Package identifiers

- Android server: `io.github.mouse233.bluehotspot.server`
- Android client: `io.github.mouse233.bluehotspot.client`
- iOS client: `io.github.mouse233.bluehotspot.client.ios`

## Repository layout

- `android/` — Android server (`:server`) and Android controller client (`:client`) applications.
- `ios/` — SwiftUI iOS controller and XcodeGen specification.
- `protocol/` — platform-neutral BLE protocol and framing documentation.
- `docs/` — architecture and project scope documentation.

## Security and scope notes

Version 1 uses an encrypted BLE link and encrypted GATT
characteristic permissions as its authentication boundary. Full app-level
identity management, trusted-peer revocation, challenge-response, and replay
protection are reserved for a later iteration.

The complete product vision may eventually include Internet connectivity
verification, hotspot credential management, and broader tethering workflows.
Those features are intentionally outside the current MVP.
