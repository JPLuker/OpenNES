#!/usr/bin/env python3
"""Check an unsigned OpenNES Play-validation App Bundle without publishing it.

This inspects ZIP structure and native core inclusion. A passing result is not
proof of Play signing, 16 KiB alignment, ownership of ROM artwork, or acceptance
on a physical device.
"""
from __future__ import annotations

import argparse
import struct
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


def inspect_arm64_elf_page_size(blob: bytes) -> bool:
    """True if every loadable ELF64 segment is compatible with 16 KiB pages.

    This checks ELF PT_LOAD alignment, not the AAB ZIP layout or runtime behavior.
    Invalid or truncated binaries fail closed rather than passing as empty ELF.
    """
    if len(blob) < 64 or blob[:4] != b"\\x7fELF" or blob[4] != 2 or blob[5] != 1:
        return False
    ph_offset = struct.unpack_from("<Q", blob, 32)[0]
    ph_size = struct.unpack_from("<H", blob, 54)[0]
    ph_count = struct.unpack_from("<H", blob, 56)[0]
    if ph_size < 56 or ph_count == 0 or ph_count > 1024:
        return False
    if ph_offset > len(blob) or ph_count * ph_size > len(blob) - ph_offset:
        return False
    saw_load = False
    for index in range(ph_count):
        start = ph_offset + index * ph_size
        if struct.unpack_from("<I", blob, start)[0] != 1:
            continue
        saw_load = True
        offset = struct.unpack_from("<Q", blob, start + 8)[0]
        vaddr = struct.unpack_from("<Q", blob, start + 16)[0]
        align = struct.unpack_from("<Q", blob, start + 48)[0]
        if align < 16384 or align & (align - 1):
            return False
        if offset % align != vaddr % align:
            return False
    return saw_load


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("aab", type=Path)
    parser.add_argument(
        "--require-16k",
        action="store_true",
        help="Require 16 KiB ELF PT_LOAD alignment for every arm64 shared library.",
    )
    args = parser.parse_args()
    if not args.aab.is_file():
        parser.error(f"Missing AAB: {args.aab}")
    try:
        with zipfile.ZipFile(args.aab) as bundle:
            names = bundle.namelist()
            if bundle.testzip() is not None:
                raise ValueError("App Bundle has a corrupt ZIP entry")
            abis = validate_names(names)
            if args.require_16k:
                arm64_libs = [
                    entry for entry in bundle.infolist()
                    if entry.filename.startswith("base/lib/arm64-v8a/")
                    and entry.filename.endswith(".so")
                ]
                if not arm64_libs:
                    raise ValueError("Missing arm64 shared libraries")
                for entry in arm64_libs:
                    if entry.file_size > 256 * 1024 * 1024:
                        raise ValueError(f"Oversized native library: {entry.filename}")
                    if not inspect_arm64_elf_page_size(bundle.read(entry)):
                        raise ValueError(f"arm64 library lacks 16 KiB ELF load alignment: {entry.filename}")
    except (OSError, zipfile.BadZipFile, ValueError) as exc:
        print(f"ERROR: AAB check failed: {exc}", file=sys.stderr)
        return 1
    print(f"Unsigned AAB ZIP inspection passed: {args.aab} ({', '.join(abis)})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
