#!/bin/bash

# VoltPur 26.2 Build Script
# ===========================
# هذا البرنامج يبني VoltPur بشكل صحيح مع تطبيق جميع الـ patches

set -e  # Exit on any error

echo "⚡ VoltPur 26.2 Build Script"
echo "=============================="
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Step 1: Stop Gradle daemon
echo -e "${YELLOW}[1/6]${NC} إيقاف Gradle daemon..."
./gradlew --stop 2>/dev/null || true
echo -e "${GREEN}✓${NC} تم إيقاف daemon"
echo ""

# Step 2: Clean previous builds
echo -e "${YELLOW}[2/6]${NC} تنظيف البناء السابق..."
./gradlew clean
echo -e "${GREEN}✓${NC} تم التنظيف"
echo ""

# Step 3: Apply all patches
echo -e "${YELLOW}[3/6]${NC} تطبيق جميع الـ patches..."
./gradlew applyAllPatches
echo -e "${GREEN}✓${NC} تم تطبيق الـ patches"
echo ""

# Step 4: Rebuild patches (important!)
echo -e "${YELLOW}[4/6]${NC} إعادة بناء الـ patches..."
./gradlew rebuildPatches
echo -e "${GREEN}✓${NC} تم إعادة البناء"
echo ""

# Step 5: Build JAR
echo -e "${YELLOW}[5/6]${NC} بناء الـ JAR النهائي..."
./gradlew createMojmapBundlerJar
echo -e "${GREEN}✓${NC} تم بناء JAR"
echo ""

# Step 6: Verify build
echo -e "${YELLOW}[6/6]${NC} التحقق من البناء..."
JAR_FILE="purpur-server/build/libs/purpur-server-26.2-all.jar"

if [ -f "$JAR_FILE" ]; then
    JAR_SIZE=$(du -h "$JAR_FILE" | cut -f1)
    echo -e "${GREEN}✓${NC} البناء نجح!"
    echo ""
    echo "📦 الملف النهائي:"
    echo "   $JAR_FILE"
    echo "   الحجم: $JAR_SIZE"
    echo ""
    echo -e "${GREEN}════════════════════════════════════════${NC}"
    echo -e "${GREEN}⚡ VoltPur 26.2 جاهز للتشغيل!${NC}"
    echo -e "${GREEN}════════════════════════════════════════${NC}"
else
    echo -e "${RED}✗${NC} البناء فشل! لم يتم العثور على JAR"
    exit 1
fi
