# OpenNES pre-release emulator research — 2026-11-10

**Purpose:** identify what NES players value and what annoys emulation enthusiasts before an eventual public release. This is a research backlog, not a claim that OpenNES already implements these features. No game ROMs or proprietary emulator artwork are bundled.

## Competitor baseline

| Product | Documented strengths | OpenNES implication |
|---|---|---|
| [NES.emu on Google Play](https://play.google.com/store/apps/details?id=com.explusalpha.NesEmu) | Focused NES UI, low audio/video latency, save slots, gamepads, cheat/gun support | Keep NES-only simplicity and performance; verify timing/latency rather than adding visual gimmicks |
| [Lemuroid on Google Play](https://play.google.com/store/apps/details?id=com.swordfish.lemuroid) | Automatic library scan, touchscreen layout customization, save slots, save sync, gamepads, no ads | Preserve reliable underlying functionality through the OpenNES UI refactor |
| [RetroArch official website](https://retroarch.com/) | Sophisticated shaders, achievement support, automatic controller configuration, netplay, recording | Selectively adopt player-facing functionality without the core-heavy UI |
| [Super8Plus on Google Play](https://play.google.com/store/apps/details?id=com.super8bit.free) | Editable controls and screen size, rewind, turbo/A+B, quick save/load shortcuts | Consider fast, discoverable quick actions and independent display sizing |
| [Phoenix Emu Android alpha discussion, 2026](https://www.reddit.com/r/EmulationOnAndroid/comments/1wycxe7/i_built_a_free_nessnes_emulator_for_android_with/) | Achievement presentation, per-game controller configuration, save/rewind shortcuts | Use as a recent exploratory comparison, not proof of mature production quality |

## Recurring enthusiast complaints

1. **Complicated or poorly adapted mobile navigation.** [RetroArch Android discussion](https://www.reddit.com/r/EmulationOnAndroid/comments/11dktem), [touchscreen menu feedback](https://www.reddit.com/r/EmulationOnAndroid/comments/wj7tn2), and a [2026 comparison praising PPSSPP's simpler UI](https://www.reddit.com/r/EmulationOnAndroid/comments/1wkgbmt/why_cant_emulators_have_ui_as_clean_as_ppsspp/) favor a clear game-first menu.
2. **Bad touch layouts and unnecessary controls.** Enthusiasts complain about full cross-console button layouts on NES and want layouts suited to thumbs, screen orientation and the specific game. See [RetroArch complaint thread](https://www.reddit.com/r/EmulationOnAndroid/comments/11dktem) and [2026 tip about hiding unused buttons](https://www.reddit.com/r/EmulationOnAndroid/comments/1xm96cb/reminder_if_you_use_touch_control_you_dont_have/).
3. **Insufficient customization at the game level.** Lemuroid users specifically ask for per-game external controller maps and screen scaling; see [Lemuroid alternative thread](https://www.reddit.com/r/EmulationOnAndroid/comments/15yefnb).
4. **Controller/touch mismatches and confusing shortcuts.** [Menu hotkey discussion](https://www.reddit.com/r/RetroArch/comments/1elgcj6) and [Android gesture bug reports](https://www.reddit.com/r/EmulationOnAndroid/comments/199d904) illustrate the need for visible, predictable menu access.
5. **Power features matter, but clutter is unpopular.** [RetroArch customization discussion](https://www.reddit.com/r/EmulationOnAndroid/comments/1oak14n) appreciates visual filters and tuning while criticizing the interface.
6. **Reliable saves and portable libraries matter.** [Save-state/cloud-folder discussion](https://www.reddit.com/r/EmulationOnAndroid/comments/1ii3wnn/retroarch_so_close_yet_so_far/) values consolidated saves, synchronization and controller mapping; do not ship untested bidirectional saves.

These are qualitative community examples, not statistically representative survey results.

## OpenNES priority decisions

### Ship-blocking for first public release
- NES ROM launch, audio, hardware input, autosave/manual saves, restored frame pacing and orientation acceptance on real devices.
- Controller size and actual touch hitboxes stay aligned in portrait and landscape. Handheld gameplay remains centered at usable scale.
- Shelf covers display loading/fallback art immediately; offline reopening uses cached art when available. Failed requests must not hang startup.
- Settings exposes RetroAchievements account status **before** starting a game. Native award/login support is honestly described and must be complete before advertising earning achievements.
- RomM read-only browsing/import and any advertised save synchronization are tested against a real RomM server. **RomM acceptance precedes the public release**, per project requirement.
- Secure signed production APK and documented license/source distribution; no copyrighted ROMs, unlicensed game-art bundles or fake achievements.

### Candidate enhancements (evaluate, do not silently add)
- Per-game touch/hardware presets, with a safe global fallback and clear reset.
- An optional tiny in-game quick-action ribbon for save/load, fast forward and rewind, hidden by default and never covering the NES picture.
- Display aspect/alignment controls and accessible CRT/scanline previews without compromising video readability.
- Touch targets calibrated for thumb reach, including separate portrait/landscape setups, accessibility sizing and haptics.
- Optional cheats, turbo/A+B, screenshot functionality and Bluetooth-controller mapping once core reliability is measured.
- A floating/thumb-following D-pad is an experimental accessibility option, not a default; see [mobile D-pad feedback](https://www.reddit.com/r/EmulationOnAndroid/comments/12s83nt).

### Explicit non-goals for first public release
- Multiple console cores, a RetroArch-style settings tree, ads, tracking, ROM discovery/download sources or game art from unofficial bundles.
- Netplay, recording and extensive shader stacking unless basic reliability and game compatibility are proven.

## Acceptance process
1. Finish the phone-level M4/M5 test matrix with multiple games, rotation, hardware controllers and resume/save-state tests.
2. Test online/offline cover loading; distinguish an unmatched art title from a transient network error.
3. Perform a genuine RetroAchievements account / valid-game / unlock flow once native client integration exists; use an actual unlock rather than simulated badges.
4. Test a RomM server with interrupted imports, duplicate ROMs, invalid ZIPs, saved credentials and backup integrity.
5. Revisit the candidate enhancements with screenshots, device feedback and issue frequency. Do not label this research an implementation commitment.

**Research status:** initial desk review complete on 2026-11-10; hardware acceptance and feature decisions remain open.
