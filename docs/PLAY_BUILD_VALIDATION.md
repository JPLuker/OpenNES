# Google Play build validation

OpenNES's public distribution target is an Android App Bundle (AAB) delivered
through Google Play. **Nothing in this workflow is a production release**.

## Source validation

The project now specifies Android compile and target API 36 and uses Android
Gradle Plugin 8.10.1 with Gradle 8.11.1. The supported release build remains
`freeBundleRelease`; inherited Play/dynamic core flavors are no longer configured.
This removes unused alternatives without changing existing debug package IDs. Development APK builds continue under
`com.jpluker.opennes.debug` with their existing debug signing identity.

The strict 16 KiB ELF/unsigned AAB structural checks passed in [CI run 43257644960](https://github.com/JPLuker/OpenNES/actions/runs/43257644960). This is build evidence, not device or Play acceptance.

Run the isolated manual GitHub workflow:
[OpenNES Play AAB Validation (Unsigned)](https://github.com/JPLuker/OpenNES/actions/workflows/play-aab-validation.yml).

It runs Python regression tests, Kotlin lint, pinned native bridge preparation,
JVM tests, NES metadata trimming and
`./gradlew :lemuroid-app:bundleFreeBundleRelease`. It verifies the resulting
AAB is a valid ZIP structure with `BundleConfig.pb`, the base manifest,
only FCEUmm libretro cores, a 64-bit ARM core, and matching emulator bridge ABIs.
Its strict pass also rejects other emulation-core binaries anywhere in the AAB, unsafe ZIP member paths, and recognizable bundled NES ROM images. Those filename checks do not replace a full content/IP audit. Each ARM64 ELF shared library's loadable segments must have 16 KiB-compatible alignment; the check rejects truncated or malformed ELF headers. That static ELF inspection is necessary but is not a runtime test on a 16 KiB-page Android device.
The workflow runs manually or when packaging Gradle files, the workflow itself, or the AAB inspector/tests change. It is intentionally not run on every gameplay source commit to avoid unnecessary release-build jobs.

The resulting artifact is named `opennes-unsigned-play-validation` and expires
in seven days. Never submit or install this **unsigned** validation build as a
normal user-facing update.

The existing `scripts/verify-apk-identity.py` separately verifies that the
rolling *debug* APK targets API 36, has the expected package/build version and
declares both required foreground-service permissions.

## Protected signed AAB validation (manual, not a release)

The separate [signed-bundle validation workflow](https://github.com/JPLuker/OpenNES/actions/workflows/play-signed-validation.yml) is manual-only, restricted to `master` and the `production-signing` GitHub Actions environment. It neither publishes a GitHub release nor uploads to Google Play. Its short-lived signed AAB artifact is a **validation artifact**, not approval to publish.

Once the app owner has created and securely backed up a **new** Play upload keystore outside Git, configure these five protected GitHub Actions environment secrets:

- `OPENNES_RELEASE_KEYSTORE_BASE64`: Base64 encoding of the binary upload keystore (one line)
- `OPENNES_RELEASE_STORE_PASSWORD`: keystore password
- `OPENNES_RELEASE_KEY_ALIAS`: upload key alias
- `OPENNES_RELEASE_KEY_PASSWORD`: upload key password
- `OPENNES_RELEASE_CERT_SHA256`: SHA-256 fingerprint of that *same upload certificate*, 64 hexadecimal characters without colons

The workflow refuses absent secrets and an unexpected certificate fingerprint, verifies Kotlin/Gradle tests, builds `freeBundleRelease` with release signing, checks the signed AAB and its 16 KiB-native layout, and writes a SHA-256 sidecar. The keystore only exists temporarily on the isolated runner and is deleted after the job. **Do not** use the tracked development `debug.keystore` or place the upload keystore in the repository. Protect the `production-signing` environment with manual reviewers before enabling the job.

The Play `versionCode` derives from the ordered `VERSION` major/minor/patch tuple (`major * 1,000,000 + minor * 1,000 + patch`). Rolling debug builds keep their separate rising Actions-run sequence and `.debug` package ID. No production submission is made until owner approval, signed Play split installation, migration testing and all remaining release gates.

## Migrating progress from the development APK

The development package `com.jpluker.opennes.debug` and production package `com.jpluker.opennes` are distinct apps. Android cannot upgrade one package ID into the other or silently copy their app-private data. OpenNES now has a source-implemented manual Settings → Transfer NES saves workflow on both variants. It exports a user-selected ZIP containing only verified Casual SRAM and FCEUmm state files, then explicitly stages and imports those files to the other variant without replacing any filename that already exists. ROMs, account credentials, app preferences and Hardcore files are not included; users must separately select/import their own ROM folder and sign in.

**Do not call the migration production-safe** before testing a complete real-device round trip, with running games closed, matching ROMs reindexed, actual save restoration verified, repeated import, cancellation and low-storage failure. Retain the original debug installation and a second external backup until restoration is confirmed.

## What remains unverified

- Google Play App Signing and a private upload key; no debug keystore or secret
  may be reused as the release key.
- Production versionCode policy, policy declarations, Data safety, privacy
  documentation, license/source availability and the Store listing.
- Physical-device 16 KiB page runtime compatibility, app behavior under
  Android 16, edge-to-edge, back navigation and display cutouts. A static ELF gate cannot prove these.
- Installation and verification of Google Play generated splits from a
  **signed** AAB on supported devices and Android ABIs.
- Separate tested export/import path for saves from the debug package to the
  production package. Installing the production app cannot silently upgrade
  `com.jpluker.opennes.debug`.
- Actual gameplay, SRAM and autosave safety, RetroAchievements and RomM
  authentication on a physical phone.

Until those pass, keep the public release blocked. Debug remains on the single
[dev-latest](https://github.com/JPLuker/OpenNES/releases/tag/dev-latest)
release, and the public app must not include an in-app APK updater.
