#!/bin/sh
# Maven Wrapper for Apache Maven 3.9.11
# Generated for distributionType=only-script.

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
PROPERTIES="$SCRIPT_DIR/.mvn/wrapper/maven-wrapper.properties"

if [ ! -f "$PROPERTIES" ]; then
  echo "Cannot find $PROPERTIES" >&2
  exit 1
fi

DISTRIBUTION_URL="$(sed -n 's/^distributionUrl=//p' "$PROPERTIES")"
if [ -z "$DISTRIBUTION_URL" ]; then
  echo "Cannot read distributionUrl from $PROPERTIES" >&2
  exit 1
fi

MAVEN_USER_HOME="${MAVEN_USER_HOME:-${HOME}/.m2}"
DIST_NAME="${DISTRIBUTION_URL##*/}"
DIST_DIR="${DIST_NAME%.zip}"
MAVEN_HOME="$MAVEN_USER_HOME/wrapper/dists/$DIST_DIR"

if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  TMP_DIR="$(mktemp -d)"
  trap 'rm -rf "$TMP_DIR"' EXIT HUP INT TERM

  ARCHIVE="$TMP_DIR/$DIST_NAME"
  echo "Downloading Maven from $DISTRIBUTION_URL" >&2

  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$DISTRIBUTION_URL" -o "$ARCHIVE"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$DISTRIBUTION_URL" -O "$ARCHIVE"
  else
    echo "Neither curl nor wget is available to download Maven." >&2
    exit 1
  fi

  mkdir -p "$MAVEN_USER_HOME/wrapper/dists"
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$ARCHIVE" -d "$TMP_DIR"
  else
    echo "unzip is required to install the Maven distribution." >&2
    exit 1
  fi

  EXTRACTED_DIR="$TMP_DIR/$DIST_DIR"
  if [ ! -x "$EXTRACTED_DIR/bin/mvn" ]; then
    EXTRACTED_DIR="$(find "$TMP_DIR" -mindepth 1 -maxdepth 1 -type d -name 'apache-maven-*' | head -n 1)"
  fi

  if [ -z "${EXTRACTED_DIR:-}" ] || [ ! -x "$EXTRACTED_DIR/bin/mvn" ]; then
    echo "Could not find Maven executable in downloaded distribution." >&2
    exit 1
  fi

  rm -rf "$MAVEN_HOME"
  mv "$EXTRACTED_DIR" "$MAVEN_HOME"
fi

exec "$MAVEN_HOME/bin/mvn" "$@"
