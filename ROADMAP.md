# OpenNES — Development Roadmap

**Status (2026-11-09):** M0 and the single rolling NES-only APK pipeline are CI-verified; M1 NES-only cleanup is mostly complete; M2 UI, M3 portrait and M4 landscape controller source implementations are substantially complete but still require on-device acceptance. The onboarding preview, six themes, per-part HSV wheel and HEX picker, swatch palette icons, and Up/Down pressed-effect correction are implemented. **M5 is the current final-testing gate** for saves, audio, touch, rotation and resume. M6 native RetroAchievements is not yet functional; **M7 RomM integration must pass acceptance before M8 public release**. The [current implementation tracker](docs/MODULE_PROGRESS.md) distinguishes source work, successful CI and outstanding device tests.
**Platform:** Native Android  
**Repository:** [JPLuker/OpenNES](https://github.com/JPLuker/OpenNES)  
**Foundation:** [Lemuroid](https://github.com/Swordfish90/Lemuroid) (GPL-3.0)  
**Primary emulator:** FCEUmm via LibretroDroid  
**Product:** A dedicated open-source NES/Famicom emulator with a wholly redesigned UI, premium NES-controller-inspired mobile gameplay, and native RetroAchievements.

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
- [ ] Hide touch controls automatically or by preference for external gamepads.

**Exit criteria:** Portrait/landscape transitions are safe; both have individually polished composition on several aspect ratios.

## Latest M2–M4 controller customization implementation

- [x] Six original preset palettes and a shared, live controller preview in Settings and first-game onboarding; themes persist and onboarding can be rerun.
- [x] Ten configurable color roles with persistent six-digit HEX input, plus a hue/saturation wheel and brightness/saturation sliders.
- [x] Add a clear palette-icon affordance on the tappable swatch; tapping the swatch opens the wheel, and the adjacent HEX field remains editable.
- [x] Correct vertical D-pad pressed highlights without altering already-correct gameplay input mapping.
- [x] Independently adjust Start, Select and Menu geometry, while preserving orientation-specific layouts and saved themes.
- [ ] Individual A/B button and D-pad segment geometry/touch-hitbox controls; these still use shared clusters.
- [ ] Complete real-device acceptance of onboarding previews, color persistence, all D-pad directions, multitouch, landscape/portrait layouts and controller ergonomics.

## M5 — Gameplay, saves, and quality

- [ ] Maintain accurate audio/video synchronization and stable frame pacing.
- [ ] Validate save RAM, save states, optional auto-save, reset, fast-forward, and sensible graphics options.
- [ ] Make state operations explicit and reversible; avoid surprise saves or lost progress.
- [ ] Provide useful controller mapping and Bluetooth/USB controller support.
- [ ] Add in-app diagnostics for crashes/loading failures, with privacy-preserving logs.
- [ ] Test battery drain, suspend/resume, calls, audio focus, background/foreground, and configuration changes.

**Exit criteria:** NES gameplay is stable across representative titles and Android device configurations. The [M0–M5 real-device test matrix](docs/M5_FINAL_TESTING.md) must pass; CI alone is insufficient.

## M6 — RetroAchievements integration

**Goal:** Native, integrated achievement experience, not a web-view workaround.

- [ ] Check current RetroAchievements emulator-integration and approval requirements.
- [ ] Select compatible `rcheevos` / `rc_client` integration approach; provide memory-read bridge from FCEUmm/LibretroDroid as needed.
- [ ] Implement secure login/logout and credential/session storage; never log tokens.
- [ ] Implement official ROM hashing/game identification and supported game lookup.
- [ ] Show per-game achievement lists, locked/unlocked state, completion progress and points in a **Steam-inspired Achievements view directly inside the in-game pause menu**, without leaving gameplay.
- [ ] Add small in-game achievement popups with optional sound/haptics and accessibility support.
- [ ] Implement game rich presence and leaderboards where supported.
- [ ] Carefully manage network outages, retries, and session state; do not falsely display unearned or unverified unlocks.
- [ ] Restrict Hardcore actions appropriately and follow official approval/compliance requirements before promising competitive Hardcore credit.

**Exit criteria:** Successfully identifies supported games, loads achievements, and verifies supported Casual unlocks on real hardware. Any Hardcore features accurately reflect official authorization status.

## M7 — RomM integration (required before public release)

**Goal:** Offer secure, integrated NES/Famicom access to a user-provided RomM server before the first public OpenNES release.

- [x] Add configurable HTTPS connection and protected read-only authentication.
- [x] Browse NES titles and safely import supported ROMs into the selected local folder.
- [x] Provide optional save listing and explicit manual backup export without overwriting live saves.
- [ ] Test connection, permissions, ROM imports, save exports, token revocation, and network interruptions against a real RomM server on Android.
- [ ] Decide and implement the release scope for safe save synchronization, including versioned backups and conflict handling if two-way sync is included.
- [ ] Finish privacy, security and failure-state reviews for the agreed RomM functionality.

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
- Hardcore-compliant claim or competitive Hardcore awards without RetroAchievements approval.
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
