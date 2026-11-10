# OpenNES — Development progress

Updated: 2026-11-10

This tracker distinguishes **source implementation**, **verified CI**, and **real-device acceptance**. The last completed successful APK baseline before the game-runtime batch was [commit `fa1286fe`](https://github.com/JPLuker/OpenNES/commit/fa1286fe26b40e1236e6b6cb5ac92747e871fa27), [CI run 42870882730](https://github.com/JPLuker/OpenNES/actions/runs/42870882730). Subsequent native session, paused-idle and UI commits still require their own CI verdict. A genuine NES award reflected on RetroAchievements has not yet been confirmed, and [M0–M5 phone acceptance](M5_FINAL_TESTING.md) remains open. M7 real-server save sync is also outstanding.

## Milestones

**Mandatory sequence:** M7 RomM acceptance comes before M8 public release. Release hardening can proceed in parallel, but the public release is blocked until the agreed RomM scope passes acceptance.

| Module | Implementation status | Next acceptance/development gate |
| --- | --- | --- |
| M0 — Foundation and OpenNES branding | Implemented: dedicated package and one CI-verified rolling debug APK/checksum workflow | Real-device regression and production signing still pending |
| M1 — NES-only debloat | Mostly implemented: FCEUmm-only APK, NES metadata/scanning, removed console UI | Finish safe source/dependency pruning and audit legacy paths |
| **M2 — UI completion and cleanup** | **SOURCE IMPLEMENTED, DEVICE ACCEPTANCE OPEN**: library, menus, settings, ROM recovery, theme/onboarding previews and color-picker UI | CI passed; verify previews, colors and navigation on phones |
| **M3 — Portrait NES controller** | **SOURCE IMPLEMENTED, DEVICE ACCEPTANCE OPEN**: live theme previews/HEX wheel, independent A/B and Start/Select/Menu geometry; first-game onboarding has a fully expanded sheet with a fixed Save controller & continue action and optional drag-editor handoff | Verify first-run confirmation, A/B hit targets, orientation persistence and multitouch on a phone |
| M4 — Landscape NES controller | **THREE SOURCE MODES, DEVICE ACCEPTANCE OPEN**: Overlay, Framed and new NES Handheld shell around the measured game viewport, sharing the controller palette with saved landscape geometry | Verify 4:3 gameplay is unobstructed, portrait/landscape rotation, control touch targets, handheld bezel appearance and compact-device fallback |
| **M5 — Gameplay reliability** | **FINAL-TEST CANDIDATE**: save failures, cancellation and quit races improved; hardware key/motion events now use immutable snapshots before async processing; latest candidate passed CI | Execute the expanded M5 device matrix for saves, audio, A/B hitboxes, controller reconnects, rotation and resume |
| **M6 — RetroAchievements** | **AUTHENTICATED GAME SESSION SOURCE IMPLEMENTED, DEVICE ACCEPTANCE OPEN**: official game identity, saved token login, native `rc_client`, core-generation-bound NES RAM, emulated frames, awards/events, safe paused idle and connectivity gating; FDS awards disabled | Exact-candidate CI plus a genuine phone unlock reflected on the RetroAchievements website; stress lifecycle/transport failures |
| **M7 — RomM integration** | **ACTIVE**: read-only HTTPS client, Android Keystore-protected token, NES-only browse/import UI, collision-safe ROM download into chosen local folder and automatic rescan implemented in source | Required before public release: verify server schema/permissions, import pipeline and offline behavior; save sync/conflicts remain unimplemented |
| **M8 — Public release** | **PRE-RELEASE HARDENING**: one rolling APK/checksum CI job is verified; production signing remains separately configured | After M7 RomM and M6 feature completion: sign, audit licenses/security, test real devices and tag stable release |

## November 10 — game-owned authenticated native rc_client

- **M6 native implementation:** Separate game-scoped client authenticates the previously saved emulator token, verifies an NES ROM hash and game load, maps RAM under the matching emulation core generation, processes actual emulated frames and forwards native events without inventing unlocks in Kotlin.
- **Lifecycle and connectivity:** Readable NES RAM is mandatory before showing Active. On disconnect, the UI hides live progress until native reconnect. `rc_client_idle` holds emulator `coreLock` before `clientMutex` to protect memory while paused, and does not run game conditions before the first valid map. FDS awards are disabled rather than using the NES map.
- **Acceptance:** CI needs to pass for the final commit; hardware must demonstrate a real server-persisted award before M6 can be considered complete.

## November 10 — Native token verification through real rc_client transport

- **M6 source implementation:** A short-lived, isolated native `rc_client` runs `rc_client_begin_login_with_token` against the user's already encrypted emulator token. Native callbacks copy bounded official requests into an ephemeral, session-ID-tagged queue. Kotlin validates the official HTTPS endpoint, drives the existing cancellable transport, and returns bounded responses to the exact pending native callback.
- **Lifecycle and safety:** A serialized single-session driver closes every native client, rejects stale/duplicate callbacks, and invalidates queued requests on stop, timeout and cancellation. It has **no game loaded, no RAM reads, no award events, and no save mutations**.
- **Settings UI:** The account screen now includes **Verify emulator connection**. A verified token is reported separately from actual in-game achievement earning.
- **Regression tests:** Added fake-native session tests for legitimate official requests, unsafe-host rejection, password-form rejection and cancellation cleanup. Device-level token verification and native runtime integration remain unaccepted.
- **Next remaining work:** A separate, game-owned `rc_client` lifetime bound to LibretroDroid core generation, official game load, emulated frames, actual achievement unlock events and server award delivery.

## November 10 — Official native game-ID recognition

- **M6 implementation:** Pinned `rcheevos` 12.5 C API now generates an official URL-encoded POST request to resolve a locally verified NES/FDS ROM fingerprint and parses the server reply on the native side. The Kotlin bridge enforces the official HTTPS host, exact endpoint, matching hash, bounded response, and cancellation. Distinct outcomes are verified game ID, verified no-match, and transport/response unavailable.
- **Player UI:** The in-game Achievements page shows the official recognized RetroAchievements game ID (or no-match/unavailable) independently of the native session status. It never treats recognized IDs as active accounts, achievements or awards.
- **Tests:** Added JVM tests for official POST semantics, hash mismatch, external/insecure hosts, server error versus no match, invalid local hashes, and cancellation. A successful native compilation is required in addition to JVM tests.
- **Remaining critical dependency:** Wiring actual `rc_client` asynchronous server calls, account session activation, game-loaded achievement definitions and frame/event processing. **No authentic achievement has been awarded** through OpenNES.

## November 10 — Emulator account login and persistent offline RomM archive

- **M6 source implementation:** `Settings → RetroAchievements` now provides a real official `login2` account sign-in. The password is submitted only over pinned official HTTPS; the response must confirm `Success`, `User`, and a valid token before it is saved in the separate encrypted, no-backup native account store. The Settings UI supports local sign-out and clearly labels earning as unavailable until the native emulator session exists. This is **not** a verified real-device login or an achievement-award implementation.
- **M6 tests:** Added offline tests for URL-encoded password fields, safe rejection of invalid credentials, invalid server tokens, and disallowed account values.
- **M7 source implementation:** Remote saves can now be manually kept in an app-private offline archive. Existing bounded downloads and remote checksum checks are reused. Each save snapshot has a fresh filename and a persisted SHA-256 manifest. Only valid copies appear in the archive after a process restart; the user can verify them and export a selected copy via Android's document picker even when disconnected from RomM. No live save is overwritten.
- **M7 tests:** Archive inventory survives store reinitialization and rejects corrupted files/manifests. Remote server compatibility and physical storage/document-picker behavior still need device and server acceptance.
- **Still blocked:** Real native `rc_client` game login/activation/event queue and authentic unlocks; RomM opt-in upload/conflict UI plus real-server integration. Neither is completed by this batch.

## November 10 — M6 cancellable transport / M7 safe recovery and snapshots

- **M6 source-only:** The official-host-only HTTPS transport now uses cancellable OkHttp requests. Cancelling a game/session coroutine cancels the associated socket; responses remain size-bounded and URL/secret contents are never logged. A cancellation regression test was added. **This transport is not connected to native `rc_client` server callbacks**; there is no functional in-app native login or earned awards yet.
- **M7 source-only:** Added a recovery helper that copies a previously verified, versioned local backup into a **new verified recovery file**; it does not replace active saves, upload anything to RomM, or auto-restore. Added tests for corruption, immutability and repeat recovery.
- **M7 source-only:** Added a file-based SHA-256 snapshotter that fingerprints a save twice and refuses empty/oversized/changing files. These snapshots are the inputs expected by the existing fail-closed sync planner, but no upload/download executor is enabled.
- **Verification discipline:** Kotlin tests and debug APK CI are not a substitute for actual Android device and real RomM/RetroAchievements account testing. This batch is not a finished M6 or M7 feature.

## November 10 M2/M4 fixes and M6 regression work — pending exact-candidate CI

- **Fix Match IME regression:** user's phone showed the keyboard covering the search field in the original game-details bottom sheet. Fix Match was moved to an independent, scrollable, keyboard-aware dialog; focused title/year fields request visibility, and Search/Done IME actions are wired. Source implemented, **physical accessibility not yet verified**.
- **Fix Match stale searches:** text remains editable while a lookup runs; changing text invalidates pending results so late responses cannot replace a newer query.
- **Fix Match recall:** verified online NES candidates can be queried by a shorter title when a parenthetical publisher or bracketed region qualifier produces no exact candidate. Added pure-JVM tests for common qualifiers. This is a controlled *search fallback*, not a claim that a game was matched correctly.
- **Landscape editor:** side controls now use measured per-grip movement limits in both renderer/editor; Start/Select/Menu follow their group while remaining clamped inside the grip. Source built in previous CI, but notch and touch-zone acceptance are still pending.
- **M6 NES ROM fingerprint tests:** new offline JUnit coverage exercises iNES/FDS header exclusions, headerless content, ZIP equivalence, multi-ROM ambiguity, empty and unsupported inputs. These tests do not implement native `rc_client` login or earn achievements.
- **Status discipline:** No physical-device acceptance is possible during this development interval. New feature claims remain **source-only** until the exact newest CI run is green and, separately, the hardware scenarios pass.

## Latest controller visual overhaul — following M5 hardware feedback

- [x] Remove the inherited D-pad and face-button rectangular group backgrounds and suppress the rendered A+B composite without removing its actual input event.
- [x] Repaint the D-pad as one continuous outline and A/B as circles; draw Start/Select/Menu as clean one-piece capsules.
- [x] Use a single rounded portrait/landscape controller faceplate instead of stacked translucent square layers.
- [x] Add Settings → Touch controller appearance, live preview, six original preset palettes and ten per-element colors with persistent hex editing.
- [x] Keep existing portrait and landscape positional settings and preserve the in-game editor.
- [x] Compile, lint, inspect and publish the combined controller/color-picker APK from `master` (CI run 42703489576).
- [ ] Validate the APK's gameplay, input and both orientations on a real phone.
- [x] Correct inverted visual Up/Down D-pad pressed highlights without changing gameplay input.
- [x] Reuse the Settings NES controller preview in onboarding, with live theme selection.
- [x] Add native HSV wheel, brightness and saturation sliders, manual HEX editing and color swatch palette icons in Settings/onboarding.
- [x] Add in-game live controller preview, full 10-color HEX wheel and selectable preset themes without navigating to Settings.
- [x] Implement independent A/B size and position using real PadKit input anchors, retaining the legacy A+B combined input regions.
- [ ] Longer-term: independent D-pad segment position/hitbox control and deeper material styling.

## Single test APK distribution

- [x] Use one `master` job for lint → NES-only APK build → APK inspection/checksum → rolling release publication.
- [x] One permanent [test APK download](https://github.com/JPLuker/OpenNES/releases/download/dev-latest/OpenNES-debug.apk) with [matching checksum](https://github.com/JPLuker/OpenNES/releases/download/dev-latest/OpenNES-debug.apk.sha256); no separate APK artifact channel.
- [x] Verified the consolidated workflow with [successful run 42703489576](https://github.com/JPLuker/OpenNES/actions/runs/42703489576).
- [ ] Production signing, licenses and stable release remain M8 tasks after RomM acceptance.

## Latest pause and controller UI refinements

- [x] Keep Save controller & continue visible below scrollable onboarding settings; fully expand the sheet and provide an explicit Adjust button positions option.
- [x] Change fast-forward from a menu-closing action to an independent persistent switch, matching the working Audio toggle.
- [x] Full-width in-game Achievements page with pinned Resume game, concise status, advanced diagnostics hidden by default, and long achievement titles allowed to wrap.
- [x] Offer Overlay, Framed and NES Handheld modes in landscape. Handheld draws an integrated single-piece theme housing around the actual measured game viewport, without moving touch hit targets.
- [ ] Accept those visual changes on a real Android device; screenshots and controller ergonomics can require follow-up adjustments.

## First-game controller onboarding and compact editor

- [x] On the first NES game launch with visible virtual controls, offer six original color palettes and a direct transition into drag editing, or keep the current layout. Suppress the prompt after a choice or dismissal.
- [x] Add Settings → Touch controller appearance → Set up my controller again for re-running the guide on the next game launch.
- [x] Replace the overlong controller dialog with a compact bottom sheet: drag first, D-pad/A-B/More/Themes tabs, one cluster's compact sliders at a time, preserved reset confirmation and per-orientation persistence.
- [ ] Confirm onboarding, sheet sizing, drag handoff, theme persistence, external-controller takeover and game-save behavior on a real phone.
- [x] Allow independent Start, Select and Menu position and size edits under More, saved per orientation; existing layout data remains backward compatible.
- [x] Each A/B button can be individually sized and repositioned in the in-game editor; its touch region follows the visible anchored button.
- [ ] Individual D-pad segment hitbox editing remains future work; the D-pad group still moves as one cluster.

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

## M5 final-testing freeze candidate

- [x] Propagate save RAM and state-write failures rather than suppressing unsuccessful disk writes.
- [x] Reject empty serialized state data and preserve coroutine cancellation through retry helpers.
- [x] Log absent background capture instead of silently returning.
- [x] Confirm **build, Kotlin lint and NES-only APK inspection** for CI commit `5dd2fac` (not the same as device acceptance).
- [ ] Execute and accept the manual [M0–M5 final-testing matrix](M5_FINAL_TESTING.md) on a real phone.
- [ ] Resolve P0/P1 issues identified by acceptance before calling M5 complete.

## M5 active source checklist

- [x] Snapshot physical controller KeyEvent/MotionEvent values before asynchronous flow emission; ignore missing devices and pass unrelated generic pointer/scroll input to Android.
- [ ] Confirm gamepad disconnect/reconnect, fast simultaneous A/B and controller axis neutrality on real hardware.
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

## M6 source-native bridge

- [x] Pin upstream LibretroDroid v0.14.0 with its recursive source dependencies; avoid mixing its AAR with the native fork.
- [x] Implement an idempotent, fail-closed native patch preparation script exposing guarded memory copies, emulated frame count and an internal frame observer slot.
- [ ] Confirm source-built NDK APK succeeds on all required ABIs and memory/frame bridge does not regress NES playback.
- [x] Pin official rcheevos v12.5.0 MIT sources and link the C client, API and rc_libretro memory modules into source-built LibretroDroid.
- [ ] Verify JNI preflight and source-linked rcheevos across Android ABIs; genuine game activation and verified per-frame awards remain separate work.
- [ ] Connect rc_client to FCEUmm memory/frame callbacks and authenticated network transport without relying on render events.

## M6 active source checklist

- [x] Verify FCEUmm is an achievement-compatible Libretro core and identify the official rc_client/native memory requirements
- [x] Implement bounded, local header-aware NES/FDS/ZIP hashing without uploading ROM bytes; cap aggregate ZIP decompression (including ignored entries) and guard provider zero-read loops
- [x] Document native LibretroDroid dependency boundary, exact emulated-frame hook and secure account/session requirements
- [ ] Expose NES emulated memory and per-frame callbacks from the native frontend with Android/JNI-safe ownership
- [ ] Integrate pinned stable rcheevos/rc_client and authenticated transport
- [x] Add optional encrypted web API credentials, an explicit user-initiated read-only website progress lookup using exact cached NES hashes, and a separate in-game web-progress section. This does not permit new unlocks or replace native authentication.
- [ ] Verify real website API responses and account behavior on a physical device (including rate limits and cache).
- [ ] Login/token security, game association, unlock events, achievement lists, progress, rich presence and leaderboard UI
- [x] Add a Steam-inspired **Achievements** entry directly to the in-game pause menu, linking to an OpenNES-native current-game screen with honest runtime status and local ROM identification.
- [x] Implement source-level verified-achievement list rendering, All/Unlocked/Locked filters and progress UI, with strict local ROM hash association and honest disconnected/no-match states. This is dormant until native data exists.
- [ ] Once native rc_client integration exists, actually supply authenticated achievement descriptions, locked/unlocked state, completion progress and points. Never fabricate unlocks or points.
- [ ] Implement hardcore pause/savestate/cheat constraints and network failure handling
- [ ] **M6 acceptance deferred:** verify actual RA account logins, supported NES game hashes and real unlocks on approved device/core

## M7 active source checklist

- [x] Add user-configurable HTTPS RomM server and scoped read-only bearer-token authentication
- [x] Encrypt tokens with Android Keystore and store ciphertext outside Android Auto Backup
- [x] Browse RomM's NES/Famicom platforms and paginated ROMs without touching non-NES entries
- [x] Explicitly download supported NES/FDS/ZIP files into the already selected local ROM folder, refuse overwrite, verify optional MD5 and schedule rescan
- [x] Provide Settings → RomM library UI with Connect, Check, Disconnect, Browse, Next page and per-ROM Import
- [ ] Validate against a real RomM server's OpenAPI schema and acceptance permissions
- [x] Add optional RomM save listing (assets.read scope) and per-save manual SAF backup export without writing to live OpenNES saves
- [x] Prevent zero-byte HTTP/SAF stream reads from looping during RomM browsing, imports and save-backup exports; preserve existing size, cancellation and hash safeguards
- [x] Reject malformed/multi-ROM ZIP imports and cap decompressed archive data; verify RomM's actual MD5 and sorted-entry ZIP save content hashes before backup export
- [ ] Add two-way save sync only with explicit conflict resolution and independently versioned backups
- [ ] **M8 acceptance deferred:** TLS/proxy, offline/revoked tokens, interrupted downloads and real ROM imports on-device

## M8 active source checklist

- [x] Preserve rolling development debug APK distribution without bundling commercial ROMs
- [x] Remove inherited hardcoded release keystore alias/passwords and require explicit OpenNES environment-provided secrets
- [x] Ignore new keystore material and document the unsigned-release default
- [x] Consolidate least-privilege lint/build/checksum/publish into one job and remove the duplicate APK artifact delivery channel
- [x] Pin GitHub Actions checkout, Java setup, artifact upload and download to verified immutable v4 commit IDs
- [ ] Finish third-party library license audit, native dependency provenance and release-binary inspection
- [ ] Inspect signing certificates, APK contents and native binaries against release requirements (testing paused)
- [ ] **M7 acceptance deferred:** verified install/upgrade path, input, audio, saves, crash reporting and RetroAchievements claims

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

## M8 source batch — isolated release signing

- Removed upstream hardcoded release key alias/password and inherited release-signing fallback. Production credentials are passed only through `OPENNES_RELEASE_*` environment variables. All four are required to activate the optional `openNesRelease` signing configuration; otherwise the release artifact is unsigned.
- `.gitignore` now ignores newly created JKS/keystore files. Existing tracked developer/debug signing material remains unaffected.
- Updated build instructions to distinguish code-complete UI source from unverified APKs, and to document production signing and publication blockers. No build or signing test was run.

## M8 source batch — development APK checksum and CI policy

- Audited the existing Android workflow: build/lint jobs use read-only repository permission, while only the publish job requests write permission and it runs after successful build and lint.
- The workflow now produces `OpenNES-debug.apk.sha256`, verifies it before release publication and uploads both APK and checksum to the rolling development prerelease.
- The old wording `tested APK` was corrected: CI compilation and lint alone are not emulator or device acceptance. Workflow action pinning and signing/production review are still open.
- This was source review only; no workflow run was triggered manually or inspected.

## M8 source batch — immutable workflow actions

- Pinned `actions/checkout`, `actions/setup-java`, `actions/upload-artifact` and `actions/download-artifact` to the exact commit IDs resolved from their official v4 tag refs. A moving tag can no longer silently change the build/upload action code.
- Existing least-privilege workflow permissions, build-then-lint-then-publish sequence and rolling debug APK distribution are unchanged.
- This is a source-level supply-chain hardening step, not a run or verification of the modified workflow.

## M7 source batch — safe remote save backup export

- RomM's current saves API supplies a bare per-ROM `SaveSchema` list, with `GET /api/saves/{id}/content` gated by the optional `assets.read` scope. The OpenNES RomM screen exposes per-ROM Saves and a manual Export backup action.
- Save backups download to bounded app cache (16 MiB maximum), optionally check a 64-hex SHA-256 content hash, then write to a document chosen explicitly in Android's system Save As picker. They are never imported into SRAM, save states or local game files automatically.
- This is manual backup/export, **not automatic or bidirectional sync**. The user must reconcile saves manually; no server or device testing was run.

## M7 source batch — verified archives and RomM save hashes

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

## November 10 no-device M6/M7 implementation batch

**Source staged for exact-commit CI, hardware and server acceptance still open.**

- **M6:** generation-scoped RetroAchievements native session gate with strict ROM/game identity and confirmed unlock rules; separate AES-GCM native login token storage; redirect-disabled and byte-bounded official-host HTTPS transport for later rc_client callbacks. These pieces are currently **not wired into JNI authentication or awards**. New JVM tests cover stale events, network loss, login-token validation, POST transport and response bounds.
- **M7:** non-mutating save synchronization planner with opt-in, authenticated RomM ID binding, immutable SHA-256 snapshots, previous-sync baselines, explicit direction/approval, verified versioned backups and conflict/offline refusal. The app still performs only the previously existing read-only RomM browsing/import and manual backup export. **No remote write or automatic restore is enabled**.
- **Next:** validate this combined source batch with lint, unit tests, APK inspection and publication. Build the native callback dispatcher and RomM transactional executor behind disabled switches, then schedule actual hardware/server acceptance when testing resumes.

- **Additional M7 safety source:** `RommVersionedSaveBackupStore` now creates distinct, SHA-256-verified, bounded private local backups without altering the original save; unit tests cover repeated versions, corruption, empty and oversized files, and backup-of-backup rejection. This remains separate from any upload/restore executor.
