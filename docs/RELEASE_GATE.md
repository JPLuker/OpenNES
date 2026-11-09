# OpenNES — Production release gate

**Status:** Not release-approved. This is an implementation checklist, not evidence of a completed test.

## Secret custody and build provenance

- [ ] Create a unique OpenNES release keystore off-repository, secure a backup and record certificate fingerprints privately.
- [x] Remove Lemuroid's inherited hardcoded release credentials. Without all four `OPENNES_RELEASE_*` environment variables, release signing is not configured.
- [ ] Verify all release environment variables come from protected CI secrets or a private interactive shell; avoid logging them.
- [x] Pin the four GitHub Actions families in the development APK workflow to their verified tag commits.
- [ ] Pin all native and Gradle dependencies to reviewed versions and verify full imported-code provenance.
- [ ] Review GitHub Actions least-privilege permissions and prevent untrusted release publishing.
- [x] Add a verified SHA-256 sidecar to rolling debug release assets (workflow source; not manually run).
- [ ] Generate and verify reproducible signed production artifacts and archived manifest/SHA-256/checksums.

## Package & source compliance

- [ ] Validate release application ID `com.jpluker.opennes`, labels, icons, versioning, supported SDK and versionCode.
- [ ] Inspect final APK: only FCEUmm emulator core, correct ABIs, valid 16KiB page alignment and acceptable binary size.
- [ ] Inspect upstream Lemuroid, LibretroDroid, FCEUmm and other dependencies for applicable GPL/MIT notices, source availability and license text.
- [ ] Verify no proprietary paid service, code, ROM or RetroAchievements credentials are bundled.
- [ ] Review app permissions, telemetry/network behavior, Android backups and privacy text.

## Functional acceptance (currently paused)

- [ ] Install/upgrade from prior OpenNES debug and test builds, verify saves survive app upgrades and uninstall/restore policy is clear.
- [ ] Run known-good, legally obtained NES ROMs across several mapper types and zip/non-zip imports.
- [ ] Verify 60Hz audio/frame sync, suspend/resume, audio-focus handoff, fast-forward and save SRAM/autosave/manual states.
- [ ] Verify portrait and landscape NES touch controls, simultaneous A+B, diagonals, external gamepads and gestures/cutouts.
- [ ] Verify metadata import/error recovery, offline operation, accessibility, shortcuts and favorites.
- [ ] Verify RetroAchievements only after approved native `rc_client` integration and actual eligible unlocks are accepted; do not advertise unimplemented features.
- [ ] Perform smoke testing on multiple Android versions, screen sizes and CPU ABIs.
- [ ] Sign off changelog, screenshots, download instructions, crash support and release rollback procedure.

**Release policy:** Debug artifacts can be development previews; do not call a `release` target production-ready merely because Gradle assembles it. No acceptance gates were executed while testing is paused.
