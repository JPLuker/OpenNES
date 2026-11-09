# OpenNES — RetroAchievements native integration boundary

**Status (2026-11-09): M6 user-facing menu shell implemented, native runtime not implemented.** A local NES/FDS ROM identification helper and a dedicated in-game Achievements page are committed. The page computes and displays a local fingerprint and clearly reports that there are no verified unlocks or account connection. The app cannot earn or report achievements yet.

## Grounded architecture

- Official emulator integration uses [rcheevos/rc_client](https://github.com/RetroAchievements/rcheevos) (C runtime) rather than treating the RetroAchievements public web API as an achievement engine.
- The supported FCEUmm Libretro core exposes memory via `retro_get_memory_data` / `retro_get_memory_size`; these symbols exist internally in [LibretroDroid's Core class](https://github.com/Swordfish90/LibretroDroid/blob/master/libretrodroid/src/main/cpp/core.h) but are **not exposed** in the Java `GLRetroView` API in OpenNES's current dependency.
- `rc_client_do_frame` must be driven **once per emulated frame**, including skipped/fast-forwarded rendering frames. `GLRetroEvents.FrameRendered` is a UI event, **not** a reliable replacement for a native per-frame hook.
- The native runtime needs an authenticated network callback, memory-read callback, a game/session lifecycle, achievements/leaderboards events, rc_client idle when paused, and safe per-frame threading. Do not run memory reads on an arbitrary Android background thread.
- Official NES identification excludes the 16-byte `NES\\x1A` header; FDS excludes its 16-byte `FDS\\x1A` header. The app-side `NesRetroAchievementsHash` helper computes this locally, supports single-ROM ZIP files and refuses ambiguous archives and oversized inputs. **It is not an awarder.**

## Verified UI boundary

- A dedicated Achievements entry opens from the current game's pause menu without exiting gameplay, with a current-game header and a scrollable status page.
- An Android IO coroutine computes the local ROM fingerprint on demand; the page distinguishes checking, identified and unavailable outcomes and allows the user to select/copy the hash.
- The status intentionally says **Not connected**. There are **no fabricated earned points, unlock timestamps, achievements, progress bars or supported-game matches**. Until a real native account/session exists, this is a diagnostic foundation, not functional RetroAchievements.
- Follow-up acceptance: verify opening/closing this page in portrait and landscape, ROM URI access, ZIP/FDS variants, and error states on a physical device.

## Next source implementation gates

1. Vendor or fork LibretroDroid under source control and expose a compatible emulation-thread memory bridge and per-emulated-frame callback. Inspect the exact *pinned dependency version* before changing the renderer. Make sure the bridge never reads destroyed/changed cores. Do not mix prebuilt native libraries and incompatible Java interfaces.
2. Include a pinned stable `rcheevos` release with its MIT notice and native build integration. Add JNI wrapper for `rc_client` and network transport with proper timeouts, user-agent, retry and cancellation.
3. Add user login/token session storage **without logging secrets**. Never ask users to paste API keys into source control. Identify loaded ROMs with native hashing or confirmed matching app hash, and reject unidentified ROMs rather than guessing titles.
4. Process unlock events, rich presence and leaderboards only from the native authenticated game session. Build coherent UI for active/locked/unlocked/unsupported achievements and game association.
5. Implement hardcore policy: state loads, rewind, cheats and other advantages must be blocked or force a clear downgrade before the action. Save-state handling in M5 is not compatible with active hardcore unless gated.
6. On loss of internet, handle retries and session closure without inventing offline awards. Obtain emulator integration/approval guidance from RetroAchievements; never market OpenNES as approved without confirmation.

## Known acceptance blockers

- GitHub source-only commits cannot validate native ABI, memory descriptors, correct frame clock, JNI lifetimes or RetroAchievements logins. Builds and device tests remain paused.
- The current external LibretroDroid binary dependency lacks the required public hooks; inserting HTTP-only achievement calls would be misleading and may violate server/session semantics.
- Developer ROMs and test accounts should be owned by the user; do not bundle copyrighted commercial ROMs or real credentials.

Sources: https://github.com/RetroAchievements/rcheevos, https://github.com/RetroAchievements/rcheevos/wiki/rc_client-integration, https://docs.retroachievements.org/developer-docs/game-identification.html, https://docs.libretro.com/guides/memorymonitoring/
