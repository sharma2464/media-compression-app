package com.sharma2464.mediacompression.compress

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import com.sharma2464.mediacompression.settings.CompressionMode
import java.io.File
import java.io.FileOutputStream

/**
 * WebP re-encode via the platform's native Bitmap/WebP codec — no third-party
 * image library needed. EXIF is read from the original and reapplied to the output.
 */
class PhotoCompressor : Compressor {
    override suspend fun compress(input: File, mode: CompressionMode, workDir: File): CompressionResult {
        val exif = ExifInterface(input)
        val bitmap = BitmapFactory.decodeFile(input.path)
            ?: error("Could not decode image: ${input.path}")

        val output = File(workDir, "${input.nameWithoutExtension}.webp")
        val lossless = mode == CompressionMode.LOSSLESS_ONLY

        FileOutputStream(output).use { out ->
            val format = if (lossless) LOSSLESS_FORMAT else LOSSY_FORMAT
            val quality = if (lossless) 100 else ADAPTIVE_QUALITY
            bitmap.compress(format, quality, out)
        }
        bitmap.recycle()

        // Adaptive mode falls back to the lossless pass if lossy didn't actually shrink it
        val finalLossless = lossless || output.length() >= input.length()
        val finalOutput = if (!lossless && finalLossless) {
            FileOutputStream(output).use { out -> BitmapFactory.decodeFile(input.path)!!.compress(LOSSLESS_FORMAT, 100, out) }
            output
        } else output

        copyExif(exif, finalOutput)
        return CompressionResult(finalOutput, finalLossless)
    }

    private fun copyExif(source: ExifInterface, outputFile: File) {
        val dest = ExifInterface(outputFile)
        for (tag in EXIF_TAGS_TO_COPY) {
            source.getAttribute(tag)?.let { dest.setAttribute(tag, it) }
        }
        dest.saveAttributes()
    }

    companion object {
        @Suppress("DEPRECATION")
        private val LOSSLESS_FORMAT = Bitmap.CompressFormat.WEBP_LOSSLESS
        @Suppress("DEPRECATION")
        private val LOSSY_FORMAT = Bitmap.CompressFormat.WEBP_LOSSY
        private const val ADAPTIVE_QUALITY = 40 // tuned to target ~90% reduction on typical camera JPEGs

        private val EXIF_TAGS_TO_COPY = listOf(
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.TAG_COPYRIGHT,
            ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_IMAGE_DESCRIPTION,
            ExifInterface.TAG_SOFTWARE,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_ISO_SPEED,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_USER_COMMENT,
        )
    }
}
