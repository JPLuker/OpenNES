# Google Play build validation

OpenNES's public distribution target is an Android App Bundle (AAB) delivered
through Google Play. **Nothing in this workflow is a production release**.

## Source validation

The project now specifies Android compile and target API 36 and uses Android
Gradle Plugin 8.10.1 with Gradle 8.11.1. Development APK builds continue under
`com.jpluker.opennes.debug` with their existing debug signing identity.

The strict 16 KiB ELF/unsigned AAB structural checks passed in [CI run 43257644960](https://github.com/JPLuker/OpenNES/actions/runs/43257644960). This is build evidence, not device or Play acceptance.

Run the isolated manual GitHub workflow:
[OpenNES Play AAB Validation (Unsigned)](https://github.com/JPLuker/OpenNES/actions/workflows/play-aab-validation.yml).

It runs Python regression tests, Kotlin lint, pinned native bridge preparation,
JVM tests, NES metadata trimming and
`./gradlew :lemuroid-app:bundleFreeBundleRelease`. It verifies the resulting
AAB is a valid ZIP structure with `BundleConfig.pb`, the base manifest,
only FCEUmm libretro cores, a 64-bit ARM core, and matching emulator bridge ABIs.
Its strict pass also requires each ARM64 ELF shared library's loadable segments to have 16 KiB-compatible alignment; the check rejects truncated or malformed ELF headers. That static ELF inspection is necessary but is not a runtime test on a 16 KiB-page Android device.
The resulting artifact is named `opennes-unsigned-play-validation` and expires
in seven days. Never submit or install this **unsigned** validation build as a
normal user-facing update.

The existing `scripts/verify-apk-identity.py` separately verifies that the
rolling *debug* APK targets API 36, has the expected package/build version and
declares both required foreground-service permissions.

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
