"""Synthetic APK fixture tests for NES-only development release gates."""
import importlib.util
import os
import subprocess
import sys
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
INSPECTOR_PATH = ROOT / "scripts" / "inspect-apk.py"
SPEC = importlib.util.spec_from_file_location("inspect_apk", INSPECTOR_PATH)
INSPECTOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(INSPECTOR)
SYMBOL_BYTES = b"\n".join(INSPECTOR.REQUIRED_NATIVE_SYMBOLS)


class ApkIntegrityTests(unittest.TestCase):
    def setUp(self):
        self.workspace = tempfile.TemporaryDirectory()
        self.addCleanup(self.workspace.cleanup)
        self.apk = Path(self.workspace.name) / "OpenNES.apk"

    def package(self, *, bridge=SYMBOL_BYTES, other_core=False, missing_abi=False, duplicate=False):
        with zipfile.ZipFile(self.apk, "w", zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("lib/arm64-v8a/libfceumm_libretro_android.so", b"NES core")
            archive.writestr("lib/arm64-v8a/liblibretrodroid.so", bridge)
            if other_core:
                archive.writestr("lib/arm64-v8a/libmgba_libretro_android.so", b"wrong core")
            if missing_abi:
                archive.writestr("lib/x86_64/liblibretrodroid.so", bridge)
            if duplicate:
                with warnings.catch_warnings():
                    warnings.simplefilter("ignore", UserWarning)
                    archive.writestr("lib/arm64-v8a/libfceumm_libretro_android.so", b"duplicate")

    def inspect(self):
        return subprocess.run(
            [sys.executable, str(INSPECTOR_PATH), "--nes-only", "--native-bridge", str(self.apk)],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            check=False,
        )

    def test_valid_nes_only_native_bridge(self):
        self.package()
        self.assertEqual(0, self.inspect().returncode)

    def test_missing_cheat_bridge_symbol_fails(self):
        missing = INSPECTOR.REQUIRED_NATIVE_SYMBOLS[-1]
        self.package(bridge=SYMBOL_BYTES.replace(missing, b""))
        result = self.inspect()
        self.assertNotEqual(0, result.returncode)
        self.assertIn(missing.decode(), result.stderr)

    def test_non_nes_core_fails(self):
        self.package(other_core=True)
        self.assertNotEqual(0, self.inspect().returncode)

    def test_native_library_missing_matching_nes_abi_fails(self):
        self.package(missing_abi=True)
        self.assertNotEqual(0, self.inspect().returncode)

    def test_duplicate_zip_members_fail(self):
        self.package(duplicate=True)
        self.assertNotEqual(0, self.inspect().returncode)


if __name__ == "__main__":
    unittest.main()
