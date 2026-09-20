---
name: release
description: >-
  Build a release APK locally and publish it to GitHub Releases for
  media-compression-app. Use when the user asks to release, ship, publish an APK,
  cut a version, or update GitHub releases.
---

# Release (local → GitHub)

This repo ships **debug-signed** release APKs (same as `.github/workflows/release.yml`). Suitable for sideload installs; no Play signing keystore is configured.

## Prerequisites

- JDK 17, Android SDK, `adb` optional
- `gh` CLI authenticated (`gh auth status`)
- On `main`, working tree clean after you commit the version bump

## Version

Edit `app/build.gradle.kts` before releasing:

- `versionName` — semver shown to users (e.g. `0.3.1`)
- `versionCode` — monotonic integer; must increase every release

Tag must match: `v{versionName}` (e.g. `v0.3.1`).

## Test gate (required)

With a device connected (`adb devices`):

```bash
./scripts/run-device-tests.sh
```

This runs JVM unit tests, instrumented tests (excluding `@LargeTest`), then large/E2E tests.

## Steps

From the repository root:

```bash
./scripts/release.sh
```

`release.sh` runs unit tests and **requires** a connected device for instrumented + E2E tests before building the APK.

Or manually:

1. `./gradlew testDebugUnitTest assembleDebug`
2. `mkdir -p release-out`
3. `cp app/build/outputs/apk/debug/app-debug.apk "release-out/media-compression-$(grep -oP 'versionName\s*=\s*"\K[^"]+' app/build.gradle.kts).apk"`
4. Commit and push `main` (include the version bump and any release fixes).
5. Create the GitHub release (skip if tag already exists):

```bash
VERSION=$(grep -oP 'versionName\s*=\s*"\K[^"]+' app/build.gradle.kts)
gh release create "v${VERSION}" \
  "release-out/media-compression-${VERSION}.apk" \
  --title "v${VERSION}" \
  --generate-notes
```

## Notes

- Do **not** commit `release-out/` (APK assets are attached only to GitHub Releases).
- `assembleRelease` produces an **unsigned** minified APK; local sideload releases use **debug** APK unless signing is added later.
- If GitHub Actions release workflow fails (runner quota), this local flow is the supported path.
