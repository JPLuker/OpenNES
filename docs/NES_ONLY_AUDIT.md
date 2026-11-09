# OpenNES — NES-only debloat audit

**Status:** M1 cleanup in progress. NES-only native APK packaging and size improvement verified; full source removal and device testing remain.  
**Initial inspection:** 2026-11-06  
**Target:** Keep a full, usable FCEUmm NES emulator, remove all other console support and unnecessary platform features.

## Confirmed sources of non-NES bulk

| Area | What exists now | Required change |
|---|---|---|
| `lemuroid-cores` Git submodule | `Swordfish90/LemuroidCores` contains native libraries for numerous consoles and multiple ABIs | Keep FCEUmm libraries required for target Android ABIs; make NES-only bundle reproducible, retaining upstream license notices |
| `settings.gradle.kts` | Dynamic-feature modules for 20+ emulator cores | Remove non-NES core modules from this app's build configuration after baseline verification |
| `lemuroid-app/build.gradle.kts` | Dynamic-feature list includes numerous cores; `bundle` vs `dynamic` and `free` vs `play` flavors | Consolidate around the open-source NES-only bundled build once old build passes |
| `retrograde-app-shared/.../GameSystem.kt` | Registry of many console systems and their per-system core settings | Restrict game-system discovery and settings to NES/Famicom |
| `retrograde-app-shared/.../SystemID.kt` / `CoreID.kt` | Multiple-system identifiers | Remove unused IDs only after confirming all references and migrations |
| `lemuroid-touchinput/.../layouts/` | Controller layouts for NES plus many other platforms | Keep NES layout, shared layout/input infrastructure; delete unused system layouts and route mappings |
| `retrograde-app-shared/src/main/res/drawable/game_system_*.xml` | Icons for 3DS, DS, Atari, Genesis, etc. alongside NES | Keep NES artwork and shared UI resources; delete unused system icons after reference removal |
| `lemuroid-metadata-libretro-db` | Multi-system game metadata database | Determine NES-only filtering/replacement without breaking identification |
| Android manifest, app modules | Android TV, microphone and system-specific UI/permissions | Audit permissions/activities based on retained NES requirements; remove unneeded code only when references are reconciled |
| ROM scanner and navigation | Multi-system library/catalog behavior | Retain shared scanner/storage infrastructure, but restrict supported formats, game lists, and user-facing navigation to NES |

## Known NES components to preserve

- `lemuroid-touchinput/src/main/java/com/swordfish/touchinput/radial/layouts/NES.kt` — current working NES input mappings, to be restyled/replaced later.
- FCEUmm libretro core and `libretrodroid` Android integration.
- ROM storage permissions/Storage Access Framework and stable library persistence.
- Save RAM, save states, gamepads, in-game rendering, and audio.
- Data needed to identify supported NES games for RetroAchievements later.
- Existing GPL and third-party attribution.

## Removal order

1. **First baseline build:** verify `:lemuroid-app:assembleFreeBundleDebug`, record APK size, inspect packaged `.so` files.
2. **Safe registry pruning:** allow only NES in system discovery; test scans and game launch.
3. **Core pruning:** create an NES-only core bundle. Avoid retaining unused console binaries merely because a Gradle module is gone.
4. **Multi-system screen pruning:** remove console picker/navigation and no-longer-used UI logic.
5. **Resources and services:** remove unused icons, controller layouts, metadata, platform-specific permissions/flows when no longer referenced.
6. **Build simplification:** consolidate unnecessary product flavors and dependencies only after NES works.
7. **Final size inspection:** enumerate all APK native libraries and compare actual size to baseline.

## Follow-up source audit (2026-11-09)

The M2 source review found the following live links, so deletion is **deferred** rather than treated as completed:

| Legacy surface | Evidence | Next safe action |
|---|---|---|
| Android TV entry points | `lemuroid-app/src/main/AndroidManifest.xml` still registers `MainTVActivity`, TV settings, picker, game/menu, and input activities with a Leanback launcher | Map the remaining TV consumers/DI registrations before removing TV components; desktop/mobile ROM and save services must not regress |
| TV-specific platform dependencies | The app Gradle file still includes Leanback, Leanback Paging, Leanback Preferences and TV Provider; the manifest requests TV EPG access | Remove only after surviving source and manifest references are accounted for |
| APK variants | `lemuroid-app/build.gradle.kts` retains free/play and bundle/dynamic variants; `settings.gradle.kts` supports the FCEUmm dynamic module | Preserve existing debug APK publishing until a single NES-only replacement build is designed and verified |
| Internal resource/package identities | Manifest/app namespace and themes use old `lemuroid` identifiers; a `lemuroid_help_content` key now contains OpenNES-specific help, and launch intents still use a legacy scheme | Preserve identifiers until saved preferences, shortcut intents, manifest references, and app migration behavior are fully mapped |
| Store review hook | `MainActivity` previously initialized the upstream review manager at startup | Initialization removed; separately determine if its ext-module dependency can be dropped safely |

M2 also added safe-drawing insets to the main navigation, top bars and pause menu. These are **source changes, not real-device acceptance**, and they must still be checked with gesture navigation, cutouts, rotations, large text and TalkBack after the testing pause ends.

## Build and functional gates

- [x] Clean checkout with recursive submodules builds a debug APK.
- [ ] APK installs with independent OpenNES package ID.
- [ ] Scan a legally obtained NES test ROM and launch it.
- [ ] D-pad, A/B, Start/Select, sound, rotation, and save RAM/state work.
- [x] Runtime ROM discovery and metadata matching are restricted to NES.
- [x] Inspect APK for every native `.so`; no non-NES emulator cores remain.
- [x] Record baseline and final APK size in bytes, including ABI breakdown.
- [ ] Preserve source and binary license attribution for included code.
- [ ] Ensure ROM recognition and save upgrades don't silently wipe existing data.

## What is **not** done yet

**Verified:** APK reduced from 222,012,846 to **32,367,426 bytes** after additional CI metadata trimming. All 76 non-NES Libretro core binaries were excluded. Source cleanup removed 23 other-console touch layouts and 20 console artwork XML files. The NES library and metadata provider have been restricted to NES. **Verified:** CI retained 29,246 NES metadata entries and reduced the SQLite asset from 12,775,424 to 3,706,880 bytes. The source DB itself remains unchanged. **Still pending:** real-device NES ROM launch/audio/saves, legacy Android TV code, and the full UI redesign.
