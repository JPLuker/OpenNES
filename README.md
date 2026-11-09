# OpenNES

**A dedicated, open-source NES emulator for Android — built around a premium touchscreen controller and a clean, mobile-first interface.**

[Development roadmap](ROADMAP.md) · [Module progress](docs/MODULE_PROGRESS.md) · [Android build instructions](docs/BUILDING.md) · [NES-only cleanup audit](docs/NES_ONLY_AUDIT.md) · [GitHub Actions](https://github.com/JPLuker/OpenNES/actions) · [Report an issue](https://github.com/JPLuker/OpenNES/issues)

> **Early development:** The last verified development APK was approximately **32.7 MB**, down from a 222 MB multi-console baseline. It packages the FCEUmm NES core, and the home shelf, game details, search, pause and major settings screens now use OpenNES-specific UI. Portrait and landscape controller layouts exist but still need substantial polish. RetroAchievements is not implemented. **Device testing and deliberate build verification are currently paused**; new source commits must not be mistaken for verified releases.

## What OpenNES is meant to be

The goal is the convenience and polish of a dedicated Android emulator, designed **specifically for NES/Famicom**, not a general-purpose emulation frontend.

- **NES-inspired controller skin:** original, polished warm-gray, charcoal and deep-red tactile controls, with classic D-pad, A/B, Start and Select.
- **Designed twice for phones:** portrait should feel like a dedicated handheld; landscape should frame a large game viewport with controls on both sides. Each orientation independently saves D-pad and A/B sizes and X/Y positions, plus shared visual settings. Direct on-screen cluster dragging and slider-based fine-tuning are implemented; their ergonomics still need real-device testing.
- **Entirely new UI:** game library, onboarding, game details, menus, settings, quick actions and achievement overlays will be redesigned under the OpenNES identity.
- **RetroAchievements:** planned native sign-in, game identification, achievement tracking and notifications. Hardcore support will follow RetroAchievements' actual emulator-approval rules.
- **Natively Android:** touchscreen play, external gamepads, local ROM folders, save data and convenient installation.
- **Truly NES-only distribution:** remove unused emulator cores, non-NES system routes, files and packaged assets, not just hide other systems in the interface.
- **Open source:** preserve applicable GPL and third-party notices. No commercial ROMs are included.

The visual direction takes inspiration from Pizza Boy's level of polish, **not its copyrighted visual assets or source code**. All final OpenNES artwork and UI will be original.

**Game metadata (Plex-style enrichment):** After ROM indexing, a separate network-constrained background worker automatically checks eligible NES titles. It verifies one unambiguous Wikidata (CC0) NES match, retrieves genre/year, and adds an attributed Wikipedia (CC BY-SA) article summary when available. It only sends cleaned game titles, never ROMs or saves. Results are stored in the game library and missing/unmatched checks are cached for 30 days; individual worker batches are limited to eight titles with pacing and retries. Existing manual metadata edits, covers and save data are preserved. **Settings → Library maintenance** can disable automatic lookups or manually run the existing 12-title metadata refresh. The Home screen remains game-first without an always-visible maintenance panel. Game descriptions appear in Continue Playing and game details when available. Matching remains conservative; some games may have missing metadata.

**Artwork progress:** Matched NES games show Libretro box art, title screens and gameplay screenshots in game details. The library caches a bounded number of visible covers, and successful background metadata batches also prefetch matched game artwork into Coil's cache. Some ROM filenames can supply an exact Libretro cover fallback. Android may evict cached art, so offline availability is best-effort. Custom covers always take priority. This is not yet a complete Plex-style provider ecosystem.

## Development status

| Milestone | Status |
| --- | --- |
| M0 — Own Android package/name and reproducible APK workflow | CI complete; device testing pending |
| M1 — Remove non-NES cores, assets and system selection | In progress; NES-only binaries, controls and system artwork cleaned |
| M2 — Replace the complete Lemuroid user interface | **Code-complete (source scope)**: NES library, details, menus, settings, import recovery, accessible layouts and Android TV UI removal; build and on-device acceptance deferred |
| M3 — Premium NES portrait controller skin | **Code-complete (source scope)**: NES housing, D-pad/A/B/Start/Select/Menu, responsive defaults and saved portrait-specific editor; device acceptance deferred |
| M4 — Dedicated landscape controller skin | **Code-complete (source scope)**: selectable framed/overlay gameplay, minimal appearance, dedicated secondary controls, adaptive sizing, cutout handling and separate settings; on-device acceptance deferred |
| M5 — Gameplay, saves, input and performance polish | Partly implemented: audio, controller persistence and stuck-input fixes; regression testing pending |
| M6 — RetroAchievements integration | Native integration started: local NES ROM hashing and LibretroDroid/rc_client technical handoff; sign-in, runtime and unlocks not implemented |
| M7 — Production releases and maintenance | Debug release pipeline in place; production signing, audits and QA pending |

See the [module progress tracker](docs/MODULE_PROGRESS.md) for the current implementation checklist, M2 code-complete disposition and deferred device acceptance; the [full roadmap](ROADMAP.md) retains original acceptance gates.

### What works in the fork today?

The inherited Lemuroid project provides a working Android emulation frontend, FCEUmm support, ROM scanning, gamepad/touch input and save-state infrastructure. **Those existing features have not all been validated in OpenNES on real hardware yet.** Current development is keeping this functionality intact while preparing the NES-only conversion.

Still inherited: shared internal package names, required emulator infrastructure and some legacy resource/API compatibility identifiers. **Non-NES Libretro binaries are excluded from the APK.** Obsolete mobile multi-console screens and the now-unregistered Android TV implementation have been removed from source, along with TV-exclusive assets. The shared storage API still requires Leanback Preferences; [NES-only audit](docs/NES_ONLY_AUDIT.md) tracks retained dependencies and migration concerns. License notices and shortcut/save compatibility identifiers are preserved.

## Download the latest OpenNES APK

**[Download OpenNES-debug.apk directly](https://github.com/JPLuker/OpenNES/releases/download/dev-latest/OpenNES-debug.apk)** · [Development release page](https://github.com/JPLuker/OpenNES/releases/tag/dev-latest)

Each successful push to `master` builds and publishes a new APK at this same address. No pull requests, ZIP extraction, or GitHub Actions artifact hunting is needed. If the build fails, the previously successful APK remains available.

1. Download `OpenNES-debug.apk` and install it on Android (allow installation from the browser/files app if prompted).
2. On first launch, choose the folder containing your own legally obtained NES ROM files (such as `.nes`). OpenNES scans it and adds supported games to your shelves. If none are detected, the home screen offers **Rescan** and **Change folder**. You can also change the folder from Settings.
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

OpenNES is forked from [Lemuroid](https://github.com/Swordfish90/Lemuroid), based on Libretro and [LibretroDroid](https://github.com/Swordfish90/LibretroDroid); the intended NES core is [FCEUmm](https://github.com/libretro/libretro-fceumm). Lemuroid is licensed under **GNU GPL version 3**. This fork retains `COPYING` and source notices; its dependencies and included native code must retain their applicable licensing and attribution. Wikimedia metadata is credited per game: Wikidata data is CC0 and Wikipedia extracts require CC BY-SA attribution and link to the original article.

OpenNES is not endorsed by or affiliated with Nintendo, Pizza Boy, Lemuroid's maintainers, or RetroAchievements. RetroAchievements integration is planned, not currently shipped.
