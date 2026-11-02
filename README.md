# GBCast

**Sega Dreamcast and compatible arcade emulation for Android**

[Download the latest APK](https://github.com/mindspotgb/GBCast/releases/latest) · [Changelog](CHANGELOG.md) · [Privacy policy](PRIVACY.md)

GBCast is an Android emulator based on the Flycast core. Its Android library and shared menus use [GBFrame](https://github.com/mindspotgb/GBFrame). This repository contains the Android app, the core revision used by it, Android build support, and the required dependencies.

## Features

- Dreamcast, Naomi, Naomi 2, and Atomiswave emulation supported by the Flycast core.
- GLES and Vulkan renderers, enhanced internal resolution, shaders and texture filtering.
- Controller-friendly game library, touch controls, configurable bindings, and quick menu.
- Save states, cheats, RetroAchievements, per-game profiles, and display options.
- Scoped Storage game library, BIOS import, cover art, and configurable audio/performance options.

GBCast does not include BIOS files or game images. Supply your own legally obtained firmware and game images.

## Requirements

- Android 9 or newer and a 64-bit ARM device (`arm64-v8a`).
- Your own Dreamcast BIOS/flash files where required, and your own game images.

## Build

Clone the repository with submodules:

```powershell
git clone --recurse-submodules https://github.com/mindspotgb/GBCast.git
```

Open `shell/android-studio` in Android Studio or use its Gradle wrapper. Native builds require the Android SDK/NDK and CMake versions declared by the project.

## Project layout

- `shell/android-studio` — Android app, Compose UI, JNI bridge, resources, and Gradle project.
- `core` — Flycast emulation core used by the Android target.
- `libs/gbframe` — shared Android app framework, pinned as a Git submodule.
- `core/deps`, `fonts`, `resources` — native dependencies and assets required by the core.
- `shell/cmake`, `.github/workflows` — Android build configuration and release workflow.

## Privacy and license

See the [privacy policy](PRIVACY.md). GBCast is distributed under [GNU GPL version 3](LICENSE); third-party components retain their respective licenses and notices in the app.

GBCast is not affiliated with or endorsed by SEGA. Sega Dreamcast is a trademark of SEGA.
