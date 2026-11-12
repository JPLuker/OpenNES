# OpenNES — RetroAchievements Hardcore compliance audit

Updated: 2026-11-10. **Hardcore mode is NOT enabled or approved.** This document is a source-level implementation and acceptance checklist, not an assertion of certification.

Authoritative rules:
- [RetroAchievements Hardcore Compliance Requirements](https://docs.retroachievements.org/general/hardcore-compliance-requirements.html)
- [RetroAchievements FAQ — Hardcore](https://docs.retroachievements.org/general/faq.html)
- [RetroAchievements emulator identity and approval guidance](https://retroachievements.org/forums/topic/29231)

## CI-verified foundation

The [OpenNES rolling APK build 42902652152](https://github.com/JPLuker/OpenNES/actions/runs/42902652152) passed Kotlin formatting, JVM regressions, Android/native compilation and APK publication.

- The production `HardcoreRuntime` explicitly supplies `emulatorApproved=false` and `nativeHardcoreAvailable=false`. `HardcoreSessionPolicy` therefore refuses activation. JNI `rc_client_set_hardcore_enabled(gameClient, 0)` also explicitly enforces Casual.
- The policy requires a fresh game reset and no active cheats before *any future* approved activation. Casual-to-Hardcore mid-session is refused by the policy; an explicit casual downgrade is supported.
- When the policy is hypothetically Hardcore, manual slot loads, quick-save loads, automatic state restoration and the final `loadSaveState` unserialize path are rejected. State creation, battery saves and fast-forward remain allowed.
- Cheat enablement is rejected; the pause-menu Cheats entry and Load State action are hidden. The core option `fceumm_game_genie` is forced to `disabled` on load and on settings refresh, and hidden from core settings.
- The game activity's Android `onStop` conservatively downgrades the runtime mode to Casual. This is intentionally strict and may also downgrade when opening the in-app pause menu; it must be refined before an approved user-facing Hardcore mode.
- FCEUmm cheat resets/replacements are now one native `coreLock` operation with a checked core-generation token. The loaded ROM's first rendered frame clears lingering codes before game-owned RetroAchievements session activation.
- Existing RetroAchievements HTTP transport identifies the app uniquely as `OpenNES/0.1.0 (Android) rcheevos/12.5.0`; it does not spoof an approved emulator.

## Remaining release and compliance gates (not satisfied)

1. Confirm **a new real Casual award** is both shown in-game and persisted on the user's RetroAchievements profile. Current evidence validates account/game/achievement retrieval only.
2. Complete an audited, **native-mode-synchronized** clean-reset activation path; only enable `rc_client_set_hardcore_enabled(1)` after eligibility, clean-session validation, cheat clearing and user confirmation. Downgrade native mode and UI atomically on resume/failure.
3. Prove all restricted features (cheats, mountable Game Genie, external cheat input, save-state load, rewind, slowdown, frame advance, memory inspection, scripting and recorded-input playback) have no alternate entrypoint. **New rewind feature #11 must be Casual-only in both UI and native serialization; clear captured history before any compliant Hardcore activation.** Do not introduce unsupported "Hardcore" toggles in the meantime.
4. Implement and verify the official **offline achievement queue** and reconciliation behavior; existing disconnect handling is not an offline award queue. Validate Rich Presence, measured/trigger visuals and leaderboard submission/indicators on a real device. Investigate save-state achievement hit-count storage for Casual as recommended.
5. Resolve Android lifecycle scenarios, including entering/exiting the pause menu without unintended downgrade, background quick resume, process death, rotation, interrupted networking, ROM switches and save-load refusal.
6. Finish privacy, dependency/license, non-commercial-core and FOSS notices review. Verify public-availability eligibility, including the exact six-month rule and whether the upstream fork qualifies, rather than assuming it.
7. Request review/approval through RetroAchievements' official emulator-support process. Unknown or unapproved emulators are Casual-only regardless of a local Hardcore switch. Do not spoof another emulator's identifier.
8. Run real-game tests for a **server-recognized Hardcore award** only after approval. Do not claim official support based on CI, self-reported state or screenshots of a local mode badge.

## Explicit no-go rules

Until every relevant approval and testing gate is cleared, OpenNES must keep native Hardcore disabled, avoid issuing Hardcore award requests, and avoid displaying user-facing controls that promise competitive Hardcore credit. It may show a clearly labeled Casual status with explanatory text. External sync or achievement metadata must never be fabricated to simulate compliance.
