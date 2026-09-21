package com.sharma2464.mediacompression.compress

import android.content.Context
import com.abedelazizshe.lightcompressorlibrary.config.StorageConfiguration
import java.io.File

/** Writes LightCompressor output directly into the pipeline work directory. */
class WorkDirStorageConfiguration(private val workDir: File) : StorageConfiguration {
    override fun createFileToSave(
        context: Context,
        videoFile: File,
        fileName: String,
        shouldSave: Boolean,
    ): File {
        workDir.mkdirs()
        return File(workDir, fileName)
    }
}
