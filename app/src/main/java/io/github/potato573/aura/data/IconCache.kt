package io.github.potato573.aura.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import io.github.potato573.aura.model.ComponentKey

/**
 * Renders app icons once into bitmaps at the exact on-screen size and keeps them in an LRU
 * cache. Drawing a cached bitmap is far cheaper than drawing an AdaptiveIconDrawable
 * (layered, path-clipped) every frame, which is what keeps scrolling and the drawer
 * animation smooth on entry-level GPUs. Bitmaps are converted to HARDWARE bitmaps, so
 * their pixels live in graphics memory instead of the Java heap.
 */
class IconCache(context: Context, private val worker: Handler) {

    private val density = context.resources.displayMetrics.densityDpi
    private val main = Handler(Looper.getMainLooper())

    /** Icon edge length in pixels; changing it clears the cache. Main thread only. */
    var sizePx: Int = 0
        private set

    private val cache = object : LruCache<ComponentKey, Bitmap>(cacheBytes()) {
        override fun sizeOf(key: ComponentKey, value: Bitmap): Int = value.width * value.height * 4
    }
    private val inFlight = HashMap<ComponentKey, MutableList<(Bitmap) -> Unit>>()
    private var generation = 0

    private fun cacheBytes(): Int {
        val max = Runtime.getRuntime().maxMemory()
        return (max / 8).coerceAtMost(32L * 1024 * 1024).toInt()
    }

    fun setSize(px: Int) {
        if (px == sizePx || px <= 0) return
        sizePx = px
        generation++
        cache.evictAll()
        inFlight.clear()
    }

    fun peek(key: ComponentKey): Bitmap? = cache.get(key)

    /**
     * Returns the cached bitmap immediately, or null and delivers it later on the main thread.
     * Concurrent requests for the same icon share one load.
     */
    fun get(app: AppEntry, onLoaded: (Bitmap) -> Unit): Bitmap? {
        cache.get(app.key)?.let { return it }
        val waiting = inFlight[app.key]
        if (waiting != null) {
            waiting += onLoaded
            return null
        }
        inFlight[app.key] = mutableListOf(onLoaded)
        val size = sizePx
        val gen = generation
        worker.post {
            val bmp = render(app, size)
            main.post {
                if (gen != generation) return@post
                val callbacks = inFlight.remove(app.key) ?: return@post
                if (bmp != null) {
                    cache.put(app.key, bmp)
                    callbacks.forEach { it(bmp) }
                }
            }
        }
        return null
    }

    /** Warms the cache in the background, most important apps first. */
    fun preload(apps: List<AppEntry>) {
        for (app in apps) if (cache.get(app.key) == null && app.key !in inFlight) get(app) {}
    }

    fun invalidate(keys: Collection<ComponentKey>) {
        keys.forEach { cache.remove(it) }
    }

    fun loadDrawable(app: AppEntry): Drawable? = try {
        app.info.getBadgedIcon(density)
    } catch (e: Exception) {
        null
    }

    private fun render(app: AppEntry, size: Int): Bitmap? {
        if (size <= 0) return null
        return try {
            val drawable = app.info.getBadgedIcon(density) ?: return null
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            bmp.copy(Bitmap.Config.HARDWARE, false)?.also { bmp.recycle() } ?: bmp
        } catch (e: Exception) {
            Log.w("AuraIcons", "Icon for ${app.key} failed", e)
            null
        }
    }
}
