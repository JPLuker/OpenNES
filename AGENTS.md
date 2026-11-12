# OpenNES developer instructions

Read this file, the current [roadmap](ROADMAP.md), [testing status](TESTING.md), release gates, and open [GitHub issues](https://github.com/JPLuker/OpenNES/issues) before changing code. The shared [InternalDesignGuidelines](https://github.com/JPLuker/InternalDesignGuidelines) are default engineering guidance, **not** permission to change this project's NES-controller visual direction.

## Project decisions

- OpenNES is a dedicated Android NES/Famicom emulator built from Lemuroid and LibretroDroid. Preserve FCEUmm, upstream notices and compatible save data; do not bundle commercial ROMs or unlicensed artwork.
- Push routine work to `master`. The single rolling **untested debug** APK is `dev-latest/OpenNES-debug.apk` plus a SHA-256 sidecar. Do not create feature APK branches or substitute a CI workflow ZIP as the normal test download.
- The development APK ID is `com.jpluker.opennes.debug`. Its persistent test signer is not production signing. Each CI run derives an increasing version code from `OPENNES_DEV_RUN_NUMBER`, sourced from GitHub Actions; the root `VERSION` file owns the base version.
- No speculative restyling. Portrait and landscape controls retain independent configurations; the controller palette drives the UI. Changes to appearance require an explicit request.
- RetroAchievements runs in Casual mode only. Do **not** enable official Hardcore unlocks or advertise compliance without real approval, native enforcement and acceptance.
- RomM is optional. Existing library browsing, imports, manual backup and offline request planning do not authorize background save uploads or remote overwrites. Preserve per-user, per-server provenance and require live credentials and explicit consent before future writes.
- Local emulation must work without signing into either service. Every network request must be scoped, cancellable, bounded and transparent to users.

## Before changing code

1. Check issues, decisions and regression notes. Prioritize save loss, crashes and reported bugs before enhancements.
2. Read all callers of the function being modified. Reproduce a bug or document why reproduction requires hardware.
3. Prefer small source changes with a test that fails on the old implementation. Check old saves and invalid inputs when touching data formats.
4. Run `python3 -m unittest discover -s scripts/tests -p 'test_*.py'`, `./gradlew ktlintCheck`, `./gradlew :lemuroid-app:testFreeBundleDebugUnitTest`, and the NES-only APK inspection/build gates when the Android toolchain is available.
5. The GitHub workflow is the supported build/test toolchain when local Android SDK/NDK or submodules are unavailable. **CI green does not equal device acceptance.**
6. Before a push, read the diff, check for unused imports and stale references, keep user data on atomic/verified write paths, and verify no secret, ROM, or private log is committed.
7. Update the changelog and the relevant roadmap/testing docs; push and confirm the branch head and workflow results. Record hardware-only tests in `TESTING.md`.

## Deviations from general guidelines

- One rolling debug build on `master`, rather than PR-specific release branches. Debug builds are labeled as test-only and kept separate from eventual stable releases.
- Existing test signer is preserved to keep in-place development installs working. The repository's public `debug.keystore` is **not** a protected production signing key and cannot be used to establish trust in stable releases.
- The optional generic visual theme is not adopted. OpenNES retains its own NES-controller-derived colors and layout.

## Release boundary

Do not call OpenNES 1.0-ready until `docs/RELEASE_GATE.md`, `docs/M5_FINAL_TESTING.md`, and [TESTING.md](TESTING.md) have actual evidence. Confirm final project licensing and production signing separately; the inherited GPLv3 obligations cannot be replaced by a generic MIT default.
