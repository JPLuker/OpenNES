# Draft: message to RetroAchievements (RAdmin) requesting Hardcore review

Send from the developer's RetroAchievements account via on-site message to **RAdmin** once the device checklist in `HARDCORE_COMPLIANCE_AUDIT.md` passes. Fill in the bracketed parts.

---

**Subject:** Emulator Hardcore compliance review request: OpenNES (Android, NES)

Hello,

I'm the developer of OpenNES, an open-source (GPL-3.0) NES/Famicom emulator for Android. I'd like to ask for a Hardcore compliance review.

- **Source:** https://github.com/JPLuker/OpenNES
- **Test build:** [link to the APK you want reviewed]
- **Core:** FCEUmm (libretro), through LibretroDroid
- **Integration:** rcheevos `rc_client` [version], game-owned client, standard libretro NES memory map
- **User agent:** `OpenNES/[version] (Android) rcheevos/[version]`. OpenNES has always identified itself with this name and has never used another client's user agent.
- **Lineage:** OpenNES is a fork of Lemuroid (public since 2019), reduced to NES only. First public OpenNES build: [date]. Could you confirm whether the parent emulator's history satisfies the six-month availability rule?
- **Monetization:** none. No ads, purchases or paid features.
- **Privacy policy:** https://github.com/JPLuker/OpenNES/blob/master/PRIVACY.md
- **Licenses:** https://github.com/JPLuker/OpenNES/blob/master/THIRD_PARTY_NOTICES.md

How Hardcore works in OpenNES:

- Hardcore is chosen per game before launch and always boots the game fresh; it cannot be turned on mid-session. Switching to Casual mid-session is allowed.
- In Hardcore: loading save states, rewind, cheats and the Game Genie option are disabled at the emulator level. Creating save states and fast-forward stay available. There is no slow motion, frame advance, memory viewer, scripting or input playback in the app.
- Leaving the app while playing drops the session to Casual.
- The current mode is shown when a game starts; leaderboard attempts, challenge indicators and measured progress are shown during play; the achievement list is in the pause menu.
- Unlocks that fail to send are retried by rc_client while the game is running.

Until approved, release builds cannot enter Hardcore at all. Only the test build has a clearly labeled switch (Settings → Advanced → Hardcore → Test build only) so the mode can be reviewed.

Two things I'd like your guidance on: whether pending unlocks must survive closing the game, and whether dropping to Casual on app background is what you expect.

Thank you,
[name / RetroAchievements username]
