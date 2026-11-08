package dev.hostyn.gallery.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.nio.channels.FileChannel

/** Where the short video of a motion photo sits inside the JPEG file. */
data class MotionVideo(val offset: Long, val length: Long)

/** What a photo has besides the picture: an HDR gain map, a motion video. */
data class Probe(val hdr: Boolean, val motion: MotionVideo?) {
    companion object {
        val None = Probe(hdr = false, motion = null)
    }
}

/** Reads a photo's metadata to find out whether it is Ultra HDR or a motion photo. Never throws. */
suspend fun probe(context: Context, media: Media): Probe {
    if (media.mimeType != "image/jpeg") return Probe.None
    return withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openFileDescriptor(media.uri, "r")?.use { fd ->
                FileInputStream(fd.fileDescriptor).channel.use(::probeJpeg)
            }
        }.getOrNull() ?: Probe.None
    }
}

/** Copies a motion photo's video to the cache, where the player can read it. Keeps only the latest one. */
suspend fun extractMotionVideo(context: Context, media: Media, video: MotionVideo): File? = withContext(Dispatchers.IO) {
    runCatching {
        val dir = File(context.cacheDir, "motion").apply { mkdirs() }
        val file = File(dir, "${media.id}-${media.modified}.mp4")
        if (file.length() == video.length) return@runCatching file
        dir.listFiles()?.forEach { it.delete() }
        context.contentResolver.openFileDescriptor(media.uri, "r")?.use { fd ->
            FileInputStream(fd.fileDescriptor).channel.use { input ->
                file.outputStream().channel.use { output ->
                    var done = 0L
                    while (done < video.length) {
                        val n = input.transferTo(video.offset + done, video.length - done, output)
                        if (n <= 0) break
                        done += n
                    }
                }
            }
        }
        file.takeIf { it.length() == video.length }
    }.getOrNull()
}

internal fun probeJpeg(channel: FileChannel): Probe {
    val size = channel.size()
    val meta = readJpegMeta(Channels.newInputStream(channel.position(0))) ?: return Probe.None
    val hdr = meta.isoGainMap || meta.xmp.any { "hdrgm:Version" in it || GAIN_MAP_ITEM.containsMatchIn(it) }
    val motion = meta.xmp.firstNotNullOfOrNull { motionVideoOf(it, size) }?.takeIf { isMp4At(channel, it.offset) }
        ?: samsungMotionVideo(channel)
    return Probe(hdr, motion)
}

/** The XMP packets and whether there is an ISO 21496-1 gain map, from the JPEG's header segments. */
internal class JpegMeta(val xmp: List<String>, val isoGainMap: Boolean)

internal fun readJpegMeta(input: InputStream): JpegMeta? {
    val data = DataInputStream(BufferedInputStream(input, 64 * 1024))
    val xmp = mutableListOf<String>()
    var iso = false
    try {
        if (data.readUnsignedShort() != 0xFFD8) return null
        while (true) {
            if (data.readUnsignedByte() != 0xFF) break
            var marker = data.readUnsignedByte()
            while (marker == 0xFF) marker = data.readUnsignedByte()
            // Start of the image data or its end: no more metadata.
            if (marker == 0xDA || marker == 0xD9) break
            if (marker in 0xD0..0xD7 || marker == 0x01) continue
            val length = data.readUnsignedShort() - 2
            if (length < 0) break
            if (marker == 0xE1 || marker == 0xE2) {
                val bytes = ByteArray(length)
                data.readFully(bytes)
                if (marker == 0xE1 && bytes.startsWith(XMP_HEADER)) {
                    xmp += String(bytes, XMP_HEADER.size, bytes.size - XMP_HEADER.size, Charsets.UTF_8)
                } else if (marker == 0xE2 && bytes.startsWith(ISO_GAIN_MAP_HEADER)) {
                    iso = true
                }
            } else {
                var left = length.toLong()
                while (left > 0) {
                    val skipped = data.skip(left)
                    if (skipped <= 0) throw EOFException()
                    left -= skipped
                }
            }
        }
    } catch (_: EOFException) {
        // A cut-off file: keep what we read.
    }
    return JpegMeta(xmp, iso)
}

/**
 * Google's motion photo format: the XMP flags the photo and says how long the video appended
 * at the end of the file is, either as a container item (current) or as an offset (MicroVideo, older).
 */
internal fun motionVideoOf(xmp: String, fileSize: Long): MotionVideo? {
    if (!MOTION_FLAG.containsMatchIn(xmp)) return null
    val length = MICRO_VIDEO_OFFSET.find(xmp)?.groupValues?.get(1)?.toLongOrNull()
        ?: CONTAINER_ITEM.findAll(xmp)
            .map { it.value }
            .firstOrNull { MOTION_ITEM.containsMatchIn(it) }
            ?.let { ITEM_LENGTH.find(it)?.groupValues?.get(1)?.toLongOrNull() }
        ?: return null
    return if (length in 1 until fileSize) MotionVideo(fileSize - length, length) else null
}

/**
 * Samsung's motion photos keep the video in their own trailer ("SEF") at the end of the file:
 * a directory of named entries, one of them "MotionPhoto_Data".
 */
internal fun samsungMotionVideo(channel: FileChannel): MotionVideo? {
    val size = channel.size()
    if (size < 16) return null
    val tail = channel.readAt(size - 8, 8) ?: return null
    if (String(tail, 4, 4, Charsets.US_ASCII) != "SEFT") return null
    val directorySize = tail.leInt(0).toLong()
    val directory = size - 8 - directorySize
    if (directorySize < 12 || directory < 0) return null
    val header = channel.readAt(directory, 12) ?: return null
    if (String(header, 0, 4, Charsets.US_ASCII) != "SEFH") return null
    val count = header.leInt(8)
    if (count !in 1..256) return null
    val entries = channel.readAt(directory + 12, count * 12) ?: return null
    for (i in 0 until count) {
        val back = entries.leInt(i * 12 + 4).toLong()
        val length = entries.leInt(i * 12 + 8).toLong()
        val start = directory - back
        if (start < 0 || length <= 0) continue
        val entryHeader = channel.readAt(start, 8) ?: continue
        val nameLength = entryHeader.leInt(4)
        if (nameLength !in 1..64) continue
        val name = channel.readAt(start + 8, nameLength)?.toString(Charsets.US_ASCII) ?: continue
        if (name != "MotionPhoto_Data") continue
        val video = MotionVideo(start + 8 + nameLength, length - 8 - nameLength)
        return video.takeIf { it.length > 0 && isMp4At(channel, it.offset) }
    }
    return null
}

/** Whether an MP4 starts there: every MP4 opens with an "ftyp" box. */
private fun isMp4At(channel: FileChannel, offset: Long): Boolean =
    channel.readAt(offset + 4, 4)?.toString(Charsets.US_ASCII) == "ftyp"

private fun FileChannel.readAt(position: Long, count: Int): ByteArray? {
    if (position < 0 || position + count > size()) return null
    val buffer = ByteBuffer.allocate(count)
    while (buffer.hasRemaining()) {
        if (read(buffer, position + buffer.position()) <= 0) return null
    }
    return buffer.array()
}

private fun ByteArray.leInt(index: Int) = ByteBuffer.wrap(this, index, 4).order(ByteOrder.LITTLE_ENDIAN).int

private fun ByteArray.startsWith(prefix: ByteArray) =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

private val XMP_HEADER = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII)
private val ISO_GAIN_MAP_HEADER = "urn:iso:std:iso:ts:21496:-1".toByteArray(Charsets.US_ASCII)
private val GAIN_MAP_ITEM = Regex("""Semantic\s*=\s*"GainMap"""")
private val MOTION_FLAG = Regex("""(?:MotionPhoto|MicroVideo)\s*(?:=\s*"|>)\s*1""")
private val MICRO_VIDEO_OFFSET = Regex("""MicroVideoOffset\s*(?:=\s*"|>)\s*(\d+)""")
private val CONTAINER_ITEM = Regex("""<(?:\w+:)?Item\b[^>]*>""")
private val MOTION_ITEM = Regex("""Semantic\s*=\s*"MotionPhoto"""")
private val ITEM_LENGTH = Regex("""Length\s*=\s*"(\d+)"""")
