#!/usr/bin/env sh
# Bootstrap Gradle 8.8 for this project.
# Once Gradle is available, running `./gradlew wrapper --gradle-version 8.8`
# may be used to replace this bootstrap with the standard Gradle wrapper.
set -eu

GRADLE_VERSION=8.8
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CACHE_DIR="$PROJECT_DIR/.gradle-bootstrap"
GRADLE_HOME="$CACHE_DIR/gradle-$GRADLE_VERSION"
ZIP="$CACHE_DIR/gradle-$GRADLE_VERSION-bin.zip"
URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$CACHE_DIR"
  if [ ! -f "$ZIP" ]; then
    echo "Downloading Gradle $GRADLE_VERSION..."
    if command -v curl >/dev/null 2>&1; then
      curl -fL "$URL" -o "$ZIP"
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$ZIP" "$URL"
    else
      echo "curl or wget is required for the first build." >&2
      exit 1
    fi
  fi
  if command -v unzip >/dev/null 2>&1; then
    unzip -q -o "$ZIP" -d "$CACHE_DIR"
  else
    echo "unzip is required for the first build." >&2
    exit 1
  fi
fi

exec "$GRADLE_HOME/bin/gradle" -p "$PROJECT_DIR" "$@"
