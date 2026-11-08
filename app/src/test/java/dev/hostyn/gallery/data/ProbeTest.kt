package dev.hostyn.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ProbeTest {
    private val video = byteArrayOf(0, 0, 0, 24) + "ftypisom".toByteArray() + ByteArray(500) { it.toByte() }

    @Test
    fun plainPhoto() {
        assertEquals(Probe.None, probeBytes(jpeg()))
    }

    @Test
    fun googleMotionPhoto() {
        val xmp = """
            <x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF><rdf:Description GCamera:MotionPhoto="1" GCamera:MotionPhotoVersion="1">
            <Container:Directory><rdf:Seq>
            <rdf:li rdf:parseType="Resource"><Container:Item Item:Mime="image/jpeg" Item:Semantic="Primary" Item:Length="0" Item:Padding="0"/></rdf:li>
            <rdf:li rdf:parseType="Resource"><Container:Item Item:Mime="video/mp4" Item:Semantic="MotionPhoto" Item:Length="${video.size}" Item:Padding="0"/></rdf:li>
            </rdf:Seq></Container:Directory></rdf:Description></rdf:RDF></x:xmpmeta>
        """.trimIndent()
        val photo = jpeg(xmp)
        val probe = probeBytes(photo + video)
        assertEquals(MotionVideo(photo.size.toLong(), video.size.toLong()), probe.motion)
        assertFalse(probe.hdr)
    }

    @Test
    fun olderMicroVideo() {
        val photo = jpeg("""<rdf:Description GCamera:MicroVideo="1" GCamera:MicroVideoVersion="1" GCamera:MicroVideoOffset="${video.size}"/>""")
        assertEquals(MotionVideo(photo.size.toLong(), video.size.toLong()), probeBytes(photo + video).motion)
    }

    @Test
    fun flaggedButNoVideo() {
        // Some editors keep the XMP but drop the video.
        val photo = jpeg("""<rdf:Description GCamera:MicroVideo="1" GCamera:MicroVideoOffset="100"/>""")
        assertNull(probeBytes(photo + ByteArray(100)).motion)
    }

    @Test
    fun samsungMotionPhoto() {
        val photo = jpeg()
        val name = "MotionPhoto_Data".toByteArray()
        val entry = byteArrayOf(0, 0, 0x30, 0x0A) + le(name.size) + name + video
        val directoryStart = photo.size + entry.size
        val directory = "SEFH".toByteArray() + le(107) + le(1) +
            byteArrayOf(0, 0, 0x30, 0x0A) + le(directoryStart - photo.size) + le(entry.size)
        val file = photo + entry + directory + le(directory.size) + "SEFT".toByteArray()
        assertEquals(MotionVideo((photo.size + 8 + name.size).toLong(), video.size.toLong()), probeBytes(file).motion)
    }

    @Test
    fun samsungTrailerWithoutMotion() {
        val photo = jpeg()
        val name = "Image_UTC_Data".toByteArray()
        val entry = byteArrayOf(0, 0, 0x01, 0x0A) + le(name.size) + name + "1700000000000".toByteArray()
        val directory = "SEFH".toByteArray() + le(107) + le(1) + byteArrayOf(0, 0, 0x01, 0x0A) + le(entry.size) + le(entry.size)
        assertNull(probeBytes(photo + entry + directory + le(directory.size) + "SEFT".toByteArray()).motion)
    }

    @Test
    fun ultraHdr() {
        val xmp = """<rdf:Description hdrgm:Version="1.0"><Container:Directory><rdf:Seq>
            <rdf:li><Container:Item Item:Semantic="Primary" Item:Mime="image/jpeg"/></rdf:li>
            <rdf:li><Container:Item Item:Semantic="GainMap" Item:Mime="image/jpeg" Item:Length="1000"/></rdf:li>
            </rdf:Seq></Container:Directory></rdf:Description>"""
        val probe = probeBytes(jpeg(xmp) + ByteArray(1000))
        assertTrue(probe.hdr)
        assertNull(probe.motion)
    }

    @Test
    fun isoGainMap() {
        val app2 = "urn:iso:std:iso:ts:21496:-1\u0000".toByteArray() + byteArrayOf(0, 0, 0, 0)
        assertTrue(probeBytes(jpeg(app2 = app2)).hdr)
    }

    @Test
    fun notAJpeg() {
        assertEquals(Probe.None, probeBytes("GIF89a".toByteArray() + ByteArray(100)))
    }

    private fun probeBytes(bytes: ByteArray): Probe {
        val file = File.createTempFile("probe", ".jpg")
        try {
            file.writeBytes(bytes)
            return RandomAccessFile(file, "r").use { probeJpeg(it.channel) }
        } finally {
            file.delete()
        }
    }

    /** A JPEG's structure: start, an EXIF-like segment, optional XMP and APP2, scan data, end. */
    private fun jpeg(xmp: String? = null, app2: ByteArray? = null): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        segment(out, 0xE1, "Exif\u0000\u0000".toByteArray() + ByteArray(40))
        if (xmp != null) segment(out, 0xE1, "http://ns.adobe.com/xap/1.0/\u0000".toByteArray() + xmp.toByteArray())
        if (app2 != null) segment(out, 0xE2, app2)
        segment(out, 0xDB, ByteArray(65))
        segment(out, 0xDA, ByteArray(10))
        out.write(ByteArray(300) { 0x55 })
        out.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
        return out.toByteArray()
    }

    private fun segment(out: ByteArrayOutputStream, marker: Int, payload: ByteArray) {
        out.write(0xFF)
        out.write(marker)
        out.write((payload.size + 2) shr 8)
        out.write((payload.size + 2) and 0xFF)
        out.write(payload)
    }

    private fun le(value: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
}
