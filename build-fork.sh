#!/bin/bash
# ============================================================
#  ⚡ VoltPur 26.2 - Build Script
#  "Your server, your rules, everywhere."
# ============================================================

set -e

echo "======================================"
echo "  VoltPur Performance - Build"
echo "  الإصدار: 26.2"
echo "======================================"

# التحقق من Java (VoltPur requires Java 25 — unified with CI)
JAVA_VER=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VER" -lt 25 ]; then
    echo "❌ خطأ: يحتاج Java 25+ (الموجود: $JAVA_VER)"
    echo "📦 حمل JDK 25 (Temurin) من: https://adoptium.net/"
    exit 1
fi
echo "✅ Java: $(java -version 2>&1 | head -1)"

# 1️⃣ تطبيق جميع الـ patches
echo ""
echo "📦 [1/3] تطبيق جميع الـ patches..."
./gradlew applyAllPatches

# 2️⃣ بناء السيرفر (Paperclip JAR = النسخة النهائية)
echo ""
echo "🔨 [2/3] بناء السيرفر..."
./gradlew :purpur-server:createPaperclipJar

# 3️⃣ نسخ الـ JAR
echo ""
echo "📋 [3/3] نسخ الـ JAR..."
JAR_FILE=$(ls purpur-server/build/libs/*-paperclip*.jar 2>/dev/null | head -1)
if [ -n "$JAR_FILE" ]; then
    cp "$JAR_FILE" ./VoltPur-Performance-26.2.jar
    echo "✅ تم البناء بنجاح!"
    ls -lh VoltPur-Performance-26.2.jar
else
    echo "⚠️ الـ JAR ما اتلقاش، شوف purpur-server/build/libs/"
fi

echo ""
echo "======================================"
echo "  ✅ بناء VoltPur Performance تم!"
echo "======================================"
