# OpenNES

**OpenNES** is a work-in-progress, open-source NES/Famicom emulator for Android.

The goal is a dedicated NES experience: polished original NES-controller-inspired touchscreen layouts for **portrait and landscape**, a redesigned game library and settings experience, and integrated [RetroAchievements](https://retroachievements.org/).

> **Early development status:** This repository is currently a fork of [Lemuroid](https://github.com/Swordfish90/Lemuroid). The user interface and multi-console code have **not yet** been replaced or removed. The initial engineering milestone is establishing a reliable Android build and OpenNES's own app identity. Please do not mistake the current fork for a released, NES-only emulator.

## Product direction

- **NES/Famicom only.** Keep the FCEUmm emulator core, remove other console emulators and unneeded assets/dependencies after baseline verification.
- **Completely original OpenNES interface.** Replace Lemuroid's onboarding, library, game details, menus, settings, and in-game overlays.
- **Premium touchscreen controls.** The classic NES gray/charcoal/red controller is the visual inspiration, with smooth tactile styling and no copied Pizza Boy artwork.
- **Two layouts designed independently.** Portrait handheld and landscape split-controller modes; adjustable control sizes and positions for each orientation.
- **RetroAchievements support.** Planned account integration, supported game identification, earned/locked achievements, discreet in-game notifications, and compliance-aware Hardcore handling.
- **Local game files.** No commercial ROMs or copyrighted game art are bundled.

Read [ROADMAP.md](ROADMAP.md) for phased milestones and acceptance criteria.

## Build from source (development)

The project uses Kotlin, Gradle and Android SDK tooling, plus a Git submodule for emulator cores.

```sh
git clone --recurse-submodules https://github.com/JPLuker/OpenNES.git
cd OpenNES
./gradlew :lemuroid-app:assembleFreeBundleDebug
```

The **free + bundle** development variant is intended to include open-source cores in the APK and avoid the Play Store dynamic-download path. An Android SDK, Java 17, and relevant native/build tools may be required. The installed APK package ID is `com.jpluker.opennes.debug` for debug builds; release is `com.jpluker.opennes`.

When GitHub Actions succeeds, the **OpenNES Android CI** workflow uploads an `OpenNES-debug-apk` build artifact on pull requests and pushes to `master`. This is a test build, not a signed production release.

## Current implementation status

| Milestone | Status |
|---|---|
| M0 — Baseline build, identity, CI | In progress |
| M1 — Remove non-NES systems and assets | Planned |
| M2 — Replace entire UI | Planned |
| M3 — Portrait controller | Planned |
| M4 — Landscape controller | Planned |
| M5 — Gameplay quality | Planned |
| M6 — RetroAchievements | Planned |
| M7 — Public release | Planned |

The source packages and modules still include Lemuroid naming until planned refactoring is complete. Renaming every source package before the baseline build would increase avoidable migration risk.

## Licensing and provenance

OpenNES is based on [Lemuroid](https://github.com/Swordfish90/Lemuroid), which is licensed under the **GNU General Public License v3.0**. The original project's `COPYING` license file and applicable code notices are retained. Included native components and other dependencies may have their own license and attribution requirements; these will be audited for distribution.

OpenNES is not affiliated with Nintendo, Pizza Boy, Lemuroid's original maintainers, or RetroAchievements. ROMs are not provided.
