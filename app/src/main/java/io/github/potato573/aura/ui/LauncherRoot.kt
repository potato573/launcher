package io.github.potato573.aura.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import io.github.potato573.aura.util.dp
import kotlin.math.abs

/**
 * Top-level container. It owns the vertical gestures of the launcher:
 *  - swipe up on home: the drawer follows the finger,
 *  - swipe down in the drawer (when its list is at the top): the drawer follows back down,
 *  - swipe down on home: opens search.
 * Horizontal gestures are left to the [Workspace] pager. Taps and long presses go to the
 * icons as usual because nothing is intercepted until the finger clearly moves vertically.
 */
class LauncherRoot(context: Context) : FrameLayout(context) {

    interface Host {
        /** 0 = home, 1 = drawer fully open. */
        val drawerProgress: Float
        fun isGestureBlocked(): Boolean
        fun isDrawerAtTop(): Boolean
        fun onDrawerDragStart()
        fun onDrawerDrag(progress: Float)
        /** [velocity] in progress units per second (positive = towards open). */
        fun onDrawerDragEnd(velocity: Float)
        fun onSwipeDown()
    }

    var host: Host? = null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFling = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
    private var downX = 0f
    private var downY = 0f
    private var startProgress = 0f
    private var mode = Mode.NONE
    private var velocityTracker: VelocityTracker? = null

    private enum class Mode { NONE, DRAWER, SWIPE_DOWN }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val h = host ?: return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                mode = Mode.NONE
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(ev) }
                // Catch a drawer that is still animating: the finger takes over immediately.
                val p = h.drawerProgress
                if (p > 0.001f && p < 0.999f && !h.isGestureBlocked()) {
                    beginDrawerDrag(ev)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(ev)
                if (h.isGestureBlocked()) return false
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dy) < slop || abs(dy) < abs(dx)) return false
                val p = h.drawerProgress
                if (p < 0.5f) {
                    if (dy < 0) {
                        beginDrawerDrag(ev)
                        return true
                    } else {
                        mode = Mode.SWIPE_DOWN
                        return true
                    }
                } else if (dy > 0 && h.isDrawerAtTop()) {
                    beginDrawerDrag(ev)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }
        return false
    }

    private fun beginDrawerDrag(ev: MotionEvent) {
        mode = Mode.DRAWER
        downY = ev.y
        startProgress = host?.drawerProgress ?: 0f
        host?.onDrawerDragStart()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val h = host ?: return false
        if (mode == Mode.NONE) return ev.actionMasked == MotionEvent.ACTION_DOWN
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
        velocityTracker?.addMovement(ev)
        val travel = height.coerceAtLeast(1).toFloat()
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (mode == Mode.DRAWER) {
                val p = (startProgress + (downY - ev.y) / travel).coerceIn(0f, 1f)
                h.onDrawerDrag(p)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val vt = velocityTracker
                vt?.computeCurrentVelocity(1000)
                val vy = vt?.yVelocity ?: 0f
                vt?.recycle()
                velocityTracker = null
                val cancelled = ev.actionMasked == MotionEvent.ACTION_CANCEL
                when (mode) {
                    Mode.DRAWER -> h.onDrawerDragEnd(if (cancelled) 0f else -vy / travel)
                    Mode.SWIPE_DOWN -> {
                        val dy = ev.y - downY
                        if (!cancelled && (dy > dp(56) || vy > minFling * 6)) h.onSwipeDown()
                    }
                    Mode.NONE -> Unit
                }
                mode = Mode.NONE
            }
        }
        return true
    }
}
