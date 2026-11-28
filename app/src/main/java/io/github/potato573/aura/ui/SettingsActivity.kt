package io.github.potato573.aura.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.github.potato573.aura.AuraApp
import io.github.potato573.aura.R
import io.github.potato573.aura.data.BackupManager
import io.github.potato573.aura.data.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Every control here changes something visible; there are no placeholder settings. */
class SettingsActivity : Activity() {

    private val settings by lazy { AuraApp.of(this).settings }
    private lateinit var defaultSummary: TextView
    private lateinit var hiddenSummary: TextView
    private var themeKey = ""

    private var stopWatchingBlur: () -> Unit = {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        build()
        // Battery saver can switch blur off while this screen is open: redraw to match.
        stopWatchingBlur = Glass.watchAvailability(this) { if (!isDestroyed) recreate() }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopWatchingBlur()
    }

    private fun build() {
        val palette = Palette.from(this, settings)
        themeKey = palette.key
        val ui = SettingsUi(this, palette)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            id = R.id.settings_scroll
            isFillViewport = true
            clipToPadding = false
            addView(list)
        }
        // The window's insets controller only exists once there is content.
        setContentView(scroll)
        ui.applyWindow(scroll, Glass.maxRadius(this, settings))

        list.addView(ui.title(getString(R.string.app_name), getString(R.string.settings_title)))

        var group = ui.group(list)
        val (defaultRow, defSum) = ui.action(getString(R.string.default_launcher), "") {
            AppActions.requestDefaultLauncher(this)
        }
        defaultSummary = defSum
        group.addView(defaultRow)

        list.addView(ui.section(getString(R.string.section_appearance)))
        group = ui.group(list)
        val themes = Settings.Theme.entries
        group.addView(ui.segmented(
            getString(R.string.theme),
            listOf(getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark)),
            themes.indexOf(settings.theme),
        ) { settings.theme = themes[it]; restyleIfNeeded() })
        group.addView(ui.swatches(getString(R.string.accent), Palette.ACCENTS, settings.accent) {
            settings.accent = it
            restyleIfNeeded()
        })
        group.addView(ui.slider(
            getString(R.string.icon_size), settings.iconScale, Settings.MIN_ICON_SCALE, Settings.MAX_ICON_SCALE, 5,
            { getString(R.string.icon_size_value, it) },
        ) { settings.iconScale = it })

        list.addView(ui.section(getString(R.string.section_glass)))
        group = ui.group(list)
        val canBlur = Glass.isBlurAvailable(this)
        group.addView(ui.slider(
            getString(R.string.blur_strength), settings.blurStrength, 0, 100, 10,
            { if (it == 0) getString(R.string.blur_off) else getString(R.string.icon_size_value, it) },
            summary = if (canBlur) getString(R.string.blur_summary) else getString(R.string.blur_unavailable),
            enabled = canBlur,
        ) {
            settings.blurStrength = it
            Glass.setWindowBlur(this, if (it > 0) Glass.maxRadius(this, settings) else 0)
            restyleIfNeeded()
        })
        group.addView(ui.slider(
            getString(R.string.glass_transparency), settings.glassTransparency,
            Settings.MIN_TRANSPARENCY, Settings.MAX_TRANSPARENCY, 5,
            { getString(R.string.icon_size_value, it) },
            summary = getString(R.string.glass_transparency_summary),
        ) {
            settings.glassTransparency = it
            restyleIfNeeded()
        })

        list.addView(ui.section(getString(R.string.section_motion)))
        group = ui.group(list)
        val effects = Settings.PageEffect.entries
        group.addView(ui.segmented(
            getString(R.string.page_effect),
            listOf(getString(R.string.effect_slide), getString(R.string.effect_zoom), getString(R.string.effect_tilt)),
            effects.indexOf(settings.pageEffect),
        ) { settings.pageEffect = effects[it] })
        val speeds = Settings.Speed.entries
        group.addView(ui.segmented(
            getString(R.string.anim_speed),
            listOf(getString(R.string.speed_calm), getString(R.string.speed_normal), getString(R.string.speed_snappy)),
            speeds.indexOf(settings.animSpeed),
        ) { settings.animSpeed = speeds[it] })
        group.addView(ui.toggle(getString(R.string.haptics), getString(R.string.haptics_summary), settings.haptics) {
            settings.haptics = it
        })

        list.addView(ui.section(getString(R.string.section_home)))
        group = ui.group(list)
        group.addView(ui.stepper(getString(R.string.home_columns), settings.homeColumns, Settings.MIN_COLUMNS, Settings.MAX_COLUMNS) {
            settings.homeColumns = it
        })
        group.addView(ui.stepper(getString(R.string.home_rows), settings.homeRows, Settings.MIN_ROWS, Settings.MAX_ROWS) {
            settings.homeRows = it
        })
        group.addView(ui.toggle(getString(R.string.home_labels), checked = settings.homeLabels) { settings.homeLabels = it })
        group.addView(ui.toggle(getString(R.string.show_clock), checked = settings.showClock) { settings.showClock = it })
        group.addView(ui.toggle(getString(R.string.show_search), checked = settings.showSearch) { settings.showSearch = it })
        group.addView(ui.toggle(getString(R.string.show_indicator), getString(R.string.show_indicator_summary), settings.showIndicator) {
            settings.showIndicator = it
        })
        group.addView(ui.toggle(getString(R.string.wallpaper_scroll), getString(R.string.wallpaper_scroll_summary), settings.wallpaperScroll) {
            settings.wallpaperScroll = it
        })
        group.addView(ui.toggle(getString(R.string.hide_nav_bar), getString(R.string.hide_nav_bar_summary), settings.hideNavBar) {
            settings.hideNavBar = it
        })
        group.addView(ui.toggle(getString(R.string.show_dock), checked = settings.showDock) { settings.showDock = it })

        list.addView(ui.section(getString(R.string.section_drawer)))
        group = ui.group(list)
        group.addView(ui.stepper(getString(R.string.drawer_columns), settings.drawerColumns, Settings.MIN_COLUMNS, Settings.MAX_COLUMNS) {
            settings.drawerColumns = it
        })
        group.addView(ui.toggle(getString(R.string.drawer_labels), checked = settings.drawerLabels) {
            settings.drawerLabels = it
        })
        group.addView(ui.toggle(getString(R.string.drawer_keyboard), getString(R.string.drawer_keyboard_summary), settings.drawerKeyboard) {
            settings.drawerKeyboard = it
        })
        val (hiddenRow, hidSum) = ui.action(getString(R.string.hidden_apps), "") {
            startActivity(Intent(this, HiddenAppsActivity::class.java))
        }
        hiddenSummary = hidSum
        group.addView(hiddenRow)

        list.addView(ui.section(getString(R.string.section_system)))
        group = ui.group(list)
        group.addView(ui.action(getString(R.string.backup), getString(R.string.backup_summary)) { startBackup() }.first)
        group.addView(ui.action(getString(R.string.restore), getString(R.string.restore_summary)) { startRestore() }.first)

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            null
        }
        list.addView(ui.footnote(getString(R.string.version, version ?: "?")))
        refreshSummaries()
    }

    /** Theme, accent and glass changes rebuild the screen in place with the new palette. */
    private fun restyleIfNeeded() {
        if (Palette.from(this, settings).key != themeKey) recreate()
    }

    override fun onResume() {
        super.onResume()
        refreshSummaries()
    }

    private fun refreshSummaries() {
        if (!::defaultSummary.isInitialized) return
        defaultSummary.text = getString(
            if (AppActions.isDefaultLauncher(this)) R.string.default_launcher_yes else R.string.default_launcher_no,
        )
        defaultSummary.visibility = android.view.View.VISIBLE
        val hidden = settings.hiddenApps.size
        hiddenSummary.text = if (hidden == 0) getString(R.string.hidden_apps_summary_none) else getString(R.string.hidden_apps_summary, hidden)
        hiddenSummary.visibility = android.view.View.VISIBLE
    }

    private fun startBackup() {
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/json")
            .putExtra(Intent.EXTRA_TITLE, "aura-backup-$stamp.json")
        launch(intent, REQ_BACKUP)
    }

    private fun startRestore() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain", "application/octet-stream"))
        launch(intent, REQ_RESTORE)
    }

    private fun launch(intent: Intent, request: Int) {
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, request)
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AppActions.REQUEST_HOME_ROLE) {
            refreshSummaries()
            return
        }
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        val backup = BackupManager(this)
        when (requestCode) {
            REQ_BACKUP -> backup.export(uri) { ok ->
                Toast.makeText(applicationContext, if (ok) R.string.backup_done else R.string.backup_failed, Toast.LENGTH_SHORT).show()
            }
            REQ_RESTORE -> backup.import(uri) { ok ->
                Toast.makeText(applicationContext, if (ok) R.string.restore_done else R.string.restore_failed, Toast.LENGTH_SHORT).show()
                if (ok && !isDestroyed) recreate()
            }
        }
    }

    companion object {
        private const val REQ_BACKUP = 7
        private const val REQ_RESTORE = 8
    }
}
