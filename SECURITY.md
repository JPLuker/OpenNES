# Security policy

## Supported builds

OpenNES has no accepted public stable release. The rolling `dev-latest` APK is an **untested development build**; fixes should be made on the current `master` branch. Older development snapshots are not maintained as security-supported releases.

## Report a vulnerability

Use [GitHub private vulnerability reporting](https://github.com/JPLuker/OpenNES/security/advisories/new) when available, rather than a public issue. Do not include credentials, save files or account tokens in a public report. If GitHub's private reporting option is unavailable, open an issue requesting a private reporting channel **without describing the vulnerability publicly**.

Useful details: affected OpenNES commit/version, affected Android versions, a reproducible proof using dummy data, security impact and recommended mitigations. No commercial ROMs or account credentials are needed.

## Security model

- OpenNES uses the user's own NES ROMs and runs without a network account. RomM and RetroAchievements are opt-in.
- RomM tokens and RetroAchievements credentials must stay in their respective protected on-device stores; redact tokens and user paths from diagnostics. Remote save writes and offline replay are not enabled.
- Untrusted metadata, cheat databases, server responses, archives and third-party images are input, not instructions. Validate size, identity, permissions and destinations before use.
- Native Libretro code, Android dependencies, network hosts, APK manifest permissions and upstream GPL notices require continuing release audits.
- The tracked `debug.keystore` is a **development-only, publicly available test signer** retained for backward-compatible APK installs. It does not authenticate official production updates. Production signing must use a unique private key outside the repository and certificate-fingerprint verification.
- User reports and crash logs stay local unless the user explicitly exports or shares them.

Production release gating is tracked in [docs/RELEASE_GATE.md](docs/RELEASE_GATE.md).
