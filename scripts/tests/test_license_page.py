"""Prevent the local license screen from fetching third-party JavaScript."""
from html.parser import HTMLParser
from pathlib import Path
import unittest


class ExternalResourceParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.resources = []

    def handle_starttag(self, tag, attrs):
        props = dict(attrs)
        if tag == "script" and props.get("src"):
            self.resources.append(props["src"])
        if tag == "link" and props.get("rel") == "stylesheet" and props.get("href"):
            self.resources.append(props["href"])


class LicensePageTests(unittest.TestCase):
    def test_license_page_requires_no_external_runtime(self):
        source = (
            Path(__file__).resolve().parents[2]
            / "lemuroid-app" / "src" / "main" / "res" / "raw" / "licenses.html"
        ).read_text(encoding="utf-8")
        parser = ExternalResourceParser()
        parser.feed(source)
        self.assertEqual([], parser.resources)
        self.assertIn("Content-Security-Policy", source)
        self.assertIn("LIBRARIES.forEach", source)
        self.assertNotIn("Handlebars.compile", source)
        self.assertNotIn("innerHTML =", source)

    def test_upstream_license_text_and_attributions_still_bundled(self):
        source = (
            Path(__file__).resolve().parents[2]
            / "lemuroid-app" / "src" / "main" / "res" / "raw" / "licenses.html"
        ).read_text(encoding="utf-8")
        self.assertIn("Copyright 2010-2017 JetBrains", source)
        self.assertIn("LibRetro", source)
        self.assertIn("Licensed under the Apache License", source)


if __name__ == "__main__":
    unittest.main()
