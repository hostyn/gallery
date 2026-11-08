package dev.hostyn.gallery.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** How far back a section is, which decides how its header is written. */
enum class DateKind { Today, Yesterday, ThisWeek, ThisYear, Older }

/** A run of photos taken on the same day (or month), [start] to [end] exclusive in the list. */
data class Section(val date: LocalDate, val kind: DateKind, val byMonth: Boolean, val start: Int, val end: Int) {
    val size get() = end - start
}

/**
 * Splits photos sorted newest first into days, or months when the grid is dense enough that
 * days would be mostly headers.
 */
fun sectionsOf(dates: List<Long>, byMonth: Boolean, zone: ZoneId, today: LocalDate): List<Section> {
    val sections = mutableListOf<Section>()
    var start = 0
    var current: LocalDate? = null
    dates.forEachIndexed { i, ms ->
        val day = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().let { if (byMonth) it.withDayOfMonth(1) else it }
        if (day != current) {
            current?.let { sections += Section(it, kindOf(it, byMonth, today), byMonth, start, i) }
            current = day
            start = i
        }
    }
    current?.let { sections += Section(it, kindOf(it, byMonth, today), byMonth, start, dates.size) }
    return sections
}

private fun kindOf(date: LocalDate, byMonth: Boolean, today: LocalDate): DateKind = when {
    byMonth -> if (date.year == today.year) DateKind.ThisYear else DateKind.Older
    date == today -> DateKind.Today
    date == today.minusDays(1) -> DateKind.Yesterday
    date.isAfter(today.minusDays(7)) && !date.isAfter(today) -> DateKind.ThisWeek
    date.year == today.year -> DateKind.ThisYear
    else -> DateKind.Older
}

/** "0:07", "12:45", "1:02:03". */
fun formatDuration(ms: Long): String {
    val s = (ms + 500) / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** Whether an item matches a requested MIME type: "image" and "video" families, exact types or anything. */
fun mimeMatches(filter: String, mimeType: String): Boolean {
    val (type, sub) = filter.lowercase().split('/', limit = 2).let { it[0] to it.getOrElse(1) { "*" } }
    val (mType, mSub) = mimeType.lowercase().split('/', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    return (type == "*" || type == mType) && (sub == "*" || sub == mSub)
}
