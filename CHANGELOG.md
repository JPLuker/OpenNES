# Changelog

## [Unreleased]

- Linked Fix Match and online metadata lookup cancellations to their running coroutine jobs, stopping network work when search UI is dismissed or changed.
- Documented the security boundary, privacy/network endpoints, proof-based device test matrix and developer handoff expectations.

- Version each CI debug APK using the workflow run number and a single `VERSION` source file; reject APKs whose built package ID, versionCode or versionName differs before publishing.
- Keep the debug signing identity and package ID stable across upgrades; release signing remains separate.


## Unreleased — Library box art and conditional Favorites

- Standardize box art at the original NES retail package proportions (5 × 7.125 inches) across the featured game, library shelves, search, and details.
- Fit the full cover in its frame rather than cropping the printed art.
- Remove the redundant tagline beneath Your NES Library.
- Show the Favorites shelf and bottom navigation destination only when at least one NES game is favorited.
- Update Favorites navigation reactively when favorites are added or removed; return to Home if the last favorite is removed while that destination is open.


## Unreleased — Automatic NES library enrichment

- Automatically enqueue rate-limited, network-constrained metadata jobs after ROM discovery without blocking the emulator or the ROM import process.
- Look up one verified NES Wikidata entry, then use that exact entity's English Wikipedia sitelink to retrieve an attributed article summary.
- Persist description/genre/year and Wikipedia article attribution in Room schema 16; migrate existing games without losing saves, favorites, custom art or edited text.
- Restore eligibility for previously unmatched or short-description games when upgrading so cached negative results do not prevent new enrichment.
- Process at most eight games per background worker, queue additional batches for larger libraries, and retry transient service failures with backoff.
- Add a default-on automatic lookup switch under Settings → Library maintenance; manual refresh remains available and the Home screen has no maintenance panel.
- Display descriptions on Continue Playing and show clickable Wikimedia source attribution in game details.


## Unreleased — Direct on-screen controller dragging

- Add a drag-edit mode launched from the existing controller editor. The D-pad and A/B clusters receive visible outlines and can be moved by touch.
- Preview movement immediately without writing preferences on every pointer update; persist the final position when a drag ends.
- Suppress virtual gameplay input while drag-edit mode is active, and support Done, Android Back and Fine-tune (return to sliders).
- Keep previously configured per-orientation and per-side size and position values. Rotation exits drag mode to prevent editing the wrong orientation.
- The original PadKit controls remain in place for normal NES gameplay and external controller handling.
- Direct dragging is bounded by the existing cluster offset limits; device ergonomics and gesture reliability still require testing.

## Independent NES controller positioning

- Give the D-pad/Select and A/B/Start clusters separate live size, horizontal, and vertical position controls.
- Display actual horizontal and vertical movement in dp instead of unlabeled sliders; support positive and negative offsets with bounded movement.
- Save the left and right clusters independently for portrait and landscape, retaining older saved shared scale and margin settings.
- Allow each cluster to reset independently without changing the other, with existing per-orientation full Reset unchanged.
- Move the underlying PadKit controls with their visual elements so touch hit regions track adjustments.
- Direct on-screen dragging remains future work and real-device layout verification is still needed.

## NES touch controller redesign (first milestone)

- Replace the inherited virtual controller appearance with an original warm-gray/charcoal/deep-red NES-inspired material palette.
- Draw raised cross-shaped D-pad segments, red A/B buttons and capsule-style Start/Select controls while keeping existing PadKit button/axis mappings intact.
- Add an opacity slider in the in-game control editor; visual opacity does not shrink or change the input hit regions.
- Improve independent portrait and landscape controller starting scales, while preserving previously saved per-orientation adjustments.
- Existing menu, multi-touch behavior, external gamepad support, and saved layouts remain handled by the upstream input framework.
- Device testing and deeper independently positioned/dragged control layout customization remain outstanding.

## Bounded library metadata queue

- Add explicit library-wide **Check missing details** action with progress and Stop controls; no unsolicited API requests at startup.
- Process at most 12 eligible NES games per tap, sequentially, with at least a 2.5-second pause between distinct lookups.
- Cache successful/unmatched game checks for 30 days in Room schema 15, and deduplicate identical titles within a batch.
- Only fill blank genre, year and description fields when Wikidata returns one verified NES match; preserve manually entered details, games, saves and playtime.
- Stop a batch on unavailable/rate-limited API responses instead of repeatedly retrying; network failures are not cached as successful checks.
- Process is scoped to the Home ViewModel and ends when the user stops it or the screen is destroyed. No background sync service.

## Verified online NES information

- Add key-free, user-requested lookup from Wikidata's CC0 game catalogue for matching NES titles.
- Verify an exact normalized title, the Wikidata video-game type and the Nintendo Entertainment System platform before suggesting an online result; ambiguous records are rejected.
- Show candidate genre, first publication year and short description before the player confirms the match.
- Fill only empty profile fields via a targeted database update. Existing descriptions, genre, dates, custom names, artwork, ROM paths, playtime and saves remain untouched.
- Store and display a link to the source Wikidata record, removable when the user edits those profile fields manually.
- Add a non-destructive Room 13→14 migration for source provenance; remote provider failures do not block emulator operation.
- This is an explicit lookup feature, not yet an unattended library-wide metadata scraper. The bundled NES catalogue remains the first line of ROM identification.

## Game profile information

- Add optional genre, release year and description fields to each NES game, including a native edit/save interface in game details.
- Preserve existing records through Room 12→13 migration; missing fields remain blank rather than displaying invented metadata.
- Use targeted SQL updates for both personal game details and selected catalogue matches to avoid overwriting concurrent playtime, favorite status or custom artwork.
- Keep existing game saves, ROM paths, custom covers and title overrides untouched.
- External enrichment of these fields is not yet implemented; they can be entered manually until a verified provider is connected.

## User-controlled metadata correction

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
