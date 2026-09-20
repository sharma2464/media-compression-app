package com.sharma2464.mediacompression.compress

/**
 * Detects when a reported progress value has stopped advancing for too long — used to bail
 * out of Media3 Transformer exports that deadlock mid-export (see androidx/media#3357),
 * where getProgress() freezes forever with no callback ever firing.
 */
class StallTracker(
    private val stallTimeoutMs: Long,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private var lastProgress = -1
    private var lastChangeMs = nowMs()

    /** Feed the latest progress reading; returns true once it's been unchanged too long. */
    fun onProgress(progress: Int): Boolean {
        if (progress != lastProgress) {
            lastProgress = progress
            lastChangeMs = nowMs()
        }
        return nowMs() - lastChangeMs > stallTimeoutMs
    }

    val lastSeenProgress: Int get() = lastProgress
}
