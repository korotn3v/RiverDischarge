#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"

if [[ ! -f "$APK_PATH" ]]; then
  echo "APK not found. Run scripts/build-debug.sh first." >&2
  exit 1
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found in PATH. Add \$ANDROID_SDK_ROOT/platform-tools to PATH." >&2
  exit 1
fi

adb install -r "$APK_PATH"
