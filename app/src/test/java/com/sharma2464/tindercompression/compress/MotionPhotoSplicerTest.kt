package com.sharma2464.tindercompression.compress

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Builds a synthetic Motion Photo (spec-conformant JPEG + XMP + appended fake MP4) and
 * round-trips it through find -> split -> reassemble, since no real device-captured
 * sample was available (see [MotionPhotoSplicer] doc).
 */
class MotionPhotoSplicerTest {

    private fun fakeMp4(size: Int): ByteArray {
        val box = "ftyp".toByteArray(Charsets.US_ASCII)
        return ByteArray(4) + box + ByteArray(size - 8) { 0x42 }
    }

    private fun buildMotionPhoto(videoBytes: ByteArray, useLegacyFormat: Boolean = false): ByteArray {
        val xmpBody = if (useLegacyFormat) {
            """<x:xmpmeta xmlns:x="adobe:ns:meta/" xmlns:GCamera="http://ns.google.com/photos/1.0/camera/">
                <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                  <rdf:Description GCamera:MicroVideo="1" GCamera:MicroVideoOffset="${videoBytes.size}"/>
                </rdf:RDF>
              </x:xmpmeta>"""
        } else {
            """<x:xmpmeta xmlns:x="adobe:ns:meta/">
                <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                    xmlns:Container="http://ns.google.com/photos/1.0/container/"
                    xmlns:Item="http://ns.google.com/photos/1.0/container/item/">
                  <rdf:Description>
                    <Container:Directory>
                      <rdf:Seq>
                        <rdf:li><Container:Item Item:Mime="image/jpeg" Item:Semantic="Primary" Item:Length="0"/></rdf:li>
                        <rdf:li><Container:Item Item:Mime="video/mp4" Item:Semantic="MotionPhoto" Item:Length="${videoBytes.size}"/></rdf:li>
                      </rdf:Seq>
                    </Container:Directory>
                  </rdf:Description>
                </rdf:RDF>
              </x:xmpmeta>"""
        }
        val sig = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.ISO_8859_1)
        val payload = sig + xmpBody.toByteArray(Charsets.UTF_8)
        val segLen = 2 + payload.size
        val app1 = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (segLen ushr 8).toByte(), (segLen and 0xFF).toByte()) + payload

        val soi = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        val fakeScanData = ByteArray(32) { 0x11 }
        val eoi = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        val primary = soi + app1 + fakeScanData + eoi
        return primary + videoBytes
    }

    @Test
    fun `finds and splits new Container-Directory format`() {
        val video = fakeMp4(200)
        val file = buildMotionPhoto(video)

        val ref = MotionPhotoSplicer.findVideoLength(file)
        assertNotNull(ref)
        val length = MotionPhotoSplicer.videoLength(file, ref!!)
        assertEquals(200, length)

        val (primary, splitVideo) = MotionPhotoSplicer.split(file, length!!)!!
        assertArrayEquals(video, splitVideo)
        assertEquals(file.size - 200, primary.size)
    }

    @Test
    fun `finds legacy MicroVideoOffset format`() {
        val video = fakeMp4(150)
        val file = buildMotionPhoto(video, useLegacyFormat = true)

        val ref = MotionPhotoSplicer.findVideoLength(file)
        assertNotNull(ref)
        assertEquals(150, MotionPhotoSplicer.videoLength(file, ref!!))
    }

    @Test
    fun `reassemble round-trips with a differently-sized recompressed video`() {
        val originalVideo = fakeMp4(5000)
        val file = buildMotionPhoto(originalVideo)
        val ref = MotionPhotoSplicer.findVideoLength(file)!!
        val (primary, _) = MotionPhotoSplicer.split(file, MotionPhotoSplicer.videoLength(file, ref)!!)!!

        val newVideo = fakeMp4(1234) // simulates a shrunk, re-encoded video
        val spliced = MotionPhotoSplicer.reassemble(primary, ref, newVideo.size, newVideo)
        assertNotNull(spliced)

        // The spliced file must itself parse back to the new video, byte for byte.
        val ref2 = MotionPhotoSplicer.findVideoLength(spliced!!)!!
        val length2 = MotionPhotoSplicer.videoLength(spliced, ref2)!!
        assertEquals(1234, length2)
        val (_, roundTrippedVideo) = MotionPhotoSplicer.split(spliced, length2)!!
        assertArrayEquals(newVideo, roundTrippedVideo)
    }

    @Test
    fun `rejects a split point that does not look like an MP4`() {
        val notMp4 = ByteArray(100) { 0x00 }
        val file = buildMotionPhoto(notMp4)
        val ref = MotionPhotoSplicer.findVideoLength(file)!!
        val length = MotionPhotoSplicer.videoLength(file, ref)!!
        assertNull(MotionPhotoSplicer.split(file, length))
    }

    @Test
    fun `returns null for a plain JPEG with no Motion Photo XMP`() {
        val plainJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        assertNull(MotionPhotoSplicer.findVideoLength(plainJpeg))
    }
}
