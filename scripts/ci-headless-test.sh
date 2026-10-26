#!/usr/bin/env bash
set -euo pipefail

echo "=================================================="
echo "⚡ VoltPur Headless CI Boot & Stability Verification"
echo "=================================================="

# Determine repo root directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

WORK_DIR="$REPO_ROOT/build/headless-test"
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"
cd "$WORK_DIR"

echo "eula=true" > eula.txt

# Locate generated paperclip jar
JAR_FILE=$(find "$REPO_ROOT/purpur-server/build/libs" -name "*paperclip*.jar" 2>/dev/null | head -n 1 || true)

if [ -z "$JAR_FILE" ] || [ ! -f "$JAR_FILE" ]; then
    echo "❌ Error: Could not find paperclip jar file in $REPO_ROOT/purpur-server/build/libs/!"
    exit 1
fi

echo "📦 Found server jar: $JAR_FILE"

# Prepare test configuration with custom comments
cat << 'EOF' > server.properties
# Custom Admin Comment Line 1
# Custom Admin Comment Line 2
view-distance=10
simulation-distance=10
EOF

cat << 'EOF' > spigot.yml
# Custom Spigot Comment Header
world-settings:
  default:
    verbose: false
    merge-radius:
      item: 2.5
      exp: 3.0
    ticks-per:
      hopper-check: 1
      hopper-transfer: 8
EOF

SERVER_LOG="$WORK_DIR/server_boot.log"

echo "🚀 Launching VoltPur headless server..."
java -Xms512M -Xmx1G -jar "$JAR_FILE" --nogui > "$SERVER_LOG" 2>&1 &
SERVER_PID=$!

BOOTED=false
# Monitor log for "Done (" or timeout after 90s
for i in $(seq 1 90); do
    if ! kill -0 $SERVER_PID 2>/dev/null; then
        echo "❌ Server exited prematurely!"
        cat "$SERVER_LOG"
        exit 1
    fi

    if grep -q "Done (" "$SERVER_LOG" 2>/dev/null; then
        echo "✅ Server successfully completed boot in ~${i}s!"
        BOOTED=true
        break
    fi
    sleep 1
done

if [ "$BOOTED" != "true" ]; then
    echo "❌ Server boot timed out after 90 seconds!"
    kill -9 $SERVER_PID 2>/dev/null || true
    cat "$SERVER_LOG"
    exit 1
fi

echo "🛑 Sending graceful SIGTERM shutdown signal to PID $SERVER_PID..."
kill -SIGTERM $SERVER_PID

# Wait for graceful shutdown (max 30s)
SHUTDOWN=false
for i in $(seq 1 30); do
    if ! kill -0 $SERVER_PID 2>/dev/null; then
        echo "✅ Server shut down cleanly."
        SHUTDOWN=true
        break
    fi
    sleep 1
done

if [ "$SHUTDOWN" != "true" ]; then
    echo "⚠️ Server did not exit in 30s; killing."
    kill -9 $SERVER_PID 2>/dev/null || true
fi

echo "🔍 Verifying boot log indicators..."
if ! grep -q "VoltPur" "$SERVER_LOG"; then
    echo "❌ VoltPur banner or module lines missing from log!"
    exit 1
fi

if [ -d "crash-reports" ] && [ "$(ls -A crash-reports 2>/dev/null)" ]; then
    echo "❌ Crash reports detected!"
    cat crash-reports/*
    exit 1
fi

echo "=================================================="
echo "🎉 VoltPur Headless CI Boot Test PASSED!"
echo "=================================================="
