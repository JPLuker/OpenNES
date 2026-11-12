"""Regression tests for Play AAB structure and emulator-core exclusions."""
import importlib.util
from pathlib import Path
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
