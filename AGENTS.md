# BlueHotspot agent instructions

## Project scope

The MVP remotely starts and stops the Android device's already-configured
Wi-Fi hotspot over authenticated BLE. Do not add SSID/password generation,
storage, or transport to the MVP.

## Validation workflow

The development machine is Windows. Do not attempt to build or sign the iOS
application locally.

After completing code changes:

1. Push the branch to GitHub or open/update a pull request.
2. Wait for the `Build iOS IPA` GitHub Actions workflow to finish on a macOS
   runner.
3. Download the `BlueHotspot-unsigned-ipa` artifact and confirm that the
   archive was produced.
4. Report the workflow run, commit, artifact name, and any failure logs.

The workflow produces an unsigned IPA for compile/package validation because
this repository does not contain Apple signing certificates or provisioning
profiles. It is not installable on a device until a separate signing workflow
and repository secrets are configured.

Android CI runs independently on Ubuntu and executes lint, unit tests, and a
debug build.

## Source layout

- `android/` — Android app and tethering control.
- `ios/` — SwiftUI source and `project.yml` used by XcodeGen in CI.
- `docs/protocol/` — shared BLE protocol documents.
- `docs/` — architecture, protocol, and project documentation.


GitHub Actions workflows explicitly use the Node 24 runtime; do not reintroduce Node 20.
