#!/usr/bin/env python3
"""Validate OpenNES Android manifest foreground-service permission pairs.

Run: python3 scripts/check_foreground_service_permissions.py path/to/AndroidManifest.xml
"""
from __future__ import annotations

import argparse
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
BASE = "android.permission.FOREGROUND_SERVICE"
PREFIX = BASE + "_"


def validate_manifest(xml: str) -> list[str]:
    """Return actionable errors; never assume a typed service is permitted."""
    root = ET.fromstring(xml)
    permissions = {
        node.get(ANDROID + "name")
        for node in root
        if node.tag in {"uses-permission", "uses-permission-sdk-23"}
    }
    errors: list[str] = []
    for service in root.iter("service"):
        service_type = service.get(ANDROID + "foregroundServiceType", "")
        for kind in (part.strip() for part in service_type.split("|")):
            if not kind:
                continue
            if BASE not in permissions:
                errors.append(f"{service.get(ANDROID + 'name', '<unnamed>')}: missing {BASE}")
            suffix = "".join(
                "_" + char if char.isupper() else char.upper()
                for char in kind
            ).lstrip("_")
            # Android's dataSync -> DATA_SYNC, mediaPlayback -> MEDIA_PLAYBACK.
            required = PREFIX + suffix
            if required not in permissions:
                errors.append(f"{service.get(ANDROID + 'name', '<unnamed>')}: missing {required}")
    return sorted(set(errors))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    args = parser.parse_args()
    try:
        errors = validate_manifest(args.manifest.read_text(encoding="utf-8"))
    except (OSError, ET.ParseError) as exc:
        print(f"Cannot validate manifest: {exc}", file=sys.stderr)
        return 2
    for error in errors:
        print(error, file=sys.stderr)
    if errors:
        return 1
    print("Foreground service permission pairs: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
