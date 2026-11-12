#!/usr/bin/env python3
"""Inspect a built OpenNES APK for bundled emulator cores and native-library size."""
import argparse
import sys
import zipfile
from pathlib import Path


REQUIRED_NATIVE_SYMBOLS = (
    b"Java_com_swordfish_libretrodroid_LibretroDroid_copyCoreMemory",
    b"Java_com_swordfish_libretrodroid_LibretroDroid_emulatedFrameCount",
    b"Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_preflight",
    b"Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_startFrameProbe",
    b"Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_stopFrameProbe",
    b"Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_observedProbeFrames",
    b"Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_mappedProbeBytes",
    b"Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_successfulMemorySamples",
    b"Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_startGameSession",
    b"Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_gameSessionStatus",
    b"Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_gameAchievementRows",
    b"Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_gameRichPresence",
    b"Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_gameLeaderboardRows",
    b"Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_takeGameEvent",
    b"Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_stopGameSession",
    b"Java_com_swordfish_lemuroid_app_shared_cheats_NesCheatNative_currentGeneration",
    b"Java_com_swordfish_lemuroid_app_shared_cheats_NesCheatNative_replaceCheats",
)



# Compressed game-data containers require manual license review. Recognizable
# bare NES ROM images may never ship inside the application.
GAME_ROM_SUFFIXES = (".nes", ".fds", ".unf", ".unif", ".nsf", ".nsfe")


def invalid_archive_names(names: list[str]) -> bool:
    for name in names:
        if name.startswith("/") or "\\\\" in name or "//" in name:
            return True
        if any(component in (".", "..") for component in name.split("/")):
            return True
        if name.lower().endswith(GAME_ROM_SUFFIXES):
            return True
    return False


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument(
        "--nes-only",
        action="store_true",
        help="Require FCEUmm to be the sole packaged libretro core (M1 acceptance gate).",
    )
    parser.add_argument(
        "--native-bridge",
        action="store_true",
        help="Require the native OpenNES memory/frame and rcheevos JNI exports in every ABI.",
    )
    args = parser.parse_args()

    if not args.apk.is_file():
        parser.error(f"APK does not exist: {args.apk}")

    with zipfile.ZipFile(args.apk) as archive:
        entries = archive.infolist()
        names = [info.filename for info in entries]
        if len(names) != len(set(names)):
            print("ERROR: APK has duplicate ZIP entries.", file=sys.stderr)
            return 1
        if archive.testzip() is not None:
            print("ERROR: APK contains a corrupt ZIP entry.", file=sys.stderr)
            return 1
        # The release APK must never contain game content or a second
        # downloadable Libretro core concealed outside the native lib folder.
        # This is an exact-file safety gate, not a substitute for license audit.
        if invalid_archive_names(names):
            print("ERROR: APK includes an unsafe path or bundled game ROM.", file=sys.stderr)
            return 1
        if any(
            name.endswith("_libretro_android.so") and
            not name.startswith("lib/")
            for name in names
        ):
            print("ERROR: APK contains a hidden emulator core.", file=sys.stderr)
            return 1
        native = [
            info
            for info in entries
            if info.filename.startswith("lib/") and info.filename.endswith(".so")
        ]
    core_libs = [
        info for info in native if "_libretro_android.so" in info.filename
    ]
    fceumm = [
        info for info in core_libs if info.filename.endswith("/libfceumm_libretro_android.so")
    ]
    other_cores = [info for info in core_libs if info not in fceumm]
    total_native_bytes = sum(info.file_size for info in native)

    print(f"APK: {args.apk}")
    print(f"APK size: {args.apk.stat().st_size:,} bytes")
    print(f"Native libraries: {len(native)} files ({total_native_bytes:,} uncompressed bytes)")
    print(f"Libretro core libraries: {len(core_libs)}")
    print(f"FCEUmm libraries: {len(fceumm)}")
    print(f"Other emulator core libraries: {len(other_cores)}")
    for info in sorted(core_libs, key=lambda item: item.filename):
        print(f"  {info.filename}: {info.file_size:,} bytes")

    if not fceumm:
        print("ERROR: FCEUmm NES core was not bundled in this APK.", file=sys.stderr)
        return 1
    if args.nes_only and other_cores:
        print("ERROR: Non-NES libretro cores are still included.", file=sys.stderr)
        return 1
    if args.native_bridge:
        libs = [info for info in native if info.filename.endswith("/liblibretrodroid.so")]
        if not libs:
            print("ERROR: Missing source-built libretrodroid.so", file=sys.stderr)
            return 1
        core_abis = {info.filename.split("/")[1] for info in fceumm}
        bridge_abis = {info.filename.split("/")[1] for info in libs}
        if core_abis != bridge_abis:
            print(
                f"ERROR: Emulator core ABIs {sorted(core_abis)} do not match "
                f"JNI bridge ABIs {sorted(bridge_abis)}.",
                file=sys.stderr,
            )
            return 1
        with zipfile.ZipFile(args.apk) as archive:
            for lib in libs:
                blob = archive.read(lib.filename)
                for symbol in REQUIRED_NATIVE_SYMBOLS:
                    if symbol not in blob:
                        print(f"ERROR: {lib.filename} lacks {symbol.decode()}", file=sys.stderr)
                        return 1
        print(f"Verified native achievement bridge exports in {len(libs)} ABI libraries")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
