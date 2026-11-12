# OpenNES — Development Roadmap

**Status (2026-11-10):** M0/M1 have a CI-verified NES-only rolling debug APK; additional debloat and release signing remain. M2/M3/M4 keyboard, metadata and controller changes are implemented in source, but latest notch/editor behavior still requires phone acceptance. M5 saves, audio and input are manual-test gates. **M6 now has a game-owned, authenticated native `rc_client` for NES ROMs**, with emulated-frame evaluation, RAM validation before activation, native achievement events, cancellable HTTPS callbacks, disconnect/reconnect gating and pause-time idle processing under the core lock. FDS native awards remain disabled pending memory mapping validation. **A real achievement award and server-side persistence have not yet been verified on a phone.** M7 RomM read-only import, manual offline save archives and explicit export are available in source; two-way sync and real-server testing remain. **M7 acceptance precedes M8 public release.** See [module progress](docs/MODULE_PROGRESS.md) and [the no-device plan](docs/NO_DEVICE_BUILD_PLAN_2026-11-10.md).

**M2/M6 no-device refinement (2026-11-10):** Source candidate includes Fix Match IME/focus handling, invalidation of stale year/title previews, bounded qualifier fallback searches, durable SAF permission for custom covers, edit/replace actions, official NES-only catalogue result filtering and deduplication, persistent successful artwork fallback selection, encoded Libretro NES gallery support, and smaller progressive achievement/leaderboard lists with collapsible emulator warnings. This source batch passed CI 42923112381; phone acceptance remains open before the milestone closes.

**Real-device M6 evidence (2026-11-10):** The user has confirmed native RetroAchievements sessions listing actual game achievements and historical unlock progress for Zelda and RoboCop. A newly triggered, server-persisted award is **not** yet confirmed. The most recent M6 source batch repairs late sign-in/reconnect/retry and separates client-generated emulator warnings from genuine achievement totals; it requires final CI plus device acceptance.

**Last verified CI baseline:** [run 42927257071](https://github.com/JPLuker/OpenNES/actions/runs/42927257071) passed Kotlin lint, JVM regression tests, native/Android compilation, NES-only APK inspection and rolling development APK publication. This includes M7 per-server journal isolation, conflict guards, read-only local request review, archive integrity auditing, stable snapshot recovery and a strict RomM 5.4 device-sync parser. CI does not verify live RomM server compatibility or phone behavior.

**Next feature tracks:** M5-C NES cheat manager is **source implemented and CI verified** (run 42886631595), with a per-ROM editor, raw and Game Genie validation, session-only opt-in toggles and native FCEUmm cheat calls. Device behavior and code persistence still need acceptance; bounded local JSON import/export is source implemented with Android document pickers and non-activating imports. Atomic native cheat replacement is now CI-verified with a core-generation lease and per-ROM reset; real-game acceptance remains. M6-H has a **CI-verified, fail-closed restrictions foundation**, but Hardcore mode is **not available** and the native RA client still operates in Casual. Emulator recognition, compliant offline unlock handling, real-device verification and a clean-reset native activation path are still required.
**Platform:** Native Android  
**Repository:** [JPLuker/OpenNES](https://github.com/JPLuker/OpenNES)  
**Foundation:** [Lemuroid](https://github.com/Swordfish90/Lemuroid) (GPL-3.0)  
**Primary emulator:** FCEUmm via LibretroDroid  
**Product:** A dedicated open-source NES/Famicom emulator with a wholly redesigned UI, premium NES-controller-inspired mobile gameplay, and native RetroAchievements.

## November 10–11 — RomM full-library and offline reliability candidate

- **Unified NES library:** all indexed local NES games and RomM-only previews share the physical shelf, with a full-screen adaptive cover grid, search, A–Z jumps, Installed/RomM filters and the regular details layout. A remote preview never enters Room as a fake playable file. Download becomes Play only once a real local ROM has been indexed.
- **Native RomM metadata:** description/summary is used where available, with a clear missing-description label. Only trusted public IGDB HTTPS cover URLs are accepted from the RomM record; Libretro NES box-art fallback remains. The selected server token is never sent to a cover host.
- **Offline metadata:** a strictly bounded metadata-only cache is kept in private no-backup storage, keyed by exact server identity plus login revision, and cleared on disconnect/reconnect. Reopening the app without RomM access can show previously fetched titles and descriptions; ROM bytes are not cached or downloaded. A visible Retry action bypasses the refresh interval.
- **Transfer workflow:** explicitly requested downloads show queued, connecting, downloading (bounded percent when known), verifying, importing and indexing states. The game details blur during work, a persistent home banner preserves status after dismiss, and the Settings browser has a cancellable loading dialog. Failures are per-ROM and actionable; a stalled WorkManager scan offers Retry library scan rather than another ROM download.
- **Correct complete pagination:** the client advances by raw server-row count, not filtered NES entries; small, filtered and total-less pages are covered by tests. Platform discovery is reused within one connection's paginated request.
- **Storage safety:** prevent case-insensitive duplicate imports, reject renamed SAF-created files, never overwrite a local ROM or save, and retain successful file imports in the indexing-recovery state if scan scheduling fails. A ROM stream retains connection/read stall limits plus a strict size bound without the JSON API's 60-second overall timeout.
- **Verification:** all work is a source candidate pending the latest CI build and an actual phone/RomM server session. Tests now cover offline cache corruption/account isolation, album-art host restrictions, paging, deduplication and index completion. Do not mark M7 complete or publish M8 stable until real-device acceptance is performed.

## Functional NES emulator comparison (November 12, 2026)

[Issue #9 comparison and implementation shortlist](docs/NES_EMULATOR_FEATURE_COMPARISON.md) evaluates NES.emu, Nostalgia.NES and FCEUmm. Rewind and opt-in Zapper touch input are candidates **after** save/session correctness, not completed or enabled features. Do not copy another emulator's design or promote Wi-Fi multiplayer into the 1.0 gate without a separate decision.

## Near-term execution while phone testing is unavailable (November 2026)

1. **M2/M4: stabilize the most recent test feedback.** Finalize the keyboard-aware Fix Match dialog and its alternate-title search, keep active search fields visible above the IME, preserve game metadata and saves on application, and test off-screen drag constraints with normalized touch coordinates. Once CI passes, freeze speculative controller design changes until physical-device feedback returns.
2. **M6: verify native achievements on hardware.** The separate game-owned `rc_client` now authenticates a saved emulator token, loads an officially recognized NES ROM, validates the core RAM map, evaluates every emulated frame and emits native unlock/network events. It pumps idle callbacks while paused with emulator memory protected by the same core lock. FDS awards fail closed. **Next critical step:** verify a real NES unlock on the device and confirm server-side persistence. Test offline, pause, reset, rotation, fast-forward, sign-out and restart before acceptance.
3. **M7: harden RomM integration.** The Settings page now supports user-requested remote save downloads into an app-private, versioned SHA-256 archive, with integrity-checked offline listings and explicit SAF export. Existing local backup, recovery, snapshot and fail-closed conflict-planning infrastructure remains non-destructive; **nothing uploads to RomM or modifies live saves automatically**. Two-way sync still requires verified RomM account/server identities, remote snapshots, conflict decisions, opt-in and live-server acceptance. A persistent **non-executing** sync-intent journal now records bounded offline requests and fails closed on corruption or changed hashes. A user can prepare a new server-bound intent from a verified archived backup plus a local document: this stores fingerprints only, not the selected document or any approval. No uploads or live-save overwrites are enabled.
4. **M1/M8: cleanup and release engineering.** Audit remaining non-NES resources/dependencies, upstream licenses and native artifacts, build reproducibility and production signing. Preserve migration paths and saved user data.
5. **When physical testing resumes:** execute the [M0–M5 acceptance matrix](docs/M5_FINAL_TESTING.md), including the newly reported keyboard/notch issues; then perform genuine RetroAchievements and RomM server acceptance. Do not mark a device gate completed because lint or compilation passed.

## Non-negotiable product direction

- **NES only.** Remove—not merely hide—unnecessary support for every other console and its cores, assets, configurations, metadata, dependencies, build variants, and navigation. Preserve shared infrastructure needed by NES.
- **Replace the entire user-facing Lemuroid UI.** Library, onboarding, settings, game details, pause menu, overlays, controller editor, notifications, and branding should all belong to OpenNES.
- **Pizza Boy-quality presentation; original assets.** A refined, premium NES-inspired controller with accurate D-pad/A/B/Start/Select placement, tactile depth, subtle NES-gray/charcoal/deep-red colors, and responsive visual feedback. Do not copy Pizza Boy artwork.
- **Portrait and landscape are independent designs**, not a stretched or rotated single skin. Store their control layouts separately.
- **Touch controls must work as well as they look.** Multiple simultaneous touches, diagonals, D-pad+A/B, haptics, accessibility, and external controllers.
- **RetroAchievements is a first-class feature.** Account, game identification, achievements and progress, rich presence/leaderboards where applicable, and tasteful in-game notifications.
- **No commercial ROMs bundled.** Users import their own compatible game files. Respect licenses and upstream attribution.
- **Small, maintainable, tested builds.** Strip unnecessary components after confirming they are not shared prerequisites.
- **One normal APK download.** Publish the newest successful, linted and inspected `master` build only as `dev-latest/OpenNES-debug.apk` plus checksum; public stable builds remain a separate M8 concern.

## Historical starting state (2026-11-06)

- OpenNES is a direct fork of Lemuroid at its `master` branch, initially unchanged.
- Android app: `lemuroid-app`, Kotlin and Jetpack Compose.
- Touch controller: `lemuroid-touchinput`; NES layout exists at `lemuroid-touchinput/src/main/java/com/swordfish/touchinput/radial/layouts/NES.kt`.
- Shared game/system configuration: `retrograde-app-shared`.
- Game metadata: `lemuroid-metadata-libretro-db`.
- Emulator cores: `lemuroid-cores` git submodule. FCEUmm is the intended sole retained system core.
- Existing Android package ID and GitHub release workflows still use Lemuroid names and release assumptions.
- Initial repository inspection showed no open GitHub issues. No APK build or real-device validation has been performed yet.

> **Progress tracking:** The checklists below preserve original acceptance gates. Unchecked items do not imply missing source implementation. See the [current implementation tracker](docs/MODULE_PROGRESS.md). The latest CI passed; final device gameplay acceptance is still open.

## M0 — Baseline, build and branding

**Goal:** Prove the fork can produce an installable APK before modifying behavior.

- [ ] Clone with all submodules; document Java/Android SDK/NDK and build prerequisites.
- [ ] Produce and install an unchanged baseline debug APK; launch an NES game on a real device.
- [ ] Record baseline APK size, bundled native libraries, startup time, and an NES gameplay smoke test.
- [ ] Change display name to **OpenNES**, use unique application ID, and establish app icon/branding placeholders.
- [ ] Replace misleading Lemuroid store, README and release links while retaining upstream notices.
- [ ] Repair/update GitHub Actions; build APK artifacts on PRs; avoid inherited publishing or release signing.
- [ ] Keep a stable working branch/release tag before invasive cleanup.

**Exit criteria:** Baseline OpenNES-branded debug APK installs alongside Lemuroid and runs an NES ROM. CI builds reliably.

## M1 — True NES-only debloat (priority: HIGH)

**Goal:** A lean, single-system application, not a multi-emulator with other systems hidden.

- [ ] Inventory every system entry, core, dynamic feature module, system icon, metadata record, core downloader, BIOS flow, and system-specific setting.
- [ ] Change the supported system registry to NES/Famicom only; constrain ROM scanning to relevant NES formats (e.g., `.nes` and verified supported archives).
- [ ] Retain **FCEUmm** as the only emulator core selected and packaged; remove/disable all other core build modules and download paths.
- [ ] Reduce the cores submodule/build pipeline to its NES dependency chain; do not assume removal of root settings alone removes core binaries.
- [ ] Remove unused non-NES assets, UI navigation, controller layouts, BIOS features, and system-specific services where safe.
- [ ] Trim the multi-system metadata database or replace it with an NES-only metadata strategy, preserving necessary game matching.
- [ ] Audit transitive dependencies and packaged APK/AAB contents. Remove unused libraries and resources without breaking shared code.
- [ ] Simplify product flavors and release pipeline to one open-source NES APK, ideally with the core bundled for offline use.
- [ ] Measure before/after APK size and inspect the final APK to verify that only NES emulator native libraries are present.

**Exit criteria:** OpenNES lists and runs NES games, contains no other console core binaries or system navigation, installs offline, and is measurably leaner than the baseline. Existing NES saves remain functional.

## M2 — Complete OpenNES UI replacement

**Goal:** No user-visible Lemuroid screens remain.

- [ ] Define original OpenNES design tokens (colors, typography, spacing, shapes, motion, icons).
- [ ] Replace onboarding and ROM folder selection.
- [ ] Replace the home/library UI: recent games, favorites, searchable collection, import feedback, empty states.
- [ ] Replace game-detail UI: artwork, metadata, Play, saves, achievements entry point.
- [ ] Replace in-game pause/settings UI with a single cohesive surface.
- [ ] Replace settings and controller configuration; keep useful features but remove redundant menus.
- [ ] Audit light/dark support, accessibility, transitions, loading states and small-screen behavior.
- [ ] Remove stale user-visible wording, Lemuroid-specific screenshots, and dead navigation.

**Exit criteria:** Every user-facing path is recognizably OpenNES and remains usable without placeholder/dead buttons.

## M3 — Signature portrait NES controller

**Goal:** A beautiful handheld-like portrait gameplay experience.

- [ ] Original NES-inspired visual skin: warm gray body, recessed charcoal D-pad, red A/B buttons, Start/Select, subtle highlights/shadows and tactile button feedback.
- [ ] Display at the top with correct aspect handling and minimal wasted area.
- [ ] Comfortable, accurately mapped controls; simultaneous input, diagonals, touch cancellation, configurable haptics.
- [ ] Proper cutout/gesture navigation handling; game always remains visible.
- [ ] Store portrait-specific button position, size, and opacity settings.
- [ ] Add a clean, discoverable menu/pause entry without obscuring primary controls.

**Exit criteria:** Comfortable full-game test in portrait, buttons visually aligned and functionally reliable.

## M4 — Dedicated landscape NES controller

**Goal:** A second premium layout engineered for horizontal phones.

- [ ] Keep large centered gameplay, with NES-inspired left D-pad surface and right A/B controls that frame rather than crowd the screen.
- [ ] Place Start/Select/menu deliberately for landscape ergonomics.
- [ ] Offer skin/full-frame and minimal/transparent-control modes.
- [ ] Store independent landscape placement, size, opacity, and scaling preferences.
- [ ] Handle wide, narrow, rounded, and cutout screens; rotation must not lose gameplay or controls.
- [x] Hide touch controls automatically when an enabled external gamepad takes over; release held virtual inputs and cancel drag editing. Verify gamepad reconnect behavior on a phone.

**Exit criteria:** Portrait/landscape transitions are safe; both have individually polished composition on several aspect ratios.

## Latest M2–M4 controller customization implementation

- [x] Six original preset palettes and a shared, live controller preview in Settings and first-game onboarding; themes persist and onboarding can be rerun.
- [x] Ten configurable color roles with persistent six-digit HEX input, plus a hue/saturation wheel and brightness/saturation sliders.
- [x] Add a clear palette-icon affordance on the tappable swatch; tapping the swatch opens the wheel, and the adjacent HEX field remains editable.
- [x] Correct vertical D-pad pressed highlights without altering already-correct gameplay input mapping.
- [x] Independently adjust Start, Select and Menu geometry, while preserving orientation-specific layouts and saved themes.
- [x] Independent A/B button size/position uses corresponding PadKit touch anchors (with A+B combination retained); layouts remain separated by orientation.
- [x] Reuse the full controller preview and color wheel directly in the in-game editor.
- [ ] Independent D-pad segment geometry/touch-hitbox controls; the D-pad remains a shared cluster.
- [ ] Complete real-device acceptance of onboarding previews, color persistence, all D-pad directions, multitouch, landscape/portrait layouts and controller ergonomics.

## M5 — Gameplay, saves, and quality

- [ ] Maintain accurate audio/video synchronization and stable frame pacing.
- [ ] Validate save RAM, save states, optional auto-save, reset, fast-forward, and sensible graphics options.
- [ ] Make state operations explicit and reversible; avoid surprise saves or lost progress.
- [ ] Provide useful controller mapping and Bluetooth/USB controller support.
- [ ] Add in-app diagnostics for crashes/loading failures, with privacy-preserving logs.
- [ ] Test battery drain, suspend/resume, calls, audio focus, background/foreground, and configuration changes.

### M5-C — NES cheats (source implemented; device acceptance open)

**Goal:** A native-feeling, optional cheat manager in the in-game pause menu, separate from normal gameplay and RetroAchievements account settings. The initial Cheat editor, per-ROM persistence, session-only toggles and LibretroDroid native calls are implemented and CI-built. Real hardware acceptance remains open.

- [x] Use source-built LibretroDroid's already exposed `setCheat`/`resetCheat` for pinned FCEUmm; validated Game Genie (6/8 characters) and raw hex formats, while rejecting unverified PAR variants.
- [x] Add **Pause → Cheats** with code entry, editable friendly name, enable/disable toggle, deletion, per-ROM saved lists and invalid/duplicate-code feedback.
- [x] Keep cheats **off by default**; saved code definitions never auto-activate. Enabled selections are in-memory per running game. Do not log codes or ROM data.
- [ ] Apply changes on the emulator/core thread with safe lifecycle teardown and no cross-ROM leakage; support turning all cheats off and verify the core actually clears them.
- [x] Add versioned local JSON import/export through Android document pickers. Validate file size, schema, names, codes, duplicates and capacity; imports never activate codes.
- [ ] Integrate with the RetroAchievements mode policy: built-in cheats, Game Genie hardware/cartridge emulation, external cheat files and memory editing must be blocked while Hardcore is active; attempting to enable them requires an explicit downgrade to Casual.
- [ ] Test code activation, save/restart, ROM switching, disabled defaults, broken codes, and cheat-free gameplay on a real phone.

**Cheat feature acceptance:** Source is CI-built; a user must still verify that a real NES cheat can be entered, saved, toggled and removed on the device without affecting another ROM. Not officially accepted yet.

**Exit criteria:** NES gameplay is stable across representative titles and Android device configurations. The [M0–M5 real-device test matrix](docs/M5_FINAL_TESTING.md) must pass; CI alone is insufficient.

### Additional M5 hardware input stabilization

- [x] Capture immutable gamepad key/motion data before asynchronous processing, avoiding recycled Android event objects and ignoring unsupported generic input.
- [ ] Validate hardware controller reconnects, held buttons, joystick motion and virtual A/B combinations on a real phone.

## November 10 feedback fixes — source submitted for verification

- **Keyboard:** Fix Match used to render deep inside game details while the IME covered its title field. It now uses a separate scrollable dialog with keyboard insets, focus visibility requests, and Search/Done IME actions.
- **Metadata:** Fix Match uses explicit selection/preview/Apply and verifies candidates are NES games. When an exact candidate search returns none, it can retry after stripping only a final publisher/region qualifier (for example, `RoboCop (Data East)` → `RoboCop`).
- **Controller:** Landscape editor drag limits and actual rendered control offsets now use the same grip geometry. Secondary buttons follow their control group and clamp independently to visible space. **Real camera-notch behavior is not yet accepted.**
- **Automated tests:** Introduced tests for manual title-query fallback and NES/FDS/ZIP RetroAchievements hashes, supplementing existing controller geometry and RomM ZIP tests. Test success depends on the exact CI candidate, not merely this checklist.

## M6 — RetroAchievements integration

**Goal:** Native, integrated achievement experience, not a web-view workaround.

- [ ] Check current RetroAchievements emulator-integration and approval requirements.
- [ ] Select compatible `rcheevos` / `rc_client` integration approach; provide memory-read bridge from FCEUmm/LibretroDroid as needed.
- [ ] Implement secure login/logout and credential/session storage; never log tokens.
- [ ] Implement official ROM hashing/game identification and supported game lookup.
- [ ] Show per-game achievement lists, locked/unlocked state, completion progress and points in a **Steam-inspired Achievements view directly inside the in-game pause menu**, without leaving gameplay.
- [ ] Add small in-game achievement popups with optional sound/haptics and accessibility support.
- [x] Display native game Rich Presence messages and official leaderboard definitions/trackers when provided by the authenticated client. Actual leaderboard scoring and hardware presentation remain acceptance gates.
- [ ] Carefully manage network outages, retries, and session state; do not falsely display unearned or unverified unlocks.
- [ ] Restrict Hardcore actions appropriately and follow official approval/compliance requirements before promising competitive Hardcore credit.

### M6-H — Hardcore RetroAchievements (CI-verified guards; NOT enabled)

**Goal:** Add a policy-enforced **Casual / Hardcore** selection, not merely a visual toggle. A fail-closed policy and entry-point guards are now in source for manual/quick/automatic state loading, cheat application, Game Genie core options and Android suspend/resume. The native `rc_client` still runs in Casual mode; both emulator recognition and native-mode readiness are explicitly false in the production build. No UI can enable Hardcore. Complete and verify a genuine Casual award and compliance work first.

- [ ] Review the current [RetroAchievements Hardcore Compliance Requirements](https://docs.retroachievements.org/general/hardcore-compliance-requirements.html) and seek the applicable emulator integration/recognition review. **Do not claim official Hardcore award eligibility until recognized/authorized.**
- [ ] Provide an explicit mode selector during game launch and in RetroAchievements settings, with visible in-game Hardcore/Casual status and a clear explanation of restrictions.
- [ ] Wire verified policy state to native `rc_client_set_hardcore_enabled` and any current integration requirements. Activation must occur only when compliant, before award evaluation.
- [x] Install policy guards at manual/quick/automatic save-state restoration and final native unserialize call sites; allow saving states. Conservative Android onStop downgrade is wired. **Device acceptance and native Hardcore synchronization remain outstanding.**
- [ ] Disable emulator cheats, memory edits, Game Genie hardware emulation, rewind, slowdown, and frame advance while Hardcore is active; audit all existing core options and alternate paths for bypasses. **Fast-forward is allowed** under current RetroAchievements rules.
- [ ] Switching **Casual → Hardcore** mid-session requires a full game reset and a clean Hardcore session. Switching **Hardcore → Casual** is allowed with an explicit confirmation; do not silently represent Casual progress as Hardcore credit.
- [ ] Preserve normal cartridge battery saves and legitimate gameplay; assess leaderboards and Rich Presence support, which must not be disabled in Hardcore when supported by the game.
- [ ] Add regression tests at each save-state, cheat, resume, core-option, and speed-control entry point, plus real-device testing of resets, background recovery, offline mode, and verified server-side Hardcore award records.
- [ ] If official emulator recognition remains outstanding, keep the feature visibly unavailable/experimental rather than spoofing another emulator identifier.

See [Hardcore compliance audit](docs/HARDCORE_COMPLIANCE_AUDIT.md) for implemented safeguards and the still-blocking server, eligibility, lifecycle and gameplay requirements.

**Hardcore acceptance:** Every prohibited operation is enforced by the emulator, the native client reports the correct mode, and RetroAchievements recognizes a genuinely earned Hardcore award. Not a substitute for the existing Casual M6 acceptance.

**Exit criteria:** Successfully identifies supported games, loads achievements, and verifies supported Casual unlocks on real hardware. Any Hardcore features accurately reflect official authorization status.

## M7 — RomM integration (required before public release)

**Goal:** Offer secure, integrated NES/Famicom access to a user-provided RomM server before the first public OpenNES release.

- [x] Add configurable HTTPS connection and protected read-only authentication.
- [x] Browse NES titles and safely import supported ROMs into the selected local folder.
- [x] Provide optional save listing and explicit manual backup export without overwriting live saves.
- [ ] Test connection, permissions, ROM imports, save exports, token revocation, and network interruptions against a real RomM server on Android.
- [x] Add verified offline save management (export, explicit deletion, comparison with a selected local document) and a non-executing, server-bound request journal with review/discard UI.
- [x] Harden offline planning to reject divergent historical baselines, stale intents, changed local files and cross-server identity mismatches. Add per-server/ROM queue isolation and read-only recheck via Android's document picker.
- [x] Audit local archive integrity without deleting damaged, orphaned or incomplete files; verify source stability when creating backups and the current manifest when recovering.
- [x] Add strict, tested **RomM 5.4** negotiation parsing and immutable dual MD5/SHA-256 file fingerprinting. Treat all upload/download/conflict/delete actions as preview-only. Older schemas are rejected, not guessed.
- [ ] Verify the actual server's version, per-user token ownership, account ID, ROM IDs, protocol shape and device permissions before any account-scoped pairing or real synchronization.
- [ ] Decide and implement the release scope for actual two-way save synchronization, including per-user/device identity, safe upload/download, transactionality, backups, conflict resolution and live-server tests.
- [ ] Finish privacy, security and failure-state reviews for the agreed RomM functionality.

**Current boundary:** The v2 offline journal is **server-bound, not account-bound**, and **cannot execute** transfers. Same-server multi-user safety, device registration, remote writes, live-save restoration and real-server conflict adjudication remain blocked. See [RomM integration](docs/ROMM_INTEGRATION.md).

**Exit criteria:** The agreed RomM feature set is functional, tested, and documented; public-release signoff cannot precede this gate.

## M8 — Release, maintenance and open source

- [ ] Complete GPL/source attributions, third-party license audit, acknowledgements, and privacy documentation.
- [x] Consolidate `master` Kotlin lint, NES-only APK build, APK inspection, SHA-256 creation, and rolling `dev-latest` publication into one GitHub Actions job.
- [ ] Add production-grade test/release checks and signed tagged releases after M7 RomM acceptance.
- [ ] Configure **OpenNES-specific** release signing securely. Never commit signing keys or secrets.
- [ ] Publish GitHub release APKs and changelog; document minimum Android version and supported formats.
- [ ] Add bug-report templates, reproducible test cases, and NES-only regression checks.
- [ ] Optional later: in-app update checks with explicit user consent/Android install flow.

**Exit criteria:** A reproducible, installable public OpenNES release with documented known limitations, only after M7 RomM acceptance.

## Implementation sequence and guardrails

1. **Build before cutting.** No untested deletion of core infrastructure.
2. **NES-only debloat before broad redesign** (M1 before M2–M4), so the UI is built on the intended product scope.
3. **Design gameplay controls early**, with an interactive prototype and real-device acceptance criteria; do not postpone controller feel until the last milestone.
4. **Start RetroAchievements feasibility investigation early**, but integrate it after the base emulator/controller reaches stability.
5. **Finish and accept M7 RomM integration before M8 public release.** Release-hardening work may run in parallel, but public distribution is gated on the agreed RomM scope.
6. Ship one coherent, reviewable workstream at a time, with APKs and clear verification.

## Deferred / out of scope for initial release

- SNES, GB/GBA, Genesis, arcade, Nintendo 64, and every other non-NES platform.
- General-purpose Libretro core browser, multiprocess console metadata, cloud ROM library.
- Built-in commercial ROM downloads or copyrighted game assets.
- Direct copying of Pizza Boy source, UI assets or controller art.
- Hardcore-compliant claim or competitive Hardcore awards without RetroAchievements approval (Hardcore is **planned**, not removed from the roadmap).
- Android TV-specific experience unless needed for a future, separately approved scope.

## Latest verified CI evidence (2026-11-09)

- [CI run 42703489576](https://github.com/JPLuker/OpenNES/actions/runs/42703489576) passed lint, compilation, NES-only APK inspection and publishing for [commit `5dd2fac`](https://github.com/JPLuker/OpenNES/commit/5dd2fac586863858b6b1d1f71fa44a387bf10007).
- One rolling [test APK](https://github.com/JPLuker/OpenNES/releases/download/dev-latest/OpenNES-debug.apk) and [SHA-256 file](https://github.com/JPLuker/OpenNES/releases/download/dev-latest/OpenNES-debug.apk.sha256) serve normal testing. The current build contains the D-pad visual fix, onboarding preview, color wheel and palette icon.
- Real-device feedback has been received, but the full M5 acceptance matrix is **not complete**; there is no verified production release.

## Historical baseline evidence (2026-11-06)

- The first GitHub Actions run successfully compiled `:lemuroid-app:assembleFreeBundleDebug` and uploaded `OpenNES-debug-apk`; its overall status was **failure due to Kotlin lint**, not failure to assemble the APK.
- Initial lint failures were resolved in later development; the consolidated lint/build/publish workflow passed on 2026-11-09.
- No mobile gameplay or NES-only APK acceptance test has yet been completed.
- APK inspection and debloat work remain subject to the M0 baseline verification gate.
