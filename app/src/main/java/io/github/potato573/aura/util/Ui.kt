package io.github.potato573.aura.util

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.WindowInsets

fun Context.dp(value: Float): Float = value * resources.displayMetrics.density
fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
fun View.dp(value: Float): Float = context.dp(value)
fun View.dp(value: Int): Int = context.dp(value)
fun Context.sp(value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

object Fonts {
    /** Variable-weight system sans where available (API 28+), with a sensible fallback. */
    fun sans(weight: Int): Typeface = if (Build.VERSION.SDK_INT >= 28) {
        Typeface.create(Typeface.SANS_SERIF, weight, false)
    } else {
        when {
            weight <= 300 -> Typeface.create("sans-serif-light", Typeface.NORMAL)
            weight >= 600 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            weight >= 500 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            else -> Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }
}

fun roundRect(color: Int, radius: Float, strokeColor: Int = 0, strokeWidth: Int = 0) = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    cornerRadius = radius
    setColor(color)
    if (strokeWidth > 0) setStroke(strokeWidth, strokeColor)
}

/** Insets in pixels. (android.graphics.Insets needs API 29; AURA supports 26+.) */
data class Edges(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    companion object {
        val NONE = Edges(0, 0, 0, 0)
    }
}

/** System bar + display cutout insets. */
fun WindowInsets.systemBarsCompat(): Edges = if (Build.VERSION.SDK_INT >= 30) {
    val i = getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
    Edges(i.left, i.top, i.right, i.bottom)
} else {
    @Suppress("DEPRECATION")
    Edges(systemWindowInsetLeft, systemWindowInsetTop, systemWindowInsetRight, systemWindowInsetBottom)
}

fun WindowInsets.imeBottomCompat(): Int = if (Build.VERSION.SDK_INT >= 30) {
    getInsets(WindowInsets.Type.ime()).bottom
} else {
    @Suppress("DEPRECATION")
    (systemWindowInsetBottom - stableInsetBottom).coerceAtLeast(0)
}

fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

fun clamp01(v: Float) = v.coerceIn(0f, 1f)

/** Maps [v] from [from]..[to] into 0..1, clamped. */
fun progress(v: Float, from: Float, to: Float) = clamp01((v - from) / (to - from))
