# Contributing to OpenNES

OpenNES is a GPL-3.0 open-source Android NES emulator project built from Lemuroid. The project is under active restructuring; consult [ROADMAP.md](ROADMAP.md) before opening substantial new feature work.

## Scope

- **NES/Famicom only.** Non-NES systems should be removed, not expanded.
- The **complete UI will be rewritten** under OpenNES. Do not introduce additional Lemuroid-style navigation.
- Prioritize **portrait and landscape touchscreen controller ergonomics**, with an authentic but polished NES-inspired appearance.
- Reuse suitable existing NES core, input, save, and Android infrastructure rather than implementing a new NES emulator.
- Plan features for RetroAchievements, but do not claim Hardcore compliance without official approval.
- Do not bundle commercial ROMs, unlicensed artwork or copied Pizza Boy UI assets.

## Before submitting changes

1. Search existing [issues](https://github.com/JPLuker/OpenNES/issues) and read the current [roadmap](ROADMAP.md).
2. Create a focused development branch and keep changes reviewable.
3. Build an APK using [docs/BUILDING.md](docs/BUILDING.md), ideally run a known-good NES ROM on a physical Android device.
4. For Kotlin changes, run `./gradlew ktlintCheck` and resolve violations. Do not disable lint to make CI green.
5. For core or packaging changes, use `python3 scripts/inspect-apk.py <path-to-apk>` and document which emulator native libraries remain.
6. Add or update tests where possible; for UI work include both portrait and landscape screenshots.
7. Describe the behavior changed, how it was tested, and any known limitations in the pull request.

## Bug reports

Include the OpenNES version/commit, Android version, device model, exact steps to reproduce, expected vs actual behavior, and relevant (redacted) logs. For controller bugs, state portrait or landscape and whether a hardware controller was connected.

Never publish passwords, RetroAchievements tokens, or personally sensitive logs. Never upload commercial game ROMs to issues or pull requests.

## Licensing

The fork retains Lemuroid's GPL-3.0 obligations and upstream license notices. Preserve original author notices and document newly incorporated third-party components. Source contributions must be compatible with the project's licensing.
