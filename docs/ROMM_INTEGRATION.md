# OpenNES — RomM integration

**Status (2026-11-09): M8 source work in progress.** A read-only HTTPS API client and Android Keystore-backed token store exist. No remote RomM instance or network behavior was tested.

## Connection & security

- Use a **RomM Client API Token** restricted to `me.read`, `platforms.read` and `roms.read`. Generate the token in your own RomM instance; never paste it into source, issues or logs.
- OpenNES stores the token as AES-GCM ciphertext in the Android no-backup directory, with an Android Keystore key. It is never copied to ordinary SharedPreferences; the server URL is not secret.
- HTTPS is required for bearer-token calls. No redirects are followed, preventing accidental token forwarding to another host. If a server is available only over HTTP, configure TLS at a reverse proxy or private VPN before connecting.
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

## Outstanding gates

- [x] Settings → RomM library now contains Connect/Browse/Import, pagination, connection check and disconnect controls. All imports are explicitly initiated and respect the configured local NES folder.
- Validate pagination, platform filtering, download sizes, content schema and checksum behavior against RomM 5.x and any legacy version to be supported.
- Verify Android document-tree permissions, duplicate handling, interruption/cancellation, server failures, token revocation and offline behavior.
- [x] Manual per-save backup export is available without modifying local progress.
- Optional two-way remote save sync still requires explicit direction/conflict UI, versioning and a design that never overwrites existing saves by default.
- Builds, CI validation and real-device testing remain paused.

## November 10 no-device synchronization safety groundwork

- Added `RommSaveSyncPlanner`: a **non-mutating** sync decision engine, deliberately **not wired to uploads or downloads**. It starts disabled, requires a confirmed RomM ROM ID, immutable local and remote SHA-256 save snapshots, a remembered pair of previous snapshots, a current online recheck, explicit upload/download direction, approval for that exact operation and an already verified versioned backup.
- A save changed on both devices is a conflict even if one file has a later timestamp. Missing baselines require explicit initial pairing. Offline changes must be revalidated against the remote state on reconnect rather than blindly replayed. The planner returns expected hashes for a future executor to recheck before modifying a file.
- JVM tests exercise opt-in, offline behavior, unverified identity, first pairing, diverged saves, required backups, explicit direction and no-op equality.
- **Still absent:** encrypted persistent sync queue, remote create/update endpoint, immutable upload snapshot storage, transactional local replacement, conflict resolution UI and server/device validation. No code enables RomM remote writes or overwrites live saves. Build each layer separately, then require real-server testing before release signoff.

### Versioned local backup writer (source implemented, not connected to sync)

`RommVersionedSaveBackupStore` copies a local save into an app-private directory under a new per-ROM version name. It streams and SHA-256 hashes the content with a 16 MiB cap, syncs the staged file to disk, renames it to a unique `.savebak` filename, and re-reads the result to verify the digest. The original save is never modified. Incomplete/corrupt backups are discarded; callers may reverify a stored snapshot before using it. A separate opt-in executor is still needed to connect this backup to the `RommSaveSyncPlanner` decision and any RomM API write. No remote mutations are enabled.

### Offline pending-sync journal (source and CI verified; NOT a transfer executor)

`RommPendingSyncJournal` writes a versioned, checksummed app-private file with at most 32 outstanding per-ROM intents. Only ROM ID, requested upload/download direction, observed local/remote SHA-256 snapshots and an informational timestamp are persisted. It contains **no token, save payload, network endpoint, user approval, or automatic-replay capability**. A corrupt journal is reported as corrupt and prevents queue changes. Duplicate requests cannot overwrite older pending intents.

A future executor must obtain fresh local and remote snapshots, verify RomM account/server and ROM identity, re-run `RommSaveSyncPlanner`, create and verify required versioned backups, and secure explicit approval for that exact operation. Any changed snapshot rejects the old intent for user review. Until real-server validation and an explicit executor exist, this journal **cannot upload, download into live saves, or replay offline writes**.

CI: https://github.com/JPLuker/OpenNES/actions/runs/42890671021
