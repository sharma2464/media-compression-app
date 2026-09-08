# Media Compression

Swipe through your media library and decide what gets compressed:

- **Swipe left** — don't compress, keep as-is
- **Swipe right** — queue for compression
- **Swipe up** — put back in the queue for later review
- **Swipe down** — show full details/metadata for the file

Accepted files are compressed in the background. Every file is backed up to a
`BACKUP/` folder (mirroring the original folder structure) before it's
replaced in place, and metadata/EXIF/timestamps are preserved.

## Compression modes

"Lossless" and "90% smaller" are mutually exclusive for most already-compressed
files (JPEG, MP4, PDF). Pick a mode in Settings:

- **Lossless only** — true bit-exact-content recompression where possible.
  Savings are modest and reported honestly (often nowhere near 90%).
- **Adaptive** — tries lossless first, falls back to high-quality lossy
  re-encoding (WebP/HEVC) to actually reach large size reductions. The app
  always reports which one happened — it never mislabels lossy output as
  lossless.

## Stack

Kotlin + Jetpack Compose, Room, WorkManager, Media3 Transformer (hardware-
accelerated video transcode), Coil, AndroidX ExifInterface, pdfbox-android.
Storage access is via the Storage Access Framework (SAF) — no broad "All
files access" permission required.

## Building

Requires JDK 17 and the Android SDK (compileSdk 34). Open in Android Studio,
or from the CLI:

```
./gradlew assembleDebug
```

## Known limitations (tracked as issues)

- Video has no practical lossless codec on-device; lossless mode currently
  passes video through unchanged rather than faking a lossless re-encode.
- PDF adaptive mode currently does lossless stream recompression only;
  per-image downsampling is tracked separately.
- Timestamp restoration depends on what the underlying SAF storage provider
  allows; not guaranteed on all providers (e.g. some cloud-backed ones).
