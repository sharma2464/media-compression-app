#!/usr/bin/env bash
# Instrumented compress + preview screenshots (reliable). Pulls PNGs into .cache/.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=cache-dir.sh
source "$ROOT/scripts/cache-dir.sh"
PKG=com.sharma2464.mediacompression
OUT="$PREVIEW_CAPTURES/watch-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"

cd "$ROOT"
./gradlew installDebug installDebugAndroidTest -q

adb shell am instrument -w \
  -e class "com.sharma2464.mediacompression.e2e.PreviewScreenshotE2ETest#capture_compress_preview_timeline" \
  "${PKG}.test/androidx.test.runner.AndroidJUnitRunner" | tail -8

adb pull /sdcard/Android/data/$PKG/files/preview_captures/. "$OUT/" 2>/dev/null || true

echo "OUT=$OUT"
ls -la "$OUT" | head -20
