package io.github.potato573.aura.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.ViewGroup
import io.github.potato573.aura.util.dp

/**
 * The dock: up to five apps on a single translucent, softly outlined pill. This is the one
 * place AURA uses a glass-like surface, so it reads as a distinct, always-there shelf.
 */
class DockView(context: Context) : ViewGroup(context) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val rect = RectF()
    var iconSize = dp(54)
    private val vPad = dp(12)

    init {
        setWillNotDraw(false)
        clipChildren = false
        setSurface(darkWallpaperText = false)
    }

    /** Light wallpapers (dark text) get a lighter, more opaque pill so icons stay crisp. */
    fun setSurface(darkWallpaperText: Boolean) {
        if (darkWallpaperText) {
            bgPaint.color = 0x59FFFFFF
            strokePaint.color = 0x33FFFFFF
        } else {
            bgPaint.color = 0x2EFFFFFF
            strokePaint.color = 0x24FFFFFF
        }
        invalidate()
    }

    /** Index where a drop at local x would land, among the current children. */
    fun slotAt(x: Float): Int {
        if (childCount == 0) return 0
        // Children are centred; find the gap the finger is closest to.
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (x < c.left + c.width / 2f) return i
        }
        return childCount
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = iconSize + vPad * 2
        setMeasuredDimension(w, h)
        val n = childCount.coerceAtLeast(1)
        val slotW = slotWidth(w, n)
        val cs = MeasureSpec.makeMeasureSpec(slotW, MeasureSpec.EXACTLY)
        val hs = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) getChildAt(i).measure(cs, hs)
    }

    private fun slotWidth(w: Int, n: Int): Int = minOf(w / n, (iconSize * 1.7f).toInt())

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        val n = childCount
        if (n == 0) return
        val slotW = slotWidth(w, n)
        var x = (w - slotW * n) / 2
        for (i in 0 until n) {
            getChildAt(i).layout(x, 0, x + slotW, b - t)
            x += slotW
        }
    }

    override fun onDraw(canvas: Canvas) {
        val inset = dp(1f)
        rect.set(inset, inset, width - inset, height - inset)
        val r = height / 2.6f
        canvas.drawRoundRect(rect, r, r, bgPaint)
        canvas.drawRoundRect(rect, r, r, strokePaint)
    }
}
