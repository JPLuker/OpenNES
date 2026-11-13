# OpenNES testing and acceptance

> **Handoff update — 2026-11-13:** See [AGENT_HANDOFF.md](AGENT_HANDOFF.md) for the latest source head, CI caveats, user-reported failures, priorities and acceptance requirements. Older milestone claims below are historical and may conflict with newer code or device reports. Do not treat source implementation as accepted.\n\n
The current rolling APK is a **development preview**, not a release candidate. A passed workflow proves Kotlin source formatting, JVM regressions, Android compilation, native bridge exports, NES-only packaging, version identity and SHA-256 publication. It does **not** prove gameplay stability, safe upgrades, actual RetroAchievements awards or compatibility with the user's live RomM server.

## November 13 — 0.1.6 on-device results and 0.1.7 source fixes

Device: Pixel 9 Pro; baseline debug 0.1.6-dev.1066, source `6ef95bc4af68`.
**Passed:** app launch and upgrade without losing settings/saves, Zelda and another NES ROM with audio/video, pause/quit/relaunch, game switching, orientation, multitouch A/B+D-pad, tilt option, favorites, recent games, playtime, and concurrent library scanning.
**Failed:** drag editor moved a label but not the visible graphic and had limited travel; some RomM artwork appeared black or flickered; remote download timeout had poor retry/error presentation; Fix Match choice (Zelda → Zelda II) did not visibly rename the selected game.
**Not accepted:** physical controller repositioning with real hitboxes, cover flicker regression, metadata selection, RomM reachability, actual save/rewind/RetroAchievements/Zapper acceptance, device stability. RomM server testing deferred while away from home.

The 0.1.7 patch is a **source candidate**, not proof of a working phone build. Install over the existing debug APK without uninstalling or clearing saves. Check that the D-pad and A/B group graphics **and touch input** move together, that known covers persist/fallback and stop flickering, that offline remote games are dimmed without affecting local ROMs, that a failed download offers an explicit retry, and that Fix Match visibly changes the library title without changing the ROM launched.

## Automated checks

Run locally with the Android SDK, JDK 17 and recursive submodules:

```sh
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
./gradlew ktlintCheck
./gradlew :lemuroid-touchinput:testDebugUnitTest :lemuroid-app:testFreeBundleDebugUnitTest
./gradlew :lemuroid-app:assembleFreeBundleDebug
python3 scripts/inspect-apk.py --nes-only --native-bridge PATH_TO_APK
```

CI performs the same relevant checks on every `master` push and verifies the actual debug package/version before publishing. An unsuccessful run must leave the previous published APK intact. The actual artifact is at the [rolling development release](https://github.com/JPLuker/OpenNES/releases/tag/dev-latest).

## November 12 — Source milestone and packaging regression batch

**M0–M4 source status:** branded NES-only APK, OpenNES interface, portrait controller, and landscape Overlay/Framed/Handheld source paths are present in the debug build. M1 is functionally complete. M0/M2/M3/M4 still need the device matrix; **do not close the P0 game-launch issue without a real ROM launch**.

**M5 manual save / rewind race:** Startup autosave restoration now takes the session/rewind locks too, and clears any already sampled rewind timeline before and after loading. Verify that a resumed game cannot rewind to the earlier pre-autosave state.  Save Slot, Quick Save, Quick Load, Load Slot, reset, and final Quit must not overlap an active held rewind state restore. Tests cover stale rewind candidates, empty/oversized state buffers, and repeated one-step rewinds. Device acceptance: enter gameplay, hold Rewind, attempt a save/load through the pause menu, release, quit, relaunch and compare SRAM/manual/autosave results to a known prior save. Also test failed saves and interrupted Quit; the latest save failure must remain retryable.

**M8 package and build variants:** the app has only the `freeBundle` supported variant; the APK and AAB validators reject recognized bare game ROM extensions, hidden Libretro cores, unsafe ZIP member paths, and unwanted ABI combinations. The new Python tests cover negative fixtures. This is **not** a complete copyright audit: ROM data inside less obvious archives, third-party artwork, license notices, production signing and user data migration still require separate checks. The unsigned Play AAB workflow is a validation artifact only.

## November 12 — RomM device registration and Play signing candidate

**RomM device identity:** With a RomM 5.4 server and a token carrying `me.read` and `devices.write`, explicitly tap **Register device** in Settings. Verify the account shown matches the token, exactly one device appears under RomM Devices, and checking again reuses the same server device ID. Rotate the token, switch users and switch servers: no previous account's registered device ID may appear under a new login. No hardware hostname, MAC or IP address should be sent; the locally generated alias is anonymous. A 401/403/404, canceled request or logout while registration is in flight must not pin a device. No save upload/download/delete should occur. Both the UI and server must be inspected before marking this working.

**RomM negotiation input:** Pure tests validate a bounded MD5-based request and SHA-256 local integrity. A live sync negotiation HTTP call and any operation executor remain **disabled**; do not interpret registration as a live save-sync acceptance.

**Signed Play workflow:** Once the app owner supplies a protected private key and pinned fingerprint, run only the manual signed-AAB validation workflow after environment approval. Verify the output has production package ID, separately monotonic Play `versionCode`, real signing, only FCEUmm, no bundled game content, and passes 16 KiB native checks. Actual Google Play installation and backup/import from `.debug` remain untested.

## November 12 — Manual debug-to-Play NES save migration (source candidate)

In OpenNES Debug Settings → Transfer NES saves, export the archive to a user-selected ZIP document **after closing the running game**. Validate the export contains the expected SRAM and FCEUmm state entries and a hash manifest, but no ROMs, Hardcore saves, account credentials or identifiers. Keep the original debug app intact. In the separately signed Play build, import through the same Settings section, review the count, and confirm. Existing production filenames must remain byte-for-byte unchanged; new files must match the source SHA-256 manifest. Reimport the same archive to verify every existing destination file is skipped.

Test cancellation, malicious or corrupt ZIP, missing file, unavailable storage, app backgrounding, reboot and error recovery; refuse export/import while the emulator process lock is held. Verify newly imported SRAM and states load correctly for the same matching legally obtained ROMs after the Play app has indexed them. Source tests cannot substitute for actual migration across different Android package IDs. Do not uninstall the original debug app until all saves are verified in the production app.

## November 13 — RomM 5.4 compare-only acceptance

Connect to a known RomM 5.4 server with a dedicated account and token holding `me.read`, `roms.read`, `assets.read`, `devices.read` and `devices.write`. Register the device explicitly in Settings. Browse one NES title, open its saved games and choose **Compare local save (no transfers)**. Select a real `.srm` from that exact title. Verify the server creates and completes one sync session, displays the proposed action list, and never uploads, downloads, deletes or overwrites any save bytes. Test each action category including a server-side deletion suggestion and real conflict.

Confirm that selecting a non-SRAM file, a stale/missing file, a SAF provider without a valid modification timestamp, an unregistered device, a revoked token, a different RomM account, a server change, cancellation or malformed response fails without modifying content. The server's session log may record comparison actions as failed/unexecuted, which is expected. **Do not call this two-way save synchronization.** It is a comparison workflow only.

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

**Play packaging:** Separate unsigned AAB CI has already passed source/ZIP and ABI inspection, and the strict `--require-16k` ELF PT_LOAD alignment gate passed [CI 43257644960](https://github.com/JPLuker/OpenNES/actions/runs/43257644960). This does not replace installation of signed Play-generated splits on actual Android 16 hardware, and does not establish user-data migration from debug to production.

## November 12 — Rewind performance preference and Zapper touch cancellation

Advanced Settings now includes a **Casual rewind** switch (default on). When off, the game session should not collect volatile rewind snapshots or offer the Rewind pause action. Re-enable, resume the game and play six seconds: the action should become available again. Test this across background/resume, app process recreation and a game change. The newer source also releases native touchscreen pointer state on Android ACTION_CANCEL and ACTION_OUTSIDE; test a cancelled shot or edge gesture with Duck Hunt to confirm no stuck trigger. These additions are not accepted on hardware.

## November 12 — Hold-to-rewind and individual D-pad hitboxes (new source candidate)

**Default controller regression:** On a previously installed debug app, launch Zelda without editing the D-pad; verify the classic four-way PadKit cross, pressed-state highlights, diagonals, and simultaneous movement plus A/B remain unchanged. Open the controller drag editor. The real D-pad region should expose UP/DOWN/LEFT/RIGHT hitbox handles in addition to the D-PAD cluster handle. Move one direction within the visible cross: only that input target should change; other three and the opposite orientation must stay unchanged. Repeated edge drags must be bounded to a direction's arm, not allowed to overlap other controls. Release and reopen the editor to verify the adjustment persists. Restore orientation defaults and verify the original cross returns without changing colors or the other orientation. Repeat in framed and handheld landscape.

**Hold-to-rewind:** In Casual with Advanced Settings → Casual rewind enabled, play for a few seconds. The separate HOLD TO REWIND control appears when at least two snapshots exist; holding should step steadily backward through prior states, and lifting or cancelling the gesture stops further restores. Try a short tap and screen-reader activation (one step). Test rotation, backgrounding, editor entry, reset, manual load, and Quit while holding. Confirm that no rewind snapshots are written into manual saves, cartridge SRAM, or RomM data, and inspect audio/video behavior after releasing. Disabling Casual rewind must hide the hold surface and stop capture. Hardcore must never permit the action if officially enabled in the future. Only CI checks can run without a phone; none of these are device-accepted.
