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
7. Drag the D-pad, Start, Select and Menu. Each graphic moves, and each still works where it now sits.
8. Rotate the phone. Landscape has its own layout and the portrait one is unchanged when you rotate back. Restore defaults resets only the current orientation.
9. Close and reopen the game: the layout was saved.
10. **Covers.** Scroll the shelves, open a game's details, go back, open search, go back. Covers stay put: no reload flash, no black boxes.
11. **Fix Match.** On Zelda, use Fix Match and pick Zelda II. The name, description and cover all change to Zelda II. Then fix it back.
12. A game with a cover you chose yourself keeps that cover after Fix Match.

## C. Never tried on a phone (10 min)

13. **Rewind.** Turn on **Settings → Advanced → Casual rewind**, play for ten seconds, hold the rewind button: the game steps backward. Sound recovers afterward. Saving and loading still work after a rewind.
14. **Zapper.** Long-press Duck Hunt → turn on **Zapper touchscreen**, then start it. Tapping a duck on the picture hits it; tapping outside the picture does nothing. Another game with Zapper off plays normally.
15. **Cheats.** Pause → Cheats: add a Game Genie code, turn it on, see the effect, turn it off. Open a different game: the code is not there.
16. **D-pad direction tuning** (fine-tune sliders): nudge one direction's touch area. Diagonals still work.
17. **External gamepad**, if you have one: touch controls hide when it connects, return when it disconnects, and no button stays stuck.
18. **Notch and gesture edges:** controls placed near the camera cutout and the bottom gesture bar are fully visible and respond.
19. **Large font setting:** with Android font size at maximum, the editor bar and game details are still usable.

## D. RetroAchievements (5 min, needs your account)

20. Sign in. Start a supported game: the achievement list and your past unlocks show.
21. Earn one new achievement. The banner appears, and the unlock shows on the RetroAchievements website afterward.
22. Hardcore is shown as unavailable, not as a working option.

## E. RomM (needs your server on the network)

23. Connect to the server. NES games on the server appear with the download badge on the cover.
24. Download one. Progress shows, then the game becomes playable and the badge is gone.
25. Turn Wi-Fi off and try a download: a clear reason and **Retry download** appear beside the button. Turn Wi-Fi on and retry: it works.
26. Download a save backup from the server and export it. No save on the phone or the server is changed without you asking.
27. Close and reopen the app offline: previously seen RomM titles are still listed, marked offline.

## F. Settings → About

28. Version, source code, open-source notices and privacy policy rows are present and the three links open.

## What a result means

- **All of A–C pass:** the app is ready for the Play internal-testing track once signing is set up.
- **D21 passes:** RetroAchievements Casual can be advertised.
- **E passes:** the RomM gate for the public release is met for the download-and-backup scope.
