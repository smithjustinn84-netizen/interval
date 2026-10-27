#!/usr/bin/env python3
"""Fails when Android and iOS do not carry the same version.

Android's `versionName`/`versionCode` live in androidApp/module.yaml; iOS's `MARKETING_VERSION`/
`CURRENT_PROJECT_VERSION` in the Xcode project. Nothing in the build keeps them in step, so CI runs
this first. Standard library only; run from anywhere: `python3 tools/check_versions.py`.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODULE_YAML = ROOT / "androidApp" / "module.yaml"
PBXPROJ = ROOT / "iosApp" / "module.xcodeproj" / "project.pbxproj"


def one(pattern: str, text: str, where: Path) -> str:
    values = set(re.findall(pattern, text, re.MULTILINE))
    if len(values) != 1:
        sys.exit(f"{where}: expected exactly one value for {pattern!r}, found {sorted(values)}")
    return values.pop()


def main() -> None:
    android = MODULE_YAML.read_text()
    ios = PBXPROJ.read_text()
    android_name = one(r'^\s*versionName:\s*"([^"]+)"', android, MODULE_YAML)
    android_code = one(r"^\s*versionCode:\s*(\d+)", android, MODULE_YAML)
    ios_name = one(r"^\s*MARKETING_VERSION = ([^;]+);", ios, PBXPROJ)
    ios_code = one(r"^\s*CURRENT_PROJECT_VERSION = (\d+);", ios, PBXPROJ)
    problems = []
    if android_name != ios_name:
        problems.append(f"versionName {android_name} (Android) != MARKETING_VERSION {ios_name} (iOS)")
    if android_code != ios_code:
        problems.append(f"versionCode {android_code} (Android) != CURRENT_PROJECT_VERSION {ios_code} (iOS)")
    if problems:
        sys.exit("Versions differ:\n  " + "\n  ".join(problems))
    print(f"Version {android_name} ({android_code}) on Android and iOS")


if __name__ == "__main__":
    main()
