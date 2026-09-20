package com.sharma2464.mediacompression.compress

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File

/**
 * Re-attaches container metadata (creation date, location, device/camera tags, chapters)
 * from the original video onto a re-encoded file so gallery apps keep the correct timeline.
 */
internal object VideoMetadataPreserver {
    fun mergeEncodedWithOriginalMetadata(original: File, encoded: File, workDir: File): File {
        val merged = File(workDir, "${encoded.nameWithoutExtension}_meta.mp4")
        merged.delete()
        val command = arrayOf(
            "-y",
            "-i", encoded.absolutePath,
            "-i", original.absolutePath,
            "-map", "0",
            "-map_metadata", "1",
            "-map_chapters", "1",
            "-c", "copy",
            "-movflags", "use_metadata_tags",
            merged.absolutePath,
        )
        val session = FFmpegKit.executeWithArguments(command)
        if (!ReturnCode.isSuccess(session.returnCode) || !merged.isFile || merged.length() <= 0) {
            Log.w(TAG, "metadata merge failed; using encoded file without remux")
            merged.delete()
            return encoded
        }
        encoded.delete()
        return merged
    }

    private const val TAG = "VideoMetadataPreserver"
}
