package com.sharma2464.mediacompression.compress

/** Josh-style initial target MB from original file size (ratio of original, not fixed chips). */
fun initialCompressTargetMb(sizeBytes: Long, hasVideo: Boolean, videoOrPhotoRatio: Float): Float {
    if (sizeBytes < 64 * 1024) return 0.5f
    val sizeMb = sizeBytes / (1024f * 1024f)
    if (sizeMb <= 0f) return 0.5f
    val cap = sizeMb.coerceAtLeast(0.1f)
    return (sizeMb * videoOrPhotoRatio).coerceIn(0.1f, cap)
}
