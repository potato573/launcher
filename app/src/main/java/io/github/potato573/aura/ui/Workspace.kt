package io.github.potato573.aura.ui

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.ViewGroup
import io.github.potato573.aura.anim.Spring
import io.github.potato573.aura.anim.SpringAnimator
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Horizontally paged home screen. The finger moves the pages 1:1; on release a spring
 * carries the release velocity into the snap, so a quick flick and a slow drag both land
 * naturally. Edges resist with a rubber band instead of a hard stop. The wallpaper scrolls
 * with the pages through the official WallpaperManager offsets.
 */
@SuppressLint("ViewConstructor")
class Workspace(context: Context) : ViewGroup(context) {

    val pages: List<CellLayout> get() = (0 until childCount).map { getChildAt(it) as CellLayout }

    var currentPage = 0
        private set

    /** Called with the scroll position in pages (e.g. 1.5 = halfway between page 1 and 2). */
    var onScroll: ((Float) -> Unit)? = null
    var onPageSettled: ((Int) -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFling = ViewConfiguration.get(context).scaledMinimumFlingVelocity * 4
    private var velocityTracker: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var dragging = false
    private val wallpaper = WallpaperManager.getInstance(context)

    private val scroller = SpringAnimator(Spring.STIFFNESS_LOW, 0.92f, 0.5f, this) { x ->
        scrollTo(x.roundToInt(), 0)
    }

    val pageCount: Int get() = childCount

    fun snapToPage(page: Int, animate: Boolean = true) {
        val target = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        currentPage = target
        val x = (target * width).toFloat()
        if (animate && width > 0) {
            if (!scroller.isRunning) scroller.spring.value = scrollX.toFloat()
            scroller.animateTo(x) { onPageSettled?.invoke(target) }
        } else {
            scroller.snapTo(x)
            onPageSettled?.invoke(target)
        }
    }

    override fun scrollTo(x: Int, y: Int) {
        super.scrollTo(x, y)
        val w = width.coerceAtLeast(1)
        val pos = x.toFloat() / w
        onScroll?.invoke(pos)
        updateWallpaper(pos)
    }

    private fun updateWallpaper(pos: Float) {
        val token = windowToken ?: return
        val n = pageCount
        val step = if (n > 1) 1f / (n - 1) else 0f
        wallpaper.setWallpaperOffsetSteps(step, 1f)
        wallpaper.setWallpaperOffsets(token, if (n > 1) (pos / (n - 1)).coerceIn(0f, 1f) else 0.5f, 0.5f)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { updateWallpaper(scrollX.toFloat() / width.coerceAtLeast(1)) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val ws = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY)
        val hs = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) getChildAt(i).measure(ws, hs)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        for (i in 0 until childCount) getChildAt(i).layout(i * w, 0, (i + 1) * w, b - t)
        if (!scroller.isRunning && !dragging) {
            currentPage = currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
            scrollTo(currentPage * w, 0)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                lastX = ev.x
                // A finger landing on a settling page catches it, like a physical surface.
                if (scroller.isRunning) {
                    scroller.cancel()
                    startDrag(ev)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (pageCount > 1 && abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.2f) {
                    startDrag(ev)
                    return true
                }
            }
        }
        return false
    }

    private fun startDrag(ev: MotionEvent) {
        dragging = true
        lastX = ev.x
        parent?.requestDisallowInterceptTouchEvent(true)
        velocityTracker?.recycle()
        velocityTracker = VelocityTracker.obtain().also { it.addMovement(ev) }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!dragging) return ev.actionMasked == MotionEvent.ACTION_DOWN && pageCount > 1
        velocityTracker?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dx = lastX - ev.x
                lastX = ev.x
                val max = ((pageCount - 1) * width).toFloat()
                var next = scrollX + dx
                // Rubber band beyond the first/last page.
                if (next < 0f || next > max) next = scrollX + dx * 0.35f
                scrollTo(next.roundToInt(), 0)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val vt = velocityTracker
                vt?.computeCurrentVelocity(1000)
                val vx = vt?.xVelocity ?: 0f
                vt?.recycle()
                velocityTracker = null
                dragging = false
                val w = width.coerceAtLeast(1)
                val pos = scrollX.toFloat() / w
                val target = when {
                    vx < -minFling -> kotlin.math.floor(pos).toInt() + 1
                    vx > minFling -> kotlin.math.ceil(pos).toInt() - 1
                    else -> pos.roundToInt()
                }.coerceIn(0, pageCount - 1)
                currentPage = target
                scroller.spring.value = scrollX.toFloat()
                scroller.animateTo((target * w).toFloat(), -vx) { onPageSettled?.invoke(target) }
            }
        }
        return true
    }

    fun isScrolling() = dragging || scroller.isRunning
}
