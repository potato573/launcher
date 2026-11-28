package io.github.potato573.aura.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import io.github.potato573.aura.util.dp
import kotlin.math.abs

/** Minimal dots; the active one stretches into a pill and follows the scroll continuously. */
class PageIndicator(context: Context) : View(context) {

    var count = 1
        set(value) {
            if (field != value) {
                field = value
                visibility = if (value > 1 && dotsEnabled) VISIBLE else INVISIBLE
                updateDescription()
                invalidate()
            }
        }
    /** The user can switch the dots off entirely. */
    var dotsEnabled = true
        set(value) {
            if (field != value) {
                field = value
                visibility = if (value && count > 1) VISIBLE else INVISIBLE
            }
        }
    var position = 0f
        set(value) {
            if (field != value) {
                field = value
                updateDescription()
                invalidate()
            }
        }
    var color = 0xFFFFFFFF.toInt()
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = dp(6f)
    private val gap = dp(8f)
    private val activeWidth = dp(16f)
    private val rect = RectF()

    init {
        visibility = INVISIBLE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun updateDescription() {
        val page = (position + 0.5f).toInt().coerceIn(0, count - 1) + 1
        contentDescription = context.getString(io.github.potato573.aura.R.string.page_indicator, page, count)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec), dp(16))
    }

    override fun onDraw(canvas: Canvas) {
        if (count <= 1) return
        val total = (count - 1) * (dot + gap) + activeWidth
        var x = (width - total) / 2f
        val cy = height / 2f
        for (i in 0 until count) {
            val t = (1f - abs(position - i)).coerceIn(0f, 1f) // 1 = active
            val w = dot + (activeWidth - dot) * t
            paint.color = Palette.withAlpha(color, 0.38f + 0.62f * t)
            rect.set(x, cy - dot / 2, x + w, cy + dot / 2)
            canvas.drawRoundRect(rect, dot / 2, dot / 2, paint)
            x += w + gap
        }
    }
}
