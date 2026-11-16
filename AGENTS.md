# AGENTS.md

OpenWiZLight is an Android app that controls WiZ smart lights over the local network. It is written in Kotlin with Jetpack Compose and Material 3.

## Build

- `make build` builds the debug APK; `make run` also installs and launches it over adb.
- When not already in the Nix dev shell, the Makefile runs commands through `nix develop`.
- The toolchain is pinned by `flake.nix`: Gradle 8.14, Android SDK 36 and JDK 17. That limits AGP to 8.13, so library versions must support AGP 8.13 and compileSdk 36. Newer AndroidX releases may need AGP 9 and SDK 37; check `minAndroidGradlePluginVersion` and `minCompileSdk` in the AAR's metadata before upgrading.
- There is no Gradle wrapper; the build uses the `gradle` provided by the flake.

## Layout

Sources are under `src/main/kotlin/io/github/rottencandy/openwizlight/`:

- `wiz/`: the WiZ protocol. It has no Android or UI dependencies.
  - `WizDiscovery`: broadcast discovery.
  - `WizClient`: request/response calls to a single device.
  - `Udp.kt`: shared socket helpers.
- `ui/`: Compose screens, their ViewModels, and `theme/`.
- `MainActivity.kt`: the entry point; it connects the ViewModel to the screen.

## WiZ protocol

- Devices speak JSON over UDP port 38899, using messages of the form `{"method": ..., "params": {...}}`. Replies echo the `method` and carry the data in `result`.
- Discovery broadcasts `registration` with `register:false` to 255.255.255.255 once a second, and each device replies with its `mac`.
- Requests are resent with exponential backoff because UDP is unreliable.
- The Python library pywizlight is the reference implementation; it is expected in `../pywizlight`. Check it for message formats before adding features.

## Conventions

- Prefer a functional style: immutable data classes, state exposed as `StateFlow` and updated with `copy`, and pure helper functions.
- Do blocking socket I/O on `Dispatchers.IO`, and expose it as `suspend` functions or a `Flow`.
- Add a short KDoc to any function whose purpose isn't obvious.
