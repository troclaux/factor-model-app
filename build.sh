#!/usr/bin/env bash
# Runs the unit tests and builds the debug APK.
#   ./build.sh            build app/build/outputs/apk/debug/app-debug.apk
#   ./build.sh --install  also install it on the phone connected over USB (adb)
set -euo pipefail
cd "$(dirname "$0")"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"

# Gradle 8.9 runs on JDK 17 or 21 only. Use JAVA_HOME if it is one of those, else mise's Temurin 21.
java_major() { "$1/bin/java" -version 2>&1 | sed -nE 's/.*version "([0-9]+).*/\1/p' | head -1; }
if [ -z "${JAVA_HOME:-}" ] || ! [[ "$(java_major "$JAVA_HOME")" =~ ^(17|21)$ ]]; then
  if command -v mise >/dev/null 2>&1 && JAVA_HOME="$(mise where java@temurin-21 2>/dev/null)"; then
    export JAVA_HOME
  else
    echo "Need JDK 17 or 21: set JAVA_HOME, or run: mise install java@temurin-21" >&2
    exit 1
  fi
fi

./gradlew testDebugUnitTest assembleDebug

apk=app/build/outputs/apk/debug/app-debug.apk
echo "APK: $apk"
if [ "${1:-}" = "--install" ]; then
  "$ANDROID_HOME/platform-tools/adb" install -r "$apk"
fi
