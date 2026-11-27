package io.github.potato573.aura.data

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.potato573.aura.AuraApp
import java.io.ByteArrayOutputStream

/**
 * Saves and restores layout + settings + hidden apps as a JSON file the user picks through
 * the Storage Access Framework (no storage permission needed). File I/O runs on the worker
 * thread; results come back on the main thread.
 */
class BackupManager(context: Context) {

    private val app = AuraApp.of(context)
    private val resolver = context.applicationContext.contentResolver
    private val main = Handler(Looper.getMainLooper())

    fun export(uri: Uri, done: (Boolean) -> Unit) {
        val text = LayoutCodec.encodeBackup(
            LayoutCodec.Backup(app.layoutStore.layout, app.settings.export(), app.settings.hiddenApps),
            System.currentTimeMillis(),
        )
        app.io.post {
            val ok = try {
                resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
            } catch (e: Exception) {
                Log.e(TAG, "Backup failed", e)
                false
            }
            main.post { done(ok) }
        }
    }

    fun import(uri: Uri, done: (Boolean) -> Unit) {
        app.io.post {
            val backup = try {
                val bytes = resolver.openInputStream(uri)?.use { input ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        // A real backup is a few KB; refuse anything absurd instead of exhausting memory.
                        if (total > MAX_BYTES) throw LayoutCodec.InvalidBackupException("too_large")
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                } ?: throw LayoutCodec.InvalidBackupException("unreadable")
                LayoutCodec.decodeBackup(String(bytes, Charsets.UTF_8))
            } catch (e: Exception) {
                Log.w(TAG, "Restore rejected", e)
                null
            }
            main.post {
                if (backup == null) {
                    done(false)
                } else {
                    app.settings.import(backup.settings, backup.hidden)
                    val cols = app.settings.homeColumns
                    val rows = app.settings.homeRows
                    // Apps that are not installed here (e.g. a backup from another phone) are dropped
                    // so they do not leave invisible, occupied cells behind.
                    val installed = app.apps.apps.mapTo(HashSet()) { it.key }
                    val layout = if (app.apps.isLoaded) backup.layout.retainApps(installed) else backup.layout
                    app.layoutStore.update { layout.reflow(cols, rows) }
                    done(true)
                }
            }
        }
    }

    companion object {
        private const val TAG = "AuraBackup"
        private const val MAX_BYTES = 2 * 1024 * 1024
    }
}
