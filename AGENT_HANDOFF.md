# OpenNES — Agent handoff (2026-11-13, evening)

**Read this first, then [AGENTS.md](AGENTS.md).** The one-page status is the table at the top of [ROADMAP.md](ROADMAP.md). The owner's phone checklist is [docs/DEVICE_TEST.md](docs/DEVICE_TEST.md).

## State

- Branch `master`, base version **0.1.8**. Check `git log -1` and the latest [Actions run](https://github.com/JPLuker/OpenNES/actions) for the exact head; this file does not record a SHA because it would be stale by the next commit.
- Every commit listed under "This session" passed the full CI job (lint, JVM tests, native bridge, APK build, NES-only inspection, publish) before the next was pushed.
- Rolling test build: `dev-latest/OpenNES-debug.apk`, app ID `com.jpluker.opennes.debug`. Install over the existing app; never ask the owner to uninstall or clear data.
- No phone, RomM server or Play account is available to an agent. CI green is not device acceptance.

## This session (2026-11-13)

Source fixes for every failure in the last phone test (0.1.6-dev.1066, Pixel 9 Pro):

1. **A/B drag editor (#13).** Dragged A/B positions were divided by 96 and clipped to the PadKit dial. `NesFaceButtonLayout` now lays dragged buttons out in real pixels; `NesFaceButtons` renders them as one shared control when close together and as separate controls when far apart. Old clipped drag values are dropped once (`faceLayoutVersion`).
2. **Fix Match** also applies the matched game's catalogue box art (`NesFixMatchCover`, exact title only).
3. **Cover flicker:** one decode size for all cover surfaces; an already-decoded cover is kept on screen while re-requested.
4. **Opening loading screen** waits for the first library snapshot (`OpenNesStartupGate`, 0.45–4 s).
5. **RomM tiles** carry a download badge; download errors and retry sit beside the button.
6. **Library responsiveness:** ROM-folder access is checked once per library change, not per game per recomposition.
7. **Release paperwork:** `PRIVACY.md`, `THIRD_PARTY_NOTICES.md`, Settings → About, `READ_EXTERNAL_STORAGE` limited to Android 12 and below.
8. **Docs:** `ROADMAP.md` reduced to one current status table; this file rewritten; `docs/DEVICE_TEST.md` added.

Audited, no change needed: Casual rewind (#11) and touchscreen Zapper (#12) are implemented in source (`GameViewModelRewind`, `BoundedRewindHistory`, `NesZapperController`, the pointer routing in `scripts/prepare-libretrodroid-bridge.py`). The issue text saying "not implemented" was stale.

## Evening (2026-11-13)

A second session merged PRs #27–#56: RomM ZIP and save-archive hardening, rewind and fast-forward buttons beside Menu (draggable, per orientation), pause-menu duplicates removed, startup screen also waits for the first RomM check (still capped at 4 s), RomM tiles start dimmed until the server answers, Leanback removed, onboarding localized.

This session then:

1. **Haptics (#44):** the rewind and fast-forward buttons, which live outside PadKit, now vibrate with PadKit's own effects under the same Touch feedback setting (`NesTouchHaptics`, `NesHapticPolicy`). The setting is re-read on every resume. Full answer on #44.
2. **Translatable text:** the last hard-coded English in Kotlin UI moved into `strings.xml`. The fast-forward button got a TalkBack label.
3. **Removed `lemuroid-app-ext-play`** (Drive sync, Play review, Play core delivery). It was already outside the build.

Deliberately left for after 1.0: the `SystemID` / `CoreID` enums still list Lemuroid's other consoles, and the DeSmuME/MelonDS save migrators and other-console shader tables remain. None of it ships a core (CI inspects the APK), but the enum names match stored database values and migration paths. Remove them only with a device available to check an upgrade from an old install.

## What only the owner can do

1. Run [docs/DEVICE_TEST.md](docs/DEVICE_TEST.md) on the phone and report pass or fail by number.
2. Run its RomM section against the home server.
3. Decide whether 1.0 ships RomM as download plus manual backups, or waits for two-way save sync.
4. Create the Play upload key, add the `OPENNES_RELEASE_*` secrets to the protected `production-signing` environment, and open the Play Console listing. Store text and form answers are drafted in [docs/PLAY_LISTING.md](docs/PLAY_LISTING.md); original screenshots and icon artwork are still needed.

## Next agent

- If device results arrive: fix failures in batches, each with a test that fails on the old code, and wait for exact-commit CI before the next push. A push cancels the run in progress.
- If no results have arrived: remaining source-only work is small. Candidates: the line-by-line transitive dependency license audit, removing inherited non-NES source and resources (M8 hygiene), moving hard-coded English strings in newer screens into `strings.xml`.
- Close an issue when its fix is accepted on a phone, with a comment giving the date and build version. Do not close #10–#14 or #8 on CI alone.

## Working without an Android SDK

The agent sandbox had no Android SDK and could not reach Maven. What worked:

- `ktlint` 1.1.1 from its GitHub release, run on changed files (matches the Gradle plugin's rules).
- `kotlinc` 2.0.21 from its GitHub release, to compile and run pure-Kotlin logic and its JUnit tests with a small JUnit shim. This is why new geometry and matching logic lives in plain objects (`NesFaceButtonLayout`, `NesFixMatchCover`) rather than inside composables.
- PadKit source read from <https://github.com/Swordfish90/PadKit> to learn how touches are routed: a control only receives touches inside its own rectangle and inscribed circle, and the first registered control whose rectangle contains the touch wins.
- GitHub Actions as the Compose compiler. Budget about eight minutes per push.

## Constraints that still hold

- FCEUmm only; no bundled ROMs. Casual RetroAchievements only; never enable or advertise Hardcore without RetroAchievements approval.
- No automatic RomM save uploads or overwrites.
- The debug signer must stay the same so test installs upgrade in place. The Play app ID and signing differ; saves move by explicit export and import.
- No restyling without a request. Portrait and landscape layouts are saved separately.
