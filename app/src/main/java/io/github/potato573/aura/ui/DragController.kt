package io.github.potato573.aura.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import io.github.potato573.aura.anim.Spring
import io.github.potato573.aura.anim.SpringAnimator
import io.github.potato573.aura.model.Cell
import io.github.potato573.aura.model.DropLogic
import io.github.potato573.aura.util.dp
import io.github.potato573.aura.util.lerp
import kotlin.math.hypot

/**
 * Long-press-then-drag. After a long press the menu appears; if the finger then moves, the
 * menu gives way and the icon lifts off and follows the finger. Hovering shows where it will
 * land (an empty cell, or a folder when over another app); holding at a screen edge turns the
 * page. On release the icon flies into place with a spring, then the layout is committed.
 */
class DragController(private val root: LauncherRoot, private val host: Host) {

    interface Host {
        val workspace: Workspace
        val dock: DockView
        val dockEnabled: Boolean
        val columns: Int
        val rows: Int
        val highlightColor: Int
        /** The finger moved: close menus/folders/drawer and hide the source. */
        fun onDragStart(source: DropLogic.Source)
        /** What the cell under the finger would hold (null if empty) for this source. */
        fun occupantId(source: DropLogic.Source, target: DropLogic.Target): Long?
        /** Commits the drop. Returns false if it was rejected. */
        fun commitDrop(source: DropLogic.Source, target: DropLogic.Target): Boolean
        /** Called last, whether the drop succeeded or not. */
        fun onDragEnd()
        /** A page to the right is needed while dragging (the last page is reached). */
        fun requestNewPage()
        fun pageHasItems(page: Int): Boolean
    }

    private enum class State { IDLE, ARMED, DRAGGING, SETTLING }

    private var state = State.IDLE
    private var source: DropLogic.Source? = null
    private var sourceView: IconView? = null
    private var sourceBitmap: Bitmap? = null
    private var downX = 0f
    private var downY = 0f
    private var grabX = 0f
    private var grabY = 0f
    private var target: DropLogic.Target? = null
    private var highlighted: CellLayout? = null
    private var dragView: DragView? = null
    private val slop = ViewConfiguration.get(root.context).scaledTouchSlop.toFloat()
    private val edge = root.dp(28f)
    private var edgeSince = 0L
    private var edgeDirection = 0

    val isActive: Boolean get() = state == State.DRAGGING || state == State.SETTLING
    val isArmed: Boolean get() = state == State.ARMED

    /**
     * Prepares a drag from [view] after a long press at root coordinates ([x], [y]).
     * [bitmap] is drawn while dragging; when null the source view itself is drawn.
     */
    fun arm(source: DropLogic.Source, view: IconView, bitmap: Bitmap?, x: Float, y: Float) {
        if (state != State.IDLE) return
        this.source = source
        sourceView = view
        sourceBitmap = bitmap
        downX = x
        downY = y
        state = State.ARMED
    }

    /** Called by [LauncherRoot] for every touch event; returns true to consume it. */
    fun onTouch(ev: MotionEvent): Boolean {
        when (state) {
            State.IDLE -> return false
            State.SETTLING -> return true
            State.ARMED -> {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_MOVE -> {
                        if (hypot(ev.x - downX, ev.y - downY) > slop) {
                            startDrag(ev)
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> reset()
                }
                return false
            }
            State.DRAGGING -> {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_MOVE -> move(ev.x, ev.y)
                    MotionEvent.ACTION_UP -> drop()
                    MotionEvent.ACTION_CANCEL -> cancel()
                }
                return true
            }
        }
    }

    private fun startDrag(ev: MotionEvent) {
        val view = sourceView ?: return reset()
        val src = source ?: return reset()
        val iconRect = rootIconRect(view)
        grabX = downX - iconRect.left
        grabY = downY - iconRect.top
        val dv = DragView(root.context, sourceBitmap, if (sourceBitmap == null) view else null, view.iconSize)
        root.addView(dv, iconRect.width(), iconRect.height())
        dragView = dv
        dv.translationX = iconRect.left.toFloat()
        dv.translationY = iconRect.top.toFloat()
        dv.lift()
        state = State.DRAGGING
        host.onDragStart(src)
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        move(ev.x, ev.y)
    }

    private fun rootIconRect(view: IconView): Rect {
        val r = Rect()
        view.iconBounds(r)
        val vLoc = IntArray(2)
        val rLoc = IntArray(2)
        view.getLocationInWindow(vLoc)
        root.getLocationInWindow(rLoc)
        r.offset(vLoc[0] - rLoc[0], vLoc[1] - rLoc[1])
        if (r.isEmpty) r.set(downX.toInt() - 60, downY.toInt() - 60, downX.toInt() + 60, downY.toInt() + 60)
        return r
    }

    private fun move(x: Float, y: Float) {
        val dv = dragView ?: return
        dv.translationX = x - grabX
        dv.translationY = y - grabY
        val src = source ?: return
        val newTarget = findTarget(x, y, src)
        if (newTarget != target) {
            target = newTarget
            updateHighlight(src, newTarget)
        }
        handleEdge(x, y)
    }

    private fun locationIn(view: View): IntArray {
        val v = IntArray(2)
        val r = IntArray(2)
        view.getLocationInWindow(v)
        root.getLocationInWindow(r)
        return intArrayOf(v[0] - r[0], v[1] - r[1])
    }

    private fun findTarget(x: Float, y: Float, src: DropLogic.Source): DropLogic.Target? {
        val dock = host.dock
        if (host.dockEnabled && dock.visibility == View.VISIBLE) {
            val loc = locationIn(dock)
            if (y >= loc[1] - dp(8) && y <= loc[1] + dock.height + dp(8) && x >= loc[0] && x <= loc[0] + dock.width) {
                var index = dock.slotAt(x - loc[0])
                // Reordering inside the dock: indices refer to the dock without the dragged app.
                if (src is DropLogic.Source.Dock) {
                    val from = (0 until dock.childCount).firstOrNull { (dock.getChildAt(it) as? IconView)?.item == src.key }
                    if (from != null && from < index) index -= 1
                }
                return DropLogic.Target.DockSlot(index)
            }
        }
        val ws = host.workspace
        val wsLoc = locationIn(ws)
        if (x < wsLoc[0] || x > wsLoc[0] + ws.width || y < wsLoc[1] || y > wsLoc[1] + ws.height) return null
        val page = ws.currentPage
        val cl = ws.pages.getOrNull(page) ?: return null
        val pLoc = locationIn(cl)
        val (cx, cy) = cl.cellAt(x - pLoc[0], y - pLoc[1])
        return DropLogic.Target.OnCell(Cell(page, cx, cy))
    }

    private fun dp(v: Int) = root.dp(v)

    private fun updateHighlight(src: DropLogic.Source, t: DropLogic.Target?) {
        highlighted?.clearDropHighlight()
        highlighted = null
        if (t !is DropLogic.Target.OnCell) return
        val cl = host.workspace.pages.getOrNull(t.cell.page) ?: return
        val occupied = host.occupantId(src, t) != null
        // Empty cell: soft glow. Occupied: a stronger tile, meaning "make a folder".
        val color = if (occupied) Palette.withAlpha(host.highlightColor, 0.38f) else 0x29FFFFFF
        cl.setDropHighlight(t.cell.x, t.cell.y, color)
        highlighted = cl
    }

    private fun handleEdge(x: Float, y: Float) {
        val ws = host.workspace
        val loc = locationIn(ws)
        val inWorkspaceY = y >= loc[1] && y <= loc[1] + ws.height
        val dir = when {
            !inWorkspaceY -> 0
            x < edge -> -1
            x > root.width - edge -> 1
            else -> 0
        }
        val now = SystemClock.uptimeMillis()
        if (dir != edgeDirection) {
            edgeDirection = dir
            edgeSince = now
            return
        }
        if (dir == 0 || now - edgeSince < EDGE_DELAY_MS || ws.isScrolling()) return
        edgeSince = now
        val page = ws.currentPage
        if (dir > 0 && page == ws.pageCount - 1) {
            // Only grow when this page holds something; never create a trail of empty pages.
            if (!host.pageHasItems(page)) return
            host.requestNewPage()
        }
        val next = (page + dir).coerceIn(0, ws.pageCount - 1)
        if (next != page) {
            highlighted?.clearDropHighlight()
            highlighted = null
            target = null
            ws.snapToPage(next)
        }
    }

    private fun drop() {
        val src = source ?: return cancel()
        val t = target ?: findTargetAfterScroll(src)
        highlighted?.clearDropHighlight()
        highlighted = null
        if (t == null) return cancel()
        val dest = targetRect(t) ?: return cancel()
        state = State.SETTLING
        dragView?.flyTo(dest.left.toFloat(), dest.top.toFloat(), landing = true) {
            val ok = host.commitDrop(src, t)
            if (!ok) sourceView?.alpha = 1f
            finish()
        }
    }

    private fun findTargetAfterScroll(src: DropLogic.Source): DropLogic.Target? {
        val dv = dragView ?: return null
        return findTarget(dv.translationX + grabX, dv.translationY + grabY, src)
    }

    private fun targetRect(t: DropLogic.Target): Rect? {
        val size = dragView?.let { Rect(0, 0, it.width, it.height) } ?: return null
        return when (t) {
            is DropLogic.Target.OnCell -> {
                val cl = host.workspace.pages.getOrNull(t.cell.page) ?: return null
                val cell = Rect()
                cl.cellRect(t.cell.x, t.cell.y, cell)
                val loc = locationIn(cl)
                // Icon sits in the upper part of its cell (label below).
                val labelSpace = root.dp(22)
                val left = loc[0] + cell.centerX() - size.width() / 2
                val top = loc[1] + cell.top + (cell.height() - size.height() - labelSpace) / 2
                Rect(left, top, left + size.width(), top + size.height())
            }
            is DropLogic.Target.DockSlot -> {
                val dock = host.dock
                val loc = locationIn(dock)
                val n = (dock.childCount + 1).coerceAtMost(io.github.potato573.aura.model.HomeLayout.MAX_DOCK)
                val slot = dock.width / n.coerceAtLeast(1)
                val left = loc[0] + slot * t.index.coerceIn(0, n - 1) + (slot - size.width()) / 2
                val top = loc[1] + (dock.height - size.height()) / 2
                Rect(left, top, left + size.width(), top + size.height())
            }
        }
    }

    private fun cancel() {
        highlighted?.clearDropHighlight()
        highlighted = null
        val view = sourceView
        val dv = dragView
        if (view == null || dv == null || !view.isAttachedToWindow) {
            sourceView?.alpha = 1f
            finish()
            return
        }
        state = State.SETTLING
        val r = rootIconRect(view)
        dv.flyTo(r.left.toFloat(), r.top.toFloat(), landing = false) {
            view.alpha = 1f
            finish()
        }
    }

    private fun finish() {
        dragView?.let { root.removeView(it) }
        dragView = null
        reset()
        host.onDragEnd()
    }

    private fun reset() {
        state = State.IDLE
        source = null
        sourceView = null
        sourceBitmap = null
        target = null
        edgeDirection = 0
    }

    /** The floating icon. */
    @SuppressLint("ViewConstructor")
    private class DragView(
        context: Context,
        private val bitmap: Bitmap?,
        private val delegate: IconView?,
        private val iconSize: Int,
    ) : View(context) {
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val dst = RectF()
        private val delegateRect = Rect()
        private var startX = 0f
        private var startY = 0f
        private var endX = 0f
        private var endY = 0f
        private var onArrive: (() -> Unit)? = null

        private val liftSpring = SpringAnimator(Spring.STIFFNESS_MEDIUM, 0.6f, 0.002f, this) { s ->
            scaleX = s
            scaleY = s
        }
        private val fly = SpringAnimator(Spring.STIFFNESS_MEDIUM, 0.86f, 0.002f, this) { p ->
            translationX = lerp(startX, endX, p)
            translationY = lerp(startY, endY, p)
        }

        init {
            elevation = dp(10f)
            // Round, soft shadow matching the icon shape rather than a square.
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, view.width * 0.3f)
                    outline.alpha = 0.5f
                }
            }
        }

        fun lift() {
            liftSpring.snapTo(1f)
            liftSpring.animateTo(1.1f)
        }

        fun flyTo(x: Float, y: Float, landing: Boolean, end: () -> Unit) {
            startX = translationX
            startY = translationY
            endX = x
            endY = y
            onArrive = end
            liftSpring.animateTo(1f)
            if (landing) elevation = dp(4f)
            fly.snapTo(0f)
            fly.animateTo(1f) {
                val cb = onArrive
                onArrive = null
                cb?.invoke()
            }
        }

        override fun onDraw(canvas: Canvas) {
            val b = bitmap
            if (b != null) {
                dst.set(0f, 0f, width.toFloat(), height.toFloat())
                canvas.drawBitmap(b, null, dst, paint)
                return
            }
            val d = delegate ?: return
            // Draw the source view's icon area (e.g. a folder preview) into our bounds.
            d.iconBounds(delegateRect)
            canvas.save()
            val s = width.toFloat() / iconSize.coerceAtLeast(1)
            canvas.scale(s, s)
            canvas.translate(-delegateRect.left.toFloat(), -delegateRect.top.toFloat())
            d.draw(canvas)
            canvas.restore()
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            liftSpring.cancel()
            fly.cancel()
        }
    }

    companion object {
        private const val EDGE_DELAY_MS = 550L
    }
}
