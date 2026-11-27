package io.github.potato573.aura.ui

import android.app.Activity
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.Toast
import io.github.potato573.aura.AuraApp
import io.github.potato573.aura.R
import io.github.potato573.aura.anim.Spring
import io.github.potato573.aura.anim.SpringAnimator
import io.github.potato573.aura.data.AppEntry
import io.github.potato573.aura.model.AppItem
import io.github.potato573.aura.model.ComponentKey
import io.github.potato573.aura.model.DropLogic
import io.github.potato573.aura.model.FolderItem
import io.github.potato573.aura.model.HomeItem
import io.github.potato573.aura.model.HomeLayout
import io.github.potato573.aura.util.Edges
import io.github.potato573.aura.util.dp
import io.github.potato573.aura.util.imeBottomCompat
import io.github.potato573.aura.util.lerp
import io.github.potato573.aura.util.progress
import io.github.potato573.aura.util.systemBarsCompat
import kotlin.math.abs

/**
 * The home screen. Framework [Activity] (no AppCompat) to keep startup lean.
 *
 * Rendering is event-driven: nothing animates or redraws unless the user touches the screen,
 * the app list changes or the clock ticks (TextClock only ticks while visible).
 */
class LauncherActivity : Activity(), LauncherRoot.Host {

    private val app by lazy { AuraApp.of(this) }
    private val settings get() = app.settings
    private val store get() = app.layoutStore
    private val repo get() = app.apps
    private val icons get() = app.icons
    private lateinit var actions: AppActions

    private lateinit var root: LauncherRoot
    private lateinit var home: HomeView
    private lateinit var drawer: DrawerView
    private lateinit var palette: Palette

    private var popup: PopupMenuView? = null
    private var openFolder: FolderView? = null
    private lateinit var drag: DragController
    private var resumed = false
    private var launchedFromDrawer = false
    private var wallpaperDarkText = false
    private var navInsets = Edges.NONE
    private val main = Handler(Looper.getMainLooper())

    // ---- Drawer motion ----------------------------------------------------------------

    override var drawerProgress = 0f
        private set

    private val drawerSpring by lazy {
        SpringAnimator(Spring.STIFFNESS_MEDIUM, 0.9f, 0.0005f, root) { applyDrawerProgress(it) }
    }

    // ---- Lifecycle --------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actions = AppActions(this)
        palette = Palette.from(this, settings)
        setupWindow()

        root = LauncherRoot(this)
        home = HomeView(this)
        drawer = DrawerView(this, icons)
        root.addView(home, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(drawer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.host = this
        drag = DragController(root, dragHost)
        root.touchInterceptor = { drag.onTouch(it) }
        setContentView(root)
        // Keep focus off the (hidden) search field until the drawer is opened.
        root.isFocusableInTouchMode = true
        root.requestFocus()

        wireHome()
        wireDrawer()
        root.setOnApplyWindowInsetsListener { _, insets -> applyInsets(insets); insets }
        root.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) applyDrawerProgress(drawerProgress)
        }

        applySettings()
        applyDrawerProgress(0f)

        settings.addListener(settingsListener)
        store.addListener(layoutListener)
        repo.addListener(appsListener)
        repo.start()
        observeWallpaper()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        updateDefaultPrompt()
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        store.flush()
    }

    override fun onStop() {
        super.onStop()
        // Coming back from an app should land on home, not the drawer it was opened from.
        if (launchedFromDrawer) {
            launchedFromDrawer = false
            closeDrawer(animate = false)
        }
        popup?.dismiss()
        openFolder?.close(animate = false)
    }

    override fun onDestroy() {
        super.onDestroy()
        settings.removeListener(settingsListener)
        store.removeListener(layoutListener)
        repo.removeListener(appsListener)
        drawerSpring.cancel()
        if (Build.VERSION.SDK_INT >= 27) {
            try {
                WallpaperManager.getInstance(this).removeOnColorsChangedListener(wallpaperListener as WallpaperManager.OnColorsChangedListener)
            } catch (e: Exception) {
                // Not registered (e.g. wallpaper service unavailable).
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action != Intent.ACTION_MAIN) return
        // Home button. If we were already in front, step back one level; otherwise reset
        // silently so the user returns to a clean home screen.
        val animate = resumed
        when {
            drag.isActive -> Unit
            popup != null -> popup?.dismiss()
            openFolder != null -> openFolder?.close(animate)
            drawerProgress > 0f -> closeDrawer(animate)
            home.workspace.currentPage != 0 -> home.workspace.snapToPage(0, animate)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // A launcher never finishes on back.
        when {
            drag.isActive -> Unit
            popup != null -> popup?.dismiss()
            openFolder != null -> openFolder?.close()
            drawerProgress > 0f -> closeDrawer()
            home.workspace.currentPage != 0 -> home.workspace.snapToPage(0)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Dark/light switch of the system: refresh colours in place, no activity restart.
        applySettings()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AppActions.REQUEST_HOME_ROLE) updateDefaultPrompt()
    }

    // ---- Window & insets --------------------------------------------------------------

    private fun setupWindow() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }

    private fun applyInsets(insets: WindowInsets) {
        val bars = insets.systemBarsCompat()
        val navBottom = if (Build.VERSION.SDK_INT >= 30) {
            bars.bottom
        } else {
            @Suppress("DEPRECATION")
            insets.stableInsetBottom
        }
        navInsets = Edges(bars.left, bars.top, bars.right, navBottom)
        home.setPadding(bars.left, bars.top, bars.right, navBottom)
        drawer.setPadding(bars.left, 0, bars.right, 0)
        drawer.setInsets(bars.top, maxOf(navBottom, insets.imeBottomCompat()))
    }

    private var lastLightBars: Boolean? = null

    private fun updateSystemBars() {
        val lightBars = if (drawerProgress > 0.5f) !palette.isDark else wallpaperDarkText
        // Called every animation frame: only talk to the window when the value flips.
        if (lightBars == lastLightBars) return
        lastLightBars = lightBars
        if (Build.VERSION.SDK_INT >= 30) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (lightBars) mask else 0, mask)
        } else {
            @Suppress("DEPRECATION")
            val base = window.decorView.systemUiVisibility and
                (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR).inv()
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (lightBars) {
                base or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                base
            }
        }
    }

    // ---- Wallpaper contrast -----------------------------------------------------------

    private val wallpaperListener: Any? = if (Build.VERSION.SDK_INT >= 27) {
        WallpaperManager.OnColorsChangedListener { colors, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) onWallpaperColors(colors)
        }
    } else {
        null
    }

    private fun observeWallpaper() {
        if (Build.VERSION.SDK_INT < 27) return
        val wm = WallpaperManager.getInstance(this)
        try {
            wm.addOnColorsChangedListener(wallpaperListener as WallpaperManager.OnColorsChangedListener, main)
        } catch (e: Exception) {
            return
        }
        // getWallpaperColors is a binder call that may compute colours: keep it off the UI thread.
        app.io.post {
            val colors = try {
                wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            } catch (e: Exception) {
                null
            }
            main.post { if (!isDestroyed) onWallpaperColors(colors) }
        }
    }

    private fun onWallpaperColors(colors: WallpaperColors?) {
        val dark = when {
            colors == null || Build.VERSION.SDK_INT < 27 -> false
            // The system's own hint (public since Android 12).
            Build.VERSION.SDK_INT >= 31 -> colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
            // Older: a clearly light dominant colour means dark text reads better.
            else -> colors.primaryColor.luminance() > 0.62f
        }
        if (dark == wallpaperDarkText) return
        wallpaperDarkText = dark
        applyHomeColors()
        bindHome(store.layout)
    }

    // ---- Settings ---------------------------------------------------------------------

    private val settingsListener: () -> Unit = { applySettings() }

    private fun baseIconSize() = dp(54) * settings.iconScale / 100

    private fun applySettings() {
        palette = Palette.from(this, settings)
        lastLightBars = null
        drawer.applyPalette(palette)
        drawer.setColumns(settings.drawerColumns)
        drawer.setIconSize(baseIconSize())
        icons.setSize(baseIconSize())
        home.dock.iconSize = baseIconSize()
        home.setSections(settings.showClock, settings.showSearch, settings.showDock)
        applyHomeColors()
        val cols = settings.homeColumns
        val rows = settings.homeRows
        // A smaller grid must never lose items: reflow moves them to free cells/pages.
        store.update { it.reflow(cols, rows) }
        bindHome(store.layout)
        refreshDrawerApps()
        updateSystemBars()
    }

    private fun applyHomeColors() {
        home.applyWallpaperContrast(wallpaperDarkText, palette)
    }

    // ---- Data binding -----------------------------------------------------------------

    private val layoutListener: (HomeLayout) -> Unit = { bindHome(it) }
    private val appsListener: (List<AppEntry>) -> Unit = { list ->
        bindHome(store.layout)
        refreshDrawerApps()
        // Warm icons: home first (visible right away), then the drawer.
        val layoutKeys = store.layout.allKeys()
        icons.preload(list.filter { it.key in layoutKeys } + list.filter { it.key !in layoutKeys })
    }

    private fun refreshDrawerApps() {
        val hidden = settings.hiddenApps
        val all = repo.apps
        drawer.setApps(all.filter { it.key !in hidden }, all)
    }

    private fun homeLabelColor() = if (wallpaperDarkText) 0xFF15161A.toInt() else IconView.LABEL_ON_WALLPAPER
    private fun folderTileColor() = if (wallpaperDarkText) 0x40FFFFFF else 0x33FFFFFF

    private fun ensurePages(layout: HomeLayout) {
        val ws = home.workspace
        val pageCount = maxOf(1, layout.pageCount)
        while (ws.childCount < pageCount) {
            ws.addView(CellLayout(this).apply {
                columns = settings.homeColumns
                rows = settings.homeRows
                setPadding(dp(8), dp(8), dp(8), 0)
                isLongClickable = true
                setOnLongClickListener { v -> showHomeMenu(v); true }
            })
        }
        home.indicator.count = ws.childCount
    }

    private fun bindHome(layout: HomeLayout) {
        if (!::home.isInitialized) return
        // While an icon is in the air the grid must stay as the user sees it; only new pages
        // are added. The full rebind happens when the drag ends.
        if (::drag.isInitialized && (drag.isActive || drag.isArmed)) {
            ensurePages(layout)
            return
        }
        val ws = home.workspace
        val cols = settings.homeColumns
        val rows = settings.homeRows
        val pageCount = maxOf(1, layout.pageCount)
        ensurePages(layout)
        while (ws.childCount > pageCount) ws.removeViewAt(ws.childCount - 1)
        val iconSize = fittedHomeIconSize()
        ws.pages.forEachIndexed { index, page ->
            page.columns = cols
            page.rows = rows
            page.setPadding(dp(8), dp(8), dp(8), 0)
            page.removeAllViews()
            for (item in layout.itemsOn(index)) {
                val view = createHomeIcon(item, iconSize) ?: continue
                page.addView(view, CellLayout.LayoutParams(item.cell.x, item.cell.y))
            }
        }
        home.indicator.count = pageCount
        if (ws.currentPage >= pageCount) ws.snapToPage(pageCount - 1, animate = false)
        bindDock(layout)
    }

    /** Icons shrink if the user picked more rows/columns than comfortably fit the screen. */
    private fun fittedHomeIconSize(): Int {
        val base = baseIconSize()
        val page = home.workspace.pages.firstOrNull() ?: return base
        if (page.cellWidth <= 0 || page.cellHeight <= 0) return base
        val labelSpace = if (settings.homeLabels) dp(26) else dp(8)
        return minOf(base, page.cellWidth - dp(12), page.cellHeight - labelSpace).coerceAtLeast(dp(32))
    }

    private fun createHomeIcon(item: HomeItem, iconSize: Int): IconView? {
        val v = IconView(this)
        v.iconSize = iconSize
        v.showLabel = settings.homeLabels
        v.setColors(homeLabelColor(), onWallpaper = true, folderColor = folderTileColor())
        v.item = item
        when (item) {
            is AppItem -> {
                val entry = repo.get(item.key) ?: return null
                v.bindApp(entry, icons)
                v.setOnClickListener { launch(entry, v, fromDrawer = false) }
                v.setOnLongClickListener { showAppMenu(entry, v, homeItem = item, inDock = false); true }
            }
            is FolderItem -> {
                val apps = item.apps.mapNotNull { repo.get(it) }
                if (apps.isEmpty()) return null
                v.bindFolder(item.title.ifEmpty { getString(R.string.folder_default_name) }, apps, icons)
                v.setOnClickListener { openFolder(item, v) }
                v.setOnLongClickListener { showFolderMenu(item, v); true }
            }
        }
        return v
    }

    private fun bindDock(layout: HomeLayout) {
        val dock = home.dock
        dock.removeAllViews()
        for (key in layout.dock) {
            val entry = repo.get(key) ?: continue
            val v = IconView(this)
            v.iconSize = baseIconSize()
            v.showLabel = false
            v.item = key
            v.bindApp(entry, icons)
            v.setColors(homeLabelColor(), onWallpaper = true)
            v.setOnClickListener { launch(entry, v, fromDrawer = false) }
            v.setOnLongClickListener { showAppMenu(entry, v, homeItem = null, inDock = true); true }
            dock.addView(v)
        }
        // An empty dock stays visible (as a quiet shelf) so apps can be dragged into it.
        dock.visibility = if (settings.showDock) View.VISIBLE else View.GONE
    }

    private fun wireHome() {
        home.time.setOnClickListener { actions.openAlarms() }
        home.date.setOnClickListener { actions.openCalendar() }
        home.searchPill.setOnClickListener { openDrawer(withKeyboard = true) }
        home.clockBlock.isLongClickable = true
        home.clockBlock.setOnLongClickListener { v -> showHomeMenu(v); true }
        home.workspace.onScroll = { pos -> home.indicator.position = pos }
        home.workspace.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob ->
            if (r - l != or_ - ol || b - t != ob - ot) main.post { bindHome(store.layout) }
        }
        home.promptAction.setOnClickListener { AppActions.requestDefaultLauncher(this) }
        home.promptClose.setOnClickListener {
            settings.defaultPromptDismissed = true
            updateDefaultPrompt()
        }
    }

    private fun updateDefaultPrompt() {
        val show = !settings.defaultPromptDismissed && !AppActions.isDefaultLauncher(this)
        home.prompt.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun wireDrawer() {
        drawer.onLaunch = { entry, v -> launch(entry, v, fromDrawer = true) }
        drawer.onLongPress = { entry, v -> showAppMenu(entry, v, homeItem = null, inDock = false, fromDrawer = true) }
        drawer.onWebSearch = { q ->
            if (q.isNotEmpty()) {
                launchedFromDrawer = true
                actions.openWebSearch(q)
            }
        }
    }

    // ---- Actions ----------------------------------------------------------------------

    private fun launch(entry: AppEntry, from: View, fromDrawer: Boolean) {
        if (actions.launch(entry, from)) {
            if (fromDrawer) launchedFromDrawer = true
        }
    }

    private fun anchorRect(v: View): Rect {
        val r = Rect()
        if (v is IconView) v.iconBounds(r) else r.set(0, 0, v.width, v.height)
        val vLoc = IntArray(2)
        val rootLoc = IntArray(2)
        v.getLocationInWindow(vLoc)
        root.getLocationInWindow(rootLoc)
        r.offset(vLoc[0] - rootLoc[0], vLoc[1] - rootLoc[1])
        return r
    }

    private fun showAppMenu(
        entry: AppEntry,
        v: IconView,
        homeItem: AppItem?,
        inDock: Boolean,
        fromDrawer: Boolean = false,
        folder: FolderItem? = null,
    ) {
        if (popup != null || drag.isActive) return
        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val shortcuts = actions.shortcuts(entry).map { info ->
            PopupMenuView.Entry(actions.shortcutIcon(info), info.shortLabel ?: "", tintIcon = false) {
                launchedFromDrawer = fromDrawer
                actions.startShortcut(info, v)
            }
        }
        val list = ArrayList<PopupMenuView.Entry>()
        val layout = store.layout
        when {
            fromDrawer && !layout.contains(entry.key) -> list += PopupMenuView.Entry(getDrawable(R.drawable.ic_add), getString(R.string.add_to_home)) {
                addToHome(entry.key)
            }
            homeItem != null -> list += PopupMenuView.Entry(getDrawable(R.drawable.ic_remove), getString(R.string.remove_from_home)) {
                store.update { it.remove(homeItem.id) }
            }
            inDock -> list += PopupMenuView.Entry(getDrawable(R.drawable.ic_remove), getString(R.string.remove_from_home)) {
                store.update { it.removeFromDock(entry.key) }
            }
            folder != null -> list += PopupMenuView.Entry(getDrawable(R.drawable.ic_remove), getString(R.string.remove_from_home)) {
                openFolder?.close(animate = false)
                store.update { it.removeFromFolder(folder.id, entry.key, settings.homeColumns, settings.homeRows, toGrid = false) }
            }
        }
        list += PopupMenuView.Entry(getDrawable(R.drawable.ic_info), getString(R.string.app_info)) { actions.openAppInfo(entry) }
        if (fromDrawer) {
            list += PopupMenuView.Entry(getDrawable(R.drawable.ic_hide), getString(R.string.hide_app)) { hideApp(entry) }
        }
        if (actions.canUninstall(entry)) {
            list += PopupMenuView.Entry(getDrawable(R.drawable.ic_delete), getString(R.string.uninstall)) { actions.uninstall(entry) }
        }
        showPopup(anchorRect(v), shortcuts, list)
        val source = when {
            homeItem != null -> DropLogic.Source.Grid(homeItem.id)
            inDock -> DropLogic.Source.Dock(entry.key)
            folder != null -> DropLogic.Source.InFolder(folder.id, entry.key)
            fromDrawer && !layout.contains(entry.key) -> DropLogic.Source.External(entry.key)
            else -> null
        }
        if (source != null) drag.arm(source, v, icons.peek(entry.key), root.lastTouchX, root.lastTouchY)
    }

    private fun showFolderMenu(folder: FolderItem, v: IconView) {
        if (popup != null || drag.isActive) return
        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        showPopup(anchorRect(v), emptyList(), listOf(
            PopupMenuView.Entry(getDrawable(R.drawable.ic_remove), getString(R.string.remove_from_home)) {
                store.update { it.remove(folder.id) }
            },
        ))
        drag.arm(DropLogic.Source.Grid(folder.id), v, null, root.lastTouchX, root.lastTouchY)
    }

    private fun openFolder(folder: FolderItem, v: IconView) {
        if (openFolder != null || drag.isActive) return
        val apps = folder.apps.mapNotNull { repo.get(it) }
        if (apps.isEmpty()) return
        val fv = FolderView(this, palette, icons, folder, apps, baseIconSize())
        fv.onLaunch = { entry, iv -> launch(entry, iv, fromDrawer = false) }
        fv.onLongPress = { entry, iv -> showAppMenu(entry, iv, homeItem = null, inDock = false, folder = folder) }
        fv.onRename = { title -> store.update { it.renameFolder(folder.id, title) } }
        fv.onClosed = { if (openFolder === fv) openFolder = null }
        openFolder = fv
        fv.open(root, anchorRect(v))
    }

    // ---- Drag and drop ----------------------------------------------------------------

    private val dragHost = object : DragController.Host {
        override val workspace: Workspace get() = home.workspace
        override val dock: DockView get() = home.dock
        override val dockEnabled: Boolean get() = settings.showDock
        override val columns: Int get() = settings.homeColumns
        override val rows: Int get() = settings.homeRows
        override val highlightColor: Int get() = palette.accent

        override fun onDragStart(source: DropLogic.Source) {
            popup?.dismissNow()
            openFolder?.close(animate = false)
            if (drawerProgress > 0f) closeDrawer()
            // The lifted icon leaves a gap where it was (not for drawer apps: they stay listed).
            if (source !is DropLogic.Source.External) {
                findHomeView(source)?.alpha = 0f
            }
        }

        override fun occupantId(source: DropLogic.Source, target: DropLogic.Target): Long? =
            DropLogic.occupant(store.layout, source, target, columns, rows)?.id

        override fun commitDrop(source: DropLogic.Source, target: DropLogic.Target): Boolean {
            val result = DropLogic.apply(store.layout, source, target, columns, rows, getString(R.string.folder_default_name))
                ?: return false
            store.update { result }
            return true
        }

        override fun onDragEnd() {
            // Drop empty pages left behind, keeping the user on the page they were looking at.
            val before = home.workspace.currentPage
            val trimmed = store.layout.trimPages()
            if (trimmed.pageCount != store.layout.pageCount) {
                store.update { trimmed }
                home.workspace.snapToPage(before.coerceAtMost(trimmed.pageCount - 1), animate = false)
            }
            bindHome(store.layout)
        }

        override fun requestNewPage() {
            store.update { it.addPage() }
        }

        override fun pageHasItems(page: Int): Boolean = store.layout.itemsOn(page).isNotEmpty()
    }

    /** The on-screen view currently showing [source], if any. */
    private fun findHomeView(source: DropLogic.Source): View? {
        when (source) {
            is DropLogic.Source.Grid -> for (page in home.workspace.pages) {
                for (i in 0 until page.childCount) {
                    val c = page.getChildAt(i)
                    if ((c as? IconView)?.let { (it.item as? HomeItem)?.id } == source.id) return c
                }
            }
            is DropLogic.Source.Dock -> for (i in 0 until home.dock.childCount) {
                val c = home.dock.getChildAt(i)
                if ((c as? IconView)?.item == source.key) return c
            }
            else -> Unit
        }
        return null
    }

    private fun showHomeMenu(v: View) {
        if (popup != null || drawerProgress > 0f || drag.isActive) return
        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val r = Rect(v.width / 2, v.height / 2, v.width / 2, v.height / 2)
        val anchor = anchorRect(v).let { full -> Rect(full.left + r.left, full.top + r.top, full.left + r.right, full.top + r.bottom) }
        showPopup(anchor, emptyList(), listOf(
            PopupMenuView.Entry(getDrawable(R.drawable.ic_wallpaper), getString(R.string.wallpaper)) { actions.openWallpaperPicker() },
            PopupMenuView.Entry(getDrawable(R.drawable.ic_settings), getString(R.string.settings)) {
                startActivity(Intent(this, SettingsActivity::class.java))
            },
        ))
    }

    private fun showPopup(anchor: Rect, shortcuts: List<PopupMenuView.Entry>, items: List<PopupMenuView.Entry>) {
        val menu = PopupMenuView(this, palette)
        menu.onDismissed = { if (popup === menu) popup = null }
        popup = menu
        menu.show(root, anchor, shortcuts, items, minTop = navInsets.top)
    }

    private fun addToHome(key: ComponentKey) {
        val cols = settings.homeColumns
        val rows = settings.homeRows
        val page = home.workspace.currentPage
        store.update { it.addApp(key, cols, rows, page) }
        Snackbar.show(root, palette, getString(R.string.added_to_home), navInsets.bottom)
    }

    private fun hideApp(entry: AppEntry) {
        settings.setHidden(entry.key, true)
        Snackbar.show(root, palette, getString(R.string.app_hidden, entry.label), maxOf(navInsets.bottom, 0),
            getString(R.string.undo)) { settings.setHidden(entry.key, false) }
    }

    // ---- Drawer -----------------------------------------------------------------------

    private fun openDrawer(withKeyboard: Boolean) {
        popup?.dismiss()
        onDrawerDragStart()
        drawerSpring.spring.dampingRatio = 0.88f
        drawerSpring.animateTo(1f, drawerSpring.velocity) { onDrawerSettled() }
        if (withKeyboard) drawer.focusSearch(true)
    }

    private fun closeDrawer(animate: Boolean = true) {
        drawer.hideKeyboard()
        if (!animate) {
            drawerSpring.snapTo(0f)
            onDrawerSettled()
            return
        }
        onDrawerDragStart()
        drawerSpring.spring.dampingRatio = 1f
        drawerSpring.animateTo(0f) { onDrawerSettled() }
    }

    private fun onDrawerSettled() {
        setAnimationLayers(false)
        if (drawerProgress <= 0f) {
            drawer.reset()
        } else if (drawerProgress >= 1f && settings.drawerKeyboard && !drawer.search.hasFocus()) {
            drawer.focusSearch(true)
        }
    }

    private var layersOn = false

    /** Hardware layers make alpha/scale changes during the motion nearly free for the GPU. */
    private fun setAnimationLayers(on: Boolean) {
        if (layersOn == on) return
        layersOn = on
        val type = if (on) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE
        home.setLayerType(type, null)
        drawer.content.setLayerType(type, null)
    }

    private fun applyDrawerProgress(p: Float) {
        drawerProgress = p
        val h = root.height.toFloat()
        drawer.visibility = if (p > 0f) View.VISIBLE else View.INVISIBLE
        drawer.translationY = (1f - p) * h
        drawer.cornerRadius = dp(28f) * (1f - progress(p, 0.6f, 1f))
        drawer.content.alpha = progress(p, 0.1f, 0.6f)
        val homeFade = progress(p, 0f, 0.55f)
        home.alpha = 1f - homeFade
        val s = lerp(1f, 0.94f, p)
        home.scaleX = s
        home.scaleY = s
        home.visibility = if (p >= 1f) View.INVISIBLE else View.VISIBLE
        updateSystemBars()
    }

    // ---- LauncherRoot.Host -------------------------------------------------------------

    override fun isGestureBlocked(): Boolean =
        popup != null || openFolder != null || drag.isActive || home.workspace.isScrolling()

    override fun isDrawerAtTop(): Boolean = drawer.isAtTop()

    override fun onDrawerDragStart() {
        drawerSpring.cancel()
        drawerSpring.spring.value = drawerProgress
        home.pivotX = home.width / 2f
        home.pivotY = home.height * 0.4f
        setAnimationLayers(true)
    }

    override fun onDrawerDrag(progress: Float) {
        drawerSpring.spring.value = progress
        applyDrawerProgress(progress)
    }

    override fun onDrawerDragEnd(velocity: Float) {
        val open = when {
            abs(velocity) > 0.35f -> velocity > 0f
            else -> drawerProgress > 0.5f
        }
        if (open) {
            drawerSpring.spring.dampingRatio = 0.88f
            drawerSpring.animateTo(1f, velocity) { onDrawerSettled() }
        } else {
            drawer.hideKeyboard()
            drawerSpring.spring.dampingRatio = 1f
            drawerSpring.animateTo(0f, velocity) { onDrawerSettled() }
        }
    }

    override fun onSwipeDown() {
        openDrawer(withKeyboard = true)
    }

    @Suppress("unused")
    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()
}
