# OpenNES — no-device development plan

**Date:** 2026-11-10  
**Platform:** Native Android, NES/Famicom only  
**Distribution:** one rolling debug APK at `dev-latest/OpenNES-debug.apk`  
**Rule:** A green CI build means compilable, inspectable source — **not** that someone accepted controller touch, emulator performance, RomM networking or actual achievements on hardware.

## Milestone snapshot

| Milestone | Source status | Blocker / next actionable work |
| --- | --- | --- |
| M0 — Build foundation | CI workflow, NES-only APK inspection, rolling debug APK/checksum implemented | Verify install/upgrade preservation on device |
| M1 — NES-only | FCEUmm-only core and NES metadata trim implemented | Audit unused dependencies/resources without breaking shared services |
| M2 — Library/UI | Covers, conditional shelves, onboarding, game details, Fix Match, metadata editing implemented | Verify standalone keyboard-aware Fix Match and stale-lookup protection; test offline/cold-start covers |
| M3 — Portrait controller | NES visual shell, layout onboarding, independent theme and button geometry implemented | Device ergonomics, simultaneous touch, orientation persistence |
| M4 — Landscape controller | Separate Overlay/Framed/Handheld, dedicated full-grip PadKit controls, centered video, shared editor travel geometry implemented | Real phone camera notch, editor touch bounds, persisted offsets and fallback layout |
| M5 — Gameplay | Save/restore/lifecycle and gamepad event hardening implemented | Real audio, frame pacing, SRAM/save state, suspend/resume and reconnect tests |
| M6 — RetroAchievements | Native memory/frame diagnostics, pinned rcheevos and read-only web progress UI; offline hash regression suite | Actual rc_client authentication, verified game activation, unlocks, rich presence and notifications |
| M7 — RomM | HTTPS token storage, browse/import, integrity checks, manual save export | Real server protocol/security acceptance; any opted-in two-way sync needs versioned backups/conflict UI |
| M8 — Release | Development CI and partial signing/attribution groundwork | License/security audits, official signing, final device sign-off; M7 acceptance required |

## Build order while human testing is unavailable

### Batch A — stabilization, no feature creep

- Run lint, unit tests, APK inspection and checksum pipeline on the newest M2/M4 candidate.
- Add or maintain regression tests for screen geometry and ROM data integrity.
- Verify Fix Match query lifecycle: text never gets hidden behind IME; the user may edit during a remote request; stale callbacks cannot replace newer results.
- Keep developer- or region-qualified ROM titles searchable with safe fallback; use only verified NES entity IDs before applying a match.
- Do not silently rewrite existing descriptions or a user's ROM/game state without explicit confirmation.

### Batch B — native achievements foundation

1. Produce a documented `rc_client` lifecycle design (login/start game/stop game/cleanup) with pin/ownership of the current FCEUmm core and game generation.
2. Design a **native** authenticated HTTP transport with no logging of passwords or tokens and no credential-bearing URLs.
3. Connect verified ROM hashing and game identification to native `rc_client` events, not to the render loop or webpage progress.
4. Route actual verified achievement states into the existing in-game list. Keep offline/not-connected states honest and do not create synthetic unlock events.
5. Add safe per-session cancellation, cancellation on game restart/quit and network failure state transitions.
6. Add tests/mocks for authentic session transitions and HTTP errors, then perform a physical award acceptance test when hardware is available.

### Batch C — RomM robustness

1. Validate endpoint schema/scope and secure credential storage against currently published RomM documentation, keeping defaults read-only.
2. Add deterministic tests for malformed metadata, ZIP/folder path traversal, md5/sha256 mismatches, timeouts, and partial downloads.
3. Prototype opt-in synchronized save snapshots **only** with versioned backups and explicit conflict resolution. Do not enable uploads or overwrite remote saves by default.
4. Test a real RomM server before counting this milestone as complete.

### Batch D — maintenance and release readiness

- Inventory unused legacy console/TV/Leanback assets, JNI/ABI dependencies and source-level licenses; remove unused code only after dependency tracing.
- Review app permissions, private key storage, network redirect behavior, crash-report privacy, APK certificate migration, and reproducibility.
- Preserve the single `master` debug APK process; do not create new APK branches or manual publishing channels.

## Tests awaiting the next phone session

- **M2:** Game details → hamburger → Fix Match → type a long/short title with keyboard up; check title/year visibility, scroll, Search/Done, disabled/failed/empty results, cancellation, and that selected values persist across relaunch without modifying saves.
- **M4:** Both landscape styles, move D-pad/A/B below the phone's camera cutout, reach the bottom-most legal positions, and verify actual touch hit regions (including A+B) follow.
- **M5:** ROM launch, long-running sound/fast-forward, save RAM and states, rotation, pause and system lifecycle.
- **M6:** Full authenticated account flow and verified real NES achievement unlock — not just native frame/memory diagnostic counters or webpage history.
- **M7:** Authorized RomM browsing/import, revoked token, retry, offline pause, integrity checks and backup restoration on a real server.
- **M8:** Signed production install/upgrade, security, source attribution and public-release approval.

**Hard release gate:** M6 must not be advertised as offering unlocks before an actual verified unlock; M7 must pass the agreed real-server acceptance before M8 is published.
