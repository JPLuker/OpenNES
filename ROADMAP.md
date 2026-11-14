# OpenNES — Roadmap

**Platform:** native Android · **Repository:** [JPLuker/OpenNES](https://github.com/JPLuker/OpenNES) · **Foundation:** [Lemuroid](https://github.com/Swordfish90/Lemuroid) (GPL-3.0) · **Core:** FCEUmm via LibretroDroid

A dedicated open-source NES/Famicom emulator with its own interface, an NES-controller-inspired touch layout and native RetroAchievements.

## Where things stand (2026-11-13)

Two words are used precisely here. **Built** means the code is on `master` and CI passed for it. **Accepted** means it was checked on a real phone. Nothing is accepted because it compiled.

| Milestone | Built | Accepted on a phone |
| --- | --- | --- |
| M0 Branding and build | Yes | Install and upgrade keep settings and saves (0.1.6, Pixel 9 Pro) |
| M1 NES-only app | Yes, CI inspects every APK | Not applicable |
| M2 OpenNES interface | Yes | Library, search, favorites and play time passed. Covers, Fix Match, loading screen and RomM tiles were fixed after that test and need a retest |
| M3 / M4 Touch controller | Yes | Taps, diagonals, A/B with D-pad and rotation passed. The drag editor failed; the fix needs a retest |
| M5 Gameplay and saves | Yes | Two games with sound, saves, pause, relaunch and switching passed. Rewind, Zapper and cheats have never been tried on a phone |
| M6 RetroAchievements (Casual) | Yes | Sign-in and achievement lists work. A newly earned unlock saved on the server is not confirmed. Hardcore is off until RetroAchievements approves the emulator |
| M7 RomM | Browse, download and manual save backups | Not tested against a live server. Two-way save sync is not built and its 1.0 scope is undecided |
| M8 Google Play release | API 36, unsigned bundle checks, privacy policy, notices, About screen, Play listing text, all UI text translatable, dead Google-services module removed | Needs the owner's signing key and Play Console account |

**Release order:** M7 acceptance comes before the M8 public release.

### What blocks 1.0, and who can clear it

1. **One phone test** of the current build, using [docs/DEVICE_TEST.md](docs/DEVICE_TEST.md). Owner.
2. **A live RomM server session** (part of the same checklist). Owner.
3. **Decide the RomM scope for 1.0:** ship with download and manual backups only, or wait for two-way save sync. Owner decision.
4. **Play release setup:** create the upload key, add the `OPENNES_RELEASE_*` secrets, open the Play Console listing and take original screenshots. Owner. Listing text and form answers are drafted in [docs/PLAY_LISTING.md](docs/PLAY_LISTING.md). Steps are in [docs/PLAY_BUILD_VALIDATION.md](docs/PLAY_BUILD_VALIDATION.md) and [docs/RELEASE_GATE.md](docs/RELEASE_GATE.md).
5. **RetroAchievements Hardcore** needs emulator approval from RetroAchievements. It is not required for 1.0; 1.0 ships Casual only.

Open issues: [#44 haptics](https://github.com/JPLuker/OpenNES/issues/44), [#10 gameplay](https://github.com/JPLuker/OpenNES/issues/10), [#13 controller](https://github.com/JPLuker/OpenNES/issues/13), [#11 rewind](https://github.com/JPLuker/OpenNES/issues/11), [#12 Zapper](https://github.com/JPLuker/OpenNES/issues/12), [#8 RomM](https://github.com/JPLuker/OpenNES/issues/8), [#14 Play release](https://github.com/JPLuker/OpenNES/issues/14), [#4 menus](https://github.com/JPLuker/OpenNES/issues/4), [#5 metadata](https://github.com/JPLuker/OpenNES/issues/5), [#16 privacy mode](https://github.com/JPLuker/OpenNES/issues/16).

Dated progress notes that used to sit here are in [CHANGELOG.md](CHANGELOG.md) and the git history. Detailed per-module notes are in [docs/MODULE_PROGRESS.md](docs/MODULE_PROGRESS.md).

### First-release scope decided on 2026-11-12

- **Casual-only rewind:** bounded, in memory, never touches saves, refused in Hardcore. Built.
- **Touchscreen Zapper** per game (Duck Hunt). Built.
- **Controller editing:** every control movable with its touch target, per orientation. Built.
- **Google Play** distribution as an App Bundle with Play App Signing. No in-app updater. The GitHub `dev-latest` APK stays for testing only.

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

> **Reading the checklists below:** they are the original acceptance gates. A box is ticked only for work that is finished, so an unticked box often means built but not yet accepted on a phone. The table at the top of this file is the current summary.

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

## M1 — NES-only conversion (FUNCTIONALLY COMPLETE; CI VERIFIED)

**Goal:** A lean, single-system application, not a multi-emulator with other systems hidden.

**Disposition (November 12, 2026): M1's functional implementation is complete and CI-verified.** The shipped debug APK registers NES only, bundles FCEUmm as its only Libretro core, prunes metadata to NES, and passes an automated NES-only binary inspection. This satisfies the single-system *behavior and packaged-core* milestone. Inherited source assets, exclusion rules, optional product flavors and transitive dependency pruning remain release hygiene under M8; their existence does not reopen the completed NES-only conversion. Gameplay, saves and migration require the separate M5/M8 acceptance gates. The [NES-only audit](docs/NES_ONLY_AUDIT.md) records earlier build measurements of 222,012,846 and 32,367,426 bytes; the historical comparison has not been reproduced during this session.

- [ ] Inventory every system entry, core, dynamic feature module, system icon, metadata record, core downloader, BIOS flow, and system-specific setting.
- [x] Change the supported system registry to NES/Famicom only; constrain ROM scanning to relevant NES formats (e.g., `.nes` and verified supported archives).
- [x] Retain **FCEUmm** as the only emulator core selected and packaged; disable other cores in the selected build. Remove obsolete optional source/build paths under M8.
- [x] Restrict the actual APK native-core pipeline to FCEUmm and inspect packaged libraries in CI. Inherited source submodule files are left for safe M8 cleanup.
- [ ] Remove unused non-NES assets, UI navigation, controller layouts, BIOS features, and system-specific services where safe.
- [x] Trim the multi-system metadata database or replace it with an NES-only metadata strategy, preserving necessary game matching.
- [ ] Audit and prune remaining inherited transitive dependencies/resources without breaking shared NES code (**carried forward to M8 release hygiene**).
- [x] Produce a single user-facing rolling NES APK with FCEUmm bundled for offline use. Obsolete inherited build flavors remain to be simplified under M8.
- [x] Inspect the final APK to verify that only NES emulator native libraries are present (enforced by CI). **No historical baseline size comparison exists**, so the before/after measurement remains unverified and is not claimed.

**Functional exit criteria:** NES-only navigation and FCEUmm-only offline APK packaging are implemented and CI-verified. Recent physical-device play/save regression testing remains part of M5. Historical size evidence is recorded in the NES-only audit; the final production-size and inherited-source/license audit belongs to M8 rather than reopening M1.

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
- [x] **Source implementation:** independent opt-in D-pad direction hitboxes and adjustment, composite diagonals, portrait/landscape persistence and restore defaults. Real-device touch correctness remains an acceptance gate.
- [ ] Complete real-device acceptance of onboarding previews, color persistence, all D-pad directions, multitouch, landscape/portrait layouts and controller ergonomics.

## M5 — Gameplay, saves, and quality

- [x] **M5-R source implementation:** bounded RAM-only **Casual rewind** with native locking, held/pause input, memory limits and fail-closed Hardcore policy. Physical save/audio tests and full native bypass audit remain open.
- [x] **M5-Z source implementation:** per-ROM **touchscreen Zapper** and native pointer routing. Port selection, viewport mapping and Duck Hunt need device acceptance.

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

- [ ] Complete GPL/source attributions, third-party license audit, acknowledgements, and privacy documentation. *[PRIVACY.md](PRIVACY.md), [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and Settings → About are in place; a line-by-line audit of transitive dependencies is still open.*
- [x] Consolidate `master` Kotlin lint, NES-only APK build, APK inspection, SHA-256 creation, and rolling `dev-latest` publication into one GitHub Actions job.
- [ ] Add production-grade test/release checks and signed tagged releases after M7 RomM acceptance.
- [ ] Configure **OpenNES-specific** release signing securely. Never commit signing keys or secrets.
- [ ] Publish GitHub release APKs and changelog; document minimum Android version and supported formats.
- [ ] Add bug-report templates, reproducible test cases, and NES-only regression checks.
- [ ] **Google Play AAB distribution:** target API 36+, Play App Signing and private upload key, production-only version codes, ABI/native compliance, Play Console internal/closed testing and license/privacy/listing review.
- [ ] **No in-app updates:** public users receive updates via Google Play. Keep the GitHub rolling debug APK solely for testing. Include an explicit debug-to-Play save/profile export-import path.

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
