# OpenNES — November 11 mini-roadmap implementation and hardware gates

This build batch follows the user's November 11 device feedback. Distinguish
source implementation, green CI, and physical-device acceptance.

## Implemented in source

1. **Full-screen landscape controller movement:** framed and handheld PadKit
   input surfaces now occupy the full gameplay viewport; game video retains
   its original centered gutters. The original grip centers are preserved,
   while absolute drag offsets and editor constraints are viewport-based.
   Button hit areas move with the actual controls. Portrait behavior still
   requires a physical-device acceptance test.
2. **Restore Defaults:** the six-control drag editor exposes a confirmation
   action that resets the current orientation through the existing settings
   manager. Controller palette and the other orientation are separate.
3. **Onboarding colors:** a permanently visible Colors tab sits beside the
   Presets & layout tab; no hidden below-the-fold color entry.
4. **Active game management:** a cross-process session marker, guarded by the
   emulator process lock, powers Home's Running/Resume card. Choosing another
   game opens Cancel/Resume/Close & Launch. Close waits for the existing
   emulator to save, finish and release its process lock before launching.
5. **Cheat discovery:** the read-only Libretro NES Git-tree index provides
   named single-code cheats, displaying source/revision before importing.
   Unsupported codes are rejected; imported cheats remain disabled.
   The remote source is Libretro Database, CC BY-SA 4.0:
   https://github.com/libretro/libretro-database
   https://creativecommons.org/licenses/by-sa/4.0/
6. **Profile save isolation foundation:** Hardcore SRAM, state slots, autosave
   and screenshots have separate directories; existing Casual paths remain
   untouched. The game-details Hardcore entry is intentionally disabled.
   No user-facing Hardcore mode is enabled, and no official achievement
   credit can be claimed until native integration, device validation, and
   RetroAchievements approval.
7. **Regression coverage:** cheat parsing/title matching and profile
   separation have JVM tests; compile, lint and APK inspection gates run
   through the one rolling OpenNES GitHub Actions workflow.

## Device acceptance checklist (NOT automatically satisfied by green CI)

- **Controller:** drag D-pad, A, B, Start, Select, Menu individually to safe
  screen edges in landscape and portrait; use camera-notch-side grips;
  confirm exact hit targets; rotate and restart to verify persistence.
- **Reset:** customize both orientations and the theme, reset landscape
  only; verify portrait and theme remain unchanged.
- **Onboarding:** rerun onboarding at phone landscape/portrait sizes and
  verify Colors tab is visible immediately and every color remains editable.
- **Resume/switch:** launch Zelda, return Home without quitting, verify
  Running and Resume return to same session. Tap another game; verify Cancel,
  Resume current, and Close & Launch, including SRAM/state preservation.
  Force-close a game process; Home must not show a stale Running indicator.
- **Cheats:** use Find Cheats on Zelda and the Mario + Duck Hunt compilation,
  check correct source revision, import a compatible single Game Genie
  code and activate manually. Confirm unsupported multi-part codes never
  import and session switches do not carry enabled cheats.
- **Existing fixes:** NES-only library scan/filtering, reordered-article
  and compilation metadata matching, offline RomM titles/artwork,
  downloaded gameplay, undownloaded download warnings, and opt-out
  connection notices.
- **Hardcore:** unavailable by design. Verify Casual saves survive an
  over-install; do not represent the separate storage foundation as
  approved Hardcore achievement functionality.

## Known external blockers

- A user's **phone** is required for touch hitboxes, lifecycle/game
  switching, Libretro network connectivity, and RomM server verification.
- A **real fresh RetroAchievements unlock** and eventual official emulator
  Hardcore review are separate M6 acceptance gates.
