package io.github.potato573.aura.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import io.github.potato573.aura.model.ComponentKey
import io.github.potato573.aura.search.AppSearch
import java.text.Collator

class AppEntry(
    val key: ComponentKey,
    val component: ComponentName,
    val user: UserHandle,
    val label: String,
    val info: LauncherActivityInfo,
    val isWorkProfile: Boolean,
) {
    val search = AppSearch.Index(label)
}

/**
 * The list of launchable apps for every profile (personal and work), read through the
 * official LauncherApps API. Package changes arrive via [LauncherApps.Callback]; only the
 * affected package is re-queried, so installs and updates are cheap.
 */
class AppRepository(context: Context, private val worker: Handler) {

    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)!!
    private val userManager = appContext.getSystemService(UserManager::class.java)!!
    private val main = Handler(Looper.getMainLooper())
    private val listeners = LinkedHashSet<(List<AppEntry>) -> Unit>()
    private val removalListeners = LinkedHashSet<(Set<ComponentKey>) -> Unit>()

    /** Latest snapshot, sorted by label. Only touched on the main thread. */
    var apps: List<AppEntry> = emptyList()
        private set
    var isLoaded = false
        private set
    private var byKey: Map<ComponentKey, AppEntry> = emptyMap()

    fun get(key: ComponentKey): AppEntry? = byKey[key]

    fun addListener(l: (List<AppEntry>) -> Unit) {
        listeners += l
        if (isLoaded) l(apps)
    }

    fun removeListener(l: (List<AppEntry>) -> Unit) {
        listeners -= l
    }

    /** Called before listeners with keys whose icon/label may have changed. */
    var onEntriesReplaced: ((Set<ComponentKey>) -> Unit)? = null

    /** Called with keys of apps that were definitively uninstalled. */
    fun addRemovalListener(l: (Set<ComponentKey>) -> Unit) {
        removalListeners += l
    }

    fun serialOf(user: UserHandle): Long = userManager.getSerialNumberForUser(user)

    fun userFor(serial: Long): UserHandle? = userManager.getUserForSerialNumber(serial)

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = reloadPackage(packageName, user)
        override fun onPackageChanged(packageName: String, user: UserHandle) = reloadPackage(packageName, user)
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            val serial = serialOf(user)
            val removed = apps.filter { it.key.packageName == packageName && it.key.userSerial == serial }
                .mapTo(HashSet()) { it.key }
            publish(apps.filterNot { it.key in removed })
            if (removed.isNotEmpty()) removalListeners.toList().forEach { it(removed) }
        }
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            loadAll()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            loadAll()
        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) = loadAll()
        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) = loadAll()
    }

    private var started = false

    fun start() {
        if (started) return
        started = true
        launcherApps.registerCallback(callback, main)
        loadAll()
    }

    fun stop() {
        if (!started) return
        started = false
        launcherApps.unregisterCallback(callback)
    }

    /** Reloads everything, e.g. after a work profile is paused or resumed. */
    fun loadAll() {
        worker.post {
            val result = try {
                launcherApps.profiles.flatMap { user -> query(null, user) }
            } catch (e: Exception) {
                Log.e(TAG, "Loading apps failed", e)
                null
            }
            if (result != null) main.post { publish(result) }
        }
    }

    private fun reloadPackage(packageName: String, user: UserHandle) {
        worker.post {
            val fresh = try {
                query(packageName, user)
            } catch (e: Exception) {
                Log.e(TAG, "Loading $packageName failed", e)
                return@post
            }
            main.post {
                val serial = serialOf(user)
                val kept = apps.filterNot { it.key.packageName == packageName && it.key.userSerial == serial }
                onEntriesReplaced?.invoke(fresh.mapTo(HashSet()) { it.key })
                publish(kept + fresh)
            }
        }
    }

    private fun query(packageName: String?, user: UserHandle): List<AppEntry> {
        val serial = serialOf(user)
        val isWork = user != android.os.Process.myUserHandle()
        return launcherApps.getActivityList(packageName, user)
            .filter { it.componentName.packageName != appContext.packageName }
            .map { info ->
                AppEntry(
                    key = ComponentKey(info.componentName.packageName, info.componentName.className, serial),
                    component = info.componentName,
                    user = user,
                    label = info.label?.toString()?.trim().orEmpty().ifEmpty { info.componentName.packageName },
                    info = info,
                    isWorkProfile = isWork,
                )
            }
    }

    private fun publish(list: List<AppEntry>) {
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        val sorted = list.distinctBy { it.key }.sortedWith { a, b -> collator.compare(a.label, b.label) }
        apps = sorted
        byKey = sorted.associateBy { it.key }
        isLoaded = true
        listeners.toList().forEach { it(sorted) }
    }

    companion object {
        private const val TAG = "AuraApps"
    }
}
