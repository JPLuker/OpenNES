# OpenNES — RetroAchievements native integration boundary

**Status (2026-11-09): M6 in-game achievements shell and optional read-only website lookup implemented, native runtime not implemented.** The page hashes the current ROM locally and supports manually fetching real, existing website awards using the user's own web API key. OpenNES still cannot generate or submit new achievement unlocks.

## Grounded architecture

- Official emulator integration uses [rcheevos/rc_client](https://github.com/RetroAchievements/rcheevos) (C runtime) rather than treating the RetroAchievements public web API as an achievement engine.
- The supported FCEUmm Libretro core exposes memory via `retro_get_memory_data` / `retro_get_memory_size`; these symbols exist internally in [LibretroDroid's Core class](https://github.com/Swordfish90/LibretroDroid/blob/master/libretrodroid/src/main/cpp/core.h) but are **not exposed** in the Java `GLRetroView` API in OpenNES's current dependency.
- `rc_client_do_frame` must be driven **once per emulated frame**, including skipped/fast-forwarded rendering frames. `GLRetroEvents.FrameRendered` is a UI event, **not** a reliable replacement for a native per-frame hook.
- The native runtime needs an authenticated network callback, memory-read callback, a game/session lifecycle, achievements/leaderboards events, rc_client idle when paused, and safe per-frame threading. Do not run memory reads on an arbitrary Android background thread.
- Official NES identification excludes the 16-byte `NES\\x1A` header; FDS excludes its 16-byte `FDS\\x1A` header. The app-side `NesRetroAchievementsHash` helper computes this locally, supports single-ROM ZIP files and refuses ambiguous archives and oversized inputs. **It is not an awarder.**

## Verified UI boundary

- A dedicated Achievements entry opens from the current game's pause menu without exiting gameplay, with a current-game header and a scrollable status page.
- An Android IO coroutine computes the local ROM fingerprint on demand; the page distinguishes checking, identified and unavailable outcomes and allows the user to select/copy the hash.
- The default status intentionally says **Not connected**. There are **no fabricated earned points, unlock timestamps, achievements, progress bars or supported-game matches**.
- The new `RetroAchievementsDisplayState` contract and verified-achievement list support authenticated/connecting/no-match states, per-game hash confirmation, filtering, real unlock totals, descriptions and points when native data becomes available. **The Active state is not emitted by current OpenNES code.** A local hash alone is insufficient.
- A supplied Active session is shown only if its ROM hash matches the currently identified local ROM, preventing an old game session from showing unrelated awards. Until the native runtime exists, this is a diagnostic foundation, not functional RetroAchievements.
- Follow-up acceptance: verify opening/closing this page in portrait and landscape, ROM URI access, ZIP/FDS variants, and error states on a physical device.

## Optional read-only website progress (source implemented, device testing pending)

- This is **not an emulator login** and must never submit unlocks. The official RetroAchievements website API is used only for user-initiated inspection of existing site awards, in a separate screen section clearly marked **view-only**.
- A personal web API key and username are encrypted via Android Keystore AES-GCM and stored as an AtomicFile inside `noBackupFilesDir`; keys are never logged or placed in ordinary settings or source control. Disconnect deletes the file. Website keys are **not** rc_client credentials.
- The app checks the supplied user profile via `API_GetUserProfile.php`, resolves the exact ROM hash through console 7's `API_GetGameList.php` with hashes enabled, then reads `API_GetGameInfoAndUserProgress.php`. It does **not** associate games by their names.
- The public NES game/hash index is cached for seven days, as recommended by the API maintainers. Responses and cache writes are byte-bounded and requests do not follow redirects. The personal API key is sent through the API's documented HTTPS query parameter, never written in the public hash cache. No website progress is persisted.
- This is an **explicitly initiated** network request: opening the pause menu or loading a ROM does not contact the website. The account and a successful hash match do not enable live awards.
- Acceptance: verify valid/invalid personal keys, matching and mismatching NES/FDS ROMs, cache expiry/offline errors, disconnect, progress filtering, screen rotation, process recreation and a second Android device. Confirm that web progress remains clearly distinguished from local emulator unlocks.

## Implementation safety

- The current `NesRetroAchievementsHash` also caps **aggregate ZIP uncompressed entry data**, including non-ROM entries, and prevents zero-length stream reads from spinning forever.
- The Android dependency is pinned to **LibretroDroid 0.14.0** in `buildSrc/src/main/java/deps.kt`. This version lacks the required Java-exposed per-frame and emulated-memory hooks; replacing or source-building that dependency is a prerequisite to functional rc_client awards. Do not layer fake HTTP achievements on top of the existing binary.
- Follow the official runtime contract: `rc_client_do_frame` once per **emulated** frame, not screen draw; `rc_client_idle` while paused. Verify on FCEUmm and hardware before enabling live awards.

## Pinned source-build native bridge

- The project now carries an **upstream LibretroDroid 0.14.0 source submodule**, pinned to commit `8835c3098514390a271e36983957f7bb5f40abf1`, together with its recursive Oboe and libretro-common dependencies. OpenNES uses a single source-built module rather than mixing that native library with its older JitPack AAR.
- Run `python3 scripts/prepare-libretrodroid-bridge.py` after checkout/submodule update. The script fails closed on a different source revision and applies an idempotent local patch, tracked in OpenNES rather than directly changing the upstream fork. The CI workflow runs the script before lint and compilation.
- The source preparation also supplies explicit `<functional>` includes in two upstream headers that relied on transitive includes and failed with the current NDK Clang. These are compatibility fixes only.
- The bridge exposes a lock-protected **copy** of supported core RAM regions and emulated frame count to Java, and installs a native after-`retro_run` observer hook for the future `rc_client_do_frame` call. The observer must run on the native core thread under the lock; it must **not** recursively call the memory-copy JNI method.
- **This is plumbing, not a working achievement runtime.** There are no native unlock or leaderboard events until the pinned rcheevos runtime is linked and authenticated, then tested on real hardware.

## Official rcheevos source linking (pending ABI acceptance)

- OpenNES pins official [rcheevos v12.5.0](https://github.com/RetroAchievements/rcheevos/releases/tag/v12.5.0) at commit `1433173220a7eaede6a9ed7a18e94117be1821e0` as a source submodule. The upstream `vendor/rcheevos/LICENSE` MIT notice is retained.
- The native preparation script links the official C client, API parser, hash support and `rc_libretro` memory mapper into the **same source-built** LibretroDroid library. A dormant JNI `preflight` creates and destroys an unattached `rc_client` (no login or network).
- The native **frame probe** uses `setEmulatedFrameObserver` to call `rc_client_do_frame` exactly once after each `retro_run` execution (including frames skipped by video and speed multiplier). It exposes start/stop and an observed-frame counter to internal Java diagnostics. `stopFrameProbe()` acquires the same `coreLock` as frame execution before destroying the client, preventing use-after-free. It does not run automatically or earn achievements; activating an actual authenticated game remains a separate milestone.
- The probe now initializes **official `rc_libretro_memory_regions_t` for NES console 7** on the emulation thread once game memory exists. Its read callback accesses the mapped regions without recursively acquiring `coreLock`, and background memory reads are disallowed. The mapped-byte counter allows later device tests to distinguish installed JNI symbols from a genuine memory map. Stop clears the map before releasing the client; no game activation, network requests, or awards occur.
- Every core creation, teardown, and ROM load invalidates a monotonic native game generation. The frame probe discards RAM pointers when this generation changes, and its displayed mapping drops to zero for a destroyed core. Starting the probe a second time also safely detaches/destroys its previous unattached client before re-arming; a teardown cannot strand the debug tool in a fake `already running` state.
- CI verifies explicit JNI exports for core memory snapshots, emulated frame count and rcheevos preflight in every packaged ABI.
- **Not yet implemented:** real emulator user login/token, rc_client memory mapping callbacks, HTTPS server transport, native game activation, unlock events, hardcore restrictions and live overlays. Linking the library is not proof of accurate achievements.

## FDS website progress and native limitation

- Read-only website lookup uses RetroAchievements system **7 for NES** and **81 for Famicom Disk System**, based on local ROM identification, with separate cached indexes. One system's results cannot appear for the other.
- Native FDS unlock compatibility is **not implemented**: the pinned FCEUmm Libretro core does not expose all additional FDS RAM required for achievements. Support must not be claimed until the core and memory map have been tested against real FDS games.

## Debug-only real-device native acceptance tool

- Open Pause → Achievements → **Native bridge diagnostics (debug only)**, then Start probe, resume NES gameplay, reopen and Refresh. Expect observed frames **and successful 1-byte NES zero-page RAM reads** to advance, with mapped memory nonzero. No memory values are exposed. Stop probe afterward.
- This manually arms the dormant `rc_client` runtime to exercise the official NES memory-region map; it does **not** create a user session, submit requests, or award achievements. Diagnostic controls are hidden in non-debug APKs.
- Test against real NES gameplay and save/load, fast-forward and rotation, and verify stopping clears the native mapping safely. Compiling these hooks is not a substitute for on-device testing.

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
