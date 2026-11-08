package dev.hostyn.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class GroupingTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val today = LocalDate.of(2026, 11, 8) // A Sunday

    private fun ms(y: Int, m: Int, d: Int, h: Int = 12) = LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun groupsByDayWithKinds() {
        val dates = listOf(
            ms(2026, 11, 8, 20), ms(2026, 11, 8, 9),
            ms(2026, 11, 7),
            ms(2026, 11, 3),
            ms(2026, 11, 1), // 7 days ago: no longer "this week"
            ms(2026, 3, 14),
            ms(2025, 12, 31, 23), ms(2025, 12, 31, 1),
        )
        val sections = sectionsOf(dates, byMonth = false, zone, today)
        assertEquals(
            listOf(DateKind.Today, DateKind.Yesterday, DateKind.ThisWeek, DateKind.ThisYear, DateKind.ThisYear, DateKind.Older),
            sections.map { it.kind },
        )
        assertEquals(listOf(0 until 2, 2 until 3, 3 until 4, 4 until 5, 5 until 6, 6 until 8), sections.map { it.start until it.end })
        assertEquals(LocalDate.of(2025, 12, 31), sections.last().date)
    }

    @Test
    fun groupsByMonth() {
        val dates = listOf(ms(2026, 11, 8), ms(2026, 11, 1), ms(2026, 10, 31), ms(2025, 11, 20))
        val sections = sectionsOf(dates, byMonth = true, zone, today)
        assertEquals(3, sections.size)
        assertEquals(listOf(2, 1, 1), sections.map { it.size })
        assertEquals(listOf(DateKind.ThisYear, DateKind.ThisYear, DateKind.Older), sections.map { it.kind })
        assertEquals(LocalDate.of(2026, 10, 1), sections[1].date)
    }

    @Test
    fun usesLocalTimeZoneForDays() {
        // 23:30 in Madrid is still the 7th there, although it's the 8th in UTC... and the reverse.
        val lateNight = LocalDateTime.of(2026, 11, 7, 23, 30).atZone(zone).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 11, 7), sectionsOf(listOf(lateNight), false, zone, today).single().date)
    }

    @Test
    fun noPhotos() {
        assertTrue(sectionsOf(emptyList(), false, zone, today).isEmpty())
    }

    @Test
    fun durations() {
        assertEquals("0:07", formatDuration(7_000))
        assertEquals("0:01", formatDuration(600))
        assertEquals("12:45", formatDuration(765_000))
        assertEquals("1:02:03", formatDuration(3_723_000))
    }

    @Test
    fun mimeTypes() {
        assertTrue(mimeMatches("image/*", "image/jpeg"))
        assertTrue(mimeMatches("*/*", "video/mp4"))
        assertTrue(mimeMatches("image/png", "IMAGE/PNG"))
        assertTrue(mimeMatches("image", "image/heic"))
        assertFalse(mimeMatches("image/*", "video/mp4"))
        assertFalse(mimeMatches("image/png", "image/jpeg"))
    }
}
