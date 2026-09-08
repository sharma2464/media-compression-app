package com.sharma2464.tindercompression.compress

import com.sharma2464.tindercompression.settings.CompressionMode
import java.io.File
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Office docs (docx/xlsx/pptx) are zip containers — rewriting every entry at max
 * Deflate is real lossless savings with just [java.util.zip] (no dependency needed).
 * Lossy re-encoding doesn't apply here, so both modes behave the same.
 */
class ZipRecompressor : Compressor {
    override suspend fun compress(input: File, mode: CompressionMode, workDir: File): CompressionResult {
        val output = File(workDir, input.name)
        ZipFile(input).use { zip ->
            ZipOutputStream(output.outputStream()).use { out ->
                out.setLevel(Deflater.BEST_COMPRESSION)
                for (entry in zip.entries()) {
                    out.putNextEntry(ZipEntry(entry.name))
                    zip.getInputStream(entry).use { it.copyTo(out) }
                    out.closeEntry()
                }
            }
        }
        return CompressionResult(output, wasLossless = true)
    }
}
