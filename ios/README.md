# iOS client

This directory contains the native SwiftUI controller for the BlueHotspot
Android server.

## Implemented

- Scans for the BlueHotspot BLE service.
- Shows discovered Android devices and signal strength.
- Lets the user choose which Android device to connect to.
- Connects to the encrypted BLE GATT service.
- Sends hotspot start/stop/status commands.
- Displays Bluetooth connection and hotspot state.
- Uses Liquid Glass on iOS 26 and a material fallback on iOS 17–25.

The client does not receive, generate, or manage the Android hotspot SSID or
password.

## Build

The Xcode project is generated from `project.yml` with XcodeGen. Build the
project on macOS. The repository's `Build iOS IPA` GitHub Actions workflow
creates an unsigned `BlueHotspot-unsigned-ipa` artifact for compile/package
validation.

The current Bundle ID is
`io.github.mouse233.bluehotspot.client.ios`. Apple signing and provisioning
are required before installing the app on a physical device.
