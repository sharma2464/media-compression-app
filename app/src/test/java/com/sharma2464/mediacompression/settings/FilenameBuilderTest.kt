package com.sharma2464.mediacompression.settings

import org.junit.Assert.assertTrue
import org.junit.Test

class FilenameBuilderTest {

    @Test
    fun preview_includes_original_and_compressed_tokens() {
        val name = FilenameBuilder.preview(
            listOf(
                FilenameSegment.Token("original_name"),
                FilenameSegment.Text("_"),
                FilenameSegment.Token("compressed"),
            ),
        )
        assertTrue(name.contains("Vacation_clip"))
        assertTrue(name.contains("Compressed"))
    }
}
