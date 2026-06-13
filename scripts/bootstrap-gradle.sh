#!/usr/bin/env bash
set -euo pipefail

GRADLE_VERSION="8.7"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/riverdischarge-cli"
DIST_DIR="$CACHE_DIR/gradle-$GRADLE_VERSION"
ZIP_PATH="$CACHE_DIR/gradle-$GRADLE_VERSION-bin.zip"
URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"

mkdir -p "$CACHE_DIR"

if [[ ! -x "$DIST_DIR/bin/gradle" ]]; then
  echo "[bootstrap] Gradle $GRADLE_VERSION not found locally. Downloading..."
  tmp_dir="$(mktemp -d)"
  trap 'rm -rf "$tmp_dir"' EXIT
  if command -v curl >/dev/null 2>&1; then
    curl -L "$URL" -o "$ZIP_PATH"
  elif command -v wget >/dev/null 2>&1; then
    wget "$URL" -O "$ZIP_PATH"
  else
    echo "Need curl or wget to download Gradle." >&2
    exit 1
  fi
  unzip -q "$ZIP_PATH" -d "$tmp_dir"
  rm -rf "$DIST_DIR"
  mv "$tmp_dir/gradle-$GRADLE_VERSION" "$DIST_DIR"
fi

exec "$DIST_DIR/bin/gradle" -p "$PROJECT_DIR" "$@"
