package com.sharma2464.mediacompression.compress

import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertTrue
import org.junit.Test

class ExifPreserverTest {

    @Test
    fun tag_list_includes_lens_and_datetime_fields() {
        val fields = ExifInterface::class.java.fields
            .filter { it.name.startsWith("TAG_") && it.type == String::class.java }
            .map { it.name }
        assertTrue(fields.contains("TAG_DATETIME_ORIGINAL"))
        assertTrue(fields.contains("TAG_MAKE"))
        assertTrue(fields.contains("TAG_MODEL"))
        assertTrue(fields.contains("TAG_LENS_MODEL") || fields.contains("TAG_FOCAL_LENGTH"))
    }
}
