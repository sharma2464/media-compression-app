package com.sharma2464.mediacompression.compress

import java.io.File

object MediaMetadataPreserver {
    /** Filesystem times used by file browsers and some gallery fallbacks. */
    fun restoreFilesystemTimestamps(original: File, output: File) {
        runCatching { output.setLastModified(original.lastModified()) }
    }
}
