package com.sharma2464.tindercompression.compress

import android.content.Context
import com.sharma2464.tindercompression.settings.CompressionMode
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.io.FileOutputStream

/**
 * Re-saves the PDF, which rewrites internal object streams at max Deflate — real
 * lossless savings on most PDFs (redundant streams, uncompressed metadata, etc.).
 *
 * ponytail: adaptive per-image downsampling (walking embedded XObjects and
 * recompressing raster images at lower resolution/quality) is tracked separately
 * — the pdfbox-android XObject replacement API is fiddly enough to deserve its
 * own change rather than guessed at here. For now ADAPTIVE mode uses the same
 * lossless resave and reports honestly rather than claiming an unearned 90%.
 */
class PdfCompressor(context: Context) : Compressor {
    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    override suspend fun compress(input: File, mode: CompressionMode, workDir: File): CompressionResult {
        val output = File(workDir, input.name)
        PDDocument.load(input).use { doc ->
            FileOutputStream(output).use { doc.save(it) }
        }
        return CompressionResult(output, wasLossless = true)
    }
}
