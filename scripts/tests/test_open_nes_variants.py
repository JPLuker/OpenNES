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

    def test_package_identity_is_kept_across_debug_and_play(self):
        app = (ROOT / "lemuroid-app" / "build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn('applicationId = "com.jpluker.opennes"', app)
        self.assertIn('applicationIdSuffix = ".debug"', app)
        self.assertIn("OPENNES_RELEASE_KEYSTORE", app)
        self.assertIn("OPENNES_RELEASE_KEY_PASSWORD", app)
        self.assertIn('signingConfigs.findByName("openNesRelease")', app)


if __name__ == "__main__":
    unittest.main()
