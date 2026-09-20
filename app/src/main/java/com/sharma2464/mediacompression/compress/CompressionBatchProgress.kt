package com.sharma2464.mediacompression.compress

fun batchOverallFraction(batch: CompressionBatch): Float {
    val done = batch.files.count { it.state == FileCompressionState.DONE }
    val currentPartial = batch.files.getOrNull(batch.currentIndex)
        ?.takeIf { it.state == FileCompressionState.IN_PROGRESS }?.percent ?: 0
    return (done + currentPartial / 100f) / batch.files.size.coerceAtLeast(1)
}
