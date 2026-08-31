# BlueHotspot

BlueHotspot is a native Android + iOS utility for remotely opening an Android
device's already-configured Wi-Fi hotspot from a trusted iPhone over BLE.

The MVP does not read, generate, transmit, or store the hotspot SSID or
passphrase. It only controls hotspot start/stop and reports the result.

## Repository layout

- `android/` — Android app and system tethering integration.
- `ios/` — native SwiftUI iPhone controller skeleton.
- `protocol/` — platform-neutral BLE protocol.
- `docs/` — architecture and product documentation.

## Current status

The initial skeleton contains a minimal Android tethering controller and a
SwiftUI iOS shell. Android API 36+ and the required tethering privilege are
needed for programmatic Wi-Fi tethering. See
`docs/BlueHotspot_PROJECT_SPEC.md` for the full scope and limitations.
