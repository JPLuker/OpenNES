# OpenNES — Development progress

Updated: 2026-11-09

This tracker records **implementation**, not test acceptance. At Joseph's request, interactive/device testing and deliberate build verification are paused while development continues. Pushing to `master` can still trigger GitHub Actions automatically; a source commit is not proof of a working APK.

## Milestones

| Module | Implementation status | Next acceptance/development gate |
| --- | --- | --- |
| M0 — Foundation and OpenNES branding | Implemented: dedicated package and GitHub debug APK workflow | Production signing and repeatable real-device regression still pending |
| M1 — NES-only debloat | Mostly implemented: FCEUmm-only APK, NES metadata/scanning, removed console UI | Finish safe source/dependency pruning and audit legacy paths |
| **M2 — UI completion and cleanup** | **CODE-COMPLETE (source scope; not device accepted)**: NES library, navigation, detail sheets, menus, settings, ROM recovery, compact layouts and accessibility pass implemented; obsolete TV UI removed | Device and build acceptance remain paused; regressions will be assessed when testing resumes |
| **M3 — Portrait NES controller** | **ACTIVE**: original NES controls, portrait-specific sizing and editing already present; first ergonomics pass adds D-pad ridges, enlarged NES touch bounds and full-screen viewport with hardware gamepads | Finish safe small-phone positioning, Start/Select/menu reachability and controller editor usability; device acceptance paused |
| M4 — Landscape NES controller | Partially implemented: dedicated orientation settings and placements | Design-specific responsive layout and cutout review |
| M5 — Gameplay reliability | Partially implemented: audio/input fixes, save infrastructure and virtual input release handling | Comprehensive save/audio/suspend/regression verification |
| M6 — RetroAchievements | Not implemented | Official integration feasibility, hashing, accounts, runtime and progress |
| M7 — Public release | Development distribution in place | Testing, security/license audit, signing and release docs |
| M8 — RomM integration (additional planned workstream) | Not implemented | Server authentication, game sync, offline save/conflict design |

## M3 active source checklist

- [x] Keep portrait and landscape controller settings independently persisted
- [x] NES-only D-pad, A/B, Start and Select foregrounds and button IDs
- [x] Allow D-pad diagonals and simultaneous A/B through existing PadKit input dispatch
- [x] Give NES D-pad a textured, recessed cross without modern arrow icons
- [x] Expand NES D-pad and face-button interactive regions without remapping input
- [x] Use an unconstrained full-screen game viewport when no virtual pads exist (physical controller or disabled overlay)
- [x] Derive compact portrait default dial sizes from available screen width; never rewrite persisted portrait/landscape layouts or older saved shared scale values
- [x] Prevent no-op editor slider release from persisting stale preview settings
- [x] Enlarge portrait Start/Select touch targets and provide a matching NES-style Menu pill; preserve existing landscape secondary controls and menu-hold behavior
- [x] Confirm before resetting the current orientation's controller layout; clarify that the editor moves Start/Select/Menu with their assigned clusters
- [x] Reserve portrait video space against the taller of the D-pad or A/B groups instead of only the left group
- [x] Add a compact-phone side gutter to generated portrait defaults, accounting for the gutter in default dial sizing; saved layouts remain authoritative
- [x] Skip zero-size viewport calculations during initial layout or rotation
- [ ] Review final portrait visual balance and custom-offset edge cases on device (testing paused)
- [ ] Physically verify multi-touch, diagonals, touch cancellation, save/resume and portrait orientation (testing paused)

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
- [ ] **Deferred to M1/M7 (not an M2 blocker):** audit shared LeanbackPreferenceFragment compatibility, inherited internal identifiers and third-party licensing without breaking saves, shortcuts or migrations
- [x] Propagate failed ROM scans instead of reporting success, preflight selected-folder read grants, preserve indexed games on failure, and show recovery actions even when earlier games remain visible (device acceptance still paused)
- [x] Improve narrow-phone and large-text layouts for game-details actions, featured cover and ROM folder recovery buttons; add explicit screen-reader labels for shelf taps and long-press actions (device acceptance paused)
- [x] Apply safe-drawing insets to main navigation, non-home top bars and the pause-menu side panel; label search actions and add Help dialog dismissal (source-only)
- [x] Final source-only accessibility cleanup: labeled settings switches with full-row targets, radio-role selection dialogs, large-font action-row minimum heights, searchable text-field semantics and screen-reader headings
- [ ] **M2 device acceptance deferred:** verify accessibility focus, large-font clipping, TalkBack, system bars and landscape cutouts (testing paused)
- [ ] **M2 device acceptance deferred:** verify all interactions on a physical device (testing paused)

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

## M2 closure — code complete, acceptance deferred (2026-11-09)

**Disposition:** The planned M2 source implementation is complete. This does **not** mean the current APK compiles, installs, is visually accepted or is verified on hardware. No local builds, deliberate CI verification, automated tests, emulator smoke tests or real-device testing were performed in these final two development batches.

The final source batch added full-row screen-reader/keyboard switch controls for standard and library-maintenance settings, radio-button semantics for choice dialogs, a named search input, library/search headings and large-text-friendly game action rows. The empty legacy shared Leanback resource file was removed; required `LeanbackPreferenceFragment` API compatibility remains intentionally intact.

**Deferred acceptance checklist (testing remains paused):**
- [ ] Confirm the development APK compiles and installs without replacing incompatible app signatures.
- [ ] Test first-run folder access, revoked/unreadable ROM trees, rescans, empty folders and retention of existing games/saves.
- [ ] Verify home shelves, conditional Favorites, game details, catalog/manual metadata, menu actions and pinned shortcuts.
- [ ] Verify TalkBack navigation, switch/radio focus order, keyboard/D-pad navigation, accessibility labels and maximum font/display scales.
- [ ] Verify edge-to-edge system bars, gesture navigation, device cutouts, portrait/landscape rotations and small screens.
- [ ] Verify game start/resume, pause menu, settings, audio, saves and gamepad/touch input through M3–M5 acceptance.

**Transferred to other milestones:** M1 owns deeper NES-only dependency/source cleanup and shared Leanback storage API migration; M7 owns license/security/release signoff. Neither is a reason to endlessly keep M2 open. Fix any future discovered regression in the module responsible instead.

**Next active work: M3 — signature portrait NES controller.**

## Latest M3 source batch — NES grip and touch-visibility layout

- Replaced modern directional arrows with subtle three-ridge grips on the NES D-pad arms and a recessed center detail. The pad remains mapped to the existing NES directional ID and diagonal logic.
- Reduced control padding **only for NES** to increase D-pad reach; face-button controls no longer consume an unnecessary inner inset. A/B pointer tracking and combination behavior are explicitly retained.
- The viewport no longer links to nonexistent virtual-pad constraints when touch controls disappear after a hardware controller takes over. A full-screen constraint set is used until touch pads return.
- The changes are source-only. No build, automated test, emulator test, APK inspection or real-device touch acceptance was performed.

## Latest M3 source batch — compact default sizing and editor protection

- Default portrait dial scale now respects the available screen width (including lateral cutout insets) using the same primary dial dimensions as the actual PadKit layout. The adjustment only applies when no stored layout exists; portrait and landscape preferences, saved left/right sizes and positions, and legacy shared-scale values are not overwritten.
- Compose remembers the touch-settings flow for the current screen dimensions to avoid unnecessarily restarting collection on unrelated UI recompositions.
- The controller editor now persists a slider value only after a real change, not on a no-op finish after a reset or layout change.
- **Source-only changes**. No build, unit tests, real-device assessment, or controller input acceptance executed during the testing pause.

## Latest M3 source batch — portrait secondary controls and safe reset

- Portrait-only Start and Select secondary PadKit targets now use a 15% radial enlargement for easier thumbs; landscape retains existing sizes and menu styling.
- A portrait-only NES-style Menu pill replaces the generic menu icon while retaining `KEYCODE_BUTTON_MODE`, its angular position and the established hold-to-open behavior.
- The controller editor explains the D-pad/Select and A/B/Start/Menu grouping and now asks for confirmation before resetting current-orientation size, position, opacity and rotation. Saved layout for the other orientation and saved games are unaffected.
- Changes are source-only; builds, device touch hit region checks and controller acceptance are still paused.

## Latest M3 source batch — portrait balance and viewport protection

- Portrait video and controller faceplate are constrained against the topmost of two independently sized control groups using a Compose top barrier. Enlarging A/B no longer leaves the game region anchored only to D-pad height.
- Phones at or under 360 dp of unobstructed width receive an 8 dp inset for each default portrait control side, and the generated dial-size calculation accounts for both insets. Saved preferences stay authoritative and landscape is untouched.
- The viewport does not receive invalid fractions while the root or video region has a zero dimension during initial layout/rotation.
- Source-only implementation. Controller alignment and video appearance still require device verification after the testing pause.

## Development rules

- Work directly on `master`; no PR workflow for day-to-day updates.
- Report **what changed, what remains, and module status** after each batch.
- Do not equate a commit or a green CI run with device testing.
- Do not delete shared emulator infrastructure without identifying its actual consumers.
- No bundled copyrighted commercial ROMs or unapproved RetroAchievements claims.
