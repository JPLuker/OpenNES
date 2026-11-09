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

## Controller visual rework (requested after M5 device testing)
- Removed inherited square backplates from NES cross and face buttons.
- A+B composite input remains, with the redundant overlapping composite artwork hidden.
- Layout and input IDs remain unchanged. Build/device visual verification pending.

## Controller appearance editor
- Settings → Touch controller appearance now offers six original theme presets and ten individually editable #RRGGBB colors with live preview.
- The existing in-game editor still owns positional changes. Settings explains where to find it.
- The preference store shares one appearance across portrait and landscape; geometric layouts remain independent.

## First-game controller onboarding
- A small guided setup sheet appears once when the first NES game reaches visible touch controls, not while loading or when a physical controller is active.
- Players pick any original theme and proceed directly to drag editing, or keep the default and continue playing.
- The completion flag prevents repeated interruptions; Settings → Touch controller appearance can request setup again for the next game.
- Geometry remains independently saved by orientation; onboarding never resets the player's custom layout.
