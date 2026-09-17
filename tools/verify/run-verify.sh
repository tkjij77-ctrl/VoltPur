#!/usr/bin/env bash
# VoltPur verification harness - compiles the real VoltPur sources against a small
# hand-written API stub set and runs the behaviour assertions in tools/verify/tests.
#
# WHAT THIS PROVES:   the VoltPur sources are internally consistent (they compile),
#                     the cross-class calls exist with the exact signatures used,
#                     and the safe-default / validation / tuning logic behaves as
#                     documented.
# WHAT THIS DOES NOT: prove behaviour inside a running Paper server, prove anything
#                     about performance, or replace a real build
#                     (./gradlew :purpur-server:createPaperclipJar).
#
# Run from anywhere:  tools/verify/run-verify.sh
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
SRC="$ROOT/purpur-server/src/main/java/org/purpurmc/purpur"
OUT="$HERE/out"

# --- find a JDK >= 21 (the project itself builds with Java 25) -----------------
jdk_major() {  # $1 = path to javac -> prints the major version, 0 if unknown
  local raw num major
  raw="$("$1" -version 2>&1 | head -1)"
  num="$(printf '%s' "$raw" | grep -oE '[0-9]+(\.[0-9]+)*' | head -1)"
  [[ -z "$num" ]] && { echo 0; return; }
  major="${num%%.*}"
  [[ "$major" == "1" ]] && major="$(printf '%s' "$num" | cut -d. -f2)"
  echo "$major"
}

JAVAC="${VP_JAVAC:-}"
JAVA="${VP_JAVA:-}"
if [[ -n "$JAVAC" ]]; then
  EXPLICIT_MAJOR="$(jdk_major "$JAVAC")"
  if [[ "$EXPLICIT_MAJOR" -ne 0 && "$EXPLICIT_MAJOR" -lt 21 ]]; then
    echo "ERROR: VP_JAVAC points to Java $EXPLICIT_MAJOR ($JAVAC); this harness needs 21+." >&2
    exit 2
  fi
fi
CANDIDATES=()
[[ -n "$JAVAC" ]] && CANDIDATES+=("$JAVAC")
[[ -n "${JAVA_HOME:-}" ]] && CANDIDATES+=("$JAVA_HOME/bin/javac")
command -v javac >/dev/null 2>&1 && CANDIDATES+=("$(command -v javac)")
for candidate in "$HOME"/.cache/jdk/jdk-*/ /usr/lib/jvm/*/ /opt/*jdk*/; do
  [[ -x "${candidate}bin/javac" ]] && CANDIDATES+=("${candidate}bin/javac")
done

BEST_MAJOR=0
for candidate in "${CANDIDATES[@]:-}"; do
  [[ -n "$candidate" && -x "$candidate" ]] || continue
  major="$(jdk_major "$candidate")"
  if [[ "$major" -ge 21 && "$major" -gt "$BEST_MAJOR" ]]; then
    BEST_MAJOR="$major"; JAVAC="$candidate"; JAVA="$(dirname "$candidate")/java"
  fi
done

if [[ "$BEST_MAJOR" -eq 0 ]]; then
  echo "ERROR: no JDK 21+ found (the project builds with Java 25)." >&2
  echo "       Install one and re-run, or pass it explicitly:" >&2
  echo "         VP_JAVAC=/path/to/jdk/bin/javac tools/verify/run-verify.sh" >&2
  exit 2
fi
[[ -x "$JAVA" ]] || { echo "ERROR: found $JAVAC but no java binary next to it." >&2; exit 2; }
MAJOR="$BEST_MAJOR"

VOLTPUR_FILES=(
  VoltPur.java VoltPurGuard.java VoltPurConfig.java VoltPurPlugin.java VoltPurTuning.java
  VoltPurHardware.java VoltPurModules.java VoltPurDiscord.java VoltPurBenchmark.java
  VoltPurOptimizer.java VoltPurBackup.java VoltPurResourcePack.java VoltPurPerformance.java
  VoltPurWorldCheck.java
)
COMMAND_FILES=( VoltPurCommand.java VoltPurHelp.java VoltPurUpdater.java PAdminCommand.java PluginJarInstaller.java )

rm -rf "$OUT" && mkdir -p "$OUT"
SOURCES="$OUT/sources.txt"
: > "$SOURCES"
find "$HERE/stubs" "$HERE/tests" -name '*.java' >> "$SOURCES"
for f in "${VOLTPUR_FILES[@]}";      do echo "$SRC/$f"                     >> "$SOURCES"; done
for f in "${COMMAND_FILES[@]}";      do echo "$SRC/command/$f"             >> "$SOURCES"; done

echo "== 1/3 compiling $(( $(wc -l < "$SOURCES") )) files (JDK $MAJOR) =="
if ! "$JAVAC" -nowarn -encoding UTF-8 -d "$OUT" @"$SOURCES" 2> "$OUT/compile.log"; then
  echo "COMPILE FAILED:"; grep -E "error" "$OUT/compile.log" | head -30; exit 1
fi
echo "   compile OK ($(find "$OUT" -name '*.class' | wc -l) classes)"

RUNDIR="$(mktemp -d)"
echo "== 2/3 core behaviour checks =="
( cd "$RUNDIR" && "$JAVA" -Dfile.encoding=UTF-8 -cp "$OUT" org.purpurmc.purpur.VerifyCore ); CORE=$?
echo "== 3/3 updater / downloader checks =="
( cd "$RUNDIR" && "$JAVA" -Dfile.encoding=UTF-8 -cp "$OUT" org.purpurmc.purpur.command.VerifyCommand ); CMD=$?
rm -rf "$RUNDIR"

echo
if [[ $CORE -eq 0 && $CMD -eq 0 ]]; then
  echo "RESULT: PASS - cores=$CORE commands=$CMD (compile OK)"
else
  echo "RESULT: FAIL - core suite exit=$CORE, command suite exit=$CMD"
fi
echo "Reminder: this is a static/unit check, not a server test. A real build still needs:"
echo "  ./gradlew applyAllPatches && ./gradlew :purpur-server:createPaperclipJar"
[[ $CORE -eq 0 && $CMD -eq 0 ]] || exit 1
