"""Regression guards for the only supported NES-only Android build variant."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]


class OpenNesVariantsTests(unittest.TestCase):
    def test_only_free_and_bundle_flavors_remain(self):
        app = (ROOT / "lemuroid-app" / "build.gradle.kts").read_text(encoding="utf-8")
        settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
        self.assertEqual(["free", "bundle"], re.findall(r'create\("(free|bundle)"\)', app))
        self.assertNotRegex(app, r'create\("(play|dynamic)"\)')
        self.assertNotIn("playImplementation", app)
        self.assertNotIn("dynamicFeatures.add", app)
        self.assertNotIn('":lemuroid-app-ext-play"', settings)
        self.assertNotIn("lemuroid_core_fceumm", settings)
        self.assertIn('"bundleImplementation"(project(":bundled-cores"))', app)
        self.assertIn('"freeImplementation"(project(":lemuroid-app-ext-free"))', app)

    def test_production_play_version_codes_are_monotonic(self):
        app = (ROOT / "lemuroid-app" / "build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn("val playVersionCode =", app)
        self.assertIn('openNesVersion.split(".").map(String::toInt)', app)
        self.assertIn("versionCode = developmentRun?.plus(100_000) ?: playVersionCode", app)
        self.assertNotIn("versionCode = developmentRun?.plus(100_000) ?: 1", app)
        # The Play sequence is ordered by semver, not by whichever Actions
        # run happened to produce the last debug APK.
        def play_code(version):
            major, minor, patch = map(int, version.split("."))
            return major * 1_000_000 + minor * 1_000 + patch

        codes = [play_code(v) for v in ("0.1.0", "0.1.1", "0.1.2", "0.2.0", "1.0.0")]
        self.assertEqual(sorted(codes), codes)
        self.assertEqual(len(set(codes)), len(codes))

    def test_package_identity_is_kept_across_debug_and_play(self):
        app = (ROOT / "lemuroid-app" / "build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn('applicationId = "com.jpluker.opennes"', app)
        self.assertIn('applicationIdSuffix = ".debug"', app)
        self.assertIn("OPENNES_RELEASE_KEYSTORE", app)
        self.assertIn("OPENNES_RELEASE_KEY_PASSWORD", app)
        self.assertIn('signingConfigs.findByName("openNesRelease")', app)


if __name__ == "__main__":
    unittest.main()
