package dev.hostyn.gallery.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode { System, Light, Dark }

/** App preferences. Each value is Compose state backed by SharedPreferences. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var theme by mutableStateOf(ThemeMode.entries.firstOrNull { it.name == prefs.getString("theme", null) } ?: ThemeMode.System)
        private set
    fun updateTheme(value: ThemeMode) { theme = value; prefs.edit().putString("theme", value.name).apply() }

    var dynamicColor by mutableStateOf(prefs.getBoolean("dynamic_color", true))
        private set
    fun updateDynamicColor(value: Boolean) { dynamicColor = value; prefs.edit().putBoolean("dynamic_color", value).apply() }

    /** Photos per row in the grids, changed by pinching. */
    var columns by mutableIntStateOf(prefs.getInt("columns", 4).coerceIn(MIN_COLUMNS, MAX_COLUMNS))
        private set
    fun updateColumns(value: Int) {
        columns = value.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        prefs.edit().putInt("columns", columns).apply()
    }

    /** Whether we already suggested skipping the system confirmations. */
    var manageMediaHintShown by mutableStateOf(prefs.getBoolean("manage_media_hint", false))
        private set
    fun markManageMediaHintShown() { manageMediaHintShown = true; prefs.edit().putBoolean("manage_media_hint", true).apply() }

    companion object {
        const val MIN_COLUMNS = 2
        const val MAX_COLUMNS = 6
    }
}
