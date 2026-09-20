package com.sharma2464.mediacompression.compress

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.min

/** From Josh Atticus Compressor (MIT). */
@UnstableApi
class VolumeAudioProcessor : BaseAudioProcessor() {
    private var volume = 1f

    fun setVolume(volume: Float) {
        this.volume = volume
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val outputEncoding = when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT -> inputAudioFormat.encoding
            else -> C.ENCODING_PCM_16BIT
        }
        if (volume == 1f && outputEncoding == inputAudioFormat.encoding) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, inputAudioFormat.channelCount, outputEncoding)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val remaining = inputBuffer.remaining()
        val output = replaceOutputBuffer(remaining)
        if (volume == 1f) {
            output.put(inputBuffer)
            return
        }
        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> {
                while (inputBuffer.hasRemaining()) {
                    val sample = inputBuffer.short.toInt()
                    val scaled = (sample * volume).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    output.putShort(scaled.toShort())
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                while (inputBuffer.hasRemaining()) {
                    val sample = inputBuffer.float
                    output.putFloat((sample * volume).coerceIn(-1f, 1f))
                }
            }
            else -> output.put(inputBuffer)
        }
    }
}
