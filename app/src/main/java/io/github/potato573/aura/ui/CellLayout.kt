package io.github.potato573.aura.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import io.github.potato573.aura.util.dp

/** One home page: a fixed grid of [columns] x [rows] equal cells. */
@SuppressLint("ViewConstructor")
class CellLayout(context: Context) : ViewGroup(context) {

    class LayoutParams(var cellX: Int, var cellY: Int) : ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)

    var columns = 4
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }
    var rows = 4
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    val cellWidth: Int get() = if (columns == 0) 0 else (width - paddingLeft - paddingRight) / columns
    val cellHeight: Int get() = if (rows == 0) 0 else (height - paddingTop - paddingBottom) / rows

    /** Cell highlighted as a drop target while dragging; null when none. */
    private var dropCell: Pair<Int, Int>? = null
    private val dropPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dropRect = RectF()

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
    }

    fun setDropHighlight(x: Int, y: Int, color: Int) {
        val next = x to y
        if (dropCell == next && dropPaint.color == color) return
        dropCell = next
        dropPaint.color = color
        invalidate()
    }

    fun clearDropHighlight() {
        if (dropCell != null) {
            dropCell = null
            invalidate()
        }
    }

    fun childAt(x: Int, y: Int): View? {
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val lp = c.layoutParams as LayoutParams
            if (lp.cellX == x && lp.cellY == y) return c
        }
        return null
    }

    /** Cell under local point ([px], [py]), clamped into the grid. */
    fun cellAt(px: Float, py: Float): Pair<Int, Int> {
        val cw = cellWidth.coerceAtLeast(1)
        val ch = cellHeight.coerceAtLeast(1)
        val x = ((px - paddingLeft) / cw).toInt().coerceIn(0, columns - 1)
        val y = ((py - paddingTop) / ch).toInt().coerceIn(0, rows - 1)
        return x to y
    }

    fun cellRect(x: Int, y: Int, out: Rect) {
        val l = paddingLeft + x * cellWidth
        val t = paddingTop + y * cellHeight
        out.set(l, t, l + cellWidth, t + cellHeight)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val cw = (w - paddingLeft - paddingRight) / columns.coerceAtLeast(1)
        val ch = (h - paddingTop - paddingBottom) / rows.coerceAtLeast(1)
        val ws = MeasureSpec.makeMeasureSpec(cw.coerceAtLeast(0), MeasureSpec.EXACTLY)
        val hs = MeasureSpec.makeMeasureSpec(ch.coerceAtLeast(0), MeasureSpec.EXACTLY)
        for (i in 0 until childCount) getChildAt(i).measure(ws, hs)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cw = cellWidth
        val ch = cellHeight
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val lp = c.layoutParams as LayoutParams
            val x = paddingLeft + lp.cellX * cw
            val y = paddingTop + lp.cellY * ch
            c.layout(x, y, x + cw, y + ch)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cell = dropCell ?: return
        val l = paddingLeft + cell.first * cellWidth
        val t = paddingTop + cell.second * cellHeight
        val inset = dp(6f)
        dropRect.set(l + inset, t + inset, l + cellWidth - inset, t + cellHeight - inset)
        val r = dp(20f)
        canvas.drawRoundRect(dropRect, r, r, dropPaint)
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?) = p is LayoutParams
    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams = LayoutParams(0, 0)
    override fun shouldDelayChildPressedState() = false
}
