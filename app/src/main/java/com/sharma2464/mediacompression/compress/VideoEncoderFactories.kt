package com.sharma2464.mediacompression.compress

import android.content.Context
import android.media.MediaCodecList
import android.os.Build
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Codec
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.VideoEncoderSettings

/**
 * MediaTek CBR/VBR encoder selection from Josh Atticus Compressor (MIT).
 * https://github.com/JoshAtticus/Compressor
 */
@UnstableApi
internal object VideoEncoderFactories {
    fun createWrappingFactory(
        context: Context,
        videoBitrateBps: Int,
        videoMimeType: String,
        targetFps: Float?,
        audioPassthrough: Boolean,
    ): Codec.EncoderFactory {
        val cbr = DefaultEncoderFactory.Builder(context)
            .setEnableFallback(true)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(videoBitrateBps)
                    .setBitrateMode(android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
                    .build(),
            )
            .build()

        val vbr = DefaultEncoderFactory.Builder(context)
            .setEnableFallback(true)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(videoBitrateBps)
                    .setBitrateMode(android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                    .build(),
            )
            .build()

        val mediaTek = isMediaTekDeviceOrEncoder(videoMimeType)
        val primary = if (mediaTek) vbr else cbr
        val fallback = if (mediaTek) cbr else vbr

        return object : Codec.EncoderFactory {
            override fun createForAudioEncoding(format: Format): Codec {
                return primary.createForAudioEncoding(format)
            }

            @Throws(ExportException::class)
            override fun createForVideoEncoding(format: Format): Codec {
                var builder = format.buildUpon()
                if (targetFps != null && targetFps > 0f) {
                    builder = builder.setFrameRate(targetFps)
                }
                if (format.colorInfo == null || !ColorInfo.isTransferHdr(format.colorInfo)) {
                    builder = builder.setColorInfo(null)
                }
                val modified = builder.build()
                return try {
                    primary.createForVideoEncoding(modified)
                } catch (e: ExportException) {
                    fallback.createForVideoEncoding(modified)
                }
            }

            override fun audioNeedsEncoding(): Boolean =
                !audioPassthrough && primary.audioNeedsEncoding()

            override fun videoNeedsEncoding(): Boolean = primary.videoNeedsEncoding()
        }
    }

    private fun isMediaTekDeviceOrEncoder(mimeType: String): Boolean {
        try {
            val hardware = Build.HARDWARE.lowercase()
            val board = Build.BOARD.lowercase()
            val manufacturer = Build.MANUFACTURER.lowercase()
            val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.lowercase() else ""
            if (
                hardware.contains("mediatek") || board.contains("mediatek") || manufacturer.contains("mediatek") ||
                soc.contains("mediatek") || soc.contains("dimensity") ||
                hardware.matches(Regex(""".*mt\d{4}.*""")) || board.matches(Regex(""".*mt\d{4}.*"""))
            ) {
                return true
            }
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in codecList.codecInfos) {
                if (!info.isEncoder) continue
                if (info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }) {
                    val name = info.name.lowercase()
                    if (name.contains("mtk") || name.contains("mediatek")) return true
                }
            }
        } catch (_: Exception) {
        }
        return false
    }
}
