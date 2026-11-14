# OpenNES — phone test checklist

One sitting, about 30 minutes plus the RomM part. This replaces chasing
individual fixes: everything that can only be judged on a phone is here.

**Before you start**

- Install the newest [dev-latest APK](https://github.com/JPLuker/OpenNES/releases/download/dev-latest/OpenNES-debug.apk) **over** the existing one. Do not uninstall or clear data: surviving the upgrade is part of the test.
- Note the version in **Settings → About → Version** and report it with the results.
- For each line, reply with the number and pass or fail. A sentence on what you saw is enough for a failure.

## A. Still works after the upgrade (5 min)

1. The app opens to a loading screen and then the library, never an empty shelf first.
2. Existing saves, favorites, play time and controller colors are unchanged.
3. Two different games start with picture and sound. Pause, quit and relaunch one of them: battery saves and autosave are intact.

## B. Fixes for what failed last time (10 min)

4. **Controller editor, A and B.** In a game: pause → edit controls → drag. Drag A somewhere far from B (for example up beside the screen). The button graphic follows your finger and stops only at the screen edge.
5. Leave the editor and press A where it now is: the game responds. Press where A used to be: nothing happens.
6. Drag A back next to B. Both still work, and pressing between and just above or below them gives A+B together.
7. Drag the D-pad, Start, Select, Menu, and the rewind and fast-forward buttons beside Menu. Each graphic moves, and each still works where it now sits. Rewind and fast-forward never cover Menu.
8. Rotate the phone. Landscape has its own layout and the portrait one is unchanged when you rotate back. Restore defaults resets only the current orientation.
9. Close and reopen the game: the layout was saved.
10. **Covers.** Scroll the shelves, open a game's details, go back, open search, go back. Covers stay put: no reload flash, no black boxes.
11. **Fix Match.** On Zelda, use Fix Match and pick Zelda II. The name, description and cover all change to Zelda II. Then fix it back.
12. A game with a cover you chose yourself keeps that cover after Fix Match.

## C. Never tried on a phone (10 min)

13. **Rewind.** Turn on **Settings → Advanced → Casual rewind**, play for ten seconds, hold the ⏪ button beside Menu: the game steps backward. Sound recovers afterward. Saving and loading still work after a rewind.
14. **Zapper.** Long-press Duck Hunt → turn on **Zapper touchscreen**, then start it. Tapping a duck on the picture hits it; tapping outside the picture does nothing. Another game with Zapper off plays normally.
14b. **Fast-forward.** Tap ⏩ beside Menu: the game runs at double speed; tap again for normal speed. The pause menu no longer has its own rewind or fast-forward entries.
15. **Cheats.** Pause → Cheats: add a Game Genie code, turn it on, see the effect, turn it off. Open a different game: the code is not there.
16. **D-pad direction tuning** (fine-tune sliders): nudge one direction's touch area. Diagonals still work.
17. **External gamepad**, if you have one: touch controls hide when it connects, return when it disconnects, and no button stays stuck.
18. **Notch and gesture edges:** controls placed near the camera cutout and the bottom gesture bar are fully visible and respond.
19. **Large font setting:** with Android font size at maximum, the editor bar and game details are still usable.

19b. **Haptics (#44).** Settings → Touch feedback = *Press*: every on-screen control gives one click when pressed (D-pad, A, B, A+B together, Start, Select, Menu, rewind, fast-forward). Set *Press & release*: a lighter tick also on lifting. Set *None*: nothing vibrates. Change the setting while a game is in the background, return to it: the new setting applies without restarting the game. Rotating the phone or opening the editor never vibrates by itself.

## D. RetroAchievements (5 min, needs your account)

20. Sign in. Start a supported game: the achievement list and your past unlocks show.
21. Earn one new achievement. The banner appears, and the unlock shows on the RetroAchievements website afterward.
22. Hardcore is shown as unavailable, not as a working option.

## E. RomM (needs your server on the network)

Before starting: in RomM, create a new client token with **assets.read, assets.write, devices.read, devices.write, me.read, platforms.read, roms.read** and connect OpenNES with it (Settings → RomM).

23. NES games on the server appear with the download badge on the cover.
24. Download one. Progress shows, then the game becomes playable and the badge is gone.
25. Turn Wi-Fi off and try a download: a clear reason and **Retry download** appear beside the button. Turn Wi-Fi on and retry: it works.
26. **Turn on sync.** Settings → RomM → Save sync → switch on. It finishes with "Last synced just now". In RomM's web UI the phone appears under your devices.
27. **Phone → server.** Play a game with battery saves (e.g. Zelda), save in-game, quit. Within a minute the save appears in RomM for that game (slot `autosave`).
28. **Server → phone.** Play the same game in RomM's web player in a browser and save in-game, then close the web player. On the phone, tap Play on that game: the newer save loads (a "Checking RomM" toast may show briefly). The old autosave state is not restored over it.
29. **Conflict.** Turn Wi-Fi off, play the game on the phone, save in-game and quit. Make different progress in RomM's web player. Turn Wi-Fi back on and tap **Sync now**: the game appears under **Choose which save to keep**. Try **Use RomM save**: the phone's save is replaced and a backup appears in the backups list. Repeat and try **Keep phone save**: RomM shows the phone's save as the newest version.
30. **First sync with existing saves.** For a game that already had a different save on the phone and on RomM before sync was turned on, the first sync asks instead of picking one.
31. **Offline launch.** With the server off, tap Play: the game starts within about 6 seconds using the phone's save.
32. **Never while playing.** Start a game, switch to the library and tap Sync now: the running game's save is not changed.
33. Close and reopen the app offline: previously seen RomM titles are still listed, marked offline.

## F. Settings → About

34. Version, source code, open-source notices and privacy policy rows are present and the three links open.
35. **TalkBack** (optional): with TalkBack on, the rewind and fast-forward buttons are read out by name, and the switch-games, download-failed and restore-defaults dialogs read normally.

## What a result means

- **All of A–C pass:** the app is ready for the Play internal-testing track once signing is set up.
- **D21 passes:** RetroAchievements Casual can be advertised.
- **E passes:** the RomM gate for the public release is met, including two-way save sync.

### Save-state slots and exit sync (added 2026-11-14)

34. Play a battery-save game, save in-game, quit to the library and wait about 30 seconds without tapping Sync now. RomM shows the new save.
35. Save to state slot 1, quit. RomM lists `<game>.state1` for the game.
36. On a second device (or after deleting the slot's files), sync: slot 1 appears and loads.
37. Tap Play with the server unreachable: "Starting game…" shows at once and the game opens within about 6 seconds.
