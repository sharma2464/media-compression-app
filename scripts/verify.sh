#!/usr/bin/env bash
# Unit + instrumented UI tests + one preview E2E (device required for steps 2–4).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
PKG=com.sharma2464.mediacompression

echo "==> Unit tests"
./gradlew testDebugUnitTest

if ! adb devices | grep -qE 'device$'; then
  echo "ERROR: No adb device connected — connect a device for instrumented tests."
  exit 1
fi

echo "==> Install app + androidTest"
./gradlew installDebug installDebugAndroidTest
adb shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true

echo "==> Instrumented tests (excludes @LargeTest)"
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.notAnnotation=androidx.test.filters.LargeTest

echo "==> Preview stability E2E (@LargeTest)"
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.sharma2464.mediacompression.e2e.PreviewStabilityE2ETest

echo "==> verify.sh finished OK"
