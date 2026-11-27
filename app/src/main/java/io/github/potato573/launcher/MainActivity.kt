package io.github.potato573.launcher

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import io.github.potato573.launcher.databinding.ActivityMainBinding
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var allApps: List<AppInfo> = emptyList()
    private var appsDirty = true
    private var isDragging = false

    private val homeAdapter = AppAdapter(onClick = ::launch)
    private val dockAdapter = AppAdapter(onClick = ::launch)
    private val drawerAdapter = AppAdapter(onClick = ::launch, onLongClick = ::showDrawerMenu)

    private lateinit var gestures: GestureDetector

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            appsDirty = true
            loadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs(this)

        ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setupHome()
        setupDock()
        setupDrawer()
        setupGestures()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Launcher tidak boleh tertutup oleh tombol kembali.
                if (isDrawerOpen()) closeDrawer()
            }
        })

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onResume() {
        super.onResume()
        applySettings()
        if (appsDirty) loadApps() else refreshHomeAndDock()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Tombol home ditekan saat launcher sudah terbuka.
        if (isDrawerOpen()) closeDrawer()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(packageReceiver)
        } catch (_: Exception) {
        }
        io.shutdown()
    }

    // ---------- Data ----------

    private fun loadApps() {
        io.execute {
            val apps = try {
                AppRepository.loadApps(applicationContext)
            } catch (e: Exception) {
                emptyList()
            }
            main.post {
                if (isFinishing || isDestroyed) return@post
                allApps = apps
                appsDirty = false
                if (!prefs.dockInitialized && apps.isNotEmpty()) {
                    prefs.dockApps = AppRepository.defaultDock(this, apps)
                    prefs.dockInitialized = true
                }
                refreshHomeAndDock()
                filterDrawer(b.search.text?.toString().orEmpty())
            }
        }
    }

    private fun resolve(keys: List<String>): List<AppInfo> {
        val byKey = allApps.associateBy { it.key }
        return keys.mapNotNull { byKey[it] }
    }

    private fun refreshHomeAndDock() {
        if (isDragging) return
        homeAdapter.submit(resolve(prefs.homeApps))
        dockAdapter.submit(resolve(prefs.dockApps))
        b.homeEmpty.visibility = if (homeAdapter.items.isEmpty() && allApps.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun applySettings() {
        val cols = prefs.columns
        (b.homeGrid.layoutManager as? GridLayoutManager)?.spanCount = cols
        (b.allApps.layoutManager as? GridLayoutManager)?.spanCount = cols
        homeAdapter.showLabels = prefs.showLabels
        b.dock.visibility = if (prefs.showDock) View.VISIBLE else View.GONE
        b.clockBox.visibility = if (prefs.showClock) View.VISIBLE else View.GONE
        // Ruang dock dibatasi agar ikon tetap muat.
        (b.dock.layoutManager as? GridLayoutManager)?.spanCount = Prefs.MAX_DOCK
    }

    // ---------- Home ----------

    private fun setupHome() {
        b.homeGrid.layoutManager = GridLayoutManager(this, prefs.columns)
        b.homeGrid.adapter = homeAdapter
        ItemTouchHelper(
            DragHelper(
                adapter = homeAdapter,
                dragDirs = ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
                onDragStateChanged = { isDragging = it },
                onOrderChanged = { prefs.homeApps = homeAdapter.items.map { it.key } },
                onTapHold = { app, vh -> showHomeMenu(app, vh.itemView, fromDock = false) },
            )
        ).attachToRecyclerView(b.homeGrid)
        b.drawerHandle.setOnClickListener { openDrawer() }
    }

    private fun setupDock() {
        b.dock.layoutManager = GridLayoutManager(this, Prefs.MAX_DOCK)
        b.dock.adapter = dockAdapter
        dockAdapter.showLabels = false
        ItemTouchHelper(
            DragHelper(
                adapter = dockAdapter,
                dragDirs = ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
                onDragStateChanged = { isDragging = it },
                onOrderChanged = { prefs.dockApps = dockAdapter.items.map { it.key } },
                onTapHold = { app, vh -> showHomeMenu(app, vh.itemView, fromDock = true) },
            )
        ).attachToRecyclerView(b.dock)
    }

    private fun showHomeMenu(app: AppInfo, anchor: View, fromDock: Boolean) {
        val menu = PopupMenu(this, anchor, Gravity.CENTER)
        val moveLabel = if (fromDock) R.string.move_to_home else R.string.move_to_dock
        menu.menu.add(0, 1, 0, moveLabel)
        menu.menu.add(0, 2, 1, R.string.remove)
        menu.menu.add(0, 3, 2, R.string.app_info)
        menu.menu.add(0, 4, 3, R.string.uninstall)
        menu.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> if (fromDock) {
                    if (addTo(Prefs.KEY_HOME, app)) removeFrom(Prefs.KEY_DOCK, app)
                } else {
                    if (addTo(Prefs.KEY_DOCK, app)) removeFrom(Prefs.KEY_HOME, app)
                }
                2 -> removeFrom(if (fromDock) Prefs.KEY_DOCK else Prefs.KEY_HOME, app)
                3 -> openAppInfo(app)
                4 -> uninstall(app)
            }
            refreshHomeAndDock()
            true
        }
        menu.show()
    }

    /** Kembalikan true jika berhasil ditambahkan. */
    private fun addTo(key: String, app: AppInfo): Boolean {
        val list = (if (key == Prefs.KEY_DOCK) prefs.dockApps else prefs.homeApps).toMutableList()
        if (app.key in list) {
            toast(getString(R.string.already_added))
            return false
        }
        if (key == Prefs.KEY_DOCK && list.size >= Prefs.MAX_DOCK) {
            toast(getString(R.string.dock_full, Prefs.MAX_DOCK))
            return false
        }
        list.add(app.key)
        if (key == Prefs.KEY_DOCK) prefs.dockApps = list else prefs.homeApps = list
        return true
    }

    private fun removeFrom(key: String, app: AppInfo) {
        if (key == Prefs.KEY_DOCK) {
            prefs.dockApps = prefs.dockApps - app.key
        } else {
            prefs.homeApps = prefs.homeApps - app.key
        }
    }

    // ---------- Drawer ----------

    private fun setupDrawer() {
        b.allApps.layoutManager = GridLayoutManager(this, prefs.columns)
        b.allApps.adapter = drawerAdapter
        b.search.doAfterTextChanged { filterDrawer(it?.toString().orEmpty()) }
        b.search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                // Enter = buka hasil pertama.
                drawerAdapter.items.firstOrNull()?.let { launch(it) }
                true
            } else {
                false
            }
        }
    }

    private fun filterDrawer(query: String) {
        val list = allApps.filter { AppRepository.matches(it, query) }
        drawerAdapter.submit(list)
        b.noResults.visibility = if (list.isEmpty() && allApps.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun showDrawerMenu(app: AppInfo, anchor: View) {
        anchor.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        val menu = PopupMenu(this, anchor, Gravity.CENTER)
        menu.menu.add(0, 1, 0, R.string.add_to_home)
        menu.menu.add(0, 2, 1, R.string.add_to_dock)
        menu.menu.add(0, 3, 2, R.string.app_info)
        menu.menu.add(0, 4, 3, R.string.uninstall)
        menu.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> if (addTo(Prefs.KEY_HOME, app)) closeDrawer()
                2 -> if (addTo(Prefs.KEY_DOCK, app)) closeDrawer()
                3 -> openAppInfo(app)
                4 -> uninstall(app)
            }
            refreshHomeAndDock()
            true
        }
        menu.show()
    }

    private fun isDrawerOpen() = b.drawerPanel.visibility == View.VISIBLE

    private fun openDrawer() {
        if (isDrawerOpen()) return
        b.search.setText("")
        filterDrawer("")
        b.allApps.scrollToPosition(0)
        b.drawerPanel.alpha = 0f
        b.drawerPanel.translationY = b.root.height * 0.15f
        b.drawerPanel.visibility = View.VISIBLE
        b.drawerPanel.animate().alpha(1f).translationY(0f).setDuration(180).start()
        b.homePanel.animate().alpha(0f).setDuration(180).start()
        if (prefs.autoKeyboard) {
            b.search.requestFocus()
            b.search.post {
                getSystemService(InputMethodManager::class.java)
                    ?.showSoftInput(b.search, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun closeDrawer() {
        if (!isDrawerOpen()) return
        hideKeyboard()
        b.search.clearFocus()
        b.homePanel.animate().alpha(1f).setDuration(160).start()
        b.drawerPanel.animate().alpha(0f).translationY(b.root.height * 0.1f).setDuration(160)
            .withEndAction { b.drawerPanel.visibility = View.GONE }
            .start()
    }

    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(b.search.windowToken, 0)
    }

    // ---------- Gestures ----------

    private fun setupGestures() {
        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (isDragging || e1 == null) return false
                val dy = e2.y - e1.y
                val dx = e2.x - e1.x
                if (abs(dy) < abs(dx) || abs(dy) < 120 || abs(vy) < 400) return false
                if (dy < 0 && !isDrawerOpen()) {
                    openDrawer()
                    return true
                }
                if (dy > 0 && isDrawerOpen() && !b.allApps.canScrollVertically(-1)) {
                    closeDrawer()
                    return true
                }
                return false
            }

            override fun onLongPress(e: MotionEvent) {
                if (isDragging || isDrawerOpen()) return
                if (isOverItem(b.homeGrid, e) || isOverItem(b.dock, e)) return
                showDesktopMenu(e)
            }
        })
    }

    private fun isOverItem(rv: RecyclerView, e: MotionEvent): Boolean {
        if (rv.visibility != View.VISIBLE) return false
        val loc = IntArray(2)
        rv.getLocationInWindow(loc)
        return rv.findChildViewUnder(e.x - loc[0], e.y - loc[1]) != null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestures.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun showDesktopMenu(e: MotionEvent) {
        val anchor = b.homeEmpty.takeIf { it.visibility == View.VISIBLE } ?: b.drawerHandle
        val menu = PopupMenu(this, anchor, Gravity.CENTER)
        menu.menu.add(0, 1, 0, R.string.settings)
        menu.menu.add(0, 2, 1, R.string.wallpaper)
        menu.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> startActivity(Intent(this, SettingsActivity::class.java))
                2 -> try {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), getString(R.string.wallpaper)))
                } catch (_: Exception) {
                }
            }
            true
        }
        b.root.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        menu.show()
    }

    // ---------- Actions ----------

    private fun launch(app: AppInfo) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(app.packageName, app.className))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            startActivity(intent)
            if (isDrawerOpen()) main.postDelayed({ closeDrawer() }, 300)
        } catch (e: Exception) {
            toast(getString(R.string.cannot_open))
            appsDirty = true
            loadApps()
        }
    }

    private fun openAppInfo(app: AppInfo) {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }

    private fun uninstall(app: AppInfo) {
        try {
            @Suppress("DEPRECATION")
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
        } catch (_: Exception) {
            openAppInfo(app)
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
