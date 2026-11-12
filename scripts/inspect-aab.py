#!/usr/bin/env python3
"""Check an unsigned OpenNES Play-validation App Bundle without publishing it.

This inspects ZIP structure and native core inclusion. A passing result is not
proof of Play signing, 16 KiB alignment, ownership of ROM artwork, or acceptance
on a physical device.
"""
from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path


def validate_names(names: list[str]) -> list[str]:
    if len(set(names)) != len(names):
        raise ValueError("App Bundle has duplicate ZIP entries")

    for required in ("BundleConfig.pb", "base/manifest/AndroidManifest.xml"):
        if required not in names:
            raise ValueError(f"Missing App Bundle member: {required}")

    cores = sorted(
        name for name in names
        if name.startswith("base/lib/") and name.endswith("_libretro_android.so")
    )
    if not cores:
        raise ValueError("No NES FCEUmm core found in App Bundle")
    for member in cores:
        if not member.endswith("/libfceumm_libretro_android.so"):
            raise ValueError(f"Non-NES emulator core bundled: {member}")

    def abi(member: str) -> str:
        parts = member.split("/")
        if len(parts) != 4 or not parts[2]:
            raise ValueError(f"Unexpected native library path: {member}")
        return parts[2]

    core_abis = {abi(member) for member in cores}
    if "arm64-v8a" not in core_abis:
        raise ValueError("Play build has no arm64-v8a FCEUmm emulator core")

    bridges = [
        name for name in names
        if name.startswith("base/lib/") and name.endswith("/liblibretrodroid.so")
    ]
    bridge_abis = {abi(member) for member in bridges}
    if core_abis != bridge_abis:
        raise ValueError(
            f"FCEUmm ABI set {sorted(core_abis)} differs from bridge ABI set {sorted(bridge_abis)}"
        )
    return sorted(core_abis)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("aab", type=Path)
    args = parser.parse_args()
    if not args.aab.is_file():
        parser.error(f"Missing AAB: {args.aab}")
    try:
        with zipfile.ZipFile(args.aab) as bundle:
            names = bundle.namelist()
            if bundle.testzip() is not None:
                raise ValueError("App Bundle has a corrupt ZIP entry")
            abis = validate_names(names)
    except (OSError, zipfile.BadZipFile, ValueError) as exc:
        print(f"ERROR: AAB check failed: {exc}", file=sys.stderr)
        return 1
    print(f"Unsigned AAB ZIP inspection passed: {args.aab} ({', '.join(abis)})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
