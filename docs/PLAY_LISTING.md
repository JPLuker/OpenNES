# Google Play listing — answers ready to paste

Drafted from the source on 2026-11-13. Check each answer against the build you
submit; change this file when the app's behavior changes.

## Store listing

- **App name:** OpenNES
- **Short and full description:** `fastlane/metadata/android/en-US/`
- **Category:** Games → Arcade (emulators have no category of their own)
- **Privacy policy URL:** <https://github.com/JPLuker/OpenNES/blob/master/PRIVACY.md>
- **Screenshots:** none yet. The inherited Lemuroid screenshot and TV banner were removed because they showed another app. Take at least two phone screenshots of OpenNES (library, and a game with the touch controller) using a homebrew or public-domain game, not a commercial title.
- **App icon / feature graphic:** must be original OpenNES artwork with no Nintendo logos or controller photos.

## Data safety form

| Question | Answer | Why |
| --- | --- | --- |
| Does the app collect or share user data? | **No data collected, no data shared** | The developer operates no server and receives nothing. Requests go straight from the device to services the user turns on |
| Is data encrypted in transit? | Yes, with one exception to disclose in the description of RomM | All built-in services use HTTPS. A RomM server on a private home network may be reached over HTTP only after an explicit warning |
| Can users request deletion? | Not applicable | Nothing is held by the developer |

Google treats data sent to a third party **at the user's direction** (signing in
to RetroAchievements, connecting to the user's own RomM server) as not
"shared". If a reviewer asks, the complete list of connections is in
`PRIVACY.md`.

One point to decide before submitting: game-detail lookups (Wikidata and
Wikipedia) run automatically after a library scan unless switched off. They
send game titles, which are not personal data, so the answer above stands.
If you would rather make that opt-in for the Play build, it is a one-line
default change in `NesAutoMetadataScheduler`.

## Permissions declaration

| Permission | Reason to give |
| --- | --- |
| `FOREGROUND_SERVICE_DATA_SYNC` | Keeps a running game alive and its save data flushed to storage while the app is briefly in the background |
| `POST_NOTIFICATIONS` | Library scan progress and the running-game notification |
| `CAMERA` (added by the QR library) | Optional scan of a RomM pairing QR code; never required |

No restricted permissions (all-files access, SMS, call log, location, package
install) are requested.

## Content rating questionnaire

- Category: **Utility / Other** (an emulator contains no game content itself).
- Violence, sexual content, profanity, drugs, gambling: **No** to all; the app ships no content.
- User-generated content or user-to-user communication: **No**.
- Shares location or personal information: **No**.

## App content declarations

- **Ads:** No.
- **Target audience:** 13 and over is the simplest accurate choice; the app is not designed for children.
- **News, COVID, financial, health, government:** No.
- **Data deletion URL:** not required (no accounts).

## Policy notes for an emulator

- The listing and the app must not include or link to commercial game files, and screenshots must not show commercial games.
- Describe compatibility in words ("NES and Famicom games you own"); do not use Nintendo logos, the NES console or controller as branding.
- GPL-3.0 source must stay public at the URL shown in Settings → About.
