package com.sharma2464.mediacompression.compress

import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * Copies EXIF/XMP-bearing tags from [source] onto [dest] (e.g. WebP after re-encode).
 * Uses every [ExifInterface] TAG_* constant so camera, lens, GPS, and author fields survive.
 */
object ExifPreserver {
    private val allTagNames: List<String> by lazy {
        ExifInterface::class.java.fields
            .filter { field ->
                field.name.startsWith("TAG_") &&
                    field.type == String::class.java &&
                    !field.name.endsWith("_IFD_POINTER")
            }
            .mapNotNull { runCatching { it.get(null) as? String }.getOrNull() }
            .distinct()
    }

    private val criticalTags = listOf(
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_ORIENTATION,
    )

    fun copy(source: File, dest: File) {
        val src = ExifInterface(source.absolutePath)
        val dst = ExifInterface(dest.absolutePath)
        val tags = (criticalTags + allTagNames).distinct()
        for (tag in tags) {
            src.getAttribute(tag)?.let { dst.setAttribute(tag, it) }
        }
        dst.saveAttributes()
    }
}
