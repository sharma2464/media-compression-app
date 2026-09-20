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

    fun copy(source: File, dest: File) {
        val src = ExifInterface(source)
        val dst = ExifInterface(dest)
        for (tag in allTagNames) {
            src.getAttribute(tag)?.let { dst.setAttribute(tag, it) }
        }
        dst.saveAttributes()
    }
}
