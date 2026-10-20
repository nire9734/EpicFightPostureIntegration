#!/usr/bin/env sh
# Bootstrap the pinned Gradle 8.8 distribution. Integrity checked before extraction.
set -eu

GRADLE_VERSION=8.8
EXPECTED_SHA256=a4b4158601f8636cdeeab09bd76afb640030bb5b144aafe261a5e8af027dc612
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CACHE_DIR="$PROJECT_DIR/.gradle-bootstrap"
GRADLE_HOME="$CACHE_DIR/gradle-$GRADLE_VERSION"
ZIP="$CACHE_DIR/gradle-$GRADLE_VERSION-bin.zip"
URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"

verify_sha256() {
  if command -v sha256sum >/dev/null 2>&1; then
    actual=$(sha256sum "$1" | awk '{print $1}')
  elif command -v shasum >/dev/null 2>&1; then
    actual=$(shasum -a 256 "$1" | awk '{print $1}')
  elif command -v openssl >/dev/null 2>&1; then
    actual=$(openssl dgst -sha256 "$1" | awk '{print $NF}')
  else
    echo "A SHA-256 verification utility is required." >&2
    return 1
  fi
  [ "$actual" = "$EXPECTED_SHA256" ]
}

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$CACHE_DIR"

  if [ -f "$ZIP" ] && ! verify_sha256 "$ZIP"; then
    echo "Cached Gradle ZIP failed SHA-256 verification; removing it." >&2
    rm -f "$ZIP"
  fi

  if [ ! -f "$ZIP" ]; then
    TMP_ZIP="$ZIP.download.$$"
    trap 'rm -f "$TMP_ZIP"' EXIT
    echo "Downloading Gradle $GRADLE_VERSION..."
    if command -v curl >/dev/null 2>&1; then
      curl -fL "$URL" -o "$TMP_ZIP"
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$TMP_ZIP" "$URL"
    else
      echo "curl or wget is required for the first build." >&2
      exit 1
    fi
    if ! verify_sha256 "$TMP_ZIP"; then
      echo "Downloaded Gradle ZIP failed SHA-256 verification." >&2
      exit 1
    fi
    mv "$TMP_ZIP" "$ZIP"
    trap - EXIT
  fi

  if ! verify_sha256 "$ZIP"; then
    echo "Gradle ZIP failed SHA-256 verification." >&2
    exit 1
  fi
  if command -v unzip >/dev/null 2>&1; then
    unzip -q -o "$ZIP" -d "$CACHE_DIR"
  else
    echo "unzip is required for the first build." >&2
    exit 1
  fi
fi

exec "$GRADLE_HOME/bin/gradle" -p "$PROJECT_DIR" "$@"
