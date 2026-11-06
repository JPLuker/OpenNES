# OpenNES

**A dedicated, open-source NES emulator for Android — built around a premium touchscreen controller and a clean, mobile-first interface.**

[Development roadmap](ROADMAP.md) · [Android build instructions](docs/BUILDING.md) · [NES-only cleanup audit](docs/NES_ONLY_AUDIT.md) · [GitHub Actions](https://github.com/JPLuker/OpenNES/actions) · [Report an issue](https://github.com/JPLuker/OpenNES/issues)

> **Early development (0.1.0):** OpenNES is currently a fork of [Lemuroid](https://github.com/Swordfish90/Lemuroid). The initial debug APK **compiles and is uploaded by GitHub Actions**, but device testing, the full UI replacement, NES-only debloating, and RetroAchievements are **not complete**. The current build will still resemble Lemuroid and still includes other systems. Please do not treat it as a finished OpenNES release.

## What OpenNES is meant to be

The goal is the convenience and polish of a dedicated Android emulator, designed **specifically for NES/Famicom**, not a general-purpose emulation frontend.

- **NES-inspired controller skin:** original, polished warm-gray, charcoal and deep-red tactile controls, with classic D-pad, A/B, Start and Select.
- **Designed twice for phones:** portrait should feel like a dedicated handheld; landscape should frame a large game viewport with controls on both sides. Each orientation will have independent control positions, sizes and opacity.
- **Entirely new UI:** game library, onboarding, game details, menus, settings, quick actions and achievement overlays will be redesigned under the OpenNES identity.
- **RetroAchievements:** planned native sign-in, game identification, achievement tracking and notifications. Hardcore support will follow RetroAchievements' actual emulator-approval rules.
- **Natively Android:** touchscreen play, external gamepads, local ROM folders, save data and convenient installation.
- **Truly NES-only distribution:** remove unused emulator cores, non-NES system routes, files and packaged assets, not just hide other systems in the interface.
- **Open source:** preserve applicable GPL and third-party notices. No commercial ROMs are included.

The visual direction takes inspiration from Pizza Boy's level of polish, **not its copyrighted visual assets or source code**. All final OpenNES artwork and UI will be original.

## Development status

| Milestone | Status |
| --- | --- |
| M0 — Own Android package/name and reproducible APK workflow | In progress; baseline APK assembled in CI |
| M1 — Remove non-NES cores, assets and system selection | Not started |
| M2 — Replace the complete Lemuroid user interface | Not started |
| M3 — Premium NES portrait controller skin | Not started |
| M4 — Dedicated landscape controller skin | Not started |
| M5 — Gameplay, saves, input and performance polish | Not started |
| M6 — RetroAchievements integration | Not started |
| M7 — Production releases and maintenance | Not started |

See the [full roadmap](ROADMAP.md) for sequencing and acceptance tests.

### What works in the fork today?

The inherited Lemuroid project provides a working Android emulation frontend, FCEUmm support, ROM scanning, gamepad/touch input and save-state infrastructure. **Those existing features have not all been validated in OpenNES on real hardware yet.** Current development is keeping this functionality intact while preparing the NES-only conversion.

Currently retained from upstream: multi-console UI, multi-console emulation binaries, Lemuroid internal source package names, and Lemuroid launcher artwork. None of those are part of the planned final OpenNES experience.

## Install a development APK

1. Open [OpenNES GitHub Actions](https://github.com/JPLuker/OpenNES/actions) and select the latest successful **OpenNES Android CI** run for the development PR/branch.
2. Open the **Artifacts** section and download `OpenNES-debug-apk` (GitHub sign-in may be required).
3. Unzip the artifact and install `OpenNES-debug.apk` on your Android device. Allow APK installation for the app you're using to open it if Android prompts you.
4. Import your own legally obtained NES ROM and test gameplay.

This is a **debug test build**, not a GitHub Release or Play Store release. No ROMs or commercial game artwork are supplied. It has the application ID `com.jpluker.opennes.debug`, so it can install alongside upstream Lemuroid.

**Important:** A CI run can be marked failed because of lint even if its earlier APK build and artifact upload succeeded. The updated workflow exposes **Build OpenNES debug APK** and **Kotlin lint** as separate jobs to distinguish those outcomes.

## Build from source

You need a working Android SDK, **JDK 17**, and Android/Gradle build tools. Clone recursively to include the emulator-core submodule:

```sh
git clone --recurse-submodules https://github.com/JPLuker/OpenNES.git
cd OpenNES
./gradlew :lemuroid-app:assembleFreeBundleDebug
```

The test APK is produced in `lemuroid-app/build/outputs/apk/freeBundle/debug/`. For local testing, use `adb install -r path/to/OpenNES-debug.apk` with the actual generated file path. On Windows, use `gradlew.bat` instead of `./gradlew`.

For more details and troubleshooting, see [docs/BUILDING.md](docs/BUILDING.md). You can inspect which native emulator binaries are packaged with:

```sh
python3 scripts/inspect-apk.py path/to/OpenNES-debug.apk
```

Once NES-only cleanup is complete, this tool will also be used with `--nes-only` to reject non-NES libretro cores.

## Contributing and reporting bugs

Open a [GitHub issue](https://github.com/JPLuker/OpenNES/issues) with your Android version, device, emulator build/commit, reproduction steps and logs or screenshots when relevant. Do not upload copyrighted ROMs, RetroAchievements credentials, or other sensitive data. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Licensing and acknowledgments

OpenNES is forked from [Lemuroid](https://github.com/Swordfish90/Lemuroid), based on Libretro and [LibretroDroid](https://github.com/Swordfish90/LibretroDroid); the intended NES core is [FCEUmm](https://github.com/libretro/libretro-fceumm). Lemuroid is licensed under **GNU GPL version 3**. This fork retains `COPYING` and source notices; its dependencies and included native code must retain their applicable licensing and attribution.

OpenNES is not endorsed by or affiliated with Nintendo, Pizza Boy, Lemuroid's maintainers, or RetroAchievements. RetroAchievements integration is planned, not currently shipped.
