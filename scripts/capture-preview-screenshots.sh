#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/screenshots/preview-captures/$(date +%Y%m%d-%H%M%S)"
PKG=com.sharma2464.mediacompression
TEST_PKG="${PKG}.test"

cd "$ROOT"
./gradlew installDebug installDebugAndroidTest -q

adb shell pm clear "$PKG" >/dev/null
# Re-allow all-files access after clear (Samsung / AOSP)
adb shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true

adb shell am instrument -w \
  -e class "com.sharma2464.mediacompression.e2e.PreviewScreenshotE2ETest#capture_compress_preview_timeline" \
  "$TEST_PKG/androidx.test.runner.AndroidJUnitRunner" | tail -8

REMOTE="/sdcard/Android/data/$PKG/files/preview_captures"
mkdir -p "$OUT"
adb pull "$REMOTE/." "$OUT/" 2>/dev/null || true
echo "Screenshots: $OUT"
ls -la "$OUT" || true
