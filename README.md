# OpenNES

**A dedicated, open-source NES emulator for Android — built around a premium touchscreen controller and a clean, mobile-first interface.**

**[Download the latest OpenNES test APK](https://github.com/JPLuker/OpenNES/releases/download/dev-latest/OpenNES-debug.apk)** · [Check its SHA-256](https://github.com/JPLuker/OpenNES/releases/download/dev-latest/OpenNES-debug.apk.sha256)

One normal testing download: `master` → validated build → rolling `dev-latest/OpenNES-debug.apk`. No separate candidate APK branches or workflow ZIPs; production releases come later after RomM acceptance.

[Development roadmap](ROADMAP.md) · [Module progress](docs/MODULE_PROGRESS.md) · [M5 final-testing checklist](docs/M5_FINAL_TESTING.md) · [Android build instructions](docs/BUILDING.md) · [NES-only cleanup audit](docs/NES_ONLY_AUDIT.md) · [GitHub Actions](https://github.com/JPLuker/OpenNES/actions) · [Report an issue](https://github.com/JPLuker/OpenNES/issues)

> **Early development:** The [rolling development APK](https://github.com/JPLuker/OpenNES/releases/download/dev-latest/OpenNES-debug.apk) is published from the latest successfully linted, compiled and inspected `master` commit. The controller UI now includes live preset previews, a HEX/HSV color picker, visible palette icons and the Up/Down feedback correction. **Full M0–M5 on-device acceptance is not finished**. Native RetroAchievements and RomM acceptance remain pending.

## What OpenNES is meant to be

The goal is the convenience and polish of a dedicated Android emulator, designed **specifically for NES/Famicom**, not a general-purpose emulation frontend.

- **NES-inspired controller skin:** original, polished warm-gray, charcoal and deep-red tactile controls, with classic D-pad, A/B, Start and Select.
- **Designed twice for phones:** portrait should feel like a dedicated handheld; landscape should frame a large game viewport with controls on both sides. Each orientation independently saves D-pad and A/B sizes and X/Y positions, plus shared visual settings. Direct on-screen cluster dragging and slider-based fine-tuning are implemented; their ergonomics still need real-device testing.
- **Entirely new UI:** game library, onboarding, game details, menus, settings, quick actions and achievement overlays will be redesigned under the OpenNES identity.
- **RetroAchievements:** native Casual login, game identification, achievement lists and an in-game unlock banner are implemented in development builds. Real award persistence remains a device test; Hardcore is disabled until emulator approval.
- **Natively Android:** touchscreen play, external gamepads, local ROM folders, save data and convenient installation.
- **Truly NES-only distribution:** remove unused emulator cores, non-NES system routes, files and packaged assets, not just hide other systems in the interface.
- **Open source:** preserve applicable GPL and third-party notices. No commercial ROMs are included.

The visual direction takes inspiration from Pizza Boy's level of polish, **not its copyrighted visual assets or source code**. All final OpenNES artwork and UI will be original.

**Game metadata (Plex-style enrichment):** After ROM indexing, a separate network-constrained background worker automatically checks eligible NES titles. It verifies one unambiguous Wikidata (CC0) NES match, retrieves genre/year, and adds an attributed Wikipedia (CC BY-SA) article summary when available. It only sends cleaned game titles, never ROMs or saves. Results are stored in the game library and missing/unmatched checks are cached for 30 days; individual worker batches are limited to eight titles with pacing and retries. Existing manual metadata edits, covers and save data are preserved. **Settings → Library maintenance** can disable automatic lookups or manually run the existing 12-title metadata refresh. The Home screen remains game-first without an always-visible maintenance panel. Game descriptions appear in Continue Playing and game details when available. Matching remains conservative; some games may have missing metadata.

**Artwork progress:** Matched NES games show Libretro box art, title screens and gameplay screenshots in game details. The library caches a bounded number of visible covers, and successful background metadata batches also prefetch matched game artwork into Coil's cache. Some ROM filenames can supply an exact Libretro cover fallback. Android may evict cached art, so offline availability is best-effort. Custom covers always take priority. This is not yet a complete Plex-style provider ecosystem.

## Development status

**Release order:** M7 RomM integration must pass acceptance before M8 public release. Production hardening may proceed in parallel.

| Milestone | Status |
| --- | --- |
| M0 — Own Android package/name and reproducible APK workflow | CI complete; device testing pending |
| M1 — Remove non-NES cores, assets and system selection | In progress; NES-only binaries, controls and system artwork cleaned |
| M2 — Replace the complete Lemuroid user interface | NES library, details, menus, settings, import recovery and Android TV UI removal are present in passing development APK builds; full on-device acceptance remains open |
| M3 — Premium NES portrait controller skin | Six themes, real-time in-game preview/HEX wheel, swatch palette icons, fixed D-pad visuals, independent A/B and Start/Select/Menu positioning with PadKit touch anchors; hardware acceptance pending |
| M4 — Dedicated landscape controller skin | Overlay, Framed and optional NES Handheld landscape layouts, controller palette integration, compact-screen fallback and independent orientation settings; real-device acceptance pending |
| M5 — Gameplay, saves, input and performance polish | CI-verified test build includes immutable queued hardware-input snapshots and save reliability fixes; expanded manual input/save/audio/controller regression testing remains |
| M6 — RetroAchievements integration | Native Casual rc_client game sessions, achievement lists and unlock notifications are implemented; a new officially persisted award and reconnect behavior still require hardware verification; Hardcore disabled |
| M7 — RomM integration (pre-release requirement) | Authenticated NES browsing/import, account-bound offline backups, conflict planning and non-executing sync intents are implemented; live server verification, uploads and automatic sync remain outstanding |
| M8 — Production releases and maintenance | Source hardening underway: inherited release credentials removed, secret-provided optional production signing configured; debug APK/checksum distribution retained; production audits/QA and M7 RomM acceptance pending |

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

This is a **debug/test APK**, not a production-signed release. Package ID: `com.jpluker.opennes.debug`. Development APKs use a persistent test signing identity and an increasing Android build code (`100000 + CI run number`) so successive CI builds can install over each other without deleting saves. The project will add separately signed production releases later. No commercial ROMs are provided. Android rejects an in-place update if the signer or application ID changes.

For diagnostics, the [GitHub Actions](https://github.com/JPLuker/OpenNES/actions) page provides build, lint and APK-inspection logs. The APK itself has one normal location: `dev-latest`.

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

CI already runs this tool with `--nes-only --native-bridge` to reject unexpected emulator cores or missing native achievement-bridge exports.

## Contributing and reporting bugs

Open a [GitHub issue](https://github.com/JPLuker/OpenNES/issues) with your Android version, device, emulator build/commit, reproduction steps and logs or screenshots when relevant. Do not upload copyrighted ROMs, RetroAchievements credentials, or other sensitive data. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Distribution and new feature scope

OpenNES will launch publicly through **Google Play**. Updates will be managed by the Play Store; there is **no in-app updater**. GitHub's `dev-latest` debug APK remains for development testing only, with a different app ID and signing identity. Preserve saves by exporting them before moving between debug and Play releases.

Casual-only rewind, touchscreen Zapper input and individual D-pad hitbox customization are implemented in development builds and **not yet verified on a phone**.

## Privacy

OpenNES can play locally imported NES games without an OpenNES account or internet connection. The RomM and RetroAchievements integrations are optional. ROM files, save states and local game settings remain on your device unless you choose to import, export or synchronize through a supported service. **Automatic RomM save uploads and overwrites are not enabled.** Do not use development backups as your only copy of a save.

### Network connections

| Destination | When used | Information sent or retrieved |
| --- | --- | --- |
| `retroachievements.org` | When connecting a RetroAchievements account or playing an eligible game while signed in | Login token and game/achievement requests required by the service; no ROM file upload |
| Your configured RomM server | Only when RomM is connected and its features are used | The chosen server account credential and requests to browse/download NES entries and explicit backups |
| `thumbnails.libretro.com` | When resolving or caching library artwork | Artwork filename requests |
| `www.wikidata.org`, `en.wikipedia.org` | When online metadata lookup or enrichment is requested/enabled | Game-title and verified Wikidata entity queries; no ROM bytes |
| `api.github.com`, `raw.githubusercontent.com` | When searching the optional Libretro cheat catalogue | Cheat index and selected game cheat-file requests; imported cheats start disabled |

These external services may receive normal HTTPS connection metadata such as an IP address. Their own privacy policies apply. A separate OpenNES-hosted account, analytics account or required cloud backend is not part of the intended local-play flow. Full inherited dependency and network audits are release gates, not completed certifications.

The standalone policy is [PRIVACY.md](PRIVACY.md); bundled work is credited in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). For security concerns, see [SECURITY.md](SECURITY.md). The current implementation and unresolved hardware checks are listed in [TESTING.md](TESTING.md).

## Licensing and acknowledgments

OpenNES is forked from [Lemuroid](https://github.com/Swordfish90/Lemuroid), based on Libretro and [LibretroDroid](https://github.com/Swordfish90/LibretroDroid); the intended NES core is [FCEUmm](https://github.com/libretro/libretro-fceumm). Lemuroid is licensed under **GNU GPL version 3**. This fork retains `COPYING` and source notices; its dependencies and included native code must retain their applicable licensing and attribution. Wikimedia metadata is credited per game: Wikidata data is CC0 and Wikipedia extracts require CC BY-SA attribution and link to the original article.

OpenNES is not endorsed by or affiliated with Nintendo, Pizza Boy, Lemuroid's maintainers, or RetroAchievements. Native Casual RetroAchievements support is implemented in development builds, but a new server-persisted award is not yet verified on hardware. Official Hardcore support remains disabled.
