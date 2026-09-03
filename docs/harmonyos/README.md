# HarmonyOS NEXT reference notes

These notes pin the external documentation used by the native HarmonyOS NEXT
client. They are deliberately a small reference set rather than a mirror of
Huawei's documentation.

Checked: 2026-09-03 against the locally installed DevEco Studio SDK
(HarmonyOS API 26.0.0, SDK package 26.0.0.105).

## SDK assumptions

- Application model: Stage
- Language/UI: ArkTS + ArkUI
- BLE import: `@kit.ConnectivityKit`
- Runtime Bluetooth permission: `ohos.permission.ACCESS_BLUETOOTH`
- Client role: BLE Central / GATT Client

The installed DevEco SDK type definitions remain the source of truth. Huawei
has examples for multiple API generations, so signatures must be confirmed by
the SDK used to build the project.

## Official references

- [GATT Development](https://developer.huawei.com/consumer/en/doc/harmonyos-guides-V13/gatt-development-guide-V13)
- [BLE advertising and scanning](https://developer.huawei.com/consumer/cn/doc/doccenter-atomic-service/atomic-bluetooth-advertising)
- [Bluetooth BLE API index](https://developer.huawei.com/consumer/en/doc/harmonyos-references/arkts-api)
- [Application configuration file](https://developer.huawei.com/consumer/cn/doc/doccenter-getting-started/app-configuration-file)

## Compatibility decisions

- The client only controls the existing Android v1 BLE service.
- It does not use `@ohos.net.sharing`, tethering APIs, Wi-Fi APIs, or hidden APIs.
- It does not read, create, transmit, or persist hotspot credentials.
- System BLE pairing/encryption remains the v1 authentication boundary.
- `deviceId` is persisted only for discovery/automatic-connection UX; it is
  never used as an authentication or trust decision.

## Building

Open `harmonyos/` as a DevEco Studio project and let DevEco select the
installed compatible SDK and signing configuration. The checked-in project
contains the standard Hvigor configuration; the wrapper executable is supplied
by the DevEco installation. A local debug HAP can be built with the discovered
`assembleApp` task. Signing is intentionally not configured in this repository.
