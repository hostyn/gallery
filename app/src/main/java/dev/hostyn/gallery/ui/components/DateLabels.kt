package dev.hostyn.gallery.ui.components

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import dev.hostyn.gallery.R
import dev.hostyn.gallery.data.DateKind
import dev.hostyn.gallery.data.Section
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A date formatter in the user's locale, from a skeleton like "EEEdMMM" ("Sat, 8 Nov" / "sáb, 8 nov"). */
fun formatter(skeleton: String, locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

fun String.capitalized(locale: Locale) = replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

@Composable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** Header of a day or month in the grid: "Today", "Monday", "Sat, 8 Nov", "November 2025"... */
@Composable
fun sectionLabel(section: Section): String {
    val locale = currentLocale()
    return when (section.kind) {
        DateKind.Today -> stringResource(R.string.today)
        DateKind.Yesterday -> stringResource(R.string.yesterday)
        else -> remember(section.date, section.kind, section.byMonth, locale) {
            val skeleton = when {
                section.byMonth && section.kind == DateKind.ThisYear -> "LLLL"
                section.byMonth -> "LLLLyyyy"
                section.kind == DateKind.ThisWeek -> "EEEE"
                section.kind == DateKind.ThisYear -> "EEEdMMM"
                else -> "EEEdMMMyyyy"
            }
            section.date.format(formatter(skeleton, locale)).capitalized(locale)
        }
    }
}

/** "November 2025", for the fast scroller. */
@Composable
fun monthLabel(ms: Long): String {
    val locale = currentLocale()
    return remember(ms, locale) {
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().withDayOfMonth(1)
            .format(formatter("LLLLyyyy", locale)).capitalized(locale)
    }
}

/** Full date and time for the viewer and the info sheet. */
@Composable
fun fullDate(ms: Long): Pair<String, String> {
    val locale = currentLocale()
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(ms, locale) {
        val time = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        val today = LocalDate.now()
        val date = time.toLocalDate()
        val skeleton = if (date.year == today.year) "EEEEdMMMM" else "EEEEdMMMMyyyy"
        val hours = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
        time.format(formatter(skeleton, locale)).capitalized(locale) to time.format(formatter(hours, locale))
    }
}
