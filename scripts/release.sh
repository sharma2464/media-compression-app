#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION_NAME=$(grep -oP 'versionName\s*=\s*"\K[^"]+' app/build.gradle.kts)
TAG="v${VERSION_NAME}"
APK_NAME="media-compression-${VERSION_NAME}.apk"

echo "==> Unit tests"
./gradlew testDebugUnitTest

if command -v adb >/dev/null && adb devices | grep -qE 'device$'; then
  echo "==> Instrumented + E2E tests (device connected)"
  ./scripts/run-device-tests.sh
else
  echo "WARN: No adb device — skipping instrumented/E2E tests. Connect a device before release."
  exit 1
fi

echo "==> Assemble debug APK (release artifact for this repo)"
./gradlew assembleDebug

mkdir -p release-out
cp "app/build/outputs/apk/debug/app-debug.apk" "release-out/${APK_NAME}"
echo "==> Built release-out/${APK_NAME}"

if gh release view "$TAG" &>/dev/null; then
  echo "GitHub release $TAG already exists. Upload asset manually if needed:"
  echo "  gh release upload $TAG release-out/${APK_NAME} --clobber"
  exit 0
fi

if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "Uncommitted changes — commit and push main before creating the GitHub release."
  exit 1
fi

echo "==> Create GitHub release $TAG"
gh release create "$TAG" \
  "release-out/${APK_NAME}" \
  --title "$TAG" \
  --generate-notes

echo "Done: https://github.com/$(gh repo view --json nameWithOwner -q .nameWithOwner)/releases/tag/${TAG}"
