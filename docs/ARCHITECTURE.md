# Minimal architecture

```text
iPhone SwiftUI
    ↓
CoreBluetooth central
    ↓ authenticated START_HOTSPOT / STOP_HOTSPOT
Android GATT server
    ↓
TetheringController
    ↓
Android TetheringManager
    ↓
system-configured Wi-Fi hotspot
```

The Android app owns only sessions that it starts. It does not stop a hotspot
that was already active before the app took control.

The current implementation intentionally keeps BLE transport and authentication
behind the directory boundaries; those components are the next milestones.
