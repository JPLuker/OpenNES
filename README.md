# OpenNES

**A dedicated, open-source NES emulator for Android — built around a premium touchscreen controller and a clean, mobile-first interface.**

[Development roadmap](ROADMAP.md) · [Android build instructions](docs/BUILDING.md) · [NES-only cleanup audit](docs/NES_ONLY_AUDIT.md) · [GitHub Actions](https://github.com/JPLuker/OpenNES/actions) · [Report an issue](https://github.com/JPLuker/OpenNES/issues)

> **Early development:** The latest NES-only APK passed CI at **32.4 MB**, down from a 222 MB multi-console baseline, after removing non-NES cores and pruning packaged metadata. It contains FCEUmm only. Other-console layouts and artwork are also being removed. The UI is still largely inherited from [Lemuroid](https://github.com/Swordfish90/Lemuroid); RetroAchievements and the custom portrait/landscape skins are not built, and NES gameplay and audio have been tested on a physical device, but the latest metadata and artwork changes still need device testing.

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

**Artwork progress:** Matched NES games can show Libretro box art, title screens and gameplay screenshots in the game-details panel. The library prefetches a bounded group of visible covers into Coil's disk cache. Android can evict this cache, so offline artwork is best-effort rather than permanent. Custom covers always take priority. External metadata providers and full Plex-style refresh controls are still planned.

## Development status

| Milestone | Status |
| --- | --- |
| M0 — Own Android package/name and reproducible APK workflow | CI complete; device testing pending |
| M1 — Remove non-NES cores, assets and system selection | In progress; NES-only binaries, controls and system artwork cleaned |
| M2 — Replace the complete Lemuroid user interface | In progress: original Home shelf, Continue Playing and recorded playtime (PR #7) |
| M3 — Premium NES portrait controller skin | Not started |
| M4 — Dedicated landscape controller skin | Not started |
| M5 — Gameplay, saves, input and performance polish | Not started |
| M6 — RetroAchievements integration | Not started |
| M7 — Production releases and maintenance | Not started |

See the [full roadmap](ROADMAP.md) for sequencing and acceptance tests.

### What works in the fork today?

The inherited Lemuroid project provides a working Android emulation frontend, FCEUmm support, ROM scanning, gamepad/touch input and save-state infrastructure. **Those existing features have not all been validated in OpenNES on real hardware yet.** Current development is keeping this functionality intact while preparing the NES-only conversion.

Still inherited: portions of Lemuroid's library/settings UI, multi-system metadata data, some dormant source, internal package names and launcher artwork. **Non-NES Libretro binaries are excluded from the APK.** The remaining upstream UI and branding are scheduled for replacement.

## Download the latest OpenNES APK

**[Download OpenNES-debug.apk directly](https://github.com/JPLuker/OpenNES/releases/download/dev-latest/OpenNES-debug.apk)** · [Development release page](https://github.com/JPLuker/OpenNES/releases/tag/dev-latest)

Each successful push to `master` builds and publishes a new APK at this same address. No pull requests, ZIP extraction, or GitHub Actions artifact hunting is needed. If the build fails, the previously successful APK remains available.

1. Download `OpenNES-debug.apk` and install it on Android (allow installation from the browser/files app if prompted).
2. Import your own legally obtained NES ROMs.
3. To update, download from the **same link** and install over the existing OpenNES debug build.

This is a **debug/test APK**, not a production-signed release. Package ID: `com.jpluker.opennes.debug`. The project will add separately signed production releases later. No commercial ROMs are provided. Android will reject an in-place update if a build uses a different signing certificate.

For diagnostics, the [GitHub Actions](https://github.com/JPLuker/OpenNES/actions) page still provides test results and build artifacts.

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
