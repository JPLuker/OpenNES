# OpenNES — Development progress

Updated: 2026-11-09

This tracker records **implementation**, not test acceptance. At Joseph's request, interactive/device testing and deliberate build verification are paused while development continues. Pushing to `master` can still trigger GitHub Actions automatically; a source commit is not proof of a working APK.

## Milestones

| Module | Implementation status | Next acceptance/development gate |
| --- | --- | --- |
| M0 — Foundation and OpenNES branding | Implemented: dedicated package and GitHub debug APK workflow | Production signing and repeatable real-device regression still pending |
| M1 — NES-only debloat | Mostly implemented: FCEUmm-only APK, NES metadata/scanning, removed console UI | Finish safe source/dependency pruning and audit legacy paths |
| **M2 — UI completion and cleanup** | **Active — cleanup pass**: NES library, first-run onboarding, settings, game menus and shortcuts implemented; dormant mobile multi-console screens and inherited UI widgets pruned | Finish unreadable-folder feedback, device-size/accessibility polish and safe legacy resource auditing; verification postponed |
| M3 — Portrait NES controller | Partially implemented: NES visual skin, independent settings, drag/slider customization | Polish D-pad/A/B ergonomics and verify input interactions |
| M4 — Landscape NES controller | Partially implemented: dedicated orientation settings and placements | Design-specific responsive layout and cutout review |
| M5 — Gameplay reliability | Partially implemented: audio/input fixes, save infrastructure and virtual input release handling | Comprehensive save/audio/suspend/regression verification |
| M6 — RetroAchievements | Not implemented | Official integration feasibility, hashing, accounts, runtime and progress |
| M7 — Public release | Development distribution in place | Testing, security/license audit, signing and release docs |
| M8 — RomM integration (additional planned workstream) | Not implemented | Server authentication, game sync, offline save/conflict design |

## M2 active checklist

- [x] OpenNES NES box-art home shelf and search without bottom navigation
- [x] Recently played and conditional Favorites shelf on the home screen
- [x] Play and hamburger actions beside game detail artwork
- [x] Automatic missing-data enrichment with optional manual maintenance
- [x] Compact source attribution, custom artwork, game editing and game shortcuts
- [x] NES-styled pause, save-state and core settings screens
- [x] NES-styled main, advanced, gamepad and save-sync settings
- [x] Controller input capture UI with retry/write completion
- [x] Remove dead bottom navigation, dedicated Favorites page and console/game picker destinations
- [x] Audit and simplify first-run ROM-folder onboarding and empty states: folder not selected / active scan / no compatible NES ROMs are distinct, with rescan and change-folder recovery
- [x] Prune legacy mobile console browser, Favorites page, BIOS/core-selection screens and their unused view models (7 files)
- [x] Remove unused inherited multi-console card/list/image/empty-view Compose components (7 files)
- [x] Restyle game-actions sheet with native OpenNES typography and NES box art; remove its last inherited text component
- [x] Replace remaining user-visible English references to Lemuroid in loading, save-state, reset and crash messages
- [ ] Audit remaining legacy resources, TV code and internal identifiers without breaking runtime dependencies or license notices
- [ ] Review ROM import failure reporting and inaccessible-folder errors (currently surfaced as an empty result when scanning cannot read a folder)
- [ ] Polish accessibility, small screens, system bars, display cutouts and orientation layouts
- [ ] Verify all interactions on a physical device (paused)

## Development rules

- Work directly on `master`; no PR workflow for day-to-day updates.
- Report **what changed, what remains, and module status** after each batch.
- Do not equate a commit or a green CI run with device testing.
- Do not delete shared emulator infrastructure without identifying its actual consumers.
- No bundled copyrighted commercial ROMs or unapproved RetroAchievements claims.
