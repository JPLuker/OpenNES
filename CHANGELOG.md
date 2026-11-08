# Changelog

OpenNES-specific history; upstream Lemuroid release history is not reproduced.

## Unreleased — OpenNES game library (M2, first batch)

- Introduce an original NES-inspired home library: recently played, favorites, discovery shelf, and a prominent resume-game panel.
- Add true cumulative foreground gameplay session tracking, displayed on game tiles; paused/background time is excluded, and prior sessions are intentionally reported as untracked.
- Add a Room 10→11 migration that preserves existing games and saves while initializing historic playtime to zero.
- Provide direct search and settings controls on the new home screen instead of the inherited top bar.
- Achievement progress is not fabricated: RetroAchievements remains a separate implementation milestone.

## NES-only cleanup (M1)

- Reduced the debug APK from **222,012,846** to **32,367,426 bytes** (85.4% smaller), eliminating all 76 unrelated Libretro core binaries while retaining FCEUmm across four ABIs.
- Restricted registered game systems and library discovery to NES/Famicom.
- Removed non-NES dynamic-feature builds, 23 unrelated touchscreen layouts, 20 non-NES system icons, and the multi-console navigation entry.
- Removed unnecessary core-selection and BIOS settings links.
- Fixed Home initialization for a single NES core, plus the old Game Boy default controller.
- Removed DS microphone/DeSmuME Home warnings and unused microphone permission.
- Hardened game metadata identification to ignore non-NES matches in the inherited database.
- Added CI SQLite trimming: 104,073 original records reduced to 29,246 NES records; database size 12,775,424 to 3,706,880 bytes.
- Made CI inspect and reject any non-NES packaged Libretro core.

**Not finished:** On-device verification, unused metadata/source pruning, original OpenNES UI, custom controller skins and RetroAchievements.

## 0.1.0 foundation (M0)

- Established OpenNES's app label, version, unique Android package ID, and open-source roadmap.
- Added GitHub Actions debug APK builds with independent Kotlin lint and an installable APK artifact.
- Added README, build/contribution guides, and NES-only audit.
- Removed the inherited Lemuroid publisher and corrected baseline Kotlin formatting.
