package io.github.potato573.aura.ui

import android.app.Activity
import android.app.ActivityOptions
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.util.Log
import android.view.View
import android.widget.Toast
import io.github.potato573.aura.R
import io.github.potato573.aura.data.AppEntry

/** Everything that leaves the launcher: starting apps, shortcuts and system screens. */
class AppActions(private val activity: Activity) {

    private val launcherApps = activity.getSystemService(LauncherApps::class.java)!!

    /**
     * Starts [app], revealing the new window out of [from]'s icon: the app opens as a clip
     * that grows from the icon to the full screen (the public, supported launcher animation).
     * Returns false on failure.
     */
    fun launch(app: AppEntry, from: View?): Boolean {
        val bounds = Rect()
        var opts: ActivityOptions? = null
        if (from != null) {
            val icon = Rect()
            if (from is IconView) from.iconBounds(icon) else icon.set(0, 0, from.width, from.height)
            opts = ActivityOptions.makeClipRevealAnimation(from, icon.left, icon.top, icon.width(), icon.height())
            val loc = IntArray(2)
            from.getLocationOnScreen(loc)
            bounds.set(loc[0] + icon.left, loc[1] + icon.top, loc[0] + icon.right, loc[1] + icon.bottom)
        }
        return try {
            launcherApps.startMainActivity(app.component, app.user, bounds, opts?.toBundle())
            true
        } catch (e: Exception) {
            // ActivityNotFoundException or SecurityException: the app was removed/disabled
            // between listing and tapping, or the activity is not exported.
            Log.w(TAG, "Cannot launch ${app.key}", e)
            Toast.makeText(activity, R.string.cannot_open, Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun openAppInfo(app: AppEntry) = guard {
        launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
    }

    fun canUninstall(app: AppEntry): Boolean {
        val flags = app.info.applicationInfo.flags
        return flags and ApplicationInfo.FLAG_SYSTEM == 0 || flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
    }

    fun uninstall(app: AppEntry) = guard {
        @Suppress("DEPRECATION")
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.fromParts("package", app.component.packageName, null))
            .putExtra(Intent.EXTRA_USER, app.user)
        activity.startActivity(intent)
    }

    /** Dynamic and manifest shortcuts; only available while AURA is the default launcher. */
    fun shortcuts(app: AppEntry): List<ShortcutInfo> {
        return try {
            if (!launcherApps.hasShortcutHostPermission()) return emptyList()
            val q = LauncherApps.ShortcutQuery()
                .setPackage(app.component.packageName)
                .setActivity(app.component)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
            launcherApps.getShortcuts(q, app.user).orEmpty()
                .filter { it.isEnabled }
                .sortedWith(compareBy({ if (it.isDeclaredInManifest) 0 else 1 }, { it.rank }))
                .take(4)
        } catch (e: Exception) {
            // SecurityException if the launcher role was revoked in the meantime.
            emptyList()
        }
    }

    fun shortcutIcon(info: ShortcutInfo): Drawable? = try {
        launcherApps.getShortcutIconDrawable(info, activity.resources.displayMetrics.densityDpi)
    } catch (e: Exception) {
        null
    }

    fun startShortcut(info: ShortcutInfo, from: View?) = guard {
        val r = Rect()
        from?.getGlobalVisibleRect(r)
        launcherApps.startShortcut(info, r, null)
    }

    fun openAlarms() = guard { activity.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS)) }

    fun openCalendar() = guard {
        val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
            .appendPath(System.currentTimeMillis().toString()).build()
        activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    fun openWallpaperPicker() = guard {
        activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), activity.getString(R.string.wallpaper)))
    }

    private inline fun guard(block: () -> Unit) {
        try {
            block()
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.cannot_open, Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(activity, R.string.cannot_open, Toast.LENGTH_SHORT).show()
        } catch (e: IllegalStateException) {
            Toast.makeText(activity, R.string.cannot_open, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "AuraActions"
        const val REQUEST_HOME_ROLE = 41

        fun isDefaultLauncher(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= 29) {
                val rm = context.getSystemService(RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) return rm.isRoleHeld(RoleManager.ROLE_HOME)
            }
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val info = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            return info?.activityInfo?.packageName == context.packageName
        }

        /** Asks the system to make AURA the home app (role dialog on Android 10+). */
        fun requestDefaultLauncher(activity: Activity) {
            if (Build.VERSION.SDK_INT >= 29) {
                val rm = activity.getSystemService(RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                    try {
                        @Suppress("DEPRECATION")
                        activity.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQUEST_HOME_ROLE)
                        return
                    } catch (e: ActivityNotFoundException) {
                        // Fall through to the settings screen.
                    }
                }
            }
            for (intent in listOf(Intent(android.provider.Settings.ACTION_HOME_SETTINGS), Intent(android.provider.Settings.ACTION_SETTINGS))) {
                try {
                    activity.startActivity(intent)
                    return
                } catch (e: ActivityNotFoundException) {
                    continue
                }
            }
        }
    }
}
