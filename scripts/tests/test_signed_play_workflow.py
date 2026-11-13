"""Prevent accidental publication or unsigned fallbacks in the Play signing gate."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "play-signed-validation.yml"


class ProtectedPlayBuildTest(unittest.TestCase):
    def test_signing_workflow_is_manual_read_only_and_protected(self):
        source = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("  workflow_dispatch:", source)
        self.assertNotIn("  push:", source)
        self.assertIn("  contents: read", source)
        self.assertIn("environment: production-signing", source)
        self.assertIn("github.ref == 'refs/heads/master'", source)
        self.assertNotIn("gh release create", source)
        self.assertNotIn("gh release upload", source)
        self.assertNotIn("publishToPlay", source)

    def test_signing_requires_private_cert_pin_and_separate_keystore(self):
        source = WORKFLOW.read_text(encoding="utf-8")
        for variable in (
            "OPENNES_RELEASE_KEYSTORE_BASE64",
            "OPENNES_RELEASE_STORE_PASSWORD",
            "OPENNES_RELEASE_KEY_ALIAS",
            "OPENNES_RELEASE_KEY_PASSWORD",
            "OPENNES_RELEASE_CERT_SHA256",
        ):
            self.assertIn(variable, source)
        self.assertIn("base64 --decode", source)
        self.assertIn("keytool -exportcert -rfc", source)
        self.assertIn("openssl x509 -noout -fingerprint -sha256", source)
        self.assertIn("does not match the pinned fingerprint", source)
        self.assertNotIn("debug.keystore", source)
        self.assertNotIn("secrets.GITHUB_TOKEN", source)

    def test_bundle_fails_closed_without_signature_and_inspection(self):
        source = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("set -euo pipefail", source)
        self.assertIn(":lemuroid-app:bundleFreeBundleRelease", source)
        self.assertIn("scripts/inspect-aab.py --require-16k", source)
        self.assertIn("jarsigner -verify", source)
        self.assertIn("grep -q 'jar verified'", source)
        self.assertIn("sha256sum -c", source)
        self.assertIn("retention-days: 1", source)
        self.assertIn('rm -f "$RUNNER_TEMP/opennes-play-upload.jks"', source)


if __name__ == "__main__":
    unittest.main()
