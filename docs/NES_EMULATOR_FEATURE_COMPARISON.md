# NES emulator feature gap review — Issue #9

**Reviewed:** November 12, 2026. **Scope:** gameplay capabilities and accessibility, **not** reusing another app's visual design, artwork, source code, or marketing text. This is a comparison and implementation shortlist, not evidence a feature is present or tested.

## Reference products and technical baseline

- [NES.emu](https://www.androidauthority.com/best-emulators-for-android-315958/) — Android Authority notes save states, fast-forward, hardware controllers, cheats, gun-controller and VS UniSystem support.
- [Nostalgia.NES](https://play.google.com/store/apps/details?id=com.nostalgiaemulators.neslite) — the developer's Play Store listing describes eight manual save slots with screenshots, rewind, customizable touch controls and a Wi-Fi controller mode for up to four players.
- [Nostalgia.NES and other Android NES options](https://www.pocketgamer.com/android/best-nes-emulators/) — independent comparison covering rewind, multiplayer and button customization.
- [FCEUmm Libretro core capabilities](https://docs.libretro.com/library/fceumm/) — the **core** supports rewind, netplay, cheats, save states and controller remapping, including a user-2 Zapper emulation path. Core support does not mean OpenNES exposes or safely implements the feature.

## Feature matrix and priorities

| Feature | Reference | OpenNES status based on current source | Priority | Implementation boundary |
| --- | --- | --- | --- | --- |
| Dedicated NES-only emulator | NES.emu | Present: only FCEUmm packaged | Retain | Keep NES-only package inspection |
| Adjustable portrait/landscape touch controls | Nostalgia.NES | Present: orientation-specific NES layouts; device geometry acceptance open | Stabilize first | Test cutouts, moving hitboxes and large fonts |
| Auto/manual save states and previews | Both | Present in gameplay/save menus; device lifecycle acceptance open | Stabilize first | Verified save/restore, never overwrite after a failed save |
| Fast-forward | NES.emu | Present; real audio/input behavior unverified | Stabilize first | Review audio/fast-forward toggle persistence |
| Cheat lookup and import | NES.emu | Present: Game Genie/raw code handling and Libretro search; activation optional | Stabilize first | Current session ownership, no activation on import, Hardcore always guarded |
| Casual RetroAchievements | OpenNES differentiator | Native runtime and UI present; new server-persisted award unverified | High | Genuine recognition, award and network-state acceptance |
| Rewind | Both | **No player-facing rewind control in current pause menu** | High after saves | Bounded state history, performance caps, explicit Casual-only guard; must never undermine save integrity or Hardcore compliance |
| Light gun / Zapper touch | NES.emu, FCEUmm | Not established end-to-end in OpenNES | Medium-high | Map touch coordinates to emulated Zapper user-2 mouse signals; test Duck Hunt on real screen/orientation |
| VS UniSystem | NES.emu | Not established in OpenNES | Medium | NES-only ROM recognition, core options, legal BIOS requirements and compatibility first |
| 4-player Wi-Fi controller mode | Nostalgia.NES | Not established in OpenNES | Defer | LAN trust, pairing, latency, lifecycle and third-party access make it a separate project-sized feature |
| State export/share | Nostalgia.NES | RomM manual backup/export foundations exist, not equivalent to general save-state exchange | Medium | Explicit document picker, version and core compatibility guards, no silent overwrite |

## Recommended work sequence

1. **Finish existing correctness gates**: safe game switching/closing, SRAM/manual/autosave restoration, controller positioning, real Casual achievements, validated RomM access.
2. **Implement Casual-only rewind as its own module** if the existing FCEUmm/LibretroDroid runtime supports safe state serialization under the same core lock. Set a memory/time cap, pause during state capture, clear history on ROM switch/reset/restart, and test snapshot corruption, power loss and memory pressure. Do not enable it in Hardcore; do not use the manual save slots as a hidden ring buffer.
3. **Evaluate Zapper input for Duck Hunt** by tracing FCEUmm's supported second-port input type and the Android touch-to-core coordinate adapter. Design for an opt-in per-ROM peripheral, not a global toggle. Do not infer accuracy from a simulated pointer.
4. Review VS UniSystem only after NES compatibility is stable.
5. Defer Wi-Fi controller mode until local multiplayer, authentication/pairing and transport threat models are accepted. This is not necessary for 1.0.

## Acceptance needed before claims

- Android phone and tablet: tap, swipe, controller, keyboard, portrait and landscape.
- No save destruction across process death, update, low storage and restore.
- No achievement unlocks on non-approved Hardcore configurations or during rewind/cheat use.
- No change to baseline NES emulation performance when a feature is disabled.
- Accessible controls and no unrequested change to the existing NES-controller design.

**Decision status:** Comparative research is complete. Rewind and Zapper are actionable candidates, not authorized shipped features. Keep Issue #9 open while the functional gaps remain. Preserve the project's own visual decisions.
