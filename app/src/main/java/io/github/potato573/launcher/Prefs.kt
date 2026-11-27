package io.github.potato573.launcher

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

class Prefs(context: Context) {
    val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    var homeApps: List<String>
        get() = readList(KEY_HOME)
        set(value) = writeList(KEY_HOME, value)

    var dockApps: List<String>
        get() = readList(KEY_DOCK)
        set(value) = writeList(KEY_DOCK, value)

    var dockInitialized: Boolean
        get() = sp.getBoolean("dock_initialized", false)
        set(value) = sp.edit().putBoolean("dock_initialized", value).apply()

    val columns: Int get() = sp.getString("columns", "4")?.toIntOrNull()?.coerceIn(3, 7) ?: 4
    val showLabels: Boolean get() = sp.getBoolean("show_labels", true)
    val showDock: Boolean get() = sp.getBoolean("show_dock", true)
    val showClock: Boolean get() = sp.getBoolean("show_clock", true)
    val autoKeyboard: Boolean get() = sp.getBoolean("auto_keyboard", false)

    fun resetLayout() {
        sp.edit().remove(KEY_HOME).remove(KEY_DOCK).putBoolean("dock_initialized", false).apply()
    }

    private fun readList(key: String): List<String> =
        sp.getString(key, "").orEmpty().split('\n').filter { it.isNotBlank() }

    private fun writeList(key: String, list: List<String>) {
        sp.edit().putString(key, list.joinToString("\n")).apply()
    }

    companion object {
        const val KEY_HOME = "home_apps"
        const val KEY_DOCK = "dock_apps"
        const val MAX_DOCK = 5
    }
}
