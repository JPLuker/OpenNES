#!/usr/bin/env python3
"""Inspect a built OpenNES APK for bundled emulator cores and native-library size."""
import argparse
import sys
import zipfile
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument(
        "--nes-only",
        action="store_true",
        help="Require FCEUmm to be the sole packaged libretro core (M1 acceptance gate).",
    )
    args = parser.parse_args()

    if not args.apk.is_file():
        parser.error(f"APK does not exist: {args.apk}")

    with zipfile.ZipFile(args.apk) as archive:
        native = [
            info
            for info in archive.infolist()
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
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
