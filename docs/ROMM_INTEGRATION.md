# OpenNES — RomM integration

**Status (2026-11-09): M8 source work in progress.** A read-only HTTPS API client and Android Keystore-backed token store exist. No remote RomM instance or network behavior was tested.

## Connection & security

- Use a **RomM Client API Token** restricted to `me.read`, `platforms.read` and `roms.read`. Generate the token in your own RomM instance; never paste it into source, issues or logs.
- OpenNES stores the token as AES-GCM ciphertext in the Android no-backup directory, with an Android Keystore key. It is never copied to ordinary SharedPreferences; the server URL is not secret.
- HTTPS is required for bearer-token calls. No redirects are followed, preventing accidental token forwarding to another host. If a server is available only over HTTP, configure TLS at a reverse proxy or private VPN before connecting.
- Supports read-only `GET /api/users/me`, `/api/platforms`, `/api/roms` and authenticated ROM download. Server schemas vary across RomM major releases; the client's pagination/filters need verification against the user's installed server's `/openapi.json`.

## NES import policy

- NES/Famicom and FDS platform slugs only. Remote files are offered only for supported `.nes`, `.fds` and `.zip` formats. No direct writes to the RomM server.
- Every download must be requested explicitly by the player. The client streams into a temporary bounded app cache file first, optionally checks the server-provided MD5, then adds a **new** document to the OpenNES-selected ROM tree. Existing same-name files are never overwritten, and local game saves and state files are not touched.
- A successful import schedules ordinary library indexing. Downloading never automatically launches a ROM.
- Save synchronization or conflict merging with RomM is **not implemented** and must not silently replace locally modified saves.

## API references

- https://docs.romm.app/latest/API-and-Development/API-Reference/
- https://docs.romm.app/latest/Getting-Started/Authentication/
- https://docs.romm.app/5.4.0/developers/client-api-tokens/
- https://docs.romm.app/latest/using/downloads/

## Outstanding gates

- Wire the client and secure store into a user-visible Connect/Browse/Import settings screen.
- Validate pagination, platform filtering, download sizes, content schema and checksum behavior against RomM 5.x and any legacy version to be supported.
- Verify Android document-tree permissions, duplicate handling, interruption/cancellation, server failures, token revocation and offline behavior.
- Optionally add remote save sync, only with explicit direction/conflict UI and a design that never overwrites existing saves by default.
- Builds, CI validation and real-device testing remain paused.
