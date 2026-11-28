package io.github.potato573.aura.data

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.AtomicFile
import android.util.Log
import io.github.potato573.aura.model.HomeLayout
import org.json.JSONObject
import java.io.File

/**
 * Owns the current [HomeLayout]. Reads happen once at startup; writes are atomic
 * (AtomicFile keeps the previous file until the new one is complete) and coalesced on a
 * background thread so rapid drag-and-drop never blocks a frame.
 */
class LayoutStore(context: Context, private val worker: Handler) {

    private val file = AtomicFile(File(context.filesDir, "layout.json"))
    private val listeners = LinkedHashSet<(HomeLayout) -> Unit>()
    private val writeLock = Any()
    @Volatile private var pending: HomeLayout? = null

    var layout: HomeLayout = read()
        private set

    /** True when no layout file existed yet, i.e. first run. */
    var isFresh: Boolean = !file.baseFile.exists()
        private set

    fun addListener(l: (HomeLayout) -> Unit) {
        listeners += l
    }

    fun removeListener(l: (HomeLayout) -> Unit) {
        listeners -= l
    }

    /** Must be called on the main thread. */
    fun update(transform: (HomeLayout) -> HomeLayout) {
        val next = transform(layout)
        if (next == layout) return
        layout = next
        isFresh = false
        scheduleWrite(next)
        listeners.toList().forEach { it(next) }
    }

    private fun scheduleWrite(value: HomeLayout) {
        pending = value
        worker.removeCallbacks(writer)
        worker.postDelayed(writer, 250)
    }

    private val writer = Runnable {
        val value = pending ?: return@Runnable
        pending = null
        synchronized(writeLock) { write(value) }
    }

    /** Writes any pending change immediately (called when the launcher goes to background). */
    fun flush() {
        worker.removeCallbacks(writer)
        worker.post(writer)
    }

    private fun read(): HomeLayout = try {
        if (!file.baseFile.exists()) HomeLayout()
        else LayoutCodec.decode(JSONObject(String(file.readFully(), Charsets.UTF_8)))
    } catch (e: Exception) {
        Log.w(TAG, "Layout file unreadable, starting empty", e)
        HomeLayout()
    }

    private fun write(value: HomeLayout) {
        val out = try {
            file.startWrite()
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open layout for writing", e)
            return
        }
        try {
            out.write(LayoutCodec.encode(value).toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            Log.e(TAG, "Writing layout failed", e)
        }
    }

    companion object {
        private const val TAG = "AuraLayout"

        fun newWorker(): Handler {
            val t = HandlerThread("aura-io").apply { start() }
            return Handler(t.looper)
        }
    }
}
