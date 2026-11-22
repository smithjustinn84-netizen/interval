#!/usr/bin/env bash
# Builds the debug APK, installs it over what's there (data kept) and opens it from the launcher.
#
#   tools/run_android.sh              # the one phone on USB, else the one device attached
#   tools/run_android.sh <serial>     # a device by its serial (`adb devices`), e.g. emulator-5554
#   ANDROID_SERIAL=<serial> tools/run_android.sh
set -euo pipefail

cd "$(dirname "$0")/.."

APK=build/tasks/_androidApp_buildAndroidDebug/gradle-project-debug.apk
PACKAGE=dev.justinsmith.interval

serial="${1:-${ANDROID_SERIAL:-}}"
if [[ -z "$serial" ]]; then
  devices=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
  phones=$(grep -v '^emulator-' <<<"$devices" || true)
  if [[ $(grep -c . <<<"$phones") -eq 1 ]]; then
    serial=$phones
  elif [[ $(grep -c . <<<"$devices") -eq 1 ]]; then
    serial=$devices
  else
    echo "Pick a device: tools/run_android.sh <serial>" >&2
    adb devices -l >&2
    exit 1
  fi
fi

echo "→ Building"
./kotlin build -m androidApp -p android

echo "→ Installing on $serial"
adb -s "$serial" install -r "$APK"

echo "→ Launching"
adb -s "$serial" shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
echo "✓ Running on $serial"
