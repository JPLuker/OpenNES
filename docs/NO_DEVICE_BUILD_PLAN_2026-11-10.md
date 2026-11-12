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
| M6 — RetroAchievements | Game-owned authenticated native `rc_client` implemented in source for NES only, with core-bound RAM, frame events, HTTPS callbacks, disconnect gating and safe idle | Exact-candidate CI, real phone award plus server persistence; FDS memory unsupported |
| M7 — RomM | HTTPS token storage, browse/import, manual remote-save archiving and SAF export, persisted verified SHA-256 manifests, recovery staging, snapshots, conflict planner | Real RomM server validation and explicit opt-in upload/conflict UI remain; no sync or replacement of emulator saves is enabled |
| M8 — Release | Development CI and partial signing/attribution groundwork | License/security audits, official signing, final device sign-off; M7 acceptance required |

## Build order while human testing is unavailable

### Batch A — stabilization, no feature creep

- Run lint, unit tests, APK inspection and checksum pipeline on the newest M2/M4 candidate.
- Add or maintain regression tests for screen geometry and ROM data integrity.
- Verify Fix Match query lifecycle: text never gets hidden behind IME; the user may edit during a remote request; stale callbacks cannot replace newer results.
- Keep developer- or region-qualified ROM titles searchable with safe fallback; use only verified NES entity IDs before applying a match.
- Do not silently rewrite existing descriptions or a user's ROM/game state without explicit confirmation.

### Latest source hardening (November 10)

- Native RetroAchievements transport now cancels the real OkHttp call when its coroutine ends, with 2 MB response limits and a pinned official HTTPS host. This is not yet called from the C++ `rc_client` runtime.
- RomM backup recovery stages a separate verified file and refuses a corrupted source; it cannot overwrite an emulated save.
- RomM local snapshotting hashes file contents twice, with strict nonempty/16 MB constraints, so a timestamp-only comparison cannot silently approve synchronization.
- Regression tests cover cancellation, backup recovery and snapshot bounds. Both integrations are still blocked on native/session and real-server acceptance.

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

## Native rc_client token-validation transport (new M6 batch)

- `Verify emulator connection` in RetroAchievements Settings invokes a **temporary real native `rc_client_begin_login_with_token`** client against the already encrypted account token. No plaintext password is persisted or re-requested.
- Native `rc_client` server callbacks queue exact URL/form requests under a session ID; Kotlin checks the official HTTPS host/path, denies password-form requests, and completes the callbacks from bounded cancellable OkHttp results.
- The native session has no game, no memory callback and no award handler. Cancellation, timeout and Settings exit dispose the client. This test **does not authorize claiming working achievements**.
- Offline tests exercise safe request forwarding, unsafe host rejection, and cancellation cleanup. Live account verification needs a future device test.
- Remaining work: bind a different game-owned native client to verified ROM generation, official game activation, exact emulated frames and real events/awards.

## Native game ID lookup (new M6 batch)

- `rcheevos` now provides the **actual** `r=gameid` / `m=<md5>` POST request and parses `GameID` natively. A Kotlin boundary pins the HTTPS endpoint and rejects requests containing any different ROM hash.
- The in-game Achievements screen can display a verified public game ID independently of the account login and award engine. A recognized game must **not** turn on achievement earning or falsely show unlocks.
- Automated tests distinguish a verified no-match from a malformed/unavailable response and reject foreign endpoints, bad hashes and interrupted requests.
- Device acceptance: launch a known NES ROM with internet, confirm a plausible official game ID; then test offline, unknown ROM, paused/reopened page, and a different game. The next M6 step remains full asynchronous `rc_client` native authentication and achievement event processing.

## Newly added acceptance scenarios

- **M6:** From Settings, sign in with valid and invalid RetroAchievements credentials; verify that a password is never remembered in the text field after submission, the confirmed account is displayed, a restart retains the encrypted token, and signing out disconnects locally. Confirm that no achievement earning is claimed before native `rc_client` activation.
- **M7:** Browse remote saves, select **Keep offline**, disconnect from RomM, reopen Settings and export the verified local archive through Android's document picker. Corrupt or missing manifests must fail closed; repeated archival must produce separate versions without modifying emulator save files.
- **M6 runtime (still not built):** Test verified in-game authentication, NES hash recognition, real-memory frame evaluation and actual award server confirmation after native `rc_client` wiring exists.
- **M7 sync (still not built):** Test account/server identity, opt-in, conflict resolution, offline retries, backup restoration and remote write permissions after full server integration exists.

## Tests awaiting the next phone session

- **M2:** Game details → hamburger → Fix Match → type a long/short title with keyboard up; check title/year visibility, scroll, Search/Done, disabled/failed/empty results, cancellation, and that selected values persist across relaunch without modifying saves.
- **M4:** Both landscape styles, move D-pad/A/B below the phone's camera cutout, reach the bottom-most legal positions, and verify actual touch hit regions (including A+B) follow.
- **M5:** ROM launch, long-running sound/fast-forward, save RAM and states, rotation, pause and system lifecycle.
- **M6:** Full authenticated account flow and verified real NES achievement unlock — not just native frame/memory diagnostic counters or webpage history.
- **M7:** Authorized RomM browsing/import, revoked token, retry, offline pause, integrity checks and backup restoration on a real server.
- **M8:** Signed production install/upgrade, security, source attribution and public-release approval.

**Hard release gate:** M6 must not be advertised as offering unlocks before an actual verified unlock; M7 must pass the agreed real-server acceptance before M8 is published.

## November 11 — Cancellable and bounded metadata HTTP

Wikidata/Wikipedia metadata lookups now cancel the underlying OkHttp call when the search coroutine is cancelled. The previous blocking request could continue after a Fix Match search closed. Redirects are disabled, Wikidata responses are bounded to 2 MiB and Wikipedia summaries to 512 KiB. Empty-read loops fail with an error. Coroutine cancellation propagates rather than becoming an inaccurate “service unavailable” result. Strict NES game-identity matching and explicit candidate selection remain unchanged. Unit tests cover response bounds and stalled/transient reads; physical device keyboard and search-cancellation behavior still require acceptance.

## November 12 — Fix Match and lookup lifecycle cancellation

The game-details UI now retains cancellable coroutine Jobs returned by the metadata ViewModel. Closing the Fix Match or online lookup sheet cancels the associated Jobs; editing the title stops the old search and selected-candidate preview; changing the release-year filter stops the prior candidate preview; and retry cancels an older lookup. The underlying Wikidata/Wikipedia OkHttp calls are cancelled by the coroutine transport. Existing request-serial guards still reject late callbacks. This wiring requires a physical-device check of system Back, keyboard dismissal and search/retry timing; it has no effect on saved game metadata unless the user explicitly selects Apply.
