# Media Compression

Android app to browse on-device storage and compress photos and videos with
target-size presets (inspired by [Josh Atticus Compressor](https://github.com/JoshAtticus/Compressor)).

- **Files** — tabbed browser (internal storage, SD when available) with cached listings
- **Compress** — full-screen flow: presets, video/audio options, batch progress, completion preview
- **Settings** — compression mode, storage behavior, theme (auto / light / dark)

Compressed output can be saved as a mirrored `COMPRESSED/` copy or replace-in-place
with backups under `ORIGINALS/` depending on settings.

## Stack

Kotlin, Jetpack Compose, Room, WorkManager, Media3 Transformer (video), Coil,
ExifInterface. **arm64-v8a** only in release builds (~20 MB APK). Optional
**Manage all files** access improves browsing across volumes and reliable timestamps.

## Building

Requires JDK 17 and Android SDK (compileSdk 34).

```bash
./gradlew assembleDebug          # installable debug APK
./gradlew installDebug           # device connected via adb
./gradlew testDebugUnitTest      # unit tests
```

Release builds use R8 (`assembleRelease`) but are **unsigned**; GitHub releases ship
the **debug-signed** APK from `assembleDebug` for sideloading.

## Releases

Published on [GitHub Releases](https://github.com/sharma2464/media-compression-app/releases)
as `media-compression-{version}.apk`.

**Maintainers — local release** (also documented in `.cursor/skills/release/SKILL.md`):

1. Bump `versionName` / `versionCode` in `app/build.gradle.kts`.
2. Commit and push `main`.
3. Run:

```bash
chmod +x scripts/release.sh   # once
./scripts/release.sh
```

This runs tests, builds the APK into `release-out/`, and creates the GitHub release
with `gh`. CI workflow `.github/workflows/release.yml` does the same on push to `main`
when Actions runners are available.

## Known limitations

- Video has no practical lossless on-device path; lossless mode may pass video through unchanged.
- Timestamp restoration depends on the storage provider (some cloud-backed paths are limited).
- Folder size labels in the browser reflect **immediate children** only (avoids heavy tree walks).
