package com.sharma2464.tindercompression.compress

/**
 * Pure byte/string logic for Google Motion Photo format 1.0
 * (https://developer.android.com/media/platform/motion-photo-format): a JPEG with an
 * MP4 appended to the end, self-described by an XMP `Item:Length` (or legacy
 * `GCamera:MicroVideoOffset`) counted backward from EOF.
 *
 * Only the JPEG's XMP APP1 segment is rewritten (the `Length`/`Offset` numeral, in
 * place, with the segment's own length header corrected to match) — every other byte
 * of the primary image is left untouched, so there's no risk of corrupting the actual
 * photo pixels/EXIF while re-splicing.
 *
 * ponytail: JPEG-based Motion Photos only (HEIC/AVIF use an `mpvd` ISOBMFF box instead
 * of an appended blob — different splice logic, not implemented). No real Pixel-captured
 * sample was available to test against (this device's camera has no Motion Photo mode),
 * so [reassemble] always re-parses its own output and refuses to return a result unless
 * the round trip checks out — see [validate].
 */
object MotionPhotoSplicer {
    private const val XMP_SIGNATURE = "http://ns.adobe.com/xap/1.0/\u0000"
    private val LEGACY_OFFSET = Regex("""GCamera:MicroVideoOffset=["'](\d+)["']""")
    private val CONTAINER_ITEM = Regex("""<Container:Item\b[^>]*?/>""")
    private val ITEM_LENGTH = Regex("""Item:Length=["'](\d+)["']""")

    data class XmpVideoRef(val markerPos: Int, val payloadEnd: Int, val xmpTextStart: Int, val valueRange: IntRange)

    /** Locates the JPEG's XMP APP1 segment and the video-length numeral inside it, if any. */
    fun findVideoLength(bytes: ByteArray): XmpVideoRef? {
        var pos = 2
        while (pos + 3 < bytes.size) {
            if (bytes[pos].toInt() and 0xFF != 0xFF) return null
            val marker = bytes[pos + 1].toInt() and 0xFF
            if (marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7) {
                pos += 2
                continue
            }
            if (marker == 0xD9 || marker == 0xDA) return null // EOI/SOS: XMP must come before this
            val length = ((bytes[pos + 2].toInt() and 0xFF) shl 8) or (bytes[pos + 3].toInt() and 0xFF)
            val payloadStart = pos + 4
            val payloadEnd = pos + 2 + length
            if (payloadEnd > bytes.size) return null
            if (marker == 0xE1) {
                val sigBytes = XMP_SIGNATURE.toByteArray(Charsets.ISO_8859_1)
                if (payloadEnd - payloadStart >= sigBytes.size &&
                    bytes.copyOfRange(payloadStart, payloadStart + sigBytes.size).contentEquals(sigBytes)
                ) {
                    val xmpTextStart = payloadStart + sigBytes.size
                    val xmpText = String(bytes, xmpTextStart, payloadEnd - xmpTextStart, Charsets.UTF_8)
                    val range = findLengthValueRange(xmpText) ?: return null
                    return XmpVideoRef(pos, payloadEnd, xmpTextStart, range)
                }
            }
            pos = payloadEnd
        }
        return null
    }

    private fun findLengthValueRange(xmpText: String): IntRange? {
        for (item in CONTAINER_ITEM.findAll(xmpText)) {
            if (!item.value.contains("Semantic=\"MotionPhoto\"") && !item.value.contains("Semantic='MotionPhoto'")) continue
            val lengthMatch = ITEM_LENGTH.find(item.value) ?: continue
            val group = lengthMatch.groups[1]!!
            return (item.range.first + group.range.first)..(item.range.first + group.range.last)
        }
        LEGACY_OFFSET.find(xmpText)?.let { return it.groups[1]!!.range }
        return null
    }

    fun videoLength(bytes: ByteArray, ref: XmpVideoRef): Int? {
        val xmpText = String(bytes, ref.xmpTextStart, ref.payloadEnd - ref.xmpTextStart, Charsets.UTF_8)
        return xmpText.substring(ref.valueRange.first, ref.valueRange.last + 1).toIntOrNull()
    }

    /**
     * Splits into (primaryImageBytes, videoBytes) using `fileSize - videoLength`, after
     * confirming the split point actually looks like the start of an MP4 (`ftyp` box) —
     * catches a mismatched XMP attribute (e.g. a GainMap's Length) before it does damage.
     */
    fun split(bytes: ByteArray, videoLength: Int): Pair<ByteArray, ByteArray>? {
        val videoStart = bytes.size - videoLength
        if (videoStart <= 0 || videoStart + 8 > bytes.size) return null
        val looksLikeMp4 = String(bytes, videoStart + 4, 4, Charsets.US_ASCII) == "ftyp"
        if (!looksLikeMp4) return null
        return bytes.copyOfRange(0, videoStart) to bytes.copyOfRange(videoStart, bytes.size)
    }

    /**
     * Rewrites the `Length`/`Offset` numeral (and the JPEG segment's own length header)
     * to reflect [newVideoLength], then appends [newVideoBytes]. Returns null if the
     * new XMP payload would overflow a JPEG segment (>65533 bytes) rather than guess.
     */
    fun reassemble(originalPrimary: ByteArray, ref: XmpVideoRef, newVideoLength: Int, newVideoBytes: ByteArray): ByteArray? {
        val xmpText = String(originalPrimary, ref.xmpTextStart, ref.payloadEnd - ref.xmpTextStart, Charsets.UTF_8)
        val newXmpText = xmpText.substring(0, ref.valueRange.first) +
            newVideoLength.toString() +
            xmpText.substring(ref.valueRange.last + 1)

        val sigBytes = XMP_SIGNATURE.toByteArray(Charsets.ISO_8859_1)
        val newPayload = sigBytes + newXmpText.toByteArray(Charsets.UTF_8)
        val newSegmentLength = 2 + newPayload.size
        if (newSegmentLength > 0xFFFF) return null

        val newPrimary = ByteArray(ref.markerPos) { originalPrimary[it] } +
            byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (newSegmentLength ushr 8).toByte(), (newSegmentLength and 0xFF).toByte()) +
            newPayload +
            originalPrimary.copyOfRange(ref.payloadEnd, originalPrimary.size)

        return validate(newPrimary, newVideoBytes.size)?.let { newPrimary + newVideoBytes }
    }

    /**
     * Re-parses [newPrimary] as if it were a fresh file and confirms the rewritten
     * XMP reports exactly [expectedVideoLength] — the safety net in place of a real
     * device sample: if this doesn't hold, the caller must not use the spliced output.
     */
    private fun validate(newPrimary: ByteArray, expectedVideoLength: Int): Unit? {
        val ref = findVideoLength(newPrimary) ?: return null
        val length = videoLength(newPrimary, ref) ?: return null
        return if (length == expectedVideoLength) Unit else null
    }
}
