# Changelog

## Unreleased — NES-only foundation (M1)

- Only NES/Famicom games and FCEUmm are registered.
- Excluded non-NES native cores from the APK and enforced the NES-only inspection in CI.
- Removed non-NES controller layouts and console-specific artwork from the source tree.
- Removed DS microphone and DeSmuME warning flows from Home; retained general notification permission handling.
- Real-device NES gameplay verification and metadata/source cleanup remain in progress.

This file tracks OpenNES-specific changes, not the upstream Lemuroid release history.

## Unreleased — NES-only foundation (M1)

- Register only NES/Famicom cartridge games, using FCEUmm.
- Exclude 19 non-NES Libretro cores across all Android ABIs from the APK.
- Remove non-NES dynamic feature modules and the multi-system bottom navigation button.
- Hide irrelevant BIOS and core-selection links from settings.
- Add CI verification that no non-NES libretro core ships.
- Full source/resource pruning and real-device gameplay testing remain.

## 0.1.0 foundation (M0)

- Established the OpenNES Android app display name and a distinct `com.jpluker.opennes` application ID.
- Added a complete NES-only roadmap and a source-level audit of non-NES emulator components.
- Replaced the inherited repository README with the OpenNES project description and actual development status.
- Added Android building, installation and contributing guides.
- Configured GitHub Actions to build and upload `OpenNES-debug.apk`, inspect included Libretro cores, and report Kotlin lint separately.
- Removed the inherited Lemuroid tag-based publisher to avoid accidental releases using upstream signing assumptions.
- Corrected initial inherited Kotlin formatting violations exposed by the CI pipeline.

### Known limitations

- The inherited Lemuroid UI, branding assets and non-NES emulation components remain.
- Device-side NES gameplay hasn't been tested for OpenNES.
- RetroAchievements and custom portrait/landscape skins have not yet been implemented.
- The first CI workflow produced an APK, but failed in the Kotlin lint step; follow-up CI verification is pending.
