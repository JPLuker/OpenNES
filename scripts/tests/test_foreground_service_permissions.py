"""Regression coverage for the Android foreground-service permission validator."""
import unittest

from check_foreground_service_permissions import validate_manifest

HEADER = '<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
SERVICE = '<application><service android:name=".GameService" android:foregroundServiceType="dataSync"/></application>'
END = '</manifest>'


class ManifestPermissionTests(unittest.TestCase):
    def test_missing_both_permissions(self):
        errors = validate_manifest(HEADER + SERVICE + END)
        self.assertEqual(len(errors), 2)
        self.assertTrue(any("FOREGROUND_SERVICE_DATA_SYNC" in e for e in errors))

    def test_missing_base_permission(self):
        xml = HEADER + '<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC"/>' + SERVICE + END
        self.assertEqual(len(validate_manifest(xml)), 1)

    def test_valid_permission_pair(self):
        xml = HEADER + ''.join(
            f'<uses-permission android:name="android.permission.{name}"/>'
            for name in ("FOREGROUND_SERVICE", "FOREGROUND_SERVICE_DATA_SYNC")
        ) + SERVICE + END
        self.assertEqual(validate_manifest(xml), [])

    def test_untyped_service_does_not_require_permission(self):
        xml = HEADER + '<application><service android:name=".PlainService"/></application>' + END
        self.assertEqual(validate_manifest(xml), [])

    def test_multiple_service_types(self):
        xml = HEADER + '<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>' + '<application><service android:foregroundServiceType="dataSync|mediaPlayback"/></application>' + END
        self.assertEqual(len(validate_manifest(xml)), 2)


if __name__ == "__main__":
    unittest.main()
