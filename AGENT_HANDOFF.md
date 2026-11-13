# OpenNES — Agent handoff (2026-11-13)

**Read this first.** This document records the state at handoff, not a declaration of feature completion. Follow [AGENTS.md](AGENTS.md), [ROADMAP.md](ROADMAP.md), [TESTING.md](TESTING.md), [docs/MODULE_PROGRESS.md](docs/MODULE_PROGRESS.md), [docs/RELEASE_GATE.md](docs/RELEASE_GATE.md), and open [issues](https://github.com/JPLuker/OpenNES/issues). The current user's instruction is to **implement all independently actionable work before asking for another APK/device test**, and to avoid stopping after trivial commits. Do not claim background execution or completion without evidence.

## Repository and latest known status

- Repo: https://github.com/JPLuker/OpenNES ; branch: `master`; head at handoff preparation: `174bcf81298889bdc1d655afb6674f4c01c3fe31` (this handoff commit will supersede it).
- Latest functional code commit at handoff: `174bcf81298889bdc1d655afb6674f4c01c3fe31` — ignore stale cover-image failures after a new artwork request. **No confirmed passing CI for this exact revision at handoff preparation**.
- CI: https://github.com/JPLuker/OpenNES/actions ; run `43448590761` was **in progress** when checked. Earlier run `43444896056` at `39ed8d7` passed; intermediate runs were cancelled on newer pushes. Run `43447451719` failed JVM tests due to an incorrect expected drag bound; test expectation was subsequently corrected but must be verified in the latest full build.
- Debug install: `com.jpluker.opennes.debug`, rolling `https://github.com/JPLuker/OpenNES/releases/tag/dev-latest` / `OpenNES-debug.apk` and SHA-256 sidecar. The rolling asset may lag the branch; compare exact SHA and version before testing. Do not uninstall/clear user data.
- API target and compile SDK already set to **36** in `buildSrc/src/main/java/deps.kt`. Do not repeat this task merely because an older issue description says 35.
- This handoff was compiled from GitHub files and issue descriptions; no local Android device, RomM server or production Play account was used to validate it.

## User's latest device evidence (build 0.1.6-dev.1066 / 101066, 6ef95bc4af68)

**Passed on phone:** cold-launch library responsiveness; two NES games launched with sound; controller taps/diagonals; rotation; switching/resume; search/favorites/playtime; existing saves; launch during scan. **Reported failed:** black/incorrect covers, artwork reloading when navigating, remote/local ROM distinction, remote download error/retry visibility, controller drag changes not moving real buttons or allowing sufficient movement, Fix Match (Zelda → Zelda II) not updating, absent opening loading screen, and RomM download action buried under long descriptions. Do not erase these failures because later source commits appear to address them. Retest only after consolidated code completion.

## Recent code changes to audit before adding more

- `dedd43d6`: brief startup overlay (~550 ms) and RomM action moved above description; `a6d2297e`: persistent download/status action. **Not accepted on device**; splash duration may not match user's intended full loading screen.
- `1b6463fe`, `d2a9a454`, `157d808a`, `174bcf81`: cover fallback/request-callback changes, preserving explicit custom cover and ignoring stale errors. **No verified device resolution**; check for asynchronous state races and repeated fallback.
- `39ed8d7d`, `620530f9`: controller drag overlay stops drawing a face-button GROUP handle when A or B measurements exist; added handle-selection unit tests. **Does not fix underlying PadKit anchor clipping or full-screen movement**.
- `bb4b2842`, `3d085bbc`, `473ff086`, `cfbf5b7f`, `1e181dd4`, `0820a3a2`, `1bae54ad`: safe-edge clamping and tests; one test failed, expectation corrected; latest test/build status must be checked. `b96aa29f` is documentation-only comment in `NES.kt`, not a controller fix.
- Inspect `lemuroid-touchinput/.../layouts/NES.kt`: `nesFaceAnchor` clamps x/y to [-1,1] and converts dragX/Y by /96, which may cause the reported inability to move A/B across the usable screen. Fix actual visual and touch geometry together, not by drawing-only offsets. Check `NesControllerDragOverlay.kt`, `NesDragBounds.kt`, `NesControlDragSettings.kt`, `NesDirectionalDpad.kt`, and orientation-specific settings.
- Inspect `HomeScreen.kt` `GameCover`, `NesCoverResolutionCache.kt`, `NesCoverPrefetcher.kt` for artwork persistence/flicker. A successful CI build cannot validate actual image behavior.

## Open work, ordered by risk

1. **P0 gameplay** [#10](https://github.com/JPLuker/OpenNES/issues/10): verify no regression on two ROMs, audio, save RAM, pause/relaunch, optional achievements. Prior phone tests suggest recovered, but issue acceptance is not complete.
2. **Controller** [#13](https://github.com/JPLuker/OpenNES/issues/13): fully movable visual+hit targets, independent D-pad direction calibration, portrait/landscape, camera notch, large fonts, multi-touch, Save/Restore defaults. Existing tests are not physical-device acceptance.
3. **Library/artwork/Fix Match/startup/RomM download UX:** reconcile the user's failures against source; add targeted regression tests and eliminate remaining defects. Do not require a new test APK for every incremental fix.
4. **Casual rewind** [#11](https://github.com/JPLuker/OpenNES/issues/11): roadmap/testing docs describe a source candidate with hold-to-rewind and snapshots, while issue still says not implemented. **Audit current code before reimplementing**. Confirm native core locking, RAM budget, save safety and Hardcore rejection, then device-test.
5. **Zapper** [#12](https://github.com/JPLuker/OpenNES/issues/12): issue says missing; inspect source for any partial work before coding. Requires actual touch-to-Libretro input mapping, viewport coordinates, per-ROM port and cancellation, not just UI.
6. **RetroAchievements M6:** native Casual session exists; genuine new award and server-side persistence remain unverified. Never advertise official Hardcore.
7. **RomM M7** [#8](https://github.com/JPLuker/OpenNES/issues/8): read-only import, local backups and compare-only save planning exist; full authenticated bidirectional sync and offline conflict handling are not accepted. **No automatic remote save writes or overwrites without explicit user authorization and verified server semantics.**
8. **Play M8** [#14](https://github.com/JPLuker/OpenNES/issues/14): API 36 source done; AAB, signing, Play policy, 16 KiB, provenance and release acceptance remain. Private production signing/account setup requires owner involvement. Do not put credentials in GitHub.

Additional open issues: [#16 Privacy Focused Mode](https://github.com/JPLuker/OpenNES/issues/16), and review the full issue tracker for #5 and other items not reproduced here. Avoid silently closing issues.

## Next agent's immediate procedure

1. Fetch `master` and check exact latest CI job failure/logs before further pushes. Repair actual failing code/tests. Run full tests; do not confuse cancelled CI with success.
2. Audit `ROADMAP.md` and `docs/MODULE_PROGRESS.md` for claims inconsistent with issues, especially rewind and Zapper; treat unverified statements as candidates, not release approvals.
3. Work in **substantive batches**: implement, add failing-before/fixed-after regression tests, inspect diffs, run tests, commit, wait for exact-commit CI and repair until green. Do not create comment-only commits that cancel an active build.
4. Keep source changes within existing NES visual direction; preserve saves, credentials, RetroAchievements policy and offline behavior. Avoid speculative refactors.
5. Once source work is exhausted, prepare **one consolidated device test** referencing exact SHA/APK checksum and each unresolved acceptance criterion. Explicitly list genuine blockers requiring the owner (hardware, RomM credentials, Play signing).
6. Update this file, `TESTING.md`, milestone progress, release gates and GitHub issues after evidence is available. Do not claim a completed release until hardware and Play acceptance pass.

## Known architectural constraints

- FCEUmm only; no bundled copyrighted ROMs. Source-built LibretroDroid native bridge, Android/Kotlin Compose frontend, PadKit touch controller.
- Distinct Casual/Hardcore save intent; official Hardcore not enabled. Never let rewind, cheats or load-state bypass an officially enabled Hardcore session.
- Debug signer must stay stable for over-install testing. Public Play app ID/signing differs; explicit export/import path is required.
- Existing docs include historical plans and statements that may be stale. Prefer current source + current CI + recent phone evidence, and document conflicts.
