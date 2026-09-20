package com.sharma2464.mediacompression.compress

import java.io.File
import java.io.InputStream
import java.io.OutputStream

internal fun copyFileWithProgress(
    source: File,
    dest: File,
    onFraction: (Int) -> Unit,
) {
    val total = source.length().coerceAtLeast(1L)
    source.inputStream().use { input ->
        dest.outputStream().use { output ->
            copyStreamWithProgress(input, output, total, onFraction)
        }
    }
}

internal fun copyStreamWithProgress(
    input: InputStream,
    output: OutputStream,
    totalBytes: Long,
    onFraction: (Int) -> Unit,
) {
    val buffer = ByteArray(64 * 1024)
    var copied = 0L
    while (true) {
        val read = input.read(buffer)
        if (read <= 0) break
        output.write(buffer, 0, read)
        copied += read
        val pct = ((copied * 100) / totalBytes).toInt().coerceIn(0, 100)
        onFraction(pct)
    }
    onFraction(100)
}
