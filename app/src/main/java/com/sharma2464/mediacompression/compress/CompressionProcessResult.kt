package com.sharma2464.mediacompression.compress

import java.io.File

data class CompressionProcessResult(
    val destinationFile: File,
    val originalBytes: Long,
    val compressedBytes: Long,
    val isVideo: Boolean,
)
