# OpenNES — Production release gate

**Status:** Not release-approved. This is an implementation checklist, not evidence of a completed test.

## Secret custody and build provenance

- [ ] Create a unique OpenNES release keystore off-repository, secure a backup and record certificate fingerprints privately.
- [x] Prepare a **manual** signed Play AAB validation workflow bound to the protected `production-signing` environment, with no release/Play publishing and no debug-key fallback.
- [ ] Supply a unique private upload key, keystore credentials and a separately pinned SHA-256 upload-certificate fingerprint as protected environment secrets; configure required environment reviewers, then run the signed validation workflow. This has **not** been executed.
- [x] Derive production versionCode monotonically from the `VERSION` semantic tuple, independent of the increasing Actions-run debug code.

- [x] Remove Lemuroid's inherited hardcoded release credentials. Without all four `OPENNES_RELEASE_*` environment variables, release signing is not configured.
- [ ] Verify all release environment variables come from protected CI secrets or a private interactive shell; avoid logging them.
- [x] Pin the four GitHub Actions families in the development APK workflow to their verified tag commits.
- [ ] Pin all native and Gradle dependencies to reviewed versions and verify full imported-code provenance.
- [ ] Review GitHub Actions least-privilege permissions and prevent untrusted release publishing.
- [x] Add a verified SHA-256 sidecar to rolling debug release assets (workflow source; not manually run).
- [ ] Generate and verify reproducible signed production artifacts and archived manifest/SHA-256/checksums.

## Google Play distribution (required before 1.0)

- [ ] **API 36+ target SDK** for new phone apps: the source now targets/compiles API 36 and debug CI checks its packaged target. CI build success and Android 16 edge-to-edge, foreground-service and native behavior still require verification.
- [ ] **Release Android App Bundle (`.aab`)** containing only FCEUmm, with correct ABI and 16 KiB page support. Unsigned AAB structural and 16 KiB arm64 ELF segment checks passed in CI run 43257644960; physical 16 KiB-page runtime testing, release signing and installed Play-generated splits are still unverified. See [Play build validation](PLAY_BUILD_VALIDATION.md).
- [ ] **Play App Signing:** configure a private upload key and permanent production ID `com.jpluker.opennes`; never use or publish the repository debug key.
- [ ] Google Play internal/closed testing, Data safety, privacy policy, content rating, permissions, Store listing, versionCode progression, crash/ANR and source-license compliance.
- [ ] Audit third-party ROM/game imagery and screenshots used in the app/Store listing. Ship no commercial ROMs and do not use unlicensed Nintendo imagery as official app branding.
- [ ] **Device-accept NES save migration:** explicit user-selected ZIP export/import for Casual `.srm` and FCEUmm save states is source implemented, with verified SHA-256 manifest, non-overwrite import, game-process locking and unit tests. Full debug-to-Play device round trip, restored game/ROM associations, power-failure recovery and non-data-loss acceptance are unverified; the separate production package cannot update `.debug` in place.
- [ ] **No in-app updater, self-download or unknown-sources APK installation** in public builds; Play Store manages updates. The rolling GitHub APK remains developer-only.

## Static package validation

### Implemented release inspection safeguards

- [x] The supported build variant is `freeBundle` only. The old Play/dynamic variant definitions and unused app-extension module are no longer in the configured app project.
- [x] APK and AAB inspectors reject hidden Libretro core libraries, recognizable bare NES/FDS/NSF ROM images and ZIP paths with traversal or platform-incompatible separators. Synthetic fixtures test these failure paths.
- [x] The isolated unsigned AAB workflow is triggered by changes to the packaging variant and AAB inspection scripts, in addition to explicit manual runs.
- [ ] Audit all transitive resources, archives, third-party cover art and source licenses. Static filename checks **cannot** establish ownership of artwork or catch all disguised commercial ROMs.
- [ ] Compare the final production release bundle and Google Play generated APK splits with the signing, source and native ABI requirements after release credentials are supplied.

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
- [ ] Verify independently editable D-pad hit regions and safe per-orientation defaults, preserving diagonals and multitouch.
- [ ] Verify Casual-only rewind under save restoration, low memory, ROM switching and Hardcore policy; prove no save corruption.
- [ ] Verify touchscreen Zapper in Duck Hunt and another title, including real-viewport mapping and per-ROM persistence.
- [ ] Verify metadata import/error recovery, offline operation, accessibility, shortcuts and favorites.
- [ ] Audit all user-facing screens for redundant guidance, repetitive disclaimers, internal/developer terminology, unnecessary empty-state filler and placeholder copy; remove it or replace it with concise actionable status. Keep essential safety and permission information accessible without cluttering ordinary game screens.
- [ ] Verify RetroAchievements only after approved native `rc_client` integration and actual eligible unlocks are accepted; do not advertise unimplemented features.
- [ ] Perform smoke testing on multiple Android versions, screen sizes and CPU ABIs.
- [ ] Sign off changelog, screenshots, download instructions, crash support and release rollback procedure.

**Release policy:** Debug artifacts can be development previews; do not call a `release` target production-ready merely because Gradle assembles it. No acceptance gates were executed while testing is paused.
