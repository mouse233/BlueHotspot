# Android client

The current implementation is intentionally small:

- `tethering/` starts and stops the Android system-configured Wi-Fi hotspot.
- `ui/` exposes a local diagnostic screen.
- `bluetooth/` is reserved for the next milestone.

The bootstrap currently compiles against API 36 because that is the highest Android SDK available on the GitHub runner. The tethering implementation itself requires API 36+; move compileSdk to 37 when the SDK is published and available in CI.

The programmatic `TetheringManager` path is API 36+ and may require a privileged
or system installation. Declaring `TETHER_PRIVILEGED` in the manifest does not
grant that permission to an ordinary APK.

Build from this directory with Android Studio or a locally installed Gradle
installation. A Gradle wrapper will be added when the Android toolchain is
available in the development environment.
