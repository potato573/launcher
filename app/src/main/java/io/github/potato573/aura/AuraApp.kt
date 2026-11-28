package io.github.potato573.aura

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.provider.MediaStore
import io.github.potato573.aura.data.AppEntry
import io.github.potato573.aura.data.AppRepository
import io.github.potato573.aura.data.IconCache
import io.github.potato573.aura.data.LayoutStore
import io.github.potato573.aura.data.Settings
import io.github.potato573.aura.model.ComponentKey
import io.github.potato573.aura.model.HomeLayout

/**
 * Process-wide singletons. Created lazily-cheap: nothing here touches the disk except the
 * small layout file, and app loading starts on a worker thread.
 */
class AuraApp : Application() {

    val io: Handler by lazy { LayoutStore.newWorker() }
    private val iconWorker: Handler by lazy {
        val t = HandlerThread("aura-icons", Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
        Handler(t.looper)
    }

    val settings by lazy { Settings(this) }
    val layoutStore by lazy { LayoutStore(this, io) }
    val icons by lazy { IconCache(this, iconWorker) }
    val apps by lazy {
        AppRepository(this, io).also { repo ->
            repo.onEntriesReplaced = { icons.invalidate(it) }
            repo.addRemovalListener { removed -> layoutStore.update { it.removeApps(removed) } }
            repo.addListener(::seedFirstRun)
            repo.addListener(::pruneOnce)
        }
    }

    private var pruned = false

    /**
     * Removes home items whose app was uninstalled while AURA was not running. Only apps that
     * the package manager confirms are gone are removed, never apps that are just temporarily
     * unavailable (for example on unmounted storage).
     */
    private fun pruneOnce(list: List<AppEntry>) {
        if (pruned || list.isEmpty()) return
        pruned = true
        val present = list.mapTo(HashSet()) { it.key }
        val mySerial = apps.serialOf(Process.myUserHandle())
        val gone = layoutStore.layout.allKeys().filter { key ->
            key !in present && key.userSerial == mySerial && !isInstalled(key.packageName)
        }.toSet()
        if (gone.isNotEmpty()) layoutStore.update { it.removeApps(gone) }
    }

    private fun isInstalled(pkg: String): Boolean = try {
        packageManager.getApplicationInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** On the very first launch, fill the dock and home with the device's core apps. */
    private fun seedFirstRun(list: List<AppEntry>) {
        if (settings.dockSeeded || list.isEmpty()) return
        settings.dockSeeded = true
        if (!layoutStore.isFresh) return
        val mySerial = apps.serialOf(Process.myUserHandle())
        val pm = packageManager
        fun resolve(intent: Intent): ComponentKey? {
            val pkg = try {
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            } catch (e: Exception) {
                null
            } ?: return null
            return list.firstOrNull { it.key.packageName == pkg && it.key.userSerial == mySerial }?.key
        }
        val dockIntents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
        )
        val homeIntents = listOf(
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL),
        )
        val cols = settings.homeColumns
        val rows = settings.homeRows
        layoutStore.update { start ->
            var l = start
            dockIntents.mapNotNull(::resolve).forEach { l = l.addToDock(it, HomeLayout.MAX_DOCK) }
            homeIntents.mapNotNull(::resolve).forEach { l = l.addApp(it, cols, rows) }
            l
        }
    }

    companion object {
        fun of(context: Context): AuraApp = context.applicationContext as AuraApp
    }
}
