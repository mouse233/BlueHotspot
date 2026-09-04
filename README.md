# BlueHotspot

BlueHotspot lets a controller device start, stop, and inspect the status of an
Android device's already-configured Wi-Fi hotspot over encrypted Bluetooth Low
Energy (BLE). It never reads, generates, stores, or transmits the hotspot SSID
or passphrase.

The Android server uses the permission method explicitly selected in its
settings: a verified Shizuku session or a KernelSU-compatible ROOT-enabled
device. It does not silently fall back from one method to the other.

<a href="https://github.com/mouse233/BlueHotspot/releases/latest"><img src="https://raw.githubusercontent.com/rubenpgrady/get-it-on-github/main/get-it-on-github.png" alt="Get it on GitHub" height="60"></a>

## Components and status

| Component | Role | Status |
| --- | --- | --- |
| Android server | Controls the Android system hotspot and exposes the BLE GATT service. | Implemented |
| Android client | Controls an Android server over BLE. | Implemented |
| iOS client | Controls an Android server over BLE. | Implemented; CI produces an unsigned IPA |
| HarmonyOS NEXT client | Native ArkTS/ArkUI BLE controller. | **Experimental**; CI builds it, but on-device BLE testing is pending |

## Requirements

### Android server

- Android 16 (API 36) or newer.
- Bluetooth permission and an enabled Bluetooth adapter.
- A Shizuku v13+ session through wireless debugging, or a KernelSU-compatible
  ROOT-enabled device, depending on the permission method selected in the app.
- Permission for the BLE foreground service to keep running.

The `TetheringManager` route requires API 36+ and may require a privileged or
system install. Declaring `TETHER_PRIVILEGED` does not grant it to a normal APK.
Shizuku support also depends on the device ROM granting the shell user that
permission.

### Controller clients

- **Android:** Android 16 (API 36)+ with Bluetooth enabled.
- **iOS:** iOS 17+ with Bluetooth permission.
- **HarmonyOS NEXT:** a BLE-capable HarmonyOS NEXT / OpenHarmony device, a
  compatible DevEco Studio and SDK, and Bluetooth permission. This client is
  experimental and has not yet received on-device BLE validation.

## What the protocol does

The controller discovers the Android server's encrypted GATT service, pairs at
the system level, and sends a small set of versioned commands:

`HELLO`, `GET_STATUS`, `START_HOTSPOT`, `STOP_HOTSPOT`, and `PING`.

The Android server returns status, lifecycle events, and stable error names.
BLE link encryption and bonding are the v1 authentication boundary. Full
application-level identity management, trusted-peer revocation,
challenge-response, and replay protection are deferred to a later iteration.

The canonical protocol documents are:

- [Protocol framing and commands](docs/protocol/PROTOCOL.md)
- [GATT service and characteristic UUIDs](docs/protocol/UUIDS.md)
- [Stable error codes](docs/protocol/ERROR_CODES.md)

## Build and CI

### Android

The development machine is Windows. From `android/`, use the checked-in Gradle
Wrapper:

```powershell
.\gradlew.bat test lint assembleDebug
```

GitHub Actions runs Android lint, unit tests, and debug builds. It uploads
`BlueHotspot-android-server-debug` and `BlueHotspot-android-client-debug`.
For Android implementation and privileged-backend details, see
[android/README.md](android/README.md).

### iOS

The iOS project is generated from `ios/project.yml` with XcodeGen and must be
built on macOS. The `Build iOS IPA` workflow compiles and packages an unsigned
`BlueHotspot-unsigned-ipa` artifact. It is a compile/package check only: it
cannot be installed until Apple signing certificates, provisioning profiles,
and a signing workflow are configured. See [ios/README.md](ios/README.md).

### HarmonyOS NEXT

Open `harmonyos/` with DevEco Studio and use the compatible SDK selected by the
IDE. The `HarmonyOS CI` workflow downloads a pinned command-line toolchain,
verifies its SHA-256 digest, installs dependencies, and builds unsigned HAP and
APP artifacts named `BlueHotspot-harmonyos-unsigned`.

This is build validation, not device validation: the client still needs
on-device BLE testing, and its existing Hypium tests require a
DevEco-compatible local or device test runtime. See
[HarmonyOS reference notes](docs/harmonyos/README.md).

## Package identifiers

- Android server: `io.github.mouse233.bluehotspot.server`
- Android client: `io.github.mouse233.bluehotspot.client`
- iOS client: `io.github.mouse233.bluehotspot.client.ios`
- HarmonyOS NEXT client: `io.github.mouse233.bluehotspot.client.harmonyos`

## Repository layout

```text
android/          Android server and Android controller client
ios/              SwiftUI iOS controller and XcodeGen specification
harmonyos/        ArkTS/ArkUI HarmonyOS NEXT controller (experimental)
docs/protocol/    Platform-neutral BLE protocol specification
docs/             Architecture, platform notes, and project specification
.github/          GitHub Actions workflows and the HarmonyOS setup action
```

## Scope

BlueHotspot is intentionally limited to remote control of an already-configured
Android hotspot. Credential management, Wi-Fi configuration, SSID/password
transport or storage, and unrelated tethering workflows are outside the MVP.
