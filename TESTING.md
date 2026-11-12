# OpenNES testing and acceptance

The current rolling APK is a **development preview**, not a release candidate. A passed workflow proves Kotlin source formatting, JVM regressions, Android compilation, native bridge exports, NES-only packaging, version identity and SHA-256 publication. It does **not** prove gameplay stability, safe upgrades, actual RetroAchievements awards or compatibility with the user's live RomM server.

## Automated checks

Run locally with the Android SDK, JDK 17 and recursive submodules:

```sh
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
./gradlew ktlintCheck
./gradlew :lemuroid-app:testFreeBundleDebugUnitTest
./gradlew :lemuroid-app:assembleFreeBundleDebug
python3 scripts/inspect-apk.py --nes-only --native-bridge PATH_TO_APK
```

CI performs the same relevant checks on every `master` push and verifies the actual debug package/version before publishing. An unsuccessful run must leave the previous published APK intact. The actual artifact is at the [rolling development release](https://github.com/JPLuker/OpenNES/releases/tag/dev-latest).

## Latest device-test checklist (current functionality, not a claim of completion)

Only the next applicable items should be requested in a test round; never send the whole list as a playtest request.

- [ ] Install the latest CI development APK **over** the previous signed development APK, without uninstalling; verify installed version increased and local saves remain.
- [ ] Open a game, leave it running, select a second ROM, and use **Close & launch**; check that the first game's SRAM/autosave survives.
- [ ] Open the pause menu in portrait and landscape; check that every action, including Quit, is reachable and text is readable without unintended clipping.
- [ ] Edit portrait and landscape controller positions across display cutouts, then restart and verify independent persistence.
- [ ] In **Fix Match**, start an online search, edit the query, close with system Back, and retry; verify the last request does not overwrite the new result.
- [ ] Sign in to RetroAchievements, play a recognized NES game and earn a **new** achievement; check the in-game banner and the official server record. Previous achievements appearing in a list are insufficient proof.
- [ ] Disconnect the network while a game is running; make sure local play and saves still work, and RetroAchievements errors do not show false awards.
- [ ] Connect to the user's actual RomM server, browse/import an NES ROM, download a manual backup, export and restore it; verify that different accounts cannot access each other's saves.
- [ ] Check that an unusable or oversized encrypted account file asks for reconnection without altering saved games.
- [ ] Validate fast-forward, audio focus, sleep/background-resume and external gamepad transitions on real hardware.
- [ ] Confirm in-app version, package ID and build revision are available in diagnostics before stable release.

## Unverified until tested on hardware or an authenticated server

- Real Nintendo/Famicom mapper compatibility and frame/audio pacing.
- The exact responsiveness and positioning of touch controls across fonts, orientations and cutouts.
- Android process death, activity recreation and save restoration under memory pressure.
- Real RetroAchievements unlock persistence and all reconnect paths; Hardcore is disabled pending approval.
- RomM server schema, account ownership and remote write permissions. Uploads and automatic two-way sync remain disabled.
- Production signing and update/rollback against production builds; the current APK is a debug test build.

Record a failed scenario with the APK's source commit, Android version, steps, expected behavior, actual result and a sanitized screenshot/log. Keep the associated issue open until the actual failure is reproduced and its fix verified.

## November 12 — Universal NES game launch failure (P0)

**Reported on device:** Multiple ROMs trigger the same generic failure screen on build 853. Gameplay is **not accepted**. The manifest declared the specific `FOREGROUND_SERVICE_DATA_SYNC` permission but omitted Android's base `FOREGROUND_SERVICE` permission, despite starting a foreground game service. Both are now explicitly declared, and a CI APK identity gate checks the merged manifest for both. This is a concrete defect and a possible launch failure, not confirmation of the sole root cause.

The game-load error result now carries one constrained non-sensitive reason: CORE, ROM, SAVE, GRAPHICS, ACCESS, IO, INVALID_INPUT or UNEXPECTED. The user can return to the library. Raw exception text, file paths and credentials are not passed through the Android result. Two ROMs must successfully launch on the user's device, and saved data must survive, before marking the regression fixed. If another failure occurs, the visible error code plus a locally redacted Android log are needed to isolate the cause.

## November 12 — ROM folder reconnection, background scanning and Tilt Sensor

Reported by device test: game details and box art remain in the library after Android's Documents folder selection is lost, causing a ROM load error. OpenNES now checks for a missing selected SAF folder or revoked persisted read grant *before* attempting to start an indexed content-URI ROM, and offers a deliberate Choose ROM folder action. The ROM error page also links directly to the Android folder picker. The Home library shows a repair notice even when old game records are still indexed. No data files or saves are deleted. This check intentionally doesn't block private file:// ROMs imported from RomM.

A separate launch gate now blocks on active **save synchronization only**, rather than WorkManager's library-index and core-update jobs. Indexed games can be played while scans continue; requesting a new scan replaces queued stale scans instead of appending more. Validate against a phone while a scan is in progress and while switching games, including old saves and recently moved content URIs.

The Tilt Sensor action uses the same NES palette, card height, spacing, and outlined style as the other pause-menu actions; its selection dialog also uses the same palette. This is only a layout change, and needs visual acceptance for touch and large fonts.

## November 12 — D-pad tilt switch (awaiting device acceptance)

Replaced the Tilt Sensor Disabled/DPAD selection dialog with one in-place switch, **Dpad tilt control**. The switch is available only where D-pad tilt is supported. Its selection is retained alongside audio and fast-forward until the pause menu exits, including state restoration after an Android activity recreation. Added unit coverage for supported mapping and on/off conversion. Test on device: enable, exit menu and tilt to move; reopen and verify ON; disable, exit and confirm tilt no longer moves the D-pad; verify pause-menu navigation and rotation do not discard the selection. Hardware acceptance pending.

## November 12 — Settings cleanup and Play/API 36 source preparation

Removed the redundant Getting started panel from root Settings. The first-run wizard remains available on initial setup; controller onboarding is separately re-requestable from Controller Appearance. Added a dedicated Hardcore information page without an enable switch. Hardware review: verify all Settings and navigation back buttons in portrait/landscape, large fonts and dark theme; confirm no misleading Hardcore activation is possible.

Android compile/target SDK is now 36, with corresponding Gradle/Android plugin upgrades. The CI Python manifest test rejects a debug APK below API 36. A new isolated `play-aab-validation.yml` action builds an **unsigned** `freeBundleRelease` App Bundle and checks its ZIP, NES-only core and bridge ABI structure. This does not verify Play signing, 16 KiB ELF behavior, production package installation or data migration; those remain blocked in `docs/RELEASE_GATE.md`. Verify clean debug APK upgrade preserves user data and gameplay on Android 16 before merging public release behavior.

## November 12 — Early Zapper and rewind source candidate (CI 43255947765)

The [debug build](https://github.com/JPLuker/OpenNES/actions/runs/43255947765) passed formatting, Python/JVM checks, native compilation, APK inspection and rolling debug publication with the initial per-game Zapper and Casual rewind implementations. Source/CI success does **not** close issues #11 or #12.

**Zapper on-device acceptance:** With a verified Duck Hunt NES ROM and another light-gun title, select *Zapper touchscreen (next launch)* in the game's context menu. Launch and test pointer accuracy in portrait/landscape at the entire rendered game viewport, not letterboxed areas or virtual controls. Lift a finger, cancel with a system gesture, background/resume, rotate, and switch ROMs: the trigger must release and unrelated NES games must retain normal joypad controls. Test selection persistence across app restart, and fallback when a core doesn't advertise a Zapper on port 2. Port-1 special variants remain unimplemented.

**Rewind on-device acceptance:** In Casual, play a game for at least six seconds and confirm a Rewind item appears in the pause menu. Use it after visible progression; it should restore approximately five seconds without overwriting any manual save slot or battery SRAM. Confirm normal sound and frame pacing afterward. Test no history on fresh load, graceful handling of paused/short games, resets, quick loads, ROM switching, app backgrounding and low memory. Explicitly test Quit after rewind and reload SRAM/autosave to ensure user-intended state is preserved. Current UI is a single pause-menu action; the planned hold-to-rewind gameplay control is not yet implemented. Hardcore remains unavailable; no official hardcore compliance claim.

**Play packaging:** Separate unsigned AAB CI has already passed source/ZIP and ABI inspection, but a subsequent strict `--require-16k` ELF PT_LOAD alignment gate was added and needs a fresh green run. This does not replace installation of signed Play-generated splits on actual Android 16 hardware, and does not establish user-data migration from debug to production.

## November 12 — Rewind performance preference and Zapper touch cancellation

Advanced Settings now includes a **Casual rewind** switch (default on). When off, the game session should not collect volatile rewind snapshots or offer the Rewind pause action. Re-enable, resume the game and play six seconds: the action should become available again. Test this across background/resume, app process recreation and a game change. The newer source also releases native touchscreen pointer state on Android ACTION_CANCEL and ACTION_OUTSIDE; test a cancelled shot or edge gesture with Duck Hunt to confirm no stuck trigger. These additions are not accepted on hardware.
