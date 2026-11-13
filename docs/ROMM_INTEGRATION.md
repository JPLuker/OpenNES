# OpenNES — RomM integration

**Status (2026-11-10): M7 integration in progress.** A scoped read-only API client (HTTPS or explicitly approved private IPv4 HTTP), Android Keystore-backed token store, immutable offline archives and server-bound non-executing request staging exist. Offline integrity auditing, local request review, strict conflict ancestry checks and a read-only RomM 5.4 negotiation parser passed [CI run 42927257071](https://github.com/JPLuker/OpenNES/actions/runs/42927257071). No real RomM server or Android UI acceptance has been verified. Per-account device registration now has a **source implementation** but has not passed a live registration; remote save writes remain disabled.

## Connection & security

- Use a **RomM Client API Token** restricted to `me.read`, `platforms.read` and `roms.read`. Generate the token in your own RomM instance; never paste it into source, issues or logs.
- OpenNES stores the token as AES-GCM ciphertext in the Android no-backup directory, with an Android Keystore key. It is never copied to ordinary SharedPreferences; the server URL is not secret.
- HTTPS is required for public hosts and domain names. A literal RFC1918 IPv4 destination (10/8, 172.16/12 or 192.168/16) can use HTTP **after an explicit warning and confirmation**. This permits local installations such as `http://192.168.1.25:8081`; anyone on an untrusted network may intercept the bearer token or downloads, so use HTTPS or an encrypted VPN for remote access. No redirects are followed, preventing token forwarding to another origin.
- Android's network-security XML cannot whitelist an IP subnet. Cleartext support is enabled at the Android network-security layer, while OpenNES' RomM URL validator enforces private literal addresses before making any RomM HTTP request. Audit other app HTTP clients before production release.
- **QR pairing (source candidate, device testing pending):** RomM → Client API Tokens → Create/Regenerate → Pair device displays a 60-second QR of the form `https://server/pair?code=XXXX-XXXX`. OpenNES can scan using an offline ZXing scanner or accept the eight-character code manually, then POST `/api/client-tokens/exchange`, verify `/api/users/me` and store the returned `rmm_` token encrypted. QR links are validated to a matching official `/pair` path; scans never automatically contact the server. A local HTTP QR triggers its own explicit warning. Pairing may regenerate an existing token, invalidating that token on other devices; use one token per device.
- Supports read-only `GET /api/users/me`, `/api/platforms` (bare array), `/api/roms` (paginated `items` object) and authenticated ROM download. For paginated ROMs, the client explicitly requests total-count metadata. Server schemas vary across RomM major releases; the client's pagination/filters need verification against the user's installed server's `/openapi.json`.

## NES import policy

- NES/Famicom and FDS platform slugs only. Remote files are offered only for supported `.nes`, `.fds` and `.zip` formats. No direct writes to the RomM server.
- Every download must be requested explicitly by the player. The client streams into a temporary bounded app cache file first, optionally checks the server-provided MD5, then adds a **new** document to the OpenNES-selected ROM tree. Existing same-name files are never overwritten, and local game saves and state files are not touched.
- Downloads are validated before import: .nes files require an iNES header, ZIPs require exactly one compatible ROM and are checked for dangerous paths, oversized expansion and entry count. A successful import schedules ordinary library indexing. Downloading never automatically launches a ROM.
- Optional `assets.read` scope enables a manual **Saves → Export backup** workflow for the selected remote NES game. The downloaded save is staged, size-bounded, compared to RomM's raw MD5 or ZIP content hash when available, and exported through the Android Save As picker. It never touches live OpenNES save RAM or states. Automatic save synchronization or conflict merging remains **not implemented** and must never silently replace local progress.

## API references

- https://docs.romm.app/latest/API-and-Development/API-Reference/
- https://docs.romm.app/latest/Getting-Started/Authentication/
- https://docs.romm.app/5.4.0/developers/client-api-tokens/
- https://docs.romm.app/latest/using/downloads/

## Pairing acceptance (not yet completed)

- Verify on an Android device that the scanner opens, the camera permission is requested, an official RomM QR is recognized and the token exchange finishes before the code expires.
- Verify both scan and manual eight-character code against the actual RomM server version, with read scopes and revoked/expired codes. A canceled QR scan must not connect; duplicate/bad codes must not save credentials.
- Verify private-LAN HTTP on the player's own Wi-Fi, and reject public HTTP, ordinary hostnames over HTTP, redirects, userinfo and malformed QR content. Prefer HTTPS outside a trusted LAN.
- Verify the new ZXing dependency's APK size, Android permission merge, license attribution and offline scanner behavior.

## Unified library previews (November 2026)

- **Remote NES/Famicom ROMs now enter the regular OpenNES library shelf and search** as read-only previews, not fake Room games. The shelf includes all indexed local NES titles plus a lazily presented RomM catalogue; local recent/favorites remain normal and every local game keeps its own saved states and playtime.
- **Remote-only games show Download instead of Play.** Selecting Download invokes the existing explicit, size-bounded and integrity-checked SAF import. It does not launch the game, import saves or download any other ROM; the library indexer then creates the real playable local entry.
- Remote catalogues are fetched over the existing authenticated RomM connection in bounded pages of 100, with a 5-minute in-memory refresh interval and no ROM downloads. A disconnected/unconfigured account yields only the local library.
- The current Room schema has no global ROM checksum identity field. The display deduplicates by exact case-insensitive local filename (not by game title) to avoid merging different-region games with the same name. Renamed local files may appear separately until an explicit identity mapping is implemented.
- A download that completes while library indexing is pending is shown as **Adding to library**, never as Play. The existing file and any local save are never overwritten.
- Remote previews use the same NES box-art renderer, which can resolve official Libretro art for recognizable ROM filenames; non-matching filenames may show a normal fallback image. The RomM endpoint and user token are never embedded into public cover URLs.
- UI/device acceptance is still pending, including remote catalogue scale, dedup, Android lifecycle/network changes and import completion. The server is *not* treated as a virtual ROM filesystem or used for streaming gameplay.

## November 10–11 — RomM full-library and offline reliability candidate

- **Unified NES library:** all indexed local NES games and RomM-only previews share the physical shelf, with a full-screen adaptive cover grid, search, A–Z jumps, Installed/RomM filters and the regular details layout. A remote preview never enters Room as a fake playable file. Download becomes Play only once a real local ROM has been indexed.
- **Native RomM metadata:** description/summary is used where available, with a clear missing-description label. Only trusted public IGDB HTTPS cover URLs are accepted from the RomM record; Libretro NES box-art fallback remains. The selected server token is never sent to a cover host.
- **Offline metadata:** a strictly bounded metadata-only cache is kept in private no-backup storage, keyed by exact server identity plus login revision, and cleared on disconnect/reconnect. Reopening the app without RomM access can show previously fetched titles and descriptions; ROM bytes are not cached or downloaded. A visible Retry action bypasses the refresh interval.
- **Transfer workflow:** explicitly requested downloads show queued, connecting, downloading (bounded percent when known), verifying, importing and indexing states. The game details blur during work, a persistent home banner preserves status after dismiss, and the Settings browser has a cancellable loading dialog. Failures are per-ROM and actionable; a stalled WorkManager scan offers Retry library scan rather than another ROM download.
- **Correct complete pagination:** the client advances by raw server-row count, not filtered NES entries; small, filtered and total-less pages are covered by tests. Platform discovery is reused within one connection's paginated request.
- **Storage safety:** prevent case-insensitive duplicate imports, reject renamed SAF-created files, never overwrite a local ROM or save, and retain successful file imports in the indexing-recovery state if scan scheduling fails. A ROM stream retains connection/read stall limits plus a strict size bound without the JSON API's 60-second overall timeout.
- **Verification:** all work is a source candidate pending the latest CI build and an actual phone/RomM server session. Tests now cover offline cache corruption/account isolation, album-art host restrictions, paging, deduplication and index completion. Do not mark M7 complete or publish M8 stable until real-device acceptance is performed.

## November 12 — Device registration and negotiation source candidate

- **Explicit registration UI:** Settings → RomM → Device sync setup offers **Register device**. OpenNES first revalidates `/api/users/me` against its pinned, encrypted-token login; it then submits a bounded `POST /api/devices` using the documented RomM 5.4 `devices.write` scope. The request identifies `platform=android`, `client=opennes`, and `sync_mode=api`. It explicitly disables remote-install capability, duplicate creation and synchronization reset.
- **Private device identity:** the registration hostname is an anonymous, stable alias derived from a random seed in Android no-backup storage and the exact server/account identity. No MAC, actual Android hostname, IP address, or token appears in the registration payload. The server-issued UUID is retained only while the server fingerprint, credential revision and verified RomM account match. Token change or disconnect clears the binding; the alias seed stays local so a reconnect can claim the same existing server device.
- **Bounded cancellation:** registration HTTP uses a cancellable OkHttp callback with a no-redirect client, response-size limits and post-response account revalidation. An in-flight response after logout or token rotation cannot pin the device to the new account.
- **Offline negotiation input:** `RommDeviceSyncNegotiationRequest` builds a bounded, **non-executing** RomM 5.4 negotiation JSON body for individually verified ROM IDs, safe `.srm` filenames, MD5 content hashes and UTC timestamps. It separately requires the current server/account/credential pin, rejects duplicate slots or unsafe content, and always sends `restore_unlisted=false`. The immutable SHA-256 fingerprint remains local for archive integrity rather than being misused as RomM's MD5.
- **Comparison preview source candidate:** After registration, a user can select one local NES `.srm` while viewing that RomM game. The app double-reads its bounded fingerprint and modification timestamp, submits a single-ROM `/api/sync/negotiate` request and displays the server's proposed upload/download/conflict/delete/no-op operations. It completes that short-lived server comparison session with unperformed actions recorded as failed. No save bytes move and no local file is touched. The token needs `assets.read`, `devices.read` and `devices.write`. The server receives the selected save's name, timestamp and MD5, not its content. Requires RomM 5.4 live verification.
- **Still disabled:** No upload/overwrite/download executor or automatic queue replay. Registration and comparison are **not** full save synchronization; safe transfer and conflict handling remain M7 gates.

## Outstanding gates

- [x] Settings → RomM library now contains Connect/Browse/Import, pagination, connection check and disconnect controls. All imports are explicitly initiated and respect the configured local NES folder.
- Validate pagination, platform filtering, download sizes, content schema and checksum behavior against RomM 5.x and any legacy version to be supported.
- Verify Android document-tree permissions, duplicate handling, interruption/cancellation, server failures, token revocation and offline behavior.
- [x] Manual per-save backup export is available without modifying local progress.
- Optional two-way remote save sync still requires explicit direction/conflict UI, versioning and a design that never overwrites existing saves by default.
- [x] Source builds and automated tests are active; real-device and RomM server acceptance remain outstanding.

## November 10 no-device synchronization safety groundwork

- Added `RommSaveSyncPlanner`: a **non-mutating** sync decision engine, deliberately **not wired to uploads or downloads**. It starts disabled, requires a confirmed RomM ROM ID, immutable local and remote SHA-256 save snapshots, a remembered pair of previous snapshots, a current online recheck, explicit upload/download direction, approval for that exact operation and an already verified versioned backup.
- A save changed on both devices is a conflict even if one file has a later timestamp. **Differing historical hashes also constitute an invalid shared baseline** and fail closed. Missing baselines require explicit initial pairing. Offline changes must be revalidated against the remote state on reconnect rather than blindly replayed. The planner returns expected hashes for a future executor to recheck before modifying a file.
- JVM tests exercise opt-in, offline behavior, unverified identity, first pairing, diverged saves, required backups, explicit direction and no-op equality.
- **Still absent:** an executable sync queue, remote create/update endpoint, immutable upload snapshot storage, transactional local replacement, full conflict resolution UI and server/device validation. No code enables RomM remote writes or overwrites live saves. Build each layer separately, then require real-server testing before release signoff.

### Versioned local backup writer (source implemented, not connected to sync)

`RommVersionedSaveBackupStore` copies a local save into an app-private directory under a new per-ROM version name. It streams and SHA-256 hashes the content with a 16 MiB cap, syncs the staged file to disk, renames it to a unique `.savebak` filename, and re-reads the result to verify the digest. The original save is never modified. Incomplete/corrupt backups are discarded; callers may reverify a stored snapshot before using it. A separate opt-in executor is still needed to connect this backup to the `RommSaveSyncPlanner` decision and any RomM API write. No remote mutations are enabled.

### Offline pending-sync journal (source and CI verified; NOT a transfer executor)

`RommPendingSyncJournal` writes a versioned, checksummed app-private file with at most 32 outstanding per-ROM intents. Only ROM ID, source-server fingerprint, requested upload/download direction, observed local/remote SHA-256 snapshots and an informational timestamp are persisted. It contains **no token, save payload, network endpoint, user approval, or automatic-replay capability**. A corrupt journal is reported as corrupt and prevents queue changes. Duplicate requests cannot overwrite older pending intents.

A future executor must obtain fresh local and remote snapshots, verify RomM account/server and ROM identity, re-run `RommSaveSyncPlanner`, create and verify required versioned backups, and secure explicit approval for that exact operation. Any changed snapshot rejects the old intent for user review. Until real-server validation and an explicit executor exist, this journal **cannot upload, download into live saves, or replay offline writes**.

CI: https://github.com/JPLuker/OpenNES/actions/runs/42890671021

### November 10 — Offline archive manager and server-bound pending request preparation

The settings screen now supports deliberate deletion of individually verified offline backups (with confirmation), read-only SHA-256 comparison against a user-selected save document, and review/discard of pending requests. Android's document picker is used only for the immediate comparison or snapshot. The selected URI and its permission are **not** saved.

Newly archived RomM saves record a non-secret SHA-256 fingerprint of the normalized HTTPS server origin in their checksum manifest. Existing legacy archives remain available for export and compare, but lack sufficient verified source provenance to prepare a transfer request. The pending request journal is upgraded to format v2 with the same fingerprint, and it refuses corrupt or legacy unbound v1 journals. Requests from another server cannot pass planner revalidation solely because their RomM game IDs happen to match.

When a verified server-bound offline archive is present, the user can prepare a **non-executing** upload/download intent by explicitly choosing a direction and a local save document. The app hashes the selected document twice, revalidates the archive's manifest and content, and persists only local/remote fingerprints, ROM ID, server fingerprint, direction, and timestamp. No network write is scheduled; no local save is copied for upload, no URI is remembered, and no previously granted approval can be replayed. Future execution must reselect/reverify sources, confirm the current RomM account and ROM mapping, obtain a new approval, and create verified versioned backups before doing anything destructive.

Baseline: https://github.com/JPLuker/OpenNES/actions/runs/42911500306 (archive management). The later server-bound request workflow requires a separate green CI build and actual phone/server tests before claiming acceptance.

## November 10 — Read-only offline integrity and RomM 5.4 device-protocol groundwork

**Verified CI:** [42927257071](https://github.com/JPLuker/OpenNES/actions/runs/42927257071). The rolling APK includes these source changes, but requires real Android and server acceptance.

### Offline review and backup safety

- Pending v2 requests are keyed by **server fingerprint plus game ID**, not game ID alone. Different RomM servers may use the same integer ROM ID; they must never be treated as the same content or account. A stale request cannot pass future planner revalidation after it is edited or discarded.
- The player may explicitly choose a local save document to **recheck its SHA-256 fingerprint** against the saved request. A match only says that this local file looks unchanged. It does not verify the remote save, authorize an upload or identify the owner of the save.
- An archive audit reports verified backups, unverified archives, orphaned integrity manifests and interrupted partial writes. It does not delete or repair any evidence automatically. Staging a backup rechecks the source file before and after copying; recovery rechecks the current manifest and content.

### Strict supported protocol (no execution)

[RomM 5.4 Device Sync Protocol](https://docs.romm.app/5.4.0/developers/device-sync-protocol/) documents **UUID device IDs, numeric session IDs, MD5 `content_hash` values**, and negotiated `upload`, `download`, `conflict`, `delete` and `no_op` actions. Older published 4.x examples use different fields and session IDs. **The user's actual server version is not yet known.**

`RommDeviceSyncProtocol` is an isolated parser for the 5.4 response shape only. It rejects unknown actions/older schemas, remote-specified paths or URLs, unverified ROM IDs, missing content IDs, malformed filenames, duplicate operations and inconsistent operation totals. It computes stable **MD5 for RomM protocol compatibility and SHA-256 for local verification** by independently rereading the source. Parsing a server request never constitutes user consent or execution.

**The following still do not exist:** production device registration, authenticated negotiation request execution, upload/download byte transport, server delete handling, conflict resolution, baseline writes, session completion, automatic polling or live emulator save replacement. `delete` and every write direction remain in-memory descriptions only.

### Multi-user and authentication release gate

RomM's [Client API Tokens](https://docs.romm.app/5.4.0/developers/client-api-tokens/) are **per user**. Every person must authenticate with their **own** scoped token; a shared OpenNES token would mix privileges and identity. Current local backup manifests and offline journal entries are **server-bound only**: they are **not yet bound to a verified RomM account/device**. This is acceptable for non-executing offline review but **blocks all future automatic replay and writes** until the actual RomM user ID, token owner, device UUID, confirmed ROM mapping and permissions are checked and persisted using a reviewed design.

Read-only browsing needs only the scopes currently requested by the client. A future full device-sync integration will additionally require relevant `devices.read`, `devices.write`, `assets.read` and `assets.write` scopes. Do not request write scopes until the user elects to enable and verify that feature. Check the installed server's `/api/docs` or `/api/openapi.json` first; never assume the user's installed version matches a documentation example.

**Next acceptance:** Connect each of two separate user accounts with separate scoped tokens, confirm visibility and save isolation, inspect the real OpenAPI schema, verify read-only endpoints, test revoked/expired tokens and interruptions, and review actual negotiation/conflict responses before implementing any write executor. Then test backup-first manual transfers on Android, with no live save ever overwritten without explicit approval.

## November 11 — Verified account identity pin (read-only groundwork)

On successful manual or QR connection, OpenNES now parses the RomM `/api/users/me` account ID as an exact positive integer, separately from the display username. A verified ID can be pinned to the current encrypted token revision and normalized server fingerprint in private app preferences. Changing the token or disconnecting clears the pin. If an older server does not expose a compatible ID, browsing remains available but no account identity is claimed for synchronization. The pin is **not** an executable sync credential, proof of write permission or permission to replay offline requests. All upload, overwrite, device registration and auto-sync operations remain disabled until the real server and separate device ownership are verified.

## November 11 — Account-bound archive provenance

Newly downloaded and verified offline save backups now contain both the normalized source-server fingerprint and the authenticated positive numeric RomM account ID in the private checksum manifest. The archive listing preserves older unbound manifests for export and recovery without treating them as account-owned. Archive deletion and current-file validation compare the recorded account ID to prevent identity substitution. Preparing a NEW offline transfer intent requires an account-bound archive and a locally pinned matching user ID; a legacy unbound backup cannot be queued for future sync. Existing server-only v2 pending requests remain non-executable and available for review/discard. This does **not** enable any upload, live save replacement or automatic sync. Revalidation of the live account, installed API schema, device ownership and write scopes remains mandatory before those operations exist.

## November 11 — Account-scoped pending requests

The existing v2 offline-intent format now supports an optional verified positive RomM account ID. New requests created from owner-bound archives persist the account ID and reject duplicates only within the same server, user and ROM ID. Existing older server-only requests stay readable and discardable, but they never pass the planner's account-binding check. A different authenticated user cannot turn a pending request into an upload/download decision. These entries remain non-executing: no credentials, save payload, standing approval, remote upload or automatic replay is stored or enabled.

## November 12 — Bounded private catalogue-cache reads

The private RomM metadata cache now uses a bounded reader rather than `InputStream.readBytes()`. A changed, malformed or stalled file cannot allocate arbitrarily past the 12 MiB catalogue limit; repeated zero-progress reads fail closed. A valid file of exactly the maximum size still decodes, and corrupt files remain recoverable by the existing online cache refresh. No changes are made to ROMs or save files. New JVM tests exercise empty, exact-sized, oversized, stalled and transient-zero-read inputs.

## November 12 — Backup manifest and verifying-reader bounds

Manifest listing now enforces the 200-byte checksum-manifest maximum **during** the read, not only through a pre-read file-size check. Manifest canonical containment is verified before parsing. Backup SHA-256 verification stops if an archive grows beyond its previously verified byte count during a read. A malformed manifest remains intact for manual recovery and cannot authorize deletion, recovery or a pending transfer. New regression coverage checks rejection without data loss.

## November 12 — Bounded pending-request journal reads

The non-executing RomM request journal now caps file reads at 64 KiB even when the private JSON grows after the initial file-length check. Journal canonical containment is checked before reading; stalled input and oversized files fail closed as `Corrupt`. The original file is retained for manual recovery instead of being silently overwritten or replayed. Regression tests verify that an oversized journal remains untouched and cannot be discarded or used for synchronization.

## November 12 — Recovery re-verification read cap

The final SHA-256 verification of a staged recovery no longer trusts a single file-length check. Its byte reader refuses to consume more data than the immutable archive's recorded length, even if a file changes between the size check and streaming. Staging also aborts as soon as input exceeds the verified archive size. Invalid, truncated or stalled streams fail safely, the temporary recovery is discarded and the original live save remains untouched. New JVM tests cover exact, oversized, truncated, wrong-digest and stalled inputs.
