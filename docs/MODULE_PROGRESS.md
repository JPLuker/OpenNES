# OpenNES — Development progress

Updated: 2026-11-09

This tracker records **implementation**, not test acceptance. At Joseph's request, interactive/device testing and deliberate build verification are paused while development continues. Pushing to `master` can still trigger GitHub Actions automatically; a source commit is not proof of a working APK.

## Milestones

| Module | Implementation status | Next acceptance/development gate |
| --- | --- | --- |
| M0 — Foundation and OpenNES branding | Implemented: dedicated package and GitHub debug APK workflow | Production signing and repeatable real-device regression still pending |
| M1 — NES-only debloat | Mostly implemented: FCEUmm-only APK, NES metadata/scanning, removed console UI | Finish safe source/dependency pruning and audit legacy paths |
| **M2 — UI completion and cleanup** | **CODE-COMPLETE (source scope; not device accepted)**: NES library, navigation, detail sheets, menus, settings, ROM recovery, compact layouts and accessibility pass implemented; obsolete TV UI removed | Device and build acceptance remain paused; regressions will be assessed when testing resumes |
| **M3 — Portrait NES controller** | **VISUAL REDESIGN IMPLEMENTED, DEVICE ACCEPTANCE OPEN**: unified non-overlapping D-pad artwork, circular A/B, themed housing, six style presets and 10 editable color roles in Settings; layout editing still accessible in-game | Recheck visual proportions, overlapping layers, touch hitboxes, editing, contrast and portraits on actual phones |
| M4 — Landscape NES controller | **VISUAL REDESIGN IMPLEMENTED, DEVICE ACCEPTANCE OPEN**: inherits new palette and single-piece controls; separate framed/overlay layouts and geometry settings retained | Verify safe layout, touch boundaries and theme visibility on compact/wide landscape screens |
| **M5 — Gameplay reliability** | **ACTIVE**: existing audio/input fixes and save infrastructure; now auditing state operation results, lifecycle ownership and errors | Improve save/resume feedback and cancellation handling; audio and device acceptance deferred |
| **M6 — RetroAchievements** | **ACTIVE — native integration boundary mapped**: local NES/FDS header-aware streaming ROM identification added; native rc_client, core memory/frame bridge, accounts and unlock events not yet implemented | Fork/vendor the LibretroDroid memory/frame API and link the official rcheevos runtime before any achievement claims |
| **M7 — Public release** | **ACTIVE — release-hardening source**: rolling debug APK workflow remains; inherited release-signing credentials removed and production signing is optional/secret-provided | Verify production key custody, reproducible builds, licensing, CI permissions, APK contents and device acceptance |
| **M8 — RomM integration** | **ACTIVE**: read-only HTTPS client, Android Keystore-protected token, NES-only browse/import UI, collision-safe ROM download into chosen local folder and automatic rescan implemented in source | Verify server schema/permissions, import pipeline and offline behavior; save sync/conflicts remain unimplemented |

## Latest controller visual overhaul — following M5 hardware feedback

- [x] Remove the inherited D-pad and face-button rectangular group backgrounds and suppress the rendered A+B composite without removing its actual input event.
- [x] Repaint the D-pad as one continuous outline and A/B as circles; draw Start/Select/Menu as clean one-piece capsules.
- [x] Use a single rounded portrait/landscape controller faceplate instead of stacked translucent square layers.
- [x] Add Settings → Touch controller appearance, live preview, six original preset palettes and ten per-element colors with persistent hex editing.
- [x] Keep existing portrait and landscape positional settings and preserve the in-game editor.
- [ ] Compile and lint the updated branch and verify the new APK with real NES gameplay and both orientations.
- [ ] Longer-term: independent per-button position/hitbox control and deeper material styling.

## First-game controller onboarding and compact editor

- [x] On the first NES game launch with visible virtual controls, offer six original color palettes and a direct transition into drag editing, or keep the current layout. Suppress the prompt after a choice or dismissal.
- [x] Add Settings → Touch controller appearance → Set up my controller again for re-running the guide on the next game launch.
- [x] Replace the overlong controller dialog with a compact bottom sheet: drag first, D-pad/A-B/More/Themes tabs, one cluster's compact sliders at a time, preserved reset confirmation and per-orientation persistence.
- [ ] Confirm onboarding, sheet sizing, drag handoff, theme persistence, external-controller takeover and game-save behavior on a real phone.
- [ ] Per-button geometry and touch-zone editing remain future enhancements; present layout edits still operate on left and right clusters.

## M3 source completion checklist

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
- [x] Keep drag editor targets within the visible editor bounds when the target can fit; freeze bounds for each gesture to avoid moving clamps
- [x] Add portrait-only charcoal/red faceplate trim and recessed legible Start/Select/Menu pills without modifying PadKit hit zones or landscape controls
- [x] Stack drag-toolbar Fine-tune/Done and editor Reset/Done buttons on narrow screens or enlarged text; respect top and horizontal system-safe areas
- [ ] **M3 acceptance deferred:** assess final portrait balance, custom-offset edge cases, cutouts and large text on hardware
- [ ] **M3 acceptance deferred:** verify multitouch, diagonals, A+B, touch cancellation, external-gamepad takeover, menu hold, rotation, saves and resume on device

## M4 active source checklist

- [x] Retain independent stored landscape control sizes, positions, opacity and rotation
- [x] Keep existing overlaid-controls and side-panel layout paths separate from portrait housing
- [x] Apply landscape side-cutout padding to the game viewport, without altering portrait layout
- [x] Add opposing charcoal/red inner trim to non-overlay left/right landscape controller panels
- [x] Add a persisted landscape-only choice between framed side panels and overlay controls, retaining legacy controller defaults until explicitly changed
- [x] Force overlay display on landscape screens narrower than 640dp while keeping the user's saved side-panel choice for wider screens
- [x] Replace the generic landscape menu button with matching NES Start/Select/Menu pills, keeping PadKit hold and mappings
- [x] Add a landscape-only minimal-overlaid-controls option, eliminating the extra A/B backplate and softening directional/button shadows without changing hit targets
- [x] Use a landscape-specific radial layout with dedicated NES Start/Select/Menu capsules and independent D-pad/A/B size and positioning
- [x] Reduce only new default landscape control scale on short screens, preserving existing saved per-orientation preferences
- [x] Select overlay mode when the combined custom control widths would leave less than the minimum reserved gameplay area
- [ ] **M4 acceptance deferred:** review extreme aspect ratios, safe touch zones, immersive mode and external gamepad transitions
- [ ] **M4 acceptance deferred:** rotate and play NES titles with touch/hardware controllers on real devices

## M5 active source checklist

- [x] Existing NES refresh-rate preference and virtual-input release handling
- [x] Correct false-positive quick-save/load feedback, report missing quick saves and failed save/load slots without claiming success
- [x] Always reset the emulator loading flag after failed or cancelled operations; report a missing save snapshot instead of getting stuck
- [x] Keep menu save/load/reset coroutines scoped to the game Activity rather than global process work
- [x] Prevent duplicate Quit/Back finish requests from racing save writes; restore retry ability after a save failure
- [x] Preserve foreground playtime across redundant resume callbacks and ignore missing menu tilt settings safely
- [x] Surface exhausted or exceptional auto-save restore failures rather than silently continuing
- [x] Preserve coroutine cancellation through game-service tasks and core-variable setup
- [ ] Review save/resume and foreground-session lifecycle ownership for interrupted games
- [ ] Guard native video/input operations against late or stale events when possible
- [ ] **M5 acceptance deferred:** on-device audio/frame pacing, save SRAM and slot versions, pause/resume, gamepad disconnect and process lifecycle

## M6 active source checklist

- [x] Verify FCEUmm is an achievement-compatible Libretro core and identify the official rc_client/native memory requirements
- [x] Implement bounded, local header-aware NES/FDS/ZIP hashing without uploading ROM bytes
- [x] Document native LibretroDroid dependency boundary, exact emulated-frame hook and secure account/session requirements
- [ ] Expose NES emulated memory and per-frame callbacks from the native frontend with Android/JNI-safe ownership
- [ ] Integrate pinned stable rcheevos/rc_client and authenticated transport
- [ ] Login/token security, game association, unlock events, achievement lists, progress, rich presence and leaderboard UI
- [ ] Implement hardcore pause/savestate/cheat constraints and network failure handling
- [ ] **M6 acceptance deferred:** verify actual RA account logins, supported NES game hashes and real unlocks on approved device/core

## M7 active source checklist

- [x] Preserve rolling development debug APK distribution without bundling commercial ROMs
- [x] Remove inherited hardcoded release keystore alias/passwords and require explicit OpenNES environment-provided secrets
- [x] Ignore new keystore material and document the unsigned-release default
- [x] Review workflow's least-privilege release permission and artifact publishing dependency on compile/lint; publish a SHA-256 checksum alongside the rolling APK
- [x] Pin GitHub Actions checkout, Java setup, artifact upload and download to verified immutable v4 commit IDs
- [ ] Finish third-party library license audit, native dependency provenance and release-binary inspection
- [ ] Inspect signing certificates, APK contents and native binaries against release requirements (testing paused)
- [ ] **M7 acceptance deferred:** verified install/upgrade path, input, audio, saves, crash reporting and RetroAchievements claims

## M8 active source checklist

- [x] Add user-configurable HTTPS RomM server and scoped read-only bearer-token authentication
- [x] Encrypt tokens with Android Keystore and store ciphertext outside Android Auto Backup
- [x] Browse RomM's NES/Famicom platforms and paginated ROMs without touching non-NES entries
- [x] Explicitly download supported NES/FDS/ZIP files into the already selected local ROM folder, refuse overwrite, verify optional MD5 and schedule rescan
- [x] Provide Settings → RomM library UI with Connect, Check, Disconnect, Browse, Next page and per-ROM Import
- [ ] Validate against a real RomM server's OpenAPI schema and acceptance permissions
- [x] Add optional RomM save listing (assets.read scope) and per-save manual SAF backup export without writing to live OpenNES saves
- [x] Reject malformed/multi-ROM ZIP imports and cap decompressed archive data; verify RomM's actual MD5 and sorted-entry ZIP save content hashes before backup export
- [ ] Add two-way save sync only with explicit conflict resolution and independently versioned backups
- [ ] **M8 acceptance deferred:** TLS/proxy, offline/revoked tokens, interrupted downloads and real ROM imports on-device

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

## Latest M3 source batch — bounded portrait controller drag editing

- Edit-mode drag previews clamp each D-pad/A/B cluster to the currently visible viewport with an 8 dp inset when that cluster can fit. The clamp uses root-relative bounds from both the target and its editing overlay.
- The editor captures starting bounds at gesture start, avoiding a feedback loop where the clamp shifts as the control is dragged. Existing saved layouts are neither modified nor forcibly brought onscreen until the user deliberately drags the controls.
- Oversized custom controls fall back to the existing normalized offset range instead of applying invalid clamps. Editor group labels may wrap to two lines rather than disappearing behind ellipsis.
- Source-only, untested while deliberate compilation and device testing remain paused.

## Latest M3 source batch — NES portrait faceplate polish

- The portrait controller housing has a restrained charcoal/red top trim and lower seam behind the actual PadKit control layers; it is decorative, does not intercept touches, and honors controller opacity.
- Portrait Start, Select and Menu pills now have a recessed outer channel, brighter lettering and larger fixed visual labels. Their existing PadKit radial hit regions and button IDs are preserved. The landscape capsule appearance remains unchanged.
- This visual pass is source-only. Device visual acceptance, font scaling and gameplay touch behavior remain unverified while testing is paused.

## Latest M3 source batch — compact controller-editor actions

- The drag-editor toolbar stacks Fine-tune and Done vertically when the available width is below 360dp or the system font scale is large. It also respects top and horizontal safe-drawing insets instead of only the display cutout.
- The slider editor stacks Reset and Done on a cramped dialog width (below 300dp) or large fonts. Neither branch changes any input bindings, settings keys or storage behavior.
- Device visual/touch checks and deliberate builds remain paused.

## M3 closure — source code complete; hardware acceptance deferred

- **Disposition:** M3's planned source-only portrait presentation and customization work is code-complete, not build- or device-accepted. A compile, install, visual review or reliable gameplay claim would be premature while testing is paused.
- The final lifecycle fix exits drag editing if physical-controller takeover hides the touchscreen pad, gates the overlay on actual visible pads and clears obsolete pad measurements while controls are absent. It leaves newly measured targets intact when pads return.
- **Device acceptance list:** first-run and restored portrait defaults on narrow/large displays; authentic D-pad visual proportions; diagonal and simultaneous A+B; separate Start/Select/Menu; long-press menu; drag previews, abort and persistence; reset and orientation isolation; video aspect/faceplate proportions; rotation/cutouts; gamepad takeover; suspend/resume; save games. Reopen M3 only for a concrete regression, not another indefinite visual audit.
- M3 source completion does **not** supersede M1/M5 dependencies or M7 release checks.

## First M4 source batch — landscape side-panel framing

- In landscape, the game viewport's existing display-cutout padding now protects the left/right edges as well as the top. Portrait keeps its original top-only behavior.
- When the controller config uses side panels instead of overlaying controls on the video, each panel gains mirrored charcoal/red NES-style trim along its **inner** edge. The decoration stays behind PadKit's input targets and follows visual opacity. Existing overlaid-controls behavior and all saved controller mappings are untouched.
- **Pending:** actual device measurements, controller ergonomics and horizontal mode acceptance. No deliberate build or tests have been performed.

## M4 source batch — selectable landscape panel/overlay modes

- Added a nullable `landscapeOverlay` field to the serialized touch settings. Existing preferences decode with the legacy controller layout choice, and new choices are persisted only within the landscape orientation's existing storage key.
- The in-game landscape editor now switches between full-width overlay controls and a gameplay viewport framed by NES-style side panels, without affecting portrait settings.
- Narrow landscape widths below 640dp render the full-width overlay to preserve a usable video area. If the user has selected side panels, the editor explains that the preference will be used on a wider screen.
- No build, tests or device validation were run.

## M4 source batch — minimal overlay and landscape control style

- Landscape now uses a unified NES Start/Select/Menu pill vocabulary rather than the inherited generic menu icon; no key IDs or 500ms menu-hold behavior changed.
- `landscapeMinimal` is a backward-compatible, landscape-only saved setting exposed in the controller editor. In overlay mode, it reduces D-pad and A/B backplates/shadows while retaining legible active controls, original input regions and existing opacity control. Side panels continue using the full controller skin.
- No deliberate compilation or device testing was performed.

## M4 source batch — height-aware landscape defaults and dynamic video protection

- Generated landscape control sizes now use available vertical space, minus safe top/bottom cutouts and a secondary-button reserve. Saved layouts remain authoritative, including legacy shared-scale preferences.
- The side-panel fallback now accounts for actual left/right user scale overrides and a minimum 240dp gameplay area, not just a hard-coded 640dp device-width threshold. Side-panel preferences are preserved for a wider orientation/device.
- The screens still require physical short/wide landscape verification once testing resumes.

## M4 closure — source implementation complete, device acceptance deferred

- Implemented both full-width overlay and NES-themed side-panel landscape modes, selectable inside the landscape controller editor; minimal overlay appearance has its own stored toggle. Portrait preferences and original input mappings are untouched.
- A unified NES D-pad, A/B, Select, Start and Menu vocabulary is used, with landscape-specific radial sizes, cutout treatment and video framing. Narrow/short screens adapt generated defaults and fall back to overlay display when necessary. Saved custom layouts remain authoritative.
- Existing external gamepad takeover continues hiding virtual controls. Device tests for real touches, physical gamepads, edge cases and visual acceptance are **not performed**. The module is **code-complete only**; no claim of reliable runtime behavior.
- M4 now closes as a source milestone. Future discovered issues belong to regression acceptance rather than indefinite cosmetic passes.

## M5 source batch — truthful save feedback and loading recovery

- Quick-save only announces success after a real snapshot is captured; quick-load only announces success after native unserialization confirms it. A missing quick save and slot save/load failures now produce specific user feedback.
- Save-slot screenshot-preview failures are nonfatal if the state itself was written, and their errors are logged. Coroutine cancellation is not swallowed by slot loading or autosave restoration.
- The ViewModel's `withLoading` uses `finally` so exceptions, early returns and cancellations restore its loading indicator. A missing exit snapshot reports a retryable save failure instead of leaving the UI blocked.
- No compile, automated or device tests performed.

## M5 source batch — lifecycle-scoped menu operations and safe exit

- Menu-driven slot loads/saves and reset now use the Activity's lifecycle scope, so stale callbacks stop when their GameActivity is destroyed. The delayed game-process termination remains separately scoped, preserving the existing process exit design.
- A single-flight guard prevents repeated Back/Quit requests from starting overlapping save snapshots and repeated finish results. A missing or failed snapshot shows an error and allows a retry; successful finish stays single-flight.
- Redundant resume callbacks no longer overwrite an ongoing playtime interval. Reset rethrows cancellation and missing menu tilt configuration no longer triggers a non-null assertion crash.
- Device suspend/resume, saves and process lifetime remain untested.

## M7 source batch — isolated release signing

- Removed upstream hardcoded release key alias/password and inherited release-signing fallback. Production credentials are passed only through `OPENNES_RELEASE_*` environment variables. All four are required to activate the optional `openNesRelease` signing configuration; otherwise the release artifact is unsigned.
- `.gitignore` now ignores newly created JKS/keystore files. Existing tracked developer/debug signing material remains unaffected.
- Updated build instructions to distinguish code-complete UI source from unverified APKs, and to document production signing and publication blockers. No build or signing test was run.

## M7 source batch — development APK checksum and CI policy

- Audited the existing Android workflow: build/lint jobs use read-only repository permission, while only the publish job requests write permission and it runs after successful build and lint.
- The workflow now produces `OpenNES-debug.apk.sha256`, verifies it before release publication and uploads both APK and checksum to the rolling development prerelease.
- The old wording `tested APK` was corrected: CI compilation and lint alone are not emulator or device acceptance. Workflow action pinning and signing/production review are still open.
- This was source review only; no workflow run was triggered manually or inspected.

## M7 source batch — immutable workflow actions

- Pinned `actions/checkout`, `actions/setup-java`, `actions/upload-artifact` and `actions/download-artifact` to the exact commit IDs resolved from their official v4 tag refs. A moving tag can no longer silently change the build/upload action code.
- Existing least-privilege workflow permissions, build-then-lint-then-publish sequence and rolling debug APK distribution are unchanged.
- This is a source-level supply-chain hardening step, not a run or verification of the modified workflow.

## M8 source batch — safe remote save backup export

- RomM's current saves API supplies a bare per-ROM `SaveSchema` list, with `GET /api/saves/{id}/content` gated by the optional `assets.read` scope. The OpenNES RomM screen exposes per-ROM Saves and a manual Export backup action.
- Save backups download to bounded app cache (16 MiB maximum), optionally check a 64-hex SHA-256 content hash, then write to a document chosen explicitly in Android's system Save As picker. They are never imported into SRAM, save states or local game files automatically.
- This is manual backup/export, **not automatic or bidirectional sync**. The user must reconcile saves manually; no server or device testing was run.

## M8 source batch — verified archives and RomM save hashes

- Added a local verifier that rejects malformed NES files, oversized/multi-ROM ZIP archives and unsafe ZIP member paths before a RomM import can create any local document. ZIP expansion is capped at 64MiB across at most 256 entries.
- Corrected RomM save backup integrity validation against upstream code: raw files use MD5, ZIP saves hash sorted `entry-name:entry-MD5` records via MD5, and future 64-hex SHA-256 hashes are checked as full-file digests. All validation happens before writing the backup document; ZIP save expansion is capped at 16MiB.
- The verifier is source-only, uncompiled and untested. Unsupported archive formats fail safely and neither live saves nor existing ROMs are overwritten.

## M5 source batch — explicit auto-save restoration failures

- On a failed auto-save restoration after the existing bounded retry window, OpenNES now displays the existing load-failed toast instead of silently proceeding. Exceptions in the restore path are also surfaced; cancellation is rethrown.
- The dedicated game service and resumed core-variable updater now propagate coroutine cancellation, rather than logging normal lifecycle teardown as an unrelated task failure.
- Source-only reliability improvements; gameplay, frame pacing, audio, saves and process lifecycle remain untested.

## Development rules

- Work directly on `master`; no PR workflow for day-to-day updates.
- Report **what changed, what remains, and module status** after each batch.
- Do not equate a commit or a green CI run with device testing.
- Do not delete shared emulator infrastructure without identifying its actual consumers.
- No bundled copyrighted commercial ROMs or unapproved RetroAchievements claims.
