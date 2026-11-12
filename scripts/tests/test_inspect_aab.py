"""Regression tests for Play AAB structure and emulator-core exclusions."""
import importlib.util
from pathlib import Path
import struct
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "inspect-aab.py"
SPEC = importlib.util.spec_from_file_location("inspect_aab", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class InspectAabTest(unittest.TestCase):
    @staticmethod
    def names():
        return [
            "BundleConfig.pb",
            "base/manifest/AndroidManifest.xml",
            "base/lib/arm64-v8a/libfceumm_libretro_android.so",
            "base/lib/arm64-v8a/liblibretrodroid.so",
            "base/lib/armeabi-v7a/libfceumm_libretro_android.so",
            "base/lib/armeabi-v7a/liblibretrodroid.so",
        ]

    @staticmethod
    def elf_with_load_alignment(align: int, *, machine: int = 183) -> bytes:
        blob = bytearray(120)
        blob[0:4] = b"\x7fELF"
        blob[4] = 2  # ELFCLASS64
        blob[5] = 1  # little endian
        struct.pack_into("<H", blob, 18, machine)
        struct.pack_into("<Q", blob, 32, 64)  # e_phoff
        struct.pack_into("<H", blob, 54, 56)  # e_phentsize
        struct.pack_into("<H", blob, 56, 1)  # e_phnum
        struct.pack_into("<I", blob, 64, 1)  # PT_LOAD
        struct.pack_into("<Q", blob, 72, 0)  # p_offset
        struct.pack_into("<Q", blob, 80, 0)  # p_vaddr
        struct.pack_into("<Q", blob, 112, align)  # p_align
        return bytes(blob)

    def test_arm64_elf_requires_16k_load_segments(self):
        self.assertTrue(
            MODULE.inspect_arm64_elf_page_size(self.elf_with_load_alignment(16384))
        )
        self.assertTrue(
            MODULE.inspect_arm64_elf_page_size(self.elf_with_load_alignment(65536))
        )
        self.assertFalse(
            MODULE.inspect_arm64_elf_page_size(self.elf_with_load_alignment(4096))
        )
        self.assertFalse(
            MODULE.inspect_arm64_elf_page_size(self.elf_with_load_alignment(0))
        )
        self.assertFalse(
            MODULE.inspect_arm64_elf_page_size(self.elf_with_load_alignment(18000))
        )
        self.assertFalse(MODULE.inspect_arm64_elf_page_size(b"not an ELF"))
        self.assertFalse(
            MODULE.inspect_arm64_elf_page_size(
                self.elf_with_load_alignment(16384, machine=62),
            )
        )

    def test_arm64_elf_rejects_truncated_program_header(self):
        self.assertFalse(MODULE.inspect_arm64_elf_page_size(self.elf_with_load_alignment(16384)[:80]))

    def test_valid_nes_only_bundle(self):
        self.assertEqual(
            ["arm64-v8a", "armeabi-v7a"],
            MODULE.validate_names(self.names()),
        )

    def test_missing_manifest_or_bundle_metadata(self):
        for required in ("BundleConfig.pb", "base/manifest/AndroidManifest.xml"):
            with self.subTest(required=required), self.assertRaises(ValueError):
                MODULE.validate_names([name for name in self.names() if name != required])

    def test_refuses_duplicates_and_unexpected_cores(self):
        with self.assertRaises(ValueError):
            MODULE.validate_names(self.names() + [self.names()[-1]])
        with self.assertRaises(ValueError):
            MODULE.validate_names(
                self.names() + ["base/lib/arm64-v8a/libsnes9x_libretro_android.so"]
            )

    def test_refuses_emulator_core_in_another_module_or_asset(self):
        for member in (
            "dynamic_core/lib/arm64-v8a/libsnes9x_libretro_android.so",
            "base/assets/libsnes9x_libretro_android.so",
            "dynamic_core/lib/arm64-v8a/libfceumm_libretro_android.so",
        ):
            with self.subTest(member=member), self.assertRaises(ValueError):
                MODULE.validate_names(self.names() + [member])

    def test_refuses_bundled_game_rom_images(self):
        for member in ("base/assets/game.nes", "base/assets/demo.FDS"):
            with self.subTest(member=member), self.assertRaises(ValueError):
                MODULE.validate_names(self.names() + [member])

    def test_refuses_archive_member_traversal(self):
        for member in ("base/assets/../tokens", r"base\\lib\\core.so", "/base/extra"):
            with self.subTest(member=member), self.assertRaises(ValueError):
                MODULE.validate_names(self.names() + [member])

    def test_refuses_mismatched_bridge_abis(self):
        with self.assertRaises(ValueError):
            MODULE.validate_names([
                item for item in self.names()
                if item != "base/lib/armeabi-v7a/liblibretrodroid.so"
            ])

    def test_refuses_no_64_bit_nes_core(self):
        with self.assertRaises(ValueError):
            MODULE.validate_names([
                item for item in self.names()
                if not item.startswith("base/lib/arm64-v8a/")
            ])

    def test_refuses_invalid_native_library_folder(self):
        with self.assertRaises(ValueError):
            MODULE.validate_names(
                self.names() + ["base/lib//libfceumm_libretro_android.so"]
            )


if __name__ == "__main__":
    unittest.main()
