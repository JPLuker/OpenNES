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
- [x] Propagate failed ROM scans instead of reporting success, preflight selected-folder read grants, preserve indexed games on failure, and show recovery actions even when earlier games remain visible (device acceptance still paused)
- [x] Improve narrow-phone and large-text layouts for game-details actions, featured cover and ROM folder recovery buttons; add explicit screen-reader labels for shelf taps and long-press actions (device acceptance paused)
- [ ] Review additional accessibility paths, system bars, display cutouts and portrait/landscape edge cases
- [ ] Verify all interactions on a physical device (paused)

## Latest M2 source batch — ROM import failure protection

- Scan exceptions now propagate out of the library indexer; cleanup of old games runs only after the scan finishes successfully.
- The worker checks selected Android document-tree permissions and readability before scanning. Folder-access and other indexing failures are saved and reported as failed work.
- The home library displays a recoverable, specific error instead of calling a failed scan an empty collection. Previously indexed shelves remain visible.
- Re-selecting an existing folder retries permission acquisition. Newly selected folder access is secured before previously granted access is released.
- **Not accepted on-device:** blocked/revoked folder flows, canceled scans, empty valid folders, successful rescan, and retention of existing saves remain regression scenarios for later verification.

## Latest M2 source batch — compact layouts and accessible actions

- Compact game-details layouts now position the Play/hamburger actions under the game header rather than squeezing them next to narrow text. Normal phone layouts retain the original beside-artwork arrangement.
- NES box-art proportions remain unchanged; the featured and game-details covers use narrower widths where needed.
- Shelf covers expose explicit tap-to-details and long-press-for-actions screen-reader labels. The hamburger action label includes the selected game title.
- ROM scan recovery actions stack vertically when narrow widths or enlarged text make side-by-side buttons cramped.
- **Implementation only:** no emulator, APK or UI device tests were executed. Next M2 review should focus on insets/cutouts, screen reader focus order, and legacy resources.

## Development rules

- Work directly on `master`; no PR workflow for day-to-day updates.
- Report **what changed, what remains, and module status** after each batch.
- Do not equate a commit or a green CI run with device testing.
- Do not delete shared emulator infrastructure without identifying its actual consumers.
- No bundled copyrighted commercial ROMs or unapproved RetroAchievements claims.
