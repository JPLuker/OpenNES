"""Regression tests for OpenNES debug APK package/version inspection."""

import importlib.util
import pathlib
import unittest


SCRIPT = pathlib.Path(__file__).resolve().parents[1] / "verify-apk-identity.py"
SPEC = importlib.util.spec_from_file_location("verify_apk_identity", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class VerifyApkIdentityTest(unittest.TestCase):
    @staticmethod
    def badge(package: str = "com.jpluker.opennes.debug", code: int = 100_844, name: str = "0.1.0-dev.844") -> str:
        return (
            f"package: name='{package}' versionCode='{code}' versionName='{name}'\nsdkVersion:'26'\ntargetSdkVersion:'36'\n"
            "uses-permission: name='android.permission.FOREGROUND_SERVICE'\n"
            "uses-permission: name='android.permission.FOREGROUND_SERVICE_DATA_SYNC'\n"
        )

    def test_known_good_ci_apk_passes(self):
        self.assertEqual((100_844, "0.1.0-dev.844"), MODULE.verify_badging(self.badge(), "0.1.0", 844))

    def test_changed_signer_package_or_reused_version_is_rejected(self):
        for badging in (
            self.badge(package="com.jpluker.opennes"),
            self.badge(code=100_843),
            self.badge(name="0.1.0-dev.843"),
            self.badge() + self.badge(),
            "sdkVersion:'26'",
            self.badge().replace("versionCode='100844'", "versionCode='100844' versionCode='100844'"),
        ):
            with self.subTest(badging=badging):
                with self.assertRaises(ValueError):
                    MODULE.verify_badging(badging, "0.1.0", 844)

    def test_api_36_is_required_from_the_built_manifest(self):
        for badging in (
            self.badge().replace("targetSdkVersion:'36'", "targetSdkVersion:'35'"),
            self.badge().replace("targetSdkVersion:'36'\n", ""),
            self.badge() + "targetSdkVersion:'36'\n",
        ):
            with self.subTest(badging=badging):
                with self.assertRaises(ValueError):
                    MODULE.verify_badging(badging, "0.1.0", 844)

    def test_refuses_missing_foreground_game_service_permission(self):
        for name in ("android.permission.FOREGROUND_SERVICE", "android.permission.FOREGROUND_SERVICE_DATA_SYNC"):
            with self.subTest(name=name):
                without = self.badge().replace(f"uses-permission: name='{name}'\n", "")
                with self.assertRaises(ValueError):
                    MODULE.verify_badging(without, "0.1.0", 844)

    def test_refuses_bad_release_version_and_invalid_run_number(self):
        for version, run in (("0.1.0-debug", 844), ("0.1.0", 0), ("0.1.0", 1_000_001)):
            with self.subTest(version=version, run=run):
                with self.assertRaises(ValueError):
                    MODULE.verify_badging(self.badge(), version, run)


if __name__ == "__main__":
    unittest.main()
