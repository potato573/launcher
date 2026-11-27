package io.github.potato573.aura.data

import android.content.Context
import android.content.SharedPreferences
import io.github.potato573.aura.model.ComponentKey
import io.github.potato573.aura.ui.Palette

/**
 * Every user preference, stored locally in SharedPreferences. Each setting listed here is
 * read by the UI; nothing is stored that is not used.
 */
class Settings(context: Context) {

    enum class Theme(val id: String) { SYSTEM("system"), LIGHT("light"), DARK("dark") }

    private val sp: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val listeners = LinkedHashSet<() -> Unit>()
    private val spListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        listeners.toList().forEach { it() }
    }

    init {
        sp.registerOnSharedPreferenceChangeListener(spListener)
    }

    fun addListener(l: () -> Unit) {
        listeners += l
    }

    fun removeListener(l: () -> Unit) {
        listeners -= l
    }

    var theme: Theme
        get() = Theme.entries.firstOrNull { it.id == sp.getString(K_THEME, null) } ?: Theme.SYSTEM
        set(value) = sp.edit().putString(K_THEME, value.id).apply()

    var accent: Int
        get() = sp.getInt(K_ACCENT, Palette.ACCENTS.first())
        set(value) = sp.edit().putInt(K_ACCENT, value).apply()

    var homeColumns: Int
        get() = sp.getInt(K_HOME_COLUMNS, 4).coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        set(value) = sp.edit().putInt(K_HOME_COLUMNS, value.coerceIn(MIN_COLUMNS, MAX_COLUMNS)).apply()

    var homeRows: Int
        get() = sp.getInt(K_HOME_ROWS, 4).coerceIn(MIN_ROWS, MAX_ROWS)
        set(value) = sp.edit().putInt(K_HOME_ROWS, value.coerceIn(MIN_ROWS, MAX_ROWS)).apply()

    var drawerColumns: Int
        get() = sp.getInt(K_DRAWER_COLUMNS, 4).coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        set(value) = sp.edit().putInt(K_DRAWER_COLUMNS, value.coerceIn(MIN_COLUMNS, MAX_COLUMNS)).apply()

    /** Icon size in percent of the default 54dp. */
    var iconScale: Int
        get() = sp.getInt(K_ICON_SCALE, 100).coerceIn(MIN_ICON_SCALE, MAX_ICON_SCALE)
        set(value) = sp.edit().putInt(K_ICON_SCALE, value.coerceIn(MIN_ICON_SCALE, MAX_ICON_SCALE)).apply()

    var homeLabels: Boolean
        get() = sp.getBoolean(K_HOME_LABELS, true)
        set(value) = sp.edit().putBoolean(K_HOME_LABELS, value).apply()

    var showDock: Boolean
        get() = sp.getBoolean(K_SHOW_DOCK, true)
        set(value) = sp.edit().putBoolean(K_SHOW_DOCK, value).apply()

    var showClock: Boolean
        get() = sp.getBoolean(K_SHOW_CLOCK, true)
        set(value) = sp.edit().putBoolean(K_SHOW_CLOCK, value).apply()

    var showSearch: Boolean
        get() = sp.getBoolean(K_SHOW_SEARCH, true)
        set(value) = sp.edit().putBoolean(K_SHOW_SEARCH, value).apply()

    /** Show the keyboard as soon as the app drawer opens. */
    var drawerKeyboard: Boolean
        get() = sp.getBoolean(K_DRAWER_KEYBOARD, false)
        set(value) = sp.edit().putBoolean(K_DRAWER_KEYBOARD, value).apply()

    var hiddenApps: Set<ComponentKey>
        get() = sp.getStringSet(K_HIDDEN, emptySet()).orEmpty().mapNotNull { ComponentKey.parse(it) }.toSet()
        set(value) = sp.edit().putStringSet(K_HIDDEN, value.map { it.serialize() }.toSet()).apply()

    var dockSeeded: Boolean
        get() = sp.getBoolean(K_DOCK_SEEDED, false)
        set(value) = sp.edit().putBoolean(K_DOCK_SEEDED, value).apply()

    /** The "make AURA your home screen" card was dismissed by the user. */
    var defaultPromptDismissed: Boolean
        get() = sp.getBoolean(K_PROMPT_DISMISSED, false)
        set(value) = sp.edit().putBoolean(K_PROMPT_DISMISSED, value).apply()

    fun setHidden(key: ComponentKey, hidden: Boolean) {
        hiddenApps = if (hidden) hiddenApps + key else hiddenApps - key
    }

    /** Values included in a backup. Only user-facing preferences, never internal state. */
    fun export(): Map<String, Any> = linkedMapOf(
        K_THEME to theme.id,
        K_ACCENT to accent,
        K_HOME_COLUMNS to homeColumns,
        K_HOME_ROWS to homeRows,
        K_DRAWER_COLUMNS to drawerColumns,
        K_ICON_SCALE to iconScale,
        K_HOME_LABELS to homeLabels,
        K_SHOW_DOCK to showDock,
        K_SHOW_CLOCK to showClock,
        K_SHOW_SEARCH to showSearch,
        K_DRAWER_KEYBOARD to drawerKeyboard,
    )

    /** Applies backed-up values; unknown keys and wrong types are ignored, ranges are clamped. */
    fun import(values: Map<String, Any>, hidden: Set<ComponentKey>) {
        val e = sp.edit()
        for ((k, v) in values) {
            when (k) {
                K_THEME -> if (v is String && Theme.entries.any { it.id == v }) e.putString(k, v)
                K_ACCENT -> if (v is Int) e.putInt(k, v)
                K_HOME_COLUMNS, K_DRAWER_COLUMNS -> if (v is Int) e.putInt(k, v.coerceIn(MIN_COLUMNS, MAX_COLUMNS))
                K_HOME_ROWS -> if (v is Int) e.putInt(k, v.coerceIn(MIN_ROWS, MAX_ROWS))
                K_ICON_SCALE -> if (v is Int) e.putInt(k, v.coerceIn(MIN_ICON_SCALE, MAX_ICON_SCALE))
                K_HOME_LABELS, K_SHOW_DOCK, K_SHOW_CLOCK, K_SHOW_SEARCH, K_DRAWER_KEYBOARD ->
                    if (v is Boolean) e.putBoolean(k, v)
            }
        }
        e.putStringSet(K_HIDDEN, hidden.map { it.serialize() }.toSet())
        e.putBoolean(K_DOCK_SEEDED, true)
        e.apply()
    }

    companion object {
        private const val FILE = "aura_settings"
        const val K_THEME = "theme"
        const val K_ACCENT = "accent"
        const val K_HOME_COLUMNS = "home_columns"
        const val K_HOME_ROWS = "home_rows"
        const val K_DRAWER_COLUMNS = "drawer_columns"
        const val K_ICON_SCALE = "icon_scale"
        const val K_HOME_LABELS = "home_labels"
        const val K_SHOW_DOCK = "show_dock"
        const val K_SHOW_CLOCK = "show_clock"
        const val K_SHOW_SEARCH = "show_search"
        const val K_DRAWER_KEYBOARD = "drawer_keyboard"
        const val K_HIDDEN = "hidden_apps"
        const val K_DOCK_SEEDED = "dock_seeded"
        const val K_PROMPT_DISMISSED = "default_prompt_dismissed"

        const val MIN_COLUMNS = 3
        const val MAX_COLUMNS = 6
        const val MIN_ROWS = 3
        const val MAX_ROWS = 6
        const val MIN_ICON_SCALE = 80
        const val MAX_ICON_SCALE = 120
    }
}
