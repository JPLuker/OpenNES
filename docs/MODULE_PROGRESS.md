# OpenNES — Development progress

Updated: 2026-11-09

This tracker records **implementation**, not test acceptance. At Joseph's request, interactive/device testing and deliberate build verification are paused while development continues. Pushing to `master` can still trigger GitHub Actions automatically; a source commit is not proof of a working APK.

## Milestones

| Module | Implementation status | Next acceptance/development gate |
| --- | --- | --- |
| M0 — Foundation and OpenNES branding | Implemented: dedicated package and GitHub debug APK workflow | Production signing and repeatable real-device regression still pending |
| M1 — NES-only debloat | Mostly implemented: FCEUmm-only APK, NES metadata/scanning, removed console UI | Finish safe source/dependency pruning and audit legacy paths |
| **M2 — UI completion and cleanup** | **Active — final accessibility review**: NES interface, ROM recovery, responsive layout, source-only cutout safeguards and Android TV source/assets removal implemented | Finish keyboard/TalkBack and layout edge cases; device acceptance postponed |
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
- [x] Remove Android TV launcher/activity registrations, channel update worker, channel provider and TV EPG permission; preserve the native mobile shortcut deep-link scheme and shared emulation infrastructure
- [x] Remove inherited post-game store-review prompt from the active game-finish handler while preserving playtime updates, save-sync rescheduling and error handling
- [x] Remove 36 now-dormant Android TV Kotlin files and 8 exclusive TV UI assets, disconnect TV-only settings providers and mobile launcher selection, prune direct Leanback and Leanback Paging app dependencies
- [ ] Audit remaining shared LeanbackPreferenceFragment API compatibility, inherited internal identifiers and third-party licensing without breaking saves, shortcuts or migrations
- [x] Propagate failed ROM scans instead of reporting success, preflight selected-folder read grants, preserve indexed games on failure, and show recovery actions even when earlier games remain visible (device acceptance still paused)
- [x] Improve narrow-phone and large-text layouts for game-details actions, featured cover and ROM folder recovery buttons; add explicit screen-reader labels for shelf taps and long-press actions (device acceptance paused)
- [x] Apply safe-drawing insets to main navigation, non-home top bars and the pause-menu side panel; label search actions and add Help dialog dismissal (source-only)
- [ ] Review further accessibility, large-font focus behavior and remaining landscape cutout edge cases on device (paused)
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

## Latest M2 source batch — system insets and search accessibility

- Main navigation's Compose scaffold uses safe-drawing insets; search/settings top bars also account for landscape cutouts.
- The separate pause-menu side panel retains its edge-to-edge background while its interactive content avoids top, bottom, and end cutouts/system bars.
- Search results announce game-specific Play and long-press actions and the specific game being added to or removed from Favorites; search IME uses the Search action.
- Settings Help now has an explicit labeled Close button, and inherited store-review initialization no longer runs on app startup.
- **Legacy audit findings:** the manifest still registers Android TV activities/Leanback launcher and TV EPG permission; Gradle retains open-source/Play and bundled/dynamic build flavors and Leanback dependencies. Internal `lemuroid` identifiers still support app resources, navigation, saved data, and compatibility paths. Removing these requires a separate reference/dependency audit; none were deleted in this batch.
- **Not tested:** TalkBack focus order, large-font clipping, cutout placements, keyboard/resume behavior, emulator/gameplay UI, and APK compatibility remain for later device verification.

## Latest M2 source batch — Android TV entry points and channel cleanup

- TV launcher/manifest activity registrations and Dagger entry module removed, preventing dead TV screens from appearing as a supported OpenNES interface.
- TV preview channel publisher and worker removed, together with TV EPG permission and tvProvider dependency.
- Regular Play/Continue, saves, native controller and ROM import are unchanged. Pinned shortcut deep links remain compatible and route into the mobile game screen.
- **Still pending:** deleting dormant TV classes, unused Leanback dependencies/resources, and optional legacy internal names after reference audit. No build or device testing was performed.

## Latest M2 source batch — post-game flow cleanup

- Removed the inherited in-app review prompt from game-finish processing, including the old rating flag and review-manager dependency.
- Preserved session duration accounting, last-played timestamps, save-sync/cache scheduling and crash handling.
- Updated every currently known call site (mobile, pinned shortcuts and dormant TV source). Existing ext modules remain until separately audited for shared uses.
- **Source-only:** no build or runtime testing was performed.

## Latest M2 source batch — dormant Android TV implementation removed

- Reviewed all 130 non-TV Kotlin files in the Android app against TV class and Leanback references. Only the application module's TV settings providers and the shared game activity's TV launcher selection needed changes.
- Deleted 36 inactive Android TV classes and 8 TV-specific UI resources (navigation graph, layout, settings XML, Leanback color/theme overrides, channel icon).
- Deleted the now-unused TV-specific Dagger settings providers and eliminated the TV launch class reference. Android game launches now always select the mobile NES GameActivity; stored launch flags remain compatible.
- Removed direct Leanback and Leanback Paging app dependencies; retained Leanback Preferences because the shared StorageProvider interface still exposes LeanbackPreferenceFragment. Retained source/package IDs, ROM/saves, shortcut deep-link URI scheme and license files.
- **Not tested:** app compilation, Android lifecycle behavior, device insets, gamepad interactions, saved games and APK packaging are all pending verification after the testing pause.

## Development rules

- Work directly on `master`; no PR workflow for day-to-day updates.
- Report **what changed, what remains, and module status** after each batch.
- Do not equate a commit or a green CI run with device testing.
- Do not delete shared emulator infrastructure without identifying its actual consumers.
- No bundled copyrighted commercial ROMs or unapproved RetroAchievements claims.
