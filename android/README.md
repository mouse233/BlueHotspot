# Android applications

The `:app` module is the Android hotspot server. The `:client` module is the Android controller client. The current implementation is intentionally small:

- `tethering/` starts and stops the Android system-configured Wi-Fi hotspot.
- `ui/` exposes a local diagnostic screen.
- `ble/` advertises the encrypted GATT control service.
- `client/` scans for the service as a BLE central, pairs with the server, and sends hotspot commands.

The project targets Android 16 as its minimum supported platform (API 36) and compiles against API 36. The build uses AGP 9.3.2 with Gradle 9.7.1. The tethering implementation itself requires API 36+.

The programmatic `TetheringManager` path is API 36+ and may require a privileged
or system installation. Declaring `TETHER_PRIVILEGED` in the manifest does not
grant that permission to an ordinary APK.

Build from this directory with the checked-in Gradle Wrapper. On Windows run:

```powershell
.\gradlew.bat :app:assembleDebug :client:assembleDebug
```

Android Studio should use the Gradle Wrapper distribution configured in
`gradle/wrapper/gradle-wrapper.properties`.

## BLE control

When the app has Bluetooth permissions and Bluetooth is enabled, it advertises
the v1 encrypted GATT service defined in `../protocol/PROTOCOL.md`. The command
 characteristic accepts only `HELLO`, `GET_STATUS`, `START_HOTSPOT`, `STOP_HOTSPOT`,
and `PING`; it never accepts shell commands or hotspot credentials. Android's
BLE link pairing/encryption is required before control characteristics can be
used.

