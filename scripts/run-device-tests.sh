#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
SERIAL="${ANDROID_SERIAL:-}"
ADB=(adb)
if [[ -n "$SERIAL" ]]; then
  ADB=(adb -s "$SERIAL")
fi

echo "==> Install app + androidTest"
./gradlew installDebug installDebugAndroidTest

PKG=com.sharma2464.mediacompression
"${ADB[@]}" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow || true

echo "==> Unit tests (JVM)"
./gradlew testDebugUnitTest

echo "==> Instrumented tests (excludes @LargeTest — grant All files access on device if prompted)"
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.notAnnotation=androidx.test.filters.LargeTest

echo "==> Large / E2E tests (may open All files access settings — allow on device)"
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.annotation=androidx.test.filters.LargeTest

echo "==> All requested test tiers finished"
