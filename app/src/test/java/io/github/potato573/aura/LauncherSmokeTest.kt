package io.github.potato573.aura

import android.content.Intent
import android.os.Looper
import android.view.View
import io.github.potato573.aura.model.ComponentKey
import io.github.potato573.aura.ui.HiddenAppsActivity
import io.github.potato573.aura.ui.LauncherActivity
import io.github.potato573.aura.ui.SettingsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * Runs the real activities on the JVM. This does not replace a device test, but it proves
 * that the view tree builds, the lifecycle is sound and the drawer motion settles.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LauncherSmokeTest {

    private fun settle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))

    @Test
    fun launcherStartsAndDrawerOpensAndCloses() {
        val controller = Robolectric.buildActivity(LauncherActivity::class.java).setup()
        val activity = controller.get()
        settle()
        assertEquals(0f, activity.drawerProgress, 0f)

        activity.onSwipeDown()
        settle()
        assertEquals(1f, activity.drawerProgress, 0.001f)

        @Suppress("DEPRECATION")
        activity.onBackPressed()
        settle()
        assertEquals(0f, activity.drawerProgress, 0.001f)
        assertTrue(!activity.isFinishing)
        controller.pause().stop().destroy()
    }

    @Test
    fun dragGestureFollowsFingerAndFlingCompletes() {
        val activity = Robolectric.buildActivity(LauncherActivity::class.java).setup().get()
        settle()
        activity.onDrawerDragStart()
        activity.onDrawerDrag(0.3f)
        assertEquals(0.3f, activity.drawerProgress, 0.0001f)
        // Fast upward fling from 30%: must open fully, not fall back.
        activity.onDrawerDragEnd(velocity = 3f)
        settle()
        assertEquals(1f, activity.drawerProgress, 0.001f)
        // Slow release below half: must close.
        activity.onDrawerDragStart()
        activity.onDrawerDrag(0.4f)
        activity.onDrawerDragEnd(velocity = 0f)
        settle()
        assertEquals(0f, activity.drawerProgress, 0.001f)
    }

    @Test
    fun homeButtonClosesDrawer() {
        val controller = Robolectric.buildActivity(LauncherActivity::class.java).setup()
        val activity = controller.get()
        activity.onSwipeDown()
        settle()
        controller.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        settle()
        assertEquals(0f, activity.drawerProgress, 0.001f)
    }

    @Test
    fun settingsChangesApplyWithoutCrash() {
        val launcher = Robolectric.buildActivity(LauncherActivity::class.java).setup().get()
        val app = AuraApp.of(launcher)
        app.settings.homeColumns = 6
        app.settings.homeRows = 3
        app.settings.iconScale = 120
        app.settings.theme = io.github.potato573.aura.data.Settings.Theme.DARK
        app.settings.showDock = false
        app.settings.glassTransparency = 70
        app.settings.blurStrength = 0
        app.settings.pageEffect = io.github.potato573.aura.data.Settings.PageEffect.TILT
        app.settings.animSpeed = io.github.potato573.aura.data.Settings.Speed.SNAPPY
        app.settings.showIndicator = false
        app.settings.drawerLabels = false
        app.settings.hideNavBar = true
        app.settings.setHidden(ComponentKey("a", "a.B"), true)
        settle()
        assertTrue(launcher.window.decorView.visibility == View.VISIBLE)
    }

    @Test
    fun settingsAndHiddenAppsScreensBuild() {
        val settings = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        assertTrue(settings.findViewById<View>(R.id.settings_scroll) != null)
        val hidden = Robolectric.buildActivity(HiddenAppsActivity::class.java).setup().get()
        assertTrue(hidden.findViewById<View>(R.id.hidden_list) != null)
    }

    @Test
    fun layoutSurvivesProcessRestart() {
        val launcher = Robolectric.buildActivity(LauncherActivity::class.java).setup().get()
        val app = AuraApp.of(launcher)
        val key = ComponentKey("com.example", "com.example.Main")
        app.layoutStore.update { it.addApp(key, 4, 4) }
        app.layoutStore.flush()
        settle()
        val reread = io.github.potato573.aura.data.LayoutStore(launcher, app.io)
        // The writer thread is real; give it a moment.
        val deadline = System.currentTimeMillis() + 3000
        var ok = reread.layout.contains(key)
        while (!ok && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
            ok = io.github.potato573.aura.data.LayoutStore(launcher, app.io).layout.contains(key)
        }
        assertTrue(ok)
    }

    @Test
    fun longPressOnEmptyHomeShowsMenuAndBackDismissesIt() {
        val activity = Robolectric.buildActivity(LauncherActivity::class.java).setup().get()
        settle()
        val root = activity.window.decorView.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
            as io.github.potato573.aura.ui.LauncherRoot
        val page = findFirst(root) { it is io.github.potato573.aura.ui.CellLayout }!!
        val before = root.childCount
        page.performLongClick()
        settle()
        assertEquals(before + 1, root.childCount)
        assertTrue(root.getChildAt(root.childCount - 1) is io.github.potato573.aura.ui.PopupMenuView)
        @Suppress("DEPRECATION")
        activity.onBackPressed()
        settle()
        assertEquals(before, root.childCount)
    }

    private fun findFirst(v: View, pred: (View) -> Boolean): View? {
        if (pred(v)) return v
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findFirst(v.getChildAt(i), pred)?.let { return it }
        return null
    }
}
