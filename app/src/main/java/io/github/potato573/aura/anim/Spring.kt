package io.github.potato573.aura.anim

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Damped harmonic oscillator solved analytically, so a step is exact for any frame time:
 * the same motion results on 60, 90 and 120 Hz displays, and a dropped frame does not make
 * the spring overshoot or explode the way naive Euler integration does.
 *
 * Unit-agnostic: values can be pixels, a 0..1 progress, or a scale.
 */
class Spring(
    stiffness: Float = STIFFNESS_MEDIUM,
    dampingRatio: Float = DAMPING_NO_BOUNCY,
) {
    var stiffness: Float = stiffness
        set(value) {
            require(value > 0f) { "stiffness must be > 0" }
            field = value
        }
    var dampingRatio: Float = dampingRatio
        set(value) {
            require(value >= 0f) { "dampingRatio must be >= 0" }
            field = value
        }

    var value = 0f
    var velocity = 0f
    var target = 0f

    /** Advances the simulation by [dtSeconds]. */
    fun step(dtSeconds: Float) {
        if (dtSeconds <= 0f) return
        val t = dtSeconds.toDouble()
        val omega = sqrt(stiffness.toDouble())
        val zeta = dampingRatio.toDouble()
        val x0 = (value - target).toDouble()
        val v0 = velocity.toDouble()
        val x: Double
        val v: Double
        when {
            zeta > 1.0 -> { // Over-damped.
                val s = omega * sqrt(zeta * zeta - 1.0)
                val gPlus = -zeta * omega + s
                val gMinus = -zeta * omega - s
                val b = (gMinus * x0 - v0) / (gMinus - gPlus)
                val a = x0 - b
                x = a * exp(gMinus * t) + b * exp(gPlus * t)
                v = a * gMinus * exp(gMinus * t) + b * gPlus * exp(gPlus * t)
            }
            zeta == 1.0 -> { // Critically damped.
                val a = x0
                val b = v0 + omega * x0
                val e = exp(-omega * t)
                x = (a + b * t) * e
                v = (b - omega * (a + b * t)) * e
            }
            else -> { // Under-damped.
                val wd = omega * sqrt(1.0 - zeta * zeta)
                val a = x0
                val b = (zeta * omega * x0 + v0) / wd
                val e = exp(-zeta * omega * t)
                val c = cos(wd * t)
                val s = sin(wd * t)
                x = e * (a * c + b * s)
                v = e * ((-zeta * omega) * (a * c + b * s) + wd * (-a * s + b * c))
            }
        }
        value = (x + target).toFloat()
        velocity = v.toFloat()
    }

    /** True when the motion is no longer perceptible for the given thresholds. */
    fun isAtRest(valueThreshold: Float, velocityThreshold: Float): Boolean =
        abs(value - target) < valueThreshold && abs(velocity) < velocityThreshold

    fun snapToTarget() {
        value = target
        velocity = 0f
    }

    companion object {
        // Tuned to match the feel of Android's own launcher motion.
        const val STIFFNESS_HIGH = 1500f
        const val STIFFNESS_MEDIUM = 700f
        const val STIFFNESS_LOW = 380f
        const val DAMPING_NO_BOUNCY = 1f
        const val DAMPING_LOW_BOUNCY = 0.82f
        const val DAMPING_MEDIUM_BOUNCY = 0.6f
    }
}
