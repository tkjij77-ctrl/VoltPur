#!/bin/bash
# ============================================================
#  ⚡ VoltPur 26.2 - local build script
#
#  This script mirrors .github/workflows/build.yml exactly:
#    Java 25  ->  applyAllPatches  ->  :purpur-server:createPaperclipJar
#
#  History note: an earlier edit reverted these three things and made the script
#  unable to produce the same artifact as CI (Java 21, the wrong Gradle task and a
#  jar glob that never matched). They are fixed here and must stay in sync with the
#  workflow - if you change one, change the other.
# ============================================================

set -euo pipefail

echo "======================================"
echo "  VoltPur 26.2 - local build"
echo "======================================"

JAVA_VER=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "${JAVA_VER:-0}" -lt 25 ]; then
    echo "ERROR: Java 25+ is required (found: ${JAVA_VER:-unknown})."
    echo "       Download Temurin 25 from https://adoptium.net/"
    exit 1
fi
echo "Java: $(java -version 2>&1 | head -1)"

echo
echo "[1/3] Applying patches..."
./gradlew applyAllPatches

echo
echo "[2/3] Building the Paperclip jar (same task as CI)..."
./gradlew :purpur-server:createPaperclipJar

echo
echo "[3/3] Collecting artifacts..."
cd purpur-server/build/libs
JAR_FILE=$(ls -1 *-paperclip*.jar 2>/dev/null | head -n 1 || true)
if [ -z "$JAR_FILE" ]; then
    echo "ERROR: no paperclip jar found in purpur-server/build/libs."
    echo "       Contents:"
    ls -lh
    exit 1
fi
cp "$JAR_FILE" VoltPur-26.2.jar
cp "$JAR_FILE" server.jar
sha256sum VoltPur-26.2.jar > VoltPur-26.2.jar.sha256
echo "Built: $JAR_FILE"
ls -lh VoltPur-26.2.jar server.jar VoltPur-26.2.jar.sha256
echo
echo "Reminder: releases must ship VoltPur-26.2.jar.sha256 next to the jar -"
echo "the in-game updater (/vo up) refuses to install a build without a verifiable checksum."
