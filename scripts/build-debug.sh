#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"

if [[ -z "${ANDROID_SDK_ROOT:-}" ]]; then
  echo "ANDROID_SDK_ROOT is not set." >&2
  echo "Example: export ANDROID_SDK_ROOT=\"$HOME/Android/Sdk\"" >&2
  exit 1
fi

if [[ ! -d "$ANDROID_SDK_ROOT" ]]; then
  echo "ANDROID_SDK_ROOT points to a missing directory: $ANDROID_SDK_ROOT" >&2
  exit 1
fi

cat > "$PROJECT_DIR/local.properties" <<LOCALPROPS
sdk.dir=${ANDROID_SDK_ROOT//:/\\:}
LOCALPROPS

"$SCRIPT_DIR/bootstrap-gradle.sh" assembleDebug

echo
APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [[ -f "$APK_PATH" ]]; then
  echo "Built: $APK_PATH"
else
  echo "Build finished, but APK not found at expected path: $APK_PATH" >&2
  exit 1
fi
