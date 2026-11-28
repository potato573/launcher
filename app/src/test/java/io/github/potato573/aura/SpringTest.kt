package io.github.potato573.aura

import io.github.potato573.aura.anim.Spring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SpringTest {
    private fun settle(spring: Spring, hz: Int): Pair<Float, Float> {
        var maxOvershoot = 0f
        val dt = 1f / hz
        var t = 0f
        while (t < 3f) {
            spring.step(dt)
            maxOvershoot = maxOf(maxOvershoot, spring.value - spring.target)
            t += dt
        }
        return spring.value to maxOvershoot
    }

    @Test fun reachesTargetCriticallyDampedWithoutOvershoot() {
        val s = Spring(Spring.STIFFNESS_MEDIUM, 1f).apply { value = 0f; target = 1f }
        val (v, over) = settle(s, 60)
        assertEquals(1f, v, 1e-3f)
        assertTrue(over <= 1e-4f)
    }

    @Test fun bouncySpringOvershootsThenSettles() {
        val s = Spring(Spring.STIFFNESS_MEDIUM, 0.5f).apply { value = 0f; target = 1f }
        val (v, over) = settle(s, 60)
        assertEquals(1f, v, 1e-3f)
        assertTrue(over > 0.05f)
    }

    @Test fun overDampedSettles() {
        val s = Spring(Spring.STIFFNESS_LOW, 1.6f).apply { value = 0f; target = 1f }
        assertEquals(1f, settle(s, 60).first, 1e-3f)
    }

    @Test fun motionIsIndependentOfRefreshRate() {
        fun at(hz: Int, seconds: Float): Float {
            val s = Spring(Spring.STIFFNESS_MEDIUM, 0.8f).apply { value = 0f; target = 1f; velocity = 3f }
            val steps = (seconds * hz).toInt()
            repeat(steps) { s.step(1f / hz) }
            return s.value
        }
        val a = at(60, 0.2f)
        val b = at(120, 0.2f)
        val c = at(90, 0.2f)
        assertTrue(abs(a - b) < 1e-3f)
        assertTrue(abs(a - c) < 1e-3f)
    }

    @Test fun initialVelocityCarriesThrough() {
        val s = Spring(Spring.STIFFNESS_MEDIUM, 1f).apply { value = 0f; target = 0f; velocity = 5f }
        s.step(0.016f)
        assertTrue(s.value > 0f)
    }

    @Test fun restDetection() {
        val s = Spring().apply { value = 1f; target = 1f }
        assertTrue(s.isAtRest(0.001f, 0.01f))
    }
}
