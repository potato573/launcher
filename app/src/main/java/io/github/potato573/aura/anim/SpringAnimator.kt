package io.github.potato573.aura.anim

import android.os.Build
import android.view.Choreographer
import android.view.View

/**
 * Drives a [Spring] from vsync. Because frames come from [Choreographer], the animation runs
 * at the display's refresh rate (60, 90 or 120 Hz) and stops requesting frames the moment it
 * comes to rest, so an idle launcher does no rendering work at all.
 *
 * A spring can be retargeted mid-flight; the current velocity is preserved, which is what
 * makes interrupted gestures feel continuous instead of restarting.
 */
class SpringAnimator(
    stiffness: Float = Spring.STIFFNESS_MEDIUM,
    dampingRatio: Float = Spring.DAMPING_NO_BOUNCY,
    private val valueThreshold: Float = 0.001f,
    private val host: View? = null,
    private val onUpdate: (Float) -> Unit,
) {
    val spring = Spring(stiffness, dampingRatio)
    private var lastFrameNanos = 0L
    var isRunning = false
        private set
    private var onEnd: (() -> Unit)? = null

    val value: Float get() = spring.value
    val target: Float get() = spring.target
    val velocity: Float get() = spring.velocity

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning) return
            val dt = if (lastFrameNanos == 0L) 1f / 60f else (frameTimeNanos - lastFrameNanos) / 1e9f
            lastFrameNanos = frameTimeNanos
            // Clamp long gaps (e.g. after the app was paused) so motion never jumps.
            spring.step(dt.coerceIn(0f, 1f / 24f))
            if (spring.isAtRest(valueThreshold, valueThreshold * 10f)) {
                spring.snapToTarget()
                isRunning = false
                requestHighFrameRate(false)
                onUpdate(spring.value)
                val end = onEnd
                onEnd = null
                end?.invoke()
            } else {
                onUpdate(spring.value)
                requestHighFrameRate(true)
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    /** Animates to [target], optionally adding [velocity] (units per second). */
    fun animateTo(target: Float, velocity: Float = spring.velocity, endAction: (() -> Unit)? = null) {
        spring.target = target
        spring.velocity = velocity
        onEnd = endAction
        if (!isRunning) {
            isRunning = true
            lastFrameNanos = 0L
            Choreographer.getInstance().postFrameCallback(frame)
        }
    }

    /** Jumps to [value] immediately, e.g. while a finger is dragging. */
    fun snapTo(value: Float) {
        cancel()
        spring.value = value
        spring.target = value
        spring.velocity = 0f
        onUpdate(value)
    }

    fun cancel() {
        if (isRunning) {
            isRunning = false
            Choreographer.getInstance().removeFrameCallback(frame)
            requestHighFrameRate(false)
        }
        onEnd = null
    }

    private var highRate = false

    /** Android 15+: ask for the panel's top refresh rate only while motion is happening. */
    private fun requestHighFrameRate(on: Boolean) {
        val v = host ?: return
        if (Build.VERSION.SDK_INT < 35 || on == highRate) return
        highRate = on
        v.requestedFrameRate = if (on) View.REQUESTED_FRAME_RATE_CATEGORY_HIGH else View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT
    }
}
