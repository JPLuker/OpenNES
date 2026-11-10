# OpenNES — M0–M5 final-testing handoff

**Scope:** Native Android NES-only foundation, library/UI, portrait/landscape touch controls, audio, input, save RAM, save states, quick saves, resume and session lifecycle through M5.

**State:** Source stabilization candidate; **not yet accepted**. Passing GitHub Actions proves compilation, formatting and APK inspection, not real-game behavior. The first test run must record a specific commit and APK SHA-256, not merely the mutable `dev-latest` release URL.

**Out of scope for M5 sign-off:** RetroAchievements runtime/UI (M6), RomM server acceptance (M7), and production release signing (M8). Their incomplete source may be present in a development APK; do not report it as functional.

## Before installing

1. Open [Android CI](https://github.com/JPLuker/OpenNES/actions) for the candidate commit and confirm **build and lint both pass**. Confirm the build artifact was inspected using `scripts/inspect-apk.py --nes-only`.
2. Confirm [the rolling development release](https://github.com/JPLuker/OpenNES/releases/tag/dev-latest) identifies **the same source commit**. If it still refers to an earlier successful commit, do not test it as the new candidate.
3. Obtain `OpenNES-debug.apk` and the matching `OpenNES-debug.apk.sha256`, verify the checksum, and record the package ID `com.jpluker.opennes.debug`. Existing debug installations must have the compatible signing certificate for an upgrade.
4. Back up important save data before changing versions. **Do not clear app data, uninstall, or replace the signing identity** during upgrade testing.
5. Use locally owned/legally obtained NES test content. Include at least one battery-backed SRAM title, a title with no SRAM, and a game that exercises diagonal+A/B input. Do not attach ROMs or private RetroAchievements/RomM tokens to test reports.
6. Record device model, Android version, ABI, controller type, source commit and whether the test was an upgrade or clean install.

## Manual test matrix

Mark each row **Pass / Fail / Blocked / Not tested** and include evidence for failures. A launch is not sufficient to pass a save/resume test.

| ID | Critical scenario | Expected result |
| --- | --- | --- |
| F-01 | Fresh install, choose NES ROM directory, scan and launch | OpenNES opens, only eligible NES files appear and game starts offline |
| F-02 | Upgrade from an existing debug APK without wiping data | Game library, custom artwork, settings, favorites, layouts and existing saves survive |
| UI-01 | Open game details, Play, search, recent, conditional Favorites, hamburger actions | Navigation remains usable; no duplicate actions, blank blocking screens or incorrect bottom navigation |
| UI-02 | From an active NES game, open Pause → Achievements; inspect local fingerprint, return to pause and resume play | Screen opens without leaving the game; hash status is accurate and no fake unlock progress appears; gameplay resumes normally |
| UI-04 | Open Achievements after loading website progress, scroll to the final entry, test long titles, and use the bottom Resume game action | Full-width list remains readable, connection notices are concise, Advanced diagnostics stay accessible, and Resume is always reachable without scrolling |
| UI-03 | Repeat Pause → Achievements with an unreadable ROM and a ZIP with multiple games, then rapidly close the page | Unavailable state is shown safely, ROM identification stops on cancellation, and malformed archives do not block navigation |
| UI-05 | Open a game's hamburger menu → Fix Match on a phone; type a long title with the keyboard open, optionally set a year, press keyboard Search/Done, cancel and reopen | The focused field and cursor stay visible above the keyboard; scroll and search remain usable; cancel makes no database changes |
| UI-06 | Try Fix Match for a publisher-qualified title such as RoboCop (Data East), switch the typed query during a slow request, select an NES candidate, preview/apply it, and reopen details | Verified NES candidates appear or an honest failure is shown; stale results are ignored; only selected editorial data is changed, preserving ROM, cover, favorites and saves |
| RA-01 | In a debug APK, open Pause → Achievements → Native bridge diagnostics, start probe, resume and play, reopen to refresh, then stop | Frame count rises during actual emulation (not only drawing), mapped NES memory is nonzero and verified zero-page RAM reads rise, and stop produces no freeze or crash |
| RA-02 | With probe active, quit and restart a game, then start again and refresh | New game does not inherit a stale positive memory map; re-arming produces fresh frame counters, and stopping does not crash or hang |
| RA-03 | With probe active, rotate/suspend/resume and stop while gameplay is paused | No stale memory access, destroyed-game crash, or core-lock deadlock; only the current game reports mapped RAM |
| ROMM-01 | Browse/import a game and export a remote save backup, then retry with an interrupted connection | Fail without hanging, do not replace an existing ROM or live save, and show an accurate retryable failure |
| P-01 | Portrait: D-pad directions/diagonals, hold direction while pressing A and B, Start/Select/Menu | Inputs register correctly and release when fingers lift; no stuck or accidental presses |
| P-02 | Portrait: onboarding, theme selection and editing controls | First-run guide appears once for visible touch controls; theme persists and setup can be rerun |
| P-06 | Finish first-run setup with the fixed Save controller & continue footer; scroll through presets/colors and rotate | Confirmation remains visible and tappable, changes persist, and Adjust button positions enters the editor without accidental completion |
| P-03 | Portrait: independently adjust Start/Select/Menu and drag control clusters | Edits persist across game launch and do not make the game viewport unusable |
| P-04 | In-game Themes: switch presets, view the live controller preview, open HEX color picker, adjust and apply a custom color | Actual controller changes immediately and persists across reopening; canceled picker changes do not persist |
| P-05 | Independently resize and reposition A and B; press each at its new location, then test two-finger A+B and the combined A+B region | The real button hit targets follow the rendered circles, remain independently responsive, and preserve the A+B gesture |
| L-01 | Landscape: framed/overlay modes, compact fallback, minimal controls | Gameplay is legible; controls remain within insets/cutouts and do not overlap each other |
| L-02 | Rotate portrait ↔ landscape repeatedly during gameplay and after customization | No crashes, stuck inputs or deleted per-orientation settings |
| L-03 | Customize A/B separately in landscape, rotate to portrait and back | Each orientation retains its own A/B geometry and visible touch zones; no clipping or ghost buttons |
| L-04 | Switch Overlay → Framed → NES Handheld on a wide landscape screen, then rotate, restart, and repeat on a compact screen | A single palette-driven console surrounds unblocked 4:3 gameplay; actual pad and button input works; compact mode falls back to overlay without losing saved selection |
| L-05 | In landscape drag editor, move each control group toward bottom and edges around the camera notch; exit, relaunch, rotate, and test touches | Movement matches the real on-screen grip limit rather than an old fixed 72dp range; buttons/hitboxes remain visible and aligned; positions persist across launches |
| C-01 | Pair or connect external controller before launch and during play; unplug it | Controller buttons map correctly and touch controls recover appropriately after disconnect |
| C-02 | Rapid joystick and button input, then disconnect/reconnect; try unrelated mouse/scroll events | No stale/recycled motion readings, stuck button or crash; non-joystick generic input is not swallowed by the gamepad pipeline |
| A-01 | Play music and sound effects for ten minutes, with fast-forward toggled on/off | Audio remains audible without sustained crackles, drift or repeated stalls; game speed returns to normal |
| A-02 | Open pause menu, toggle Fast-forward without leaving the menu, then toggle Audio; resume and reopen | Both switch states are retained and applied independently; the menu does not unexpectedly close or lose either pending selection |
| S-01 | Save to each manual slot, load each, then select an empty slot | Saved progress restores; empty slot reports failure, never success |
| S-02 | Quick-load before any quick-save; quick-save then quick-load | Missing quick-save reports correctly; later quick-load restores captured progress |
| S-03 | Save game with battery-backed SRAM, quit normally, reopen | SRAM progress survives; the app must not claim exit/save success when disk write fails |
| S-04 | Enable auto-save, leave the game, reopen with restore | Snapshot restores the expected gameplay state; failures are surfaced, not silently ignored |
| S-05 | Leave game via Home, return from recent apps, lock/unlock screen, open/close pause menu | Gameplay, input and music recover with no black screen or duplicate scene |
| S-06 | Save/quit, press Back/Quit multiple times quickly | One finish sequence; no race, stuck spinner or corrupted save |
| S-07 | Switch apps while a save is pending; simulate constrained storage in a controlled test | Error is visible/logged and retry remains possible; no false success |
| T-01 | Play for a period, pause/background app, return, quit | Recent-played duration counts foreground time without adding the full background interval |
| D-01 | Optional second phone / smaller screen / different Android version | All critical controller and save behaviors repeat without layout clipping |

## Failure rules

- **Blocker (P0):** Crash on launch or normal play, data loss, corrupted save, false successful save on write failure, unusable controller, or unsupported-core APK.
- **High (P1):** Severe audio failure, broken orientation, input stuck after release, loss of layout configuration, or resume failure.
- **Medium (P2):** Styling, minor layout overlap, imperfect previews, or recoverable metadata failure.

**M5 exit conditions:** green compile and lint for the exact candidate, checked APK/checksum, F/P/L/C/A/S/T critical manual cases passing on at least one real Android phone, and no open P0/P1 issue. Any skipped test must be disclosed as **Blocked** or **Not tested**, never silently checked off. The human tester must explicitly accept remaining P2 issues.

## Reporting a test failure

Open a [GitHub issue](https://github.com/JPLuker/OpenNES/issues) and include: source commit, APK checksum, device/Android version, install/upgrade path, orientation, which matrix case failed, exact steps, expected/actual results, and screenshots or sanitized log excerpts. Never upload copyrighted ROMs, save files with personal information, credentials, or a full unredacted log.

## M5 handoff disposition

- **Ready for acceptance testing** only when the exact candidate passes GitHub Actions compilation, lint and NES-only APK inspection.
- **Accepted** only after the manual test matrix is performed and the outstanding blocker/high-priority regressions are resolved.
- **Not a public release.** RomM and native RetroAchievements remain later gates before production approval.
