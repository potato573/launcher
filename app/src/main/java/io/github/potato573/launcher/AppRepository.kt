package io.github.potato573.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.provider.MediaStore
import java.text.Normalizer
import java.util.Locale

object AppRepository {

    /** Semua aplikasi yang punya ikon launcher, urut nama. Jalankan di thread latar. */
    fun loadApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return resolved
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                AppInfo(
                    label = it.loadLabel(pm).toString().trim(),
                    packageName = it.activityInfo.packageName,
                    className = it.activityInfo.name,
                    icon = it.loadIcon(pm),
                )
            }
            .distinctBy { it.key }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    /** Isi dock awal: telepon, pesan, browser, kamera (jika ada). */
    fun defaultDock(context: Context, apps: List<AppInfo>): List<String> {
        val pm = context.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
        )
        val result = mutableListOf<String>()
        for (i in intents) {
            val pkg = try {
                @Suppress("DEPRECATION")
                pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            } catch (e: Exception) {
                null
            } ?: continue
            val app = apps.firstOrNull { it.packageName == pkg } ?: continue
            if (app.key !in result) result.add(app.key)
        }
        return result
    }

    /** Cocokkan pencarian tanpa peduli huruf besar dan aksen ("cafe" cocok "Café"). */
    fun matches(app: AppInfo, query: String): Boolean {
        if (query.isBlank()) return true
        val q = normalize(query)
        val label = normalize(app.label)
        if (label.contains(q)) return true
        // Cocokkan huruf awal tiap kata, misal "gm" untuk "Google Maps"
        val initials = label.split(' ', '-', '_').filter { it.isNotEmpty() }.joinToString("") { it.take(1) }
        return q.length >= 2 && initials.startsWith(q)
    }

    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
            .trim()
}
