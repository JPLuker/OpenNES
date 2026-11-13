# Things learned from OpenNES

This log records fixes and lessons supported by OpenNES source, CI or reported device behavior. It is not a list of claims that Android acceptance has passed. Cross-project lessons can later be reviewed for incorporation into InternalDesignGuidelines/LESSONS.md.

## 1. A passing build cannot prove a usable touch layout

**Problem:** A row of weighted launch actions squeezed “Play Casual” into multiple one-letter lines on a phone. Other controller controls were limited by layout bounds and camera cutouts.

**Change:** Use a full-width Play action with a separate ROM-specific Casual/Hardcore selector; keep controller geometry and hit-testing separate per orientation.

**Check:** Build/test responsive layouts with large fonts, then verify visible controls and actual touch behavior on a device. Do not infer visual correctness from Kotlin compilation.

## 2. A paused game cannot rely on its active graphics thread to save and close

**Problem:** “Close & launch” could fail when the emulator Activity had paused and a synchronous save depended on the emulation thread.

**Change:** Keep quit requests pending through busy operations and use the background-safe save path when closing an inactive game.

**Check:** Switch ROMs, inspect persisted SRAM/autosaves, and verify failure leaves the original session intact. Real hardware acceptance remains open.

## 3. Storage providers and networks may stop making progress

**Problem:** Repeated zero-byte reads can spin forever when hashing, backing up or restoring a save. Unbounded metadata responses can consume excessive memory.

**Change:** Bound zero-progress retries and response sizes, reject malformed results, and add tests for stalled streams and transient zero reads.

**Check:** Exercise corrupt, stalled and truncated inputs, plus a valid retry. No partial result may be reported as a verified backup.

## 4. RomM ownership must bind both server and account

**Problem:** A hostname alone cannot distinguish two users on the same RomM server, especially when using queued offline save requests.

**Change:** Require a verified numeric account identity tied to the server fingerprint and credential revision. Account-bound archives and requests never authorize automatic uploads or overwrites.

**Check:** Test account switching and same-ROM identities, preserve legacy backups for review, and fail closed when identity cannot be verified.

## 5. Cancelling a UI result is not the same as cancelling the request

**Problem:** A metadata search could outlive Fix Match and continue its network work, even when late callbacks were ignored.

**Change:** Use cancellable HTTP requests and return coroutine Jobs so the UI can cancel search/preview work on edit, retry or dismissal.

**Check:** Confirm closing the dialog cancels the request; a late response must not overwrite a later query. Physical-device timing is still pending.

## 6. Pushes need preflight checks and increasing Android versions

**Problem:** Import-order lint failures blocked several builds, and debug APKs previously reused `versionCode = 1`.

**Change:** Follow Kotlin import ordering, test the actual CI compiler path, derive rolling debug codes from the GitHub Actions run number, and inspect packaged identity before publishing.

**Check:** Negative identity tests, the full CI result and an Android in-place upgrade retaining saves.

## 7. Encrypted credentials still need bounded file reads

**Problem:** A file can grow or become malformed before an encrypted account store reads it. Checking size only after `AtomicFile.readFully()` has already allocated the bytes.

**Change:** Read no more than the accepted encrypted blob capacity before decrypting in the RomM and both RetroAchievements account stores, and reject stalled streams.

**Check:** Exact-limit, oversized, stalled and transient-zero-read tests. Existing account formats and save data stay unchanged.

## 8. Move the thing the library hit-tests, not a number inside it

**Problem:** Dragging A or B changed an anchor inside a fixed touch-control dial. The library only accepts touches inside the dial, so the buttons lagged the finger (the drag was divided by a guessed constant) and stopped at the dial's edge, while the saved value kept growing.

**Change:** Read the dependency's source to find the real hit-test rule, then compute button positions in real pixels in one plain object and place the control itself there, so paint and touch share one rectangle. Give saved data a layout version so values written under the old meaning are dropped once instead of being reinterpreted.

**Check:** Unit tests that a drag of N pixels moves the button N pixels with no dial limit, that the other button stays put, and that old saved values cannot place a button off screen. Feel and reach still need a phone.

## 9. An explicit placeholder can hide the cache you meant to show

**Problem:** Covers flashed a generated title card on every navigation. The image request asked for the cached bitmap as its placeholder, but an explicit placeholder painter passed to the composable replaced it, and each screen requested a different size, so the cache entry was rejected as too small.

**Change:** Decode every cover at one shared size, and pass the title-card placeholder only when the cover has never been decoded.

**Check:** Scroll, open details, go back and search on a phone; a cover seen once must not flash.
