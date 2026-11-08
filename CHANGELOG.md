# Changelog

## Unreleased — User-controlled metadata correction

- Add an offline **Find game info** search inside game details, using the bundled NES Libretro catalogue.
- Present candidate titles and developers for explicit selection; do not automatically assign uncertain matches.
- Applying a chosen catalogue match corrects the base title, developer and box art while preserving custom names, user artwork, saved games, favorites and playtime.
- No new internet provider, account or credential required. ROM file paths and emulator-core data are not altered.

## NES artwork gallery and caching

- Display available NES title screens and gameplay screenshots in the game-details panel, using trusted URLs derived from matched Libretro box art.
- Load gallery images lazily; unavailable images disappear instead of leaving broken previews.
- Prefetch up to 24 shelf cover images into Coil's disk cache for faster repeat browsing and best-effort offline access.
- Suppress only permanent missing-thumbnail (404/410) requests; transient 429/5xx server errors remain retryable.
- Keep manual custom covers, ROMs and saves unchanged.

## Metadata enrichment foundation

- Recheck previously imported NES ROMs when their indexed title, developer, or default box art is incomplete, without requiring users to remove and reimport games.
- Cache the last metadata check per game and defer unsuccessful or incomplete repeat lookups for 30 days to avoid unnecessary work on each library scan.
- Restrict filename, CRC and serial metadata queries to NES at the SQL level and cautiously match region-tagged ROM filenames against catalogue names only when the title match is unambiguous.
- Preserve favorites, custom names, user-supplied covers, saves and cumulative playtime when enriching records.
- Add a non-destructive Room 11→12 database migration. New enrichment uses the existing bundled NES metadata; external Plex-style provider matching remains future work.

## NES audio pacing experiment (Issue #3)

- Request a display mode near 60 Hz for phone NES gameplay when available, to test whether more consistent frame pacing resolves intermittent audio cutouts on high-refresh-rate displays.
- Add a default-on **Prefer 60 Hz NES gameplay** Advanced setting, with a user-controlled opt-out for comparison. Mode selection never changes display resolution.
- Preserve the existing low-latency audio option (off by default) independently.
- **Unverified fix:** actual sound quality must be checked on affected hardware with the setting both on and off.

OpenNES-specific history; upstream Lemuroid release history is not reproduced.

## Unreleased — Library details and artwork fixes

- Tap a game on the OpenNES shelf to see its NES game-detail panel: existing cover, title, developer when available, ROM filename, last played, and recorded playtime.
- Provide direct Play and More options actions using existing functional game/context handlers.
- Switch Libretro box-art URLs to HTTPS for newly scanned games and upgrade legacy HTTP links at display time without changing custom user cover URIs.
- This uses existing indexed metadata; automatic enrichment, artwork matching beyond Libretro, and RetroAchievements remain separate ongoing work.

## Simple APK distribution

- Publish a directly downloadable `OpenNES-debug.apk` to a rolling GitHub development prerelease after every successful master build and Kotlin lint pass.
- Update the same download URL after successful builds and keep the previous known-good APK if validation fails.
- Use direct commits to `master` for routine development rather than new pull requests; retain the unsigned-production-key debug build path.

## OpenNES game library (M2, first batch)

- Introduce an original NES-inspired home library: recently played, favorites, discovery shelf, and a prominent resume-game panel.
- Add true cumulative foreground gameplay session tracking, displayed on game tiles; paused/background time is excluded, and prior sessions are intentionally reported as untracked.
- Add a Room 10→11 migration that preserves existing games and saves while initializing historic playtime to zero.
- Provide direct search and settings controls on the new home screen instead of the inherited top bar.
- Achievement progress is not fabricated: RetroAchievements remains a separate implementation milestone.

## NES-only cleanup (M1)

- Reduced the debug APK from **222,012,846** to **32,367,426 bytes** (85.4% smaller), eliminating all 76 unrelated Libretro core binaries while retaining FCEUmm across four ABIs.
- Restricted registered game systems and library discovery to NES/Famicom.
- Removed non-NES dynamic-feature builds, 23 unrelated touchscreen layouts, 20 non-NES system icons, and the multi-console navigation entry.
- Removed unnecessary core-selection and BIOS settings links.
- Fixed Home initialization for a single NES core, plus the old Game Boy default controller.
- Removed DS microphone/DeSmuME Home warnings and unused microphone permission.
- Hardened game metadata identification to ignore non-NES matches in the inherited database.
- Added CI SQLite trimming: 104,073 original records reduced to 29,246 NES records; database size 12,775,424 to 3,706,880 bytes.
- Made CI inspect and reject any non-NES packaged Libretro core.

**Not finished:** On-device verification, unused metadata/source pruning, original OpenNES UI, custom controller skins and RetroAchievements.

## 0.1.0 foundation (M0)

- Established OpenNES's app label, version, unique Android package ID, and open-source roadmap.
- Added GitHub Actions debug APK builds with independent Kotlin lint and an installable APK artifact.
- Added README, build/contribution guides, and NES-only audit.
- Removed the inherited Lemuroid publisher and corrected baseline Kotlin formatting.
