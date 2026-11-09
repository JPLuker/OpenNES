# OpenNES — Development Roadmap

**Status (2026-11-09):** M0 foundation implemented, M1 NES debloat mostly implemented, **M2 UI source implementation code-complete (device acceptance deferred)**, and **M3 portrait NES controller source code-complete (hardware acceptance deferred)**. **M4 landscape NES controller source implementation is code-complete (device acceptance deferred)**; **M5 gameplay reliability is active**. M6 RetroAchievements native integration is in progress, beginning with ROM identification and native bridge analysis; M8 RomM remains planned. Device testing and deliberate build verification are paused at the user's request. See [per-module progress](docs/MODULE_PROGRESS.md) for the current checklist.  
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

## Historical starting state (2026-11-06)

- OpenNES is a direct fork of Lemuroid at its `master` branch, initially unchanged.
- Android app: `lemuroid-app`, Kotlin and Jetpack Compose.
- Touch controller: `lemuroid-touchinput`; NES layout exists at `lemuroid-touchinput/src/main/java/com/swordfish/touchinput/radial/layouts/NES.kt`.
- Shared game/system configuration: `retrograde-app-shared`.
- Game metadata: `lemuroid-metadata-libretro-db`.
- Emulator cores: `lemuroid-cores` git submodule. FCEUmm is the intended sole retained system core.
- Existing Android package ID and GitHub release workflows still use Lemuroid names and release assumptions.
- Initial repository inspection showed no open GitHub issues. No APK build or real-device validation has been performed yet.

> **Progress tracking:** The checklists below are the original acceptance gates. Unchecked items must not be treated as proof that a feature has never been implemented. See the [current implementation tracker](docs/MODULE_PROGRESS.md). No new verification is being claimed while testing is paused.

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

## M5 — Gameplay, saves, and quality

- [ ] Maintain accurate audio/video synchronization and stable frame pacing.
- [ ] Validate save RAM, save states, optional auto-save, reset, fast-forward, and sensible graphics options.
- [ ] Make state operations explicit and reversible; avoid surprise saves or lost progress.
- [ ] Provide useful controller mapping and Bluetooth/USB controller support.
- [ ] Add in-app diagnostics for crashes/loading failures, with privacy-preserving logs.
- [ ] Test battery drain, suspend/resume, calls, audio focus, background/foreground, and configuration changes.

**Exit criteria:** NES gameplay is stable across representative titles and Android device configurations.

## M6 — RetroAchievements integration

**Goal:** Native, integrated achievement experience, not a web-view workaround.

- [ ] Check current RetroAchievements emulator-integration and approval requirements.
- [ ] Select compatible `rcheevos` / `rc_client` integration approach; provide memory-read bridge from FCEUmm/LibretroDroid as needed.
- [ ] Implement secure login/logout and credential/session storage; never log tokens.
- [ ] Implement official ROM hashing/game identification and supported game lookup.
- [ ] Surface per-game achievement lists, locked/unlocked progress and earned points.
- [ ] Add small in-game achievement popups with optional sound/haptics and accessibility support.
- [ ] Implement game rich presence and leaderboards where supported.
- [ ] Carefully manage network outages, retries, and session state; do not falsely display unearned or unverified unlocks.
- [ ] Restrict Hardcore actions appropriately and follow official approval/compliance requirements before promising competitive Hardcore credit.

**Exit criteria:** Successfully identifies supported games, loads achievements, and verifies supported Casual unlocks on real hardware. Any Hardcore features accurately reflect official authorization status.

## M7 — Release, maintenance and open source

- [ ] Complete GPL/source attributions, third-party license audit, acknowledgements, and privacy documentation.
- [ ] Create deterministic GitHub Actions CI for lint, tests, debug artifacts, and tagged releases.
- [ ] Configure **OpenNES-specific** release signing securely. Never commit signing keys or secrets.
- [ ] Publish GitHub release APKs and changelog; document minimum Android version and supported formats.
- [ ] Add bug-report templates, reproducible test cases, and NES-only regression checks.
- [ ] Optional later: in-app update checks with explicit user consent/Android install flow.

**Exit criteria:** A reproducible, installable public OpenNES release with documented known limitations.

## Implementation sequence and guardrails

1. **Build before cutting.** No untested deletion of core infrastructure.
2. **NES-only debloat before broad redesign** (M1 before M2–M4), so the UI is built on the intended product scope.
3. **Design gameplay controls early**, with an interactive prototype and real-device acceptance criteria; do not postpone controller feel until the last milestone.
4. **Start RetroAchievements feasibility investigation early**, but integrate it after the base emulator/controller reaches stability.
5. Ship one coherent, reviewable workstream at a time, with APKs and clear verification.

## Deferred / out of scope for initial release

- SNES, GB/GBA, Genesis, arcade, Nintendo 64, and every other non-NES platform.
- General-purpose Libretro core browser, multiprocess console metadata, cloud ROM library.
- Built-in commercial ROM downloads or copyrighted game assets.
- Direct copying of Pizza Boy source, UI assets or controller art.
- Hardcore-compliant claim or competitive Hardcore awards without RetroAchievements approval.
- Android TV-specific experience unless needed for a future, separately approved scope.

## Current baseline evidence (2026-11-06)

- The first GitHub Actions run successfully compiled `:lemuroid-app:assembleFreeBundleDebug` and uploaded `OpenNES-debug-apk`; its overall status was **failure due to Kotlin lint**, not failure to assemble the APK.
- Lint violations from inherited Lemuroid files are being corrected; separate APK and lint jobs now make failures unambiguous.
- No mobile gameplay or NES-only APK acceptance test has yet been completed.
- APK inspection and debloat work remain subject to the M0 baseline verification gate.
