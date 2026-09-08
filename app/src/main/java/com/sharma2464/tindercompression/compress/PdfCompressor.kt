package com.sharma2464.tindercompression.compress

import android.content.Context
import com.sharma2464.tindercompression.settings.CompressionMode
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File
import java.io.FileOutputStream

/**
 * Lossless mode re-saves the PDF, which rewrites internal object streams at max
 * Deflate — real savings on most PDFs (redundant streams, uncompressed metadata).
 *
 * Adaptive mode additionally walks each page's embedded raster images and
 * recompresses them as JPEG at [ADAPTIVE_QUALITY], replacing the XObject in place
 * via PDResources.put — this is where most of the size on image-heavy PDFs lives.
 *
 * ponytail: only top-level page XObjects are visited; images nested inside Form
 * XObjects aren't walked. Upgrade path: recurse into PDFormXObject.resources too.
 */
class PdfCompressor(context: Context) : Compressor {
    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    override suspend fun compress(input: File, mode: CompressionMode, workDir: File): CompressionResult {
        val output = File(workDir, input.name)
        val lossless = mode == CompressionMode.LOSSLESS_ONLY
        PDDocument.load(input).use { doc ->
            if (!lossless) recompressImages(doc)
            FileOutputStream(output).use { doc.save(it) }
        }
        return CompressionResult(output, wasLossless = lossless)
    }

    private fun recompressImages(doc: PDDocument) {
        for (page in doc.pages) {
            recompressResourceImages(doc, page.resources)
        }
    }

    private fun recompressResourceImages(doc: PDDocument, resources: PDResources?) {
        resources ?: return
        for (name in resources.xObjectNames.toList()) {
            if (!resources.isImageXObject(name)) continue
            val image = resources.getXObject(name) as? PDImageXObject ?: continue
            runCatching {
                val bitmap = image.image ?: return@runCatching
                val recompressed = JPEGFactory.createFromImage(doc, bitmap, ADAPTIVE_QUALITY)
                resources.put(name, recompressed)
            }
            // ponytail: a decode/encode failure on one embedded image (e.g. an
            // unsupported color space) just leaves that image untouched rather
            // than failing the whole PDF.
        }
    }

    companion object {
        private const val ADAPTIVE_QUALITY = 0.4f
    }
}
