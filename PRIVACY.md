# OpenNES privacy policy

_Last updated: 14 November 2026. Applies to the OpenNES Android app._

OpenNES is an open-source NES emulator. It has no OpenNES account, no
advertising, no analytics and no crash-reporting service. The developer runs
no server and receives no data from the app.

## What stays on your device

- Your game files (ROMs), save files and save states.
- Your library: titles, covers you choose, favorites and play time.
- Controller layout, colors, cheats and other settings.
- Sign-in tokens for the optional services below. They are encrypted with a
  key held by the Android Keystore and are excluded from Android backups.

Nothing in this list is sent anywhere unless you use one of the optional
features described next.

## Connections the app can make

Playing a game you imported needs no internet connection. The app only
connects to these services, and only for the purpose shown.

| Service | When | What is sent |
| --- | --- | --- |
| Libretro thumbnails (`thumbnails.libretro.com`) | Showing box art, title screens and screenshots for games in your library | The catalogue name of the game, as part of the image address |
| Wikidata and Wikipedia (`www.wikidata.org`, `en.wikipedia.org`) | Looking up genre, release year and description. This runs automatically after a library scan unless you turn it off in **Settings → Library maintenance**, and when you use Fix Match | The cleaned game title, and the ID of a matching Wikidata record |
| RetroAchievements (`retroachievements.org`) | Only after you sign in to RetroAchievements | Your RetroAchievements username and sign-in token, an identifying hash of the game you start, achievement progress and activity status |
| Your RomM server (address you enter) | Only after you connect a RomM server | Your RomM sign-in for that server, and requests to list and download your NES games and the save backups you ask for. If you turn on **Save sync**, also your in-game save files (battery saves) and a random device name for this phone |
| GitHub (`api.github.com`, `raw.githubusercontent.com`) | Only when you search the Libretro cheat catalogue from the Cheats screen | The game name used to find a cheat file |

Game files are never uploaded. Save files are uploaded only to your own RomM
server, and only after you turn on **Settings → RomM → Save sync**. Save states
are never uploaded. Before a save on the phone is replaced by one from RomM, the
phone's copy is backed up on the phone.

Each of these services sees your IP address and ordinary connection details,
as any website does, and handles them under its own privacy policy. A RomM
server is run by you or by someone you chose.

## Permissions

- **Internet / network state:** the connections listed above.
- **Notifications:** progress of library scans and a running game.
- **Foreground service:** keeps a running game and its save data safe while
  the app is in the background.
- **Vibration:** touch-control feedback.
- **Camera:** only if you choose to scan a RomM pairing QR code. Images are
  processed on the device and not stored.
- **Storage:** reading the game folder you select.

## Children

OpenNES does not collect personal information from anyone, including
children.

## Deleting your data

Uninstalling the app, or clearing its storage in Android settings, removes
everything the app stored. Sign out of RetroAchievements or disconnect RomM in
Settings to delete those tokens. Data held by RetroAchievements or your RomM
server is managed through those services.

## Changes and contact

Changes to this policy are published in this file with a new date. Questions
and requests: open an issue at
<https://github.com/JPLuker/OpenNES/issues>.
