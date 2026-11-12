#!/usr/bin/env python3
"""Validate the APK's actual Android manifest identity before publication."""

import argparse
import re
import sys
from pathlib import Path

PACKAGE_ID = "com.jpluker.opennes.debug"
MAX_RUN_NUMBER = 1_000_000
VERSION_CODE_OFFSET = 100_000


def verify_badging(badging: str, version: str, run_number: int) -> tuple[int, str]:
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version):
        raise ValueError("VERSION is not a valid major.minor.patch value")
    if not 1 <= run_number <= MAX_RUN_NUMBER:
        raise ValueError("CI run number is outside the supported range")

    package_lines = [line for line in badging.splitlines() if line.startswith("package: ")]
    if len(package_lines) != 1:
        raise ValueError("APK badging must contain exactly one package line")
    pairs = re.findall(r"\b([A-Za-z]+)='([^']*)'", package_lines[0])
    fields = dict(pairs)
    if len(fields) != len(pairs):
        raise ValueError("APK badging has duplicate fields")
    if fields.get("name") != PACKAGE_ID:
        raise ValueError("Unexpected APK application ID")

    expected_code = VERSION_CODE_OFFSET + run_number
    expected_name = f"{version}-dev.{run_number}"
    if fields.get("versionCode") != str(expected_code):
        raise ValueError("Unexpected APK versionCode; update install could be rejected")
    if fields.get("versionName") != expected_name:
        raise ValueError("Unexpected APK versionName; Gradle and release numbering differ")
    return expected_code, expected_name


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version-file", type=Path, required=True)
    parser.add_argument("--run-number", type=int, required=True)
    args = parser.parse_args()
    try:
        version = args.version_file.read_text(encoding="utf-8").strip()
        code, name = verify_badging(sys.stdin.read(), version, args.run_number)
    except (ValueError, OSError) as exc:
        print(f"ERROR: APK identity verification failed: {exc}", file=sys.stderr)
        return 1
    print(f"Verified {PACKAGE_ID} versionCode={code} versionName={name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
