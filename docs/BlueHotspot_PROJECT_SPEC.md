# BlueHotspot — Project Specification

> A native Android + iOS utility that lets an iPhone discover a nearby Android device over Bluetooth Low Energy (BLE), remotely start a **real Wi‑Fi Internet tethering hotspot**, receive its credentials over BLE, join it, and use the Android device's upstream Internet connection.
>
> This document is intended to be handed directly to Codex as the implementation specification.

---

## 0. Document status

**Specification date:** 2026-08-31  
**Revision:** Internet Tethering Edition

The core product requirement is:

> **The iPhone must be able to access the Internet through the Android device's remotely-started Wi‑Fi hotspot.**

The following do **not** count as project success:

- BLE connection alone
- Wi‑Fi association alone
- `WifiManager.startLocalOnlyHotspot()`
- a hotspot with no working upstream Internet connection
- silently substituting Local-Only Hotspot when real tethering fails

---

# 1. Platform baseline

## 1.1 Android

Use the latest modern native Android stack.

- Language: **Kotlin**
- UI: **Jetpack Compose**
- Design system: **Material 3**
- Architecture: unidirectional data flow
- Async/state: **Kotlin Coroutines + Flow**
- Dependency injection: **Hilt**, if compatible with the current stable toolchain
- Persistence: **DataStore**
- Secret storage: **Android Keystore**
- Build scripts: **Gradle Kotlin DSL**
- Dependency management: **Version Catalog**
- Kotlin: **2.4.10 stable**
- Compose BOM: **2026.08.00**
- `compileSdk`: **36**
- `targetSdk`: **36**
- `minSdk`: **36**

Use stable dependencies whenever possible.

The reference seamless Internet-tethering implementation requires:

- Android 16 / API 36 or newer
- a deployment where the app has the privilege needed to control Wi‑Fi tethering
- preferably a **privileged/system app installation** on the reference Android device

The project is installable on Android 16 / API 36 and newer only; seamless Internet tethering is not supported on older Android versions.

## 1.2 iOS

- Language: **Swift**
- UI: **SwiftUI**
- Bluetooth: **CoreBluetooth**
- Wi‑Fi configuration: **NetworkExtension**
- Wi‑Fi join API: **`NEHotspotConfigurationManager`**
- Concurrency: **Swift Concurrency**
- Secret storage: **Keychain**
- Use the latest stable Xcode and iOS SDK available to the developer.

Do not use Flutter, React Native, Kotlin Multiplatform, Compose Multiplatform, or other cross-platform UI frameworks.

---

# 2. Critical Android tethering constraint

This project requires **real Android Wi‑Fi Internet tethering**, not Local-Only Hotspot.

On Android 16 / API 36+, the preferred public framework is:

```text
TetheringManager
TetheringManager.TETHERING_WIFI
TetheringManager.TetheringRequest
SoftApConfiguration
```

Android documents `TetheringManager` as the API for starting/stopping tethering.

The framework also defines:

```text
CONNECTIVITY_SCOPE_GLOBAL
```

as connectivity that extends beyond the device, including global Internet connectivity.

However, do not assume that an ordinary third-party APK can silently start and fully configure Wi‑Fi tethering.

Current Android API documentation states that:

1. non-system callers using `TETHERING_WIFI` must specify a `SoftApConfiguration`;
2. `TetheringRequest.Builder.setSoftApConfiguration(...)` requires:

```text
android.permission.TETHER_PRIVILEGED
```

Therefore this project must explicitly model **tethering execution capability**.

The app must never pretend that declaring a permission in the manifest means the permission was actually granted.

---

# 3. Product goal

Build two native clients.

## 3.1 Android client

Responsibilities:

- advertise over BLE
- accept authenticated BLE connections
- pair/trust an iPhone
- report tethering capability
- start a real Wi‑Fi Internet tethering hotspot
- stop an app-owned tethering hotspot
- configure or retrieve the real SSID/passphrase
- report Soft AP state
- report upstream Internet state
- send credentials/state/errors to iOS over BLE
- distinguish privilege failure from carrier/provisioning/upstream failure

## 3.2 iOS client

Responsibilities:

- scan for compatible Android devices
- pair/trust an Android device
- display the Android device's tethering capability
- send `START_HOTSPOT`
- receive real SSID/passphrase
- request Wi‑Fi join through `NEHotspotConfigurationManager`
- verify Internet access after association
- clearly distinguish:
  - BLE connected
  - Wi‑Fi associated
  - hotspot active
  - Internet available
- send `STOP_HOTSPOT`

---

# 4. Target user flow

```text
Open iPhone app
        ↓
Scan BLE
        ↓
Android appears
        ↓
Connect + authenticate
        ↓
GET_STATUS
        ↓
Android reports:
internetTethering = available
backend = privileged
seamlessStart = true
        ↓
Tap "Start Internet Hotspot & Connect"
        ↓
START_HOTSPOT over BLE
        ↓
Android starts TETHERING_WIFI
        ↓
Android Soft AP comes up
        ↓
Android tethering stack selects upstream
        ↓
Android reports:
SSID
passphrase
security
upstreamAvailable
        ↓
iOS calls NEHotspotConfigurationManager
        ↓
User approves Wi‑Fi join if iOS asks
        ↓
iPhone associates to Android hotspot
        ↓
iPhone validates Internet connectivity
        ↓
ConnectedInternet
```

The final success state is:

```text
ConnectedInternet
```

not merely:

```text
WifiAssociated
```

---

# 5. Repository model

Use a single GitHub monorepo.

```text
bluehotspot/
├── android/
├── ios/
├── harmonyos/
├── docs/
│   └── protocol/
├── .github/
│   └── workflows/
├── README.md
├── LICENSE
└── .gitignore
```

Reason:

- Android, iOS, and HarmonyOS clients share one BLE protocol.
- Protocol changes should update all affected clients in one PR.
- Releases can contain Android and iOS artifacts together; HarmonyOS remains
  experimental until it has completed on-device validation.

Do not initialize nested Git repositories inside `android/` or `ios/`.

---

# 6. High-level architecture

The system has three logical planes.

## 6.1 BLE control plane

Used for:

- discovery
- pairing
- authentication
- capability negotiation
- start/stop commands
- hotspot credentials
- tethering status
- upstream status
- errors
- heartbeat

BLE must not carry normal Internet traffic.

## 6.2 Android tethering plane

```text
iPhone
  ↓ Wi‑Fi
Android Soft AP
  ↓
Android tethering/NAT/forwarding
  ↓
cellular / eligible upstream
  ↓
Internet
```

## 6.3 Execution backend plane

All tethering control must go through a backend abstraction.

Required/possible backends:

```text
PrivilegedTetheringBackend
ManualTetheringBackend
RootTetheringBackend        // post-MVP
ShizukuTetheringBackend     // post-MVP, only if verified
LocalOnlyDebugBackend       // diagnostics only
```

The app selects a backend only after capability probing.

---

# 7. Android tethering backend API

Create a domain interface similar to:

```kotlin
interface TetheringBackend {
    val type: TetheringBackendType

    suspend fun probe(): TetheringCapability

    suspend fun start(
        config: HotspotConfig
    ): Result<HotspotCredentials>

    suspend fun stop(): Result<Unit>

    fun observeState(): Flow<TetheringRuntimeState>
}
```

Suggested enum:

```kotlin
enum class TetheringBackendType {
    PRIVILEGED,
    MANUAL,
    ROOT,
    SHIZUKU,
    LOCAL_ONLY_DEBUG
}
```

Suggested capability model:

```kotlin
sealed interface TetheringCapability {

    data class Available(
        val backend: TetheringBackendType,
        val internetCapable: Boolean,
        val seamlessStart: Boolean
    ) : TetheringCapability

    data class Unavailable(
        val reason: TetheringUnavailableReason
    ) : TetheringCapability
}
```

Possible unavailable reasons:

```text
UnsupportedOs
PrivilegedInstallRequired
PermissionDenied
CarrierRestricted
ProvisioningFailed
NoTetheringService
NoWifiHardware
BackendUnavailable
Unknown
```

---

# 8. Canonical backend: PrivilegedTetheringBackend

This is the required MVP backend.

Reference target:

```text
Android 16 / API 36+
privileged/system deployment
public Android tethering API
```

Use:

```text
TetheringManager
TetheringManager.TETHERING_WIFI
TetheringManager.TetheringRequest.Builder
TetheringManager.startTethering(...)
TetheringManager.stopTethering(...)
TetheringManager.TetheringEventCallback
SoftApConfiguration
```

Do not implement the canonical path with:

- hidden API reflection
- Settings database hacks
- accessibility automation
- UI clicking
- OEM-specific intents
- `startLocalOnlyHotspot()`

---

# 9. Tethering request lifecycle

The controller must retain the exact logical request/session that it starts.

Expected pattern:

```text
create SoftApConfiguration
        ↓
create TetheringRequest for TETHERING_WIFI
        ↓
store request/session ownership
        ↓
startTethering(...)
        ↓
wait for callback
        ↓
observe tethering/upstream state
        ↓
...
        ↓
stopTethering(the matching request)
```

Do not create an unrelated new request when stopping if Android requires the original matching request.

Track ownership:

```kotlin
enum class TetheringOwnership {
    NONE,
    STARTED_BY_BLUEHOTSPOT,
    PRE_EXISTING_EXTERNAL,
    UNKNOWN
}
```

The app must not shut down a hotspot that was already active before BlueHotspot took control unless ownership is explicitly established.

---

# 10. Soft AP configuration

The Android client should know the exact credentials that iOS must use.

Preferred behavior:

- generate an SSID
- generate a strong random passphrase
- use a compatibility-safe security mode
- let Android choose normal band/channel parameters unless a reason exists to override

Suggested:

```text
SSID:
BlueHotspot-XXXX

Passphrase:
cryptographically random

Security:
WPA2-PSK or a compatibility-safe WPA2/WPA3 option

BSSID:
framework-selected

Channel:
framework-selected
```

Do not default to an open hotspot.

Use secure random generation.

Do not derive the password from:

- device name
- Bluetooth address
- Android ID
- timestamp
- predictable counter

The actual `SoftApConfiguration.Builder` APIs must be checked against the runtime Android SDK/module version.

If credential setters require a newer extension/module version:

1. perform availability checks;
2. use them when available;
3. otherwise use the existing platform tethering configuration only if the app can securely obtain the real credentials.

Never send guessed credentials to iOS.

---

# 11. Upstream Internet monitoring

The Android app must distinguish:

```text
hotspot stopped
hotspot starting
hotspot active, no upstream
hotspot active, upstream available
hotspot failed
```

Suggested domain model:

```kotlin
sealed interface TetheringRuntimeState {

    data object Idle : TetheringRuntimeState

    data object Starting : TetheringRuntimeState

    data class Active(
        val credentials: HotspotCredentials,
        val upstreamAvailable: Boolean,
        val backend: TetheringBackendType
    ) : TetheringRuntimeState

    data object Stopping : TetheringRuntimeState

    data class Failed(
        val error: TetheringError
    ) : TetheringRuntimeState
}
```

An active Soft AP is **not sufficient** to declare Internet success.

Android must report upstream changes to iOS.

Example:

```text
UPSTREAM_AVAILABLE
UPSTREAM_LOST
```

---

# 12. Standard APK behavior

The repository may produce an ordinary installable APK.

The ordinary build must remain honest.

If seamless Internet tethering cannot be started:

```text
Tethering control
Unavailable

Reason
Privileged installation required
```

It may offer:

```text
Open hotspot settings
```

through a `ManualTetheringBackend`.

This fallback may:

- open the system tethering settings UI
- observe accessible network state
- let the user manually turn the hotspot on

It does **not** satisfy seamless BLE-triggered remote-start acceptance.

---

# 13. Optional post-MVP backends

## 13.1 RootTetheringBackend

Allowed later.

Requirements:

- explicitly enabled by the user
- fixed internal operations only
- no arbitrary command execution from BLE payloads
- real device capability probing
- fail closed
- clear separation from normal app process logic

Prefer a small auditable privileged helper over scattered `su` shell strings.

## 13.2 ShizukuTetheringBackend

Allowed for investigation.

Do not assume Shizuku/shell identity automatically has the exact tethering privileges required.

Only ship after real-device verification on explicitly supported Android versions.

## 13.3 LocalOnlyDebugBackend

May use:

```text
WifiManager.startLocalOnlyHotspot(...)
```

only for:

- BLE testing
- protocol testing
- UI testing
- development without privileged tethering

It must report:

```text
internetCapable = false
mode = local_only_debug
```

The UI must display:

```text
Local only — no Internet
```

It may never silently replace the requested Internet tethering operation.

---

# 14. Android permissions and privilege

Handle version-specific BLE permissions correctly.

Possible permissions include:

```text
BLUETOOTH
BLUETOOTH_ADMIN
BLUETOOTH_SCAN
BLUETOOTH_CONNECT
BLUETOOTH_ADVERTISE
NEARBY_WIFI_DEVICES
ACCESS_FINE_LOCATION
ACCESS_NETWORK_STATE
ACCESS_WIFI_STATE
CHANGE_WIFI_STATE
```

For the canonical privileged tethering backend, account explicitly for:

```text
android.permission.TETHER_PRIVILEGED
```

Rules:

- do not treat it like an ordinary runtime permission prompt
- do not assume a manifest declaration grants it
- probe actual capability before enabling the UI
- catch permission/security failures
- expose a stable error instead of crashing

If useful, use separate Android build flavors:

```text
standard
privileged
```

Keep privileged-only declarations/configuration isolated where practical.

---

# 15. Android BLE role

Android acts primarily as:

```text
BLE Peripheral
GATT Server
BLE Advertiser
```

iOS acts primarily as:

```text
BLE Central
GATT Client
BLE Scanner
```

This matches the product model:

```text
iPhone = controller
Android = controlled Internet hotspot device
```

---

# 16. BLE GATT service

Use one custom 128-bit service UUID.

Generate fixed UUIDs once and store them centrally.

Suggested logical characteristics:

## Device Info

```text
READ
```

Contains:

- protocol version
- device ID
- display name
- app version
- Android version
- feature flags

## Command

```text
WRITE
```

iOS → Android

## Event

```text
NOTIFY
```

Android → iOS

## Pairing

```text
READ
WRITE
NOTIFY
```

Used during initial trust establishment.

Do not scatter UUID literals through the codebase.

---

# 17. BLE transport framing

Do not assume an arbitrary message fits one ATT write.

Implement fragmentation/reassembly.

Suggested frame:

```text
byte 0       protocol major version
byte 1       message type
byte 2       flags
byte 3       reserved
bytes 4-7    request ID
bytes 8-11   total payload length
bytes 12...  payload fragment
```

Payload format:

- prefer **CBOR**
- JSON is acceptable for the first transport milestone if framing is correct

Must enforce:

- maximum message size
- fragment timeout
- malformed frame rejection
- request ID matching
- duplicate handling
- no crash on invalid remote input

---

# 18. Protocol messages

Start with:

```text
HELLO
HELLO_ACK

PAIR_REQUEST
PAIR_APPROVED
PAIR_REJECTED

AUTH_CHALLENGE
AUTH_RESPONSE

GET_STATUS
STATUS

START_HOTSPOT
HOTSPOT_STARTING
HOTSPOT_READY
HOTSPOT_FAILED

STOP_HOTSPOT
HOTSPOT_STOPPED

UPSTREAM_AVAILABLE
UPSTREAM_LOST

CLIENT_WIFI_ASSOCIATED
CLIENT_INTERNET_VERIFIED

PING
PONG

ERROR
```

Every request must have a request ID.

Every command must eventually produce:

```text
success
```

or:

```text
error
```

---

# 19. Feature negotiation

`HELLO_ACK` should contain runtime capability.

Example:

```json
{
  "protocolVersion": 1,
  "features": [
    "internet_tethering",
    "hotspot_credentials",
    "upstream_status",
    "authenticated_commands"
  ],
  "tethering": {
    "backend": "privileged",
    "internetCapable": true,
    "seamlessStart": true
  }
}
```

Potential backend flags:

```text
privileged_tethering
manual_tethering
root_tethering
shizuku_tethering
local_only_debug
```

Do not infer support only from:

```text
Android version >= 36
```

Actual runtime/backend capability is authoritative.

---

# 20. START_HOTSPOT protocol flow

```text
iOS                                  Android
 |                                      |
 |--------- GET_STATUS ---------------->|
 |<-------- STATUS ---------------------|
 |          backend=privileged          |
 |          seamlessStart=true          |
 |                                      |
 |--------- START_HOTSPOT ------------->|
 |<-------- HOTSPOT_STARTING -----------|
 |                                      |
 |        start TETHERING_WIFI          |
 |        start Soft AP                 |
 |        establish upstream            |
 |                                      |
 |<-------- HOTSPOT_READY --------------|
 |          SSID                        |
 |          passphrase                  |
 |          security                    |
 |          upstreamAvailable           |
 |                                      |
 |        iOS joins Wi-Fi               |
 |                                      |
 |--------- CLIENT_WIFI_ASSOCIATED ---->|
 |                                      |
 |        iOS validates Internet        |
 |                                      |
 |--------- CLIENT_INTERNET_VERIFIED -->|
```

Example `HOTSPOT_READY`:

```json
{
  "ssid": "BlueHotspot-A17F",
  "passphrase": "generated-secret",
  "security": "WPA2",
  "mode": "internet_tethering",
  "backend": "privileged",
  "upstreamAvailable": true
}
```

If Soft AP is active but no upstream exists:

```json
{
  "mode": "internet_tethering",
  "upstreamAvailable": false
}
```

The iOS UI then displays:

```text
Connected to Android hotspot
Waiting for Internet upstream…
```

---

# 21. Pairing and security

A random nearby iPhone must not be able to turn on an Android Internet hotspot.

First-time trust requires explicit Android-side approval.

Never authenticate by BLE MAC address.

## 21.1 Installation identity

Each installation generates:

- installation ID
- long-term key material

Store secrets in:

Android:

```text
Android Keystore
```

iOS:

```text
Keychain
```

## 21.2 Pairing

Recommended:

1. iOS connects
2. iOS sends installation identity/public key
3. Android displays pairing request
4. Android user approves
5. both sides establish trusted peer state
6. subsequent privileged commands require authentication

Use standard crypto primitives.

Possible design:

```text
X25519
HKDF
HMAC-SHA256
```

or a platform-native P-256 design if cleaner.

Do not invent custom cryptographic primitives.

## 21.3 Replay protection

Authenticated commands must include enough state to reject replays.

Example:

- peer ID
- request ID
- monotonic counter or nonce
- MAC/authenticator

Never log:

- hotspot password
- private key
- shared secret
- authentication token

---

# 22. Android architecture

Suggested layers:

```text
Compose UI
   ↓
ViewModel
   ↓
Domain/controller
   ↓
Tethering / BLE / Security / Persistence
```

Use:

- immutable UI state
- `StateFlow`
- `SharedFlow` only for genuine events
- structured concurrency
- lifecycle-aware collection

Do not use:

- `GlobalScope`
- mutable global singletons
- business logic inside composables
- direct Bluetooth callbacks in UI code

---

# 23. Android package structure

```text
io.github.mouse233.bluehotspot.server
├── app/
├── ui/
│   ├── home/
│   ├── pairing/
│   ├── devices/
│   ├── settings/
│   └── diagnostics/
├── bluetooth/
│   ├── advertise/
│   ├── gatt/
│   └── session/
├── tethering/
│   ├── domain/
│   ├── privileged/
│   ├── manual/
│   ├── root/
│   ├── shizuku/
│   └── debug/
├── protocol/
│   ├── codec/
│   ├── framing/
│   └── model/
├── security/
├── data/
├── domain/
└── util/
```

The post-MVP backend packages may initially be absent.

---

# 24. Android UI

## 24.1 Home

Example on a privileged reference device:

```text
BlueHotspot

Bluetooth
Ready

Tethering backend
Privileged Android API

Internet tethering control
Available

Upstream
5G — Internet available

Trusted controller
iPhone
Connected

Wi-Fi Internet hotspot
Off

[ Start Internet Hotspot ]
```

When active:

```text
Wi-Fi Internet hotspot
Active

SSID
BlueHotspot-A17F

Internet
Available

[ Stop Internet Hotspot ]
```

On an ordinary APK:

```text
Internet tethering control
Unavailable

Reason
Privileged installation required

[ Open Android hotspot settings ]
```

Never present Local-Only Hotspot as the main feature.

## 24.2 Pairing

Show:

- iPhone name
- verification/fingerprint information
- Approve
- Reject

Never auto-approve a new controller.

## 24.3 Trusted devices

Show:

- peer name
- last seen
- trust state
- revoke

## 24.4 Diagnostics

Show non-secret data:

- Android version
- app version
- protocol version
- BLE permissions
- tethering backend
- tethering capability
- privilege status
- Soft AP state
- upstream state
- recent sanitized errors

---

# 25. Android foreground/background policy

Use public background APIs conservatively.

The app should not keep a permanent foreground service purely to stay alive unless Android requires it for a clearly user-visible active operation.

When a foreground service is required:

- use the correct service type
- show a real notification
- stop it when no longer necessary

Do not abuse background execution exemptions.

Companion Device APIs may be added later to improve trusted-device presence behavior.

---

# 26. iOS architecture

Suggested structure:

```text
BlueHotspot/
├── App/
├── Features/
│   ├── Nearby/
│   ├── Device/
│   ├── Pairing/
│   ├── TrustedDevices/
│   ├── Settings/
│   └── Diagnostics/
├── Bluetooth/
├── WiFi/
├── Protocol/
├── Security/
├── Persistence/
└── Utilities/
```

Suggested core components:

```text
BluetoothCentral
PeripheralSession
MessageCodec
MessageFramer
PeerAuthenticator
PeerStore
HotspotJoiner
InternetValidator
```

Use Swift Concurrency carefully around CoreBluetooth delegate callbacks.

Actors are appropriate where they simplify mutable session state.

---

# 27. iOS Wi‑Fi join

Use:

```swift
NEHotspotConfigurationManager.shared
```

Construct a configuration from the real credentials received over BLE.

Prefer:

```text
joinOnce = true
```

for an ephemeral remotely-created hotspot unless real-device tests show persistent configuration is preferable.

Important:

- Hotspot Configuration capability/entitlement is required.
- iOS may show user confirmation.
- user denial is a normal state
- do not promise completely silent joining

Suggested abstraction:

```swift
protocol WiFiJoining {
    func join(
        ssid: String,
        passphrase: String
    ) async throws
}
```

---

# 28. iOS Internet validation

Joining the SSID does not prove Internet access.

Create an `InternetValidator`.

The UI should only enter:

```text
ConnectedInternet
```

when Internet connectivity is actually usable.

Possible stages:

```text
JoiningWifi
WifiAssociated
VerifyingInternet
ConnectedInternet
```

If Android reports no upstream:

```text
Hotspot active
No Internet upstream
```

If iOS joins but validation fails:

```text
Connected to hotspot
Internet validation failed
```

Do not conflate these states.

---

# 29. Unified state machine

```text
Idle
Scanning
Discovered
ConnectingBle
BleConnected
Authenticating
Authenticated
CheckingTetheringCapability
TetheringUnavailable
RequestingHotspot
HotspotStarting
HotspotReadyNoUpstream
HotspotReadyInternet
JoiningWifi
WifiAssociated
VerifyingInternet
ConnectedInternet
Stopping
Failed
```

Avoid unrelated Boolean state such as:

```text
isBleConnected
isHotspotOn
hasInternet
isLoading
```

when one explicit state machine is clearer.

---

# 30. Error model

Define stable protocol-level errors.

```text
UNKNOWN
INVALID_MESSAGE
UNSUPPORTED_VERSION
UNSUPPORTED_COMMAND

NOT_AUTHENTICATED
PAIRING_REQUIRED
PAIRING_REJECTED
BUSY

BLUETOOTH_UNAVAILABLE
BLUETOOTH_PERMISSION_DENIED

TETHERING_UNSUPPORTED
TETHERING_BACKEND_UNAVAILABLE
PRIVILEGED_INSTALL_REQUIRED
TETHERING_PERMISSION_DENIED
TETHERING_ACCESS_PERMISSION_DENIED
TETHERING_CHANGE_PERMISSION_DENIED
TETHERING_PROVISIONING_FAILED
TETHERING_SERVICE_UNAVAILABLE
TETHERING_DUPLICATE_REQUEST
TETHERING_UNKNOWN_REQUEST
TETHERING_START_FAILED
TETHERING_STOP_FAILED

UPSTREAM_UNAVAILABLE
UPSTREAM_LOST

HOTSPOT_CREDENTIALS_UNAVAILABLE

IOS_WIFI_JOIN_DENIED
IOS_WIFI_JOIN_FAILED
INTERNET_VALIDATION_FAILED

TIMEOUT
INTERNAL
```

Map Android's documented `TETHER_ERROR_*` values to stable app errors.

Keep the original numeric Android error in sanitized diagnostics if useful.

Do not send Java/Kotlin stack traces over BLE.

---

# 31. Timeouts

Suggested starting values:

```text
BLE connection:        10 s
service discovery:     10 s
authentication:        15 s
tethering start:       30 s
command response:      15 s
Wi-Fi join:            30 s
Internet validation:   20 s
heartbeat interval:    15 s
```

Centralize timeout constants.

All waits must be cancellable.

---

# 32. Auto-stop and ownership

Default policy:

```text
auto-stop app-owned tethering after 10 minutes of inactivity
```

unless:

- an active trusted controller session exists
- user explicitly locked the hotspot on
- the hotspot existed before BlueHotspot and is not owned by the app

Stop an app-owned hotspot when:

- Android user presses Stop
- trusted iPhone sends `STOP_HOTSPOT`
- idle timeout expires
- backend enters unrecoverable failure

Do not tie tethering lifetime to a Compose screen lifecycle.

---

# 33. Protocol versioning

Start with:

```text
major = 1
minor = 0
```

Rules:

- incompatible wire change → bump major
- backwards-compatible addition → bump minor
- ignore unknown optional fields
- unknown commands → `UNSUPPORTED_COMMAND`
- incompatible major versions → fail gracefully

Canonical protocol document:

```text
docs/protocol/PROTOCOL.md
```

Canonical UUID document:

```text
docs/protocol/UUIDS.md
```

---

# 34. Persistence

Android:

```text
DataStore:
- settings
- peer metadata
- backend preference
- timeout preference

Android Keystore:
- local private key
- peer secrets
```

iOS:

```text
Keychain:
- private keys
- shared secrets

native lightweight storage:
- peer metadata
- settings
```

Do not persist hotspot passphrases longer than necessary.

---

# 35. Logging

Never log:

- hotspot passphrase
- shared secret
- private key
- auth token

Safe diagnostics may include:

- request ID
- backend type
- state transition
- Android tethering numeric error
- peer ID suffix
- OS version
- privilege state

Use structured logging.

---

# 36. Testing requirements

## 36.1 Protocol tests

Both platforms should test:

- encode/decode round trip
- fragmentation
- reassembly
- malformed frames
- message size limits
- version mismatch
- request ID correlation
- timeout
- unknown message handling

Put shared vectors in:

```text
docs/protocol/examples/
```

## 36.2 Android unit tests

Test:

- backend selection
- capability model
- state transitions
- ownership logic
- error mapping
- timeout logic
- authentication/replay protection
- ViewModel state

## 36.3 Android real-device tests

Mandatory on the privileged reference device:

- capability probe succeeds
- real `TetheringManager.startTethering(...)`
- Wi‑Fi Soft AP becomes visible
- external device joins
- external device accesses Internet
- upstream loss is detected
- upstream recovery is detected
- matching stop request works
- permission failure is handled
- provisioning failure is handled where reproducible

A Local-Only Hotspot test is insufficient.

## 36.4 iOS real-device tests

Mandatory:

- BLE scan
- BLE connect
- pairing
- credential reception
- `NEHotspotConfigurationManager`
- user approval flow
- Wi‑Fi association
- Internet validation
- stop command

Simulator-only success is insufficient.

---

# 37. CI

Android GitHub Actions:

```text
checkout
JDK setup
Gradle cache
./gradlew lint
./gradlew test
./gradlew assembleDebug
```

If multiple flavors exist, compile at least:

```text
standardDebug
privilegedDebug
```

iOS CI may use a macOS runner:

```text
xcodebuild test
```

Do not pretend iOS builds can be fully validated on Linux.

---

# 38. Privacy

Normal operation must not require:

- cloud account
- analytics SDK
- ad SDK
- telemetry server
- location history

Control traffic stays between the iPhone and Android device.

If crash reporting is later added:

- document it
- scrub secrets
- preferably make it opt-in

---

# 39. Accessibility and localization

Support:

- dark mode
- dynamic type/text scaling
- VoiceOver/TalkBack
- semantic controls
- sufficient touch targets
- no color-only status meaning

Initial localization:

```text
English
Simplified Chinese
```

Keep UI strings in localization resources from the start.

---

# 40. README requirements

Root README must clearly state:

1. what the project does
2. Android + iOS architecture
3. BLE → tethering → Wi‑Fi flow
4. supported Android versions
5. **privileged deployment requirement for seamless tethering**
6. ordinary APK limitations
7. iOS Wi‑Fi confirmation limitation
8. security model
9. build instructions
10. current status
11. license

Do not advertise:

```text
"works on every stock Android phone"
```

unless that has actually been achieved.

---

# 41. MVP milestones

## Milestone 1 — Monorepo bootstrap

Deliver:

- root repository
- Android project
- iOS project
- protocol docs
- root README
- CI skeleton
- optional Android `standard` / `privileged` flavor structure

Acceptance:

- Android project builds
- iOS project opens/builds on supported Xcode
- clean repository structure

## Milestone 2 — Real Android Internet tethering

Deliver:

- `TetheringBackend`
- `PrivilegedTetheringBackend`
- capability probe
- `TetheringManager` integration
- `TETHERING_WIFI`
- `TetheringRequest`
- `SoftApConfiguration`
- start
- stop
- upstream observation
- ownership tracking
- error mapping
- Compose test UI
- `ManualTetheringBackend`

Acceptance on privileged reference device:

```text
tap Start
↓
real Wi-Fi hotspot appears
↓
second device joins
↓
second device reaches Internet through Android
↓
tap Stop
↓
app-owned tethering stops
```

If only `startLocalOnlyHotspot()` works, Milestone 2 fails.

## Milestone 3 — BLE discovery

Android:

- advertising
- GATT server
- Device Info

iOS:

- scan
- nearby list
- connect
- service discovery

Acceptance:

- iPhone discovers Android
- reads runtime tethering capability

## Milestone 4 — BLE protocol transport

Deliver:

- framing
- codec
- request IDs
- events
- errors
- fragmentation
- tests

Acceptance:

```text
PING → PONG
```

and fragmented messages reassemble correctly.

## Milestone 5 — Pairing/authentication

Deliver:

- pairing request
- Android approval
- trusted peer storage
- command authentication
- replay protection
- revoke trust

Acceptance:

- untrusted iPhone cannot start tethering
- trusted iPhone can

## Milestone 6 — Remote tethering start

Deliver:

```text
iOS START_HOTSPOT
↓
Android TETHERING_WIFI
↓
HOTSPOT_READY
↓
real credentials
↓
upstream state
```

Acceptance:

- tethering is triggered over BLE
- independent client can join and access Internet

## Milestone 7 — iOS Wi‑Fi join + Internet verification

Deliver:

- `NEHotspotConfigurationManager`
- Wi‑Fi join state
- iOS user approval handling
- Internet validation

Acceptance:

```text
tap Start Internet Hotspot & Connect
↓
Android remotely starts Internet tethering
↓
iPhone joins
↓
iPhone reaches Internet through Android
↓
ConnectedInternet
```

If iPhone joins but has no Internet, Milestone 7 fails.

## Milestone 8 — polish

Deliver:

- trusted devices UI
- settings
- diagnostics
- localization
- accessibility
- sanitized logs
- documentation
- final CI

---

# 42. MVP acceptance criteria

The MVP only needs to remotely control the Android device's already-configured Wi-Fi hotspot.
The SSID and passphrase are managed by Android system settings and are not part of the
BlueHotspot protocol or acceptance flow.

The project is MVP-complete only if:

- [ ] Android project builds from clean checkout.
- [ ] iOS project builds from clean checkout.
- [ ] Android uses Kotlin + Compose + Material 3.
- [ ] iOS uses Swift + SwiftUI.
- [ ] Android has explicit tethering backend capability detection.
- [ ] The target Android device can start its configured Wi-Fi hotspot through the supported
      tethering backend.
- [ ] The implementation does not generate, modify, read, transmit, or persist the hotspot
      SSID or passphrase.
- [ ] Android advertises BLE service.
- [ ] iPhone discovers Android.
- [ ] iPhone pairs/authenticates.
- [ ] Untrusted iPhone cannot control tethering.
- [ ] iPhone sends `START_HOTSPOT`.
- [ ] Android reports `HOTSPOT_STARTING`, followed by `HOTSPOT_READY` or a stable error.
- [ ] iPhone can request Stop.
- [ ] Android stops only a hotspot session started by BlueHotspot; it does not stop a
      pre-existing user-managed hotspot.
- [ ] Secrets are absent from production logs.
- [ ] malformed BLE input cannot crash either client.
- [ ] README documents privilege requirements accurately.

Explicit rejection criterion:

> An implementation whose final data path is `WifiManager.startLocalOnlyHotspot()` is **not an acceptable implementation of this MVP**, because it creates a separate local-only network instead of opening the Android device's configured Wi-Fi tethering hotspot.

---

# 43. Definition of done for Codex tasks

Every completed task must:

1. compile
2. preserve existing tests
3. add tests for non-trivial pure logic
4. avoid fake success paths
5. surface errors
6. handle permission failure
7. use version/API checks
8. update docs when behavior changes
9. avoid leaving critical placeholder TODOs
10. report what could not be verified on real hardware

Suggested commits:

```text
feat(android): add tethering backend abstraction
feat(android): add privileged internet tethering backend
feat(android): observe tethering upstream state
feat(protocol): add hotspot capability messages
feat(ios): discover BlueHotspot peripherals
feat(security): authenticate privileged BLE commands
fix(android): preserve tethering request ownership
docs: document privileged deployment requirements
```

---

# 44. Engineering rules for Codex

## 44.1 Verify current official APIs

Before implementing version-sensitive Android networking code, inspect current official Android documentation.

Do not guess.

## 44.2 Public framework first

Canonical backend priority:

```text
1. public TetheringManager API + legitimate privileged deployment
2. manual system Settings fallback
3. optional audited root backend
4. optional verified Shizuku backend
```

Do not make hidden API reflection the primary design.

## 44.3 Never hide platform limitations

If Android lacks tethering privilege:

```text
Privileged installation required
```

If Android hotspot is up but has no upstream:

```text
Hotspot active — no Internet upstream
```

If iOS requires confirmation:

show the confirmation flow.

If iOS joins but Internet fails:

do not report success.

## 44.4 Local-Only Hotspot is debug-only

Codex must not "solve" a difficult tethering problem by replacing it with Local-Only Hotspot.

## 44.5 Keep dependencies small

Prefer native Android/iOS APIs.

Every third-party dependency should have a reason.

---

# 45. Official references

Codex must re-check these before coding.

## Android

`TetheringManager`

https://developer.android.com/reference/android/net/TetheringManager

`TetheringManager.TetheringRequest`

https://developer.android.com/reference/android/net/TetheringManager.TetheringRequest

`TetheringManager.TetheringRequest.Builder`

https://developer.android.com/reference/android/net/TetheringManager.TetheringRequest.Builder

`TetheringManager.StartTetheringCallback`

https://developer.android.com/reference/android/net/TetheringManager.StartTetheringCallback

`SoftApConfiguration`

https://developer.android.com/reference/android/net/wifi/SoftApConfiguration

`SoftApConfiguration.Builder`

https://developer.android.com/reference/android/net/wifi/SoftApConfiguration.Builder

`WifiManager`

https://developer.android.com/reference/android/net/wifi/WifiManager

Local-Only Hotspot documentation — debug fallback only

https://developer.android.com/develop/connectivity/wifi/localonlyhotspot

Companion Device APIs

https://developer.android.com/reference/android/companion/package-summary

Android 16 SDK

https://developer.android.com/about/versions/16/setup-sdk

Compose

https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler

## Kotlin

https://kotlinlang.org/docs/releases.html

## Apple

`NEHotspotConfigurationManager`

https://developer.apple.com/documentation/networkextension/nehotspotconfigurationmanager

Wi‑Fi configuration

https://developer.apple.com/documentation/networkextension/wi-fi-configuration

iOS Wi‑Fi API overview

https://developer.apple.com/documentation/technotes/tn3111-ios-wifi-api-overview

CoreBluetooth

https://developer.apple.com/documentation/corebluetooth

---

# 46. First prompt to give Codex

```text
Read BlueHotspot_PROJECT_SPEC.md in full before modifying the repository.

The central requirement is REAL ANDROID WI-FI INTERNET TETHERING.

A Local-Only Hotspot is not an acceptable substitute.

Implement Milestone 1 and Milestone 2 only.

Requirements:

1. Create the monorepo layout from the specification.

2. Bootstrap Android using:
   - Kotlin
   - Jetpack Compose
   - Material 3
   - Gradle Kotlin DSL
   - Version Catalog
   - compileSdk 36
   - targetSdk 36
   - minSdk 36

3. Create the TetheringBackend abstraction.

4. Implement PrivilegedTetheringBackend for Android 16/API 36+ using the current public Android tethering APIs:
   - TetheringManager
   - TETHERING_WIFI
   - TetheringRequest
   - SoftApConfiguration
   - startTethering
   - stopTethering
   - tethering/upstream callbacks

5. Before coding, inspect the current official Android documentation for:
   - TetheringManager
   - TetheringRequest.Builder
   - SoftApConfiguration.Builder

6. Account explicitly for android.permission.TETHER_PRIVILEGED.
   Do not assume an ordinary sideloaded APK receives this privilege.
   Capability-probe it and report PrivilegedInstallRequired when unavailable.

7. Configure or obtain a REAL tethering SSID/passphrase that can be sent to an iPhone.
   Never invent or guess credentials.

8. Track the exact app-owned tethering request/session so Stop does not accidentally stop unrelated user tethering.

9. Observe upstream state.
   The app must distinguish:
   - hotspot active, no Internet
   - hotspot active, Internet available

10. Map documented TETHER_ERROR_* results into stable domain errors.

11. Add ManualTetheringBackend for ordinary builds.
    It may open Android hotspot settings but does not count as seamless MVP success.

12. LocalOnlyDebugBackend is optional and must be clearly marked NO INTERNET.
    Do not use startLocalOnlyHotspot() as the canonical solution.

13. Build a minimal Compose diagnostics screen showing:
    - backend
    - privilege capability
    - hotspot state
    - upstream state
    - Start Internet Hotspot
    - Stop Internet Hotspot

14. Add unit tests for:
    - capability selection
    - state transitions
    - ownership
    - error mapping

15. Create the native Swift/SwiftUI iOS project skeleton only.
    Do not implement BLE yet.

16. Run all available Android builds, tests and lint before finishing.

17. If this specification conflicts with current official Android documentation, follow the official documentation and record the discrepancy.

At the end, report:

1. files created/modified
2. architecture decisions
3. exact privilege/deployment requirements
4. build/test/lint commands executed
5. what works in a normal APK
6. what requires a privileged/system installation
7. what was verified only in code vs on real hardware
8. known limitations
9. exact next milestone
```

---

# 47. Final engineering intent

The canonical successful path is:

```text
SwiftUI iPhone app
        ↓
CoreBluetooth
        ↓
authenticated START_HOTSPOT
        ↓
Kotlin Android app
        ↓
PrivilegedTetheringBackend
        ↓
TetheringManager.startTethering(TETHERING_WIFI)
        ↓
Android Wi-Fi Soft AP
        ↓
Android tethering / NAT / forwarding
        ↓
Android upstream Internet
        ↓
SSID + passphrase + upstream state over BLE
        ↓
NEHotspotConfigurationManager
        ↓
iPhone joins Android hotspot
        ↓
iPhone validates Internet
        ↓
ConnectedInternet
```

The product is successful only when:

> **The iPhone can actually browse the Internet through the Android device's remotely-started hotspot.**

Build that path correctly before adding unrelated features.
