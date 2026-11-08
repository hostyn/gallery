package dev.hostyn.gallery.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers what [probe] found for each photo, so the grid can mark motion photos without reading
 * every file again. Kept in a small file in the cache; an edited photo (new modified time) is read again.
 */
class ProbeCache(context: Context, private val scope: CoroutineScope) {
    private val context = context.applicationContext
    private val file = File(context.cacheDir, "probes.txt")
    private val entries = ConcurrentHashMap<Long, Entry>()
    private val loaded = scope.async(Dispatchers.IO) { load() }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(4)
    private var saveJob: Job? = null

    private data class Entry(val modified: Long, val probe: Probe)

    /** What we already know about this photo, without reading anything. */
    fun peek(media: Media): Probe? =
        if (media.mimeType != "image/jpeg") Probe.None else entries[media.id]?.takeIf { it.modified == media.modified }?.probe

    suspend fun get(media: Media): Probe {
        if (media.mimeType != "image/jpeg") return Probe.None
        // Not in the library (opened from another app): nothing to key it by.
        if (media.id < 0) return probe(context, media)
        loaded.await()
        peek(media)?.let { return it }
        val result = withContext(io) { probe(context, media) }
        entries[media.id] = Entry(media.modified, result)
        scheduleSave()
        return result
    }

    @Synchronized
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(2_000)
            save()
        }
    }

    private fun load() {
        runCatching {
            if (!file.exists()) return
            file.forEachLine { line ->
                val parts = line.split(' ')
                if (parts.size != 5) return@forEachLine
                val id = parts[0].toLongOrNull() ?: return@forEachLine
                val modified = parts[1].toLongOrNull() ?: return@forEachLine
                val offset = parts[3].toLongOrNull() ?: return@forEachLine
                val length = parts[4].toLongOrNull() ?: return@forEachLine
                val motion = if (length > 0) MotionVideo(offset, length) else null
                entries.putIfAbsent(id, Entry(modified, Probe(hdr = parts[2] == "1", motion = motion)))
            }
        }
    }

    private fun save() {
        runCatching {
            val temp = File(file.path + ".tmp")
            temp.bufferedWriter().use { out ->
                for ((id, entry) in entries) {
                    val motion = entry.probe.motion
                    out.write("$id ${entry.modified} ${if (entry.probe.hdr) 1 else 0} ${motion?.offset ?: 0} ${motion?.length ?: 0}\n")
                }
            }
            temp.renameTo(file)
        }
    }
}
