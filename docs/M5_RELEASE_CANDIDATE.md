# M5 release candidate (source and build gates)

Branch: `release/m5-candidate` created from the M5 gameplay reliability milestone; **not** from the newer, unfinished M6–M8 code. These later features do not ship in this candidate.

## Included
- M0 branding and NES-only FCEUmm app identity
- M1 console/TV UI cleanup
- M2 NES library UI, home shelves, game details/search, favorites and settings
- M3 and M4 portrait/landscape NES controls with saved independent positions and visual styles
- M5 quick-save/load truthful feedback, save slot errors, reliable Quit single-flight, and auto-save restoration failure reporting

## Status and limitations
- The candidate is a **debug-signed APK**, not a production release. A successful GitHub Actions build and lint run verifies compilation and source format, not gameplay on hardware.
- Hardware acceptance still requires ROM loading, audio, 60Hz pacing, touch controls, D-pad diagonals, SRAM, save-state round-trip, pause/resume, rotation, keyboard and gamepad tests.
- The candidate receives an isolated prerelease tag `m5-preview`. It does not overwrite `dev-latest` or change the newer `master` branch.
- Do not claim verified compatibility until the Android APK can be installed and exercised on a real device.
