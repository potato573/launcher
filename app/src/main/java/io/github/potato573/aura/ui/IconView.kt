package io.github.potato573.aura.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import io.github.potato573.aura.anim.Spring
import io.github.potato573.aura.anim.SpringAnimator
import io.github.potato573.aura.data.AppEntry
import io.github.potato573.aura.data.IconCache
import io.github.potato573.aura.util.Fonts
import io.github.potato573.aura.util.dp
import io.github.potato573.aura.util.sp

/**
 * One app (or folder) tile: icon plus optional label, drawn directly on the canvas.
 * No child views, no layout passes, a single cached bitmap per icon: cheap to bind,
 * measure and draw, which matters in a scrolling grid of hundreds of apps.
 */
@SuppressLint("ViewConstructor")
class IconView(context: Context) : View(context) {

    var iconSize: Int = dp(54)
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }
    var showLabel = true
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    /** Item this tile represents (an AppEntry, a HomeItem, …); used by click handlers. */
    var item: Any? = null

    private var app: AppEntry? = null
    private var label: CharSequence = ""
    private var shownLabel: CharSequence = ""
    private var bitmap: Bitmap? = null
    private var folderBitmaps: List<Bitmap?> = emptyList()
    private var isFolder = false
    private var bindToken = 0

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.sp(12f)
        typeface = Fonts.sans(450)
        textAlign = Paint.Align.CENTER
    }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val folderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val iconRect = Rect()
    private val tmpRect = Rect()
    private val tmpRectF = RectF()
    private val labelGap = dp(6)

    var highlightColor = 0
    var highlighted = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val pressSpring = SpringAnimator(Spring.STIFFNESS_HIGH, 0.62f, 0.002f, this) { s ->
        scaleX = s
        scaleY = s
    }.also { it.snapTo(1f) }

    init {
        isClickable = true
        isLongClickable = true
        isHapticFeedbackEnabled = true
        setColors(LABEL_ON_WALLPAPER, onWallpaper = true)
    }

    /**
     * [onWallpaper] adds a soft shadow under the label so it stays readable over any
     * wallpaper; inside the drawer (opaque sheet) the shadow is off.
     */
    fun setColors(labelColor: Int, onWallpaper: Boolean, folderColor: Int = 0x33FFFFFF) {
        textPaint.color = labelColor
        if (onWallpaper && labelColor == LABEL_ON_WALLPAPER) {
            textPaint.setShadowLayer(dp(3f), 0f, dp(0.5f), 0x66000000)
        } else {
            textPaint.clearShadowLayer()
        }
        placeholderPaint.color = (labelColor and 0x00FFFFFF) or 0x1F000000
        folderPaint.color = folderColor
        invalidate()
    }

    fun bindApp(entry: AppEntry?, icons: IconCache, labelOverride: CharSequence? = null) {
        val token = ++bindToken
        app = entry
        isFolder = false
        folderBitmaps = emptyList()
        setLabel(labelOverride ?: entry?.label.orEmpty())
        contentDescription = label
        bitmap = entry?.let { e ->
            icons.get(e) { bmp ->
                if (token == bindToken) {
                    bitmap = bmp
                    invalidate()
                }
            }
        }
        invalidate()
    }

    fun bindFolder(title: CharSequence, apps: List<AppEntry>, icons: IconCache) {
        val token = ++bindToken
        app = null
        isFolder = true
        bitmap = null
        setLabel(title)
        contentDescription = title
        val preview = apps.take(4)
        val result = arrayOfNulls<Bitmap>(preview.size)
        preview.forEachIndexed { i, e ->
            result[i] = icons.get(e) { bmp ->
                if (token == bindToken) {
                    result[i] = bmp
                    folderBitmaps = result.toList()
                    invalidate()
                }
            }
        }
        folderBitmaps = result.toList()
        invalidate()
    }

    private fun setLabel(text: CharSequence) {
        label = text
        updateShownLabel()
    }

    private fun updateShownLabel() {
        val avail = (width - paddingLeft - paddingRight - dp(4)).toFloat()
        shownLabel = if (avail > 0) TextUtils.ellipsize(label, textPaint, avail, TextUtils.TruncateAt.END) else label
    }

    /** Bounds of the icon (without label) in this view's coordinates. */
    fun iconBounds(out: Rect) {
        out.set(iconRect)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fm = textPaint.fontMetricsInt
        val labelH = if (showLabel) labelGap + (fm.descent - fm.ascent) else 0
        val desiredW = iconSize + dp(16) + paddingLeft + paddingRight
        val desiredH = iconSize + labelH + dp(12) + paddingTop + paddingBottom
        setMeasuredDimension(resolveSize(desiredW, widthMeasureSpec), resolveSize(desiredH, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        updateShownLabel()
    }

    override fun onDraw(canvas: Canvas) {
        val fm = textPaint.fontMetricsInt
        val textH = fm.descent - fm.ascent
        val blockH = iconSize + if (showLabel) labelGap + textH else 0
        val top = paddingTop + ((height - paddingTop - paddingBottom - blockH) / 2).coerceAtLeast(0)
        val left = (width - iconSize) / 2
        iconRect.set(left, top, left + iconSize, top + iconSize)

        if (isFolder) drawFolder(canvas) else drawIcon(canvas)

        if (highlighted && highlightColor != 0) {
            ringPaint.color = highlightColor
            ringPaint.strokeWidth = dp(2f)
            val inset = -dp(4f)
            tmpRectF.set(iconRect)
            tmpRectF.inset(inset, inset)
            val r = iconSize * 0.34f
            canvas.drawRoundRect(tmpRectF, r, r, ringPaint)
        }

        if (showLabel && shownLabel.isNotEmpty()) {
            val baseline = iconRect.bottom + labelGap - fm.ascent
            canvas.drawText(shownLabel, 0, shownLabel.length, width / 2f, baseline.toFloat(), textPaint)
        }
    }

    private fun drawIcon(canvas: Canvas) {
        val bmp = bitmap
        if (bmp != null) {
            canvas.drawBitmap(bmp, null, iconRect, bitmapPaint)
        } else {
            canvas.drawCircle(iconRect.exactCenterX(), iconRect.exactCenterY(), iconSize * 0.42f, placeholderPaint)
        }
    }

    private fun drawFolder(canvas: Canvas) {
        tmpRectF.set(iconRect)
        val r = iconSize * 0.3f
        canvas.drawRoundRect(tmpRectF, r, r, folderPaint)
        val pad = iconSize * 0.14f
        val gap = iconSize * 0.07f
        val cell = (iconSize - 2 * pad - gap) / 2f
        folderBitmaps.forEachIndexed { i, bmp ->
            val cx = iconRect.left + pad + (i % 2) * (cell + gap)
            val cy = iconRect.top + pad + (i / 2) * (cell + gap)
            tmpRect.set(cx.toInt(), cy.toInt(), (cx + cell).toInt(), (cy + cell).toInt())
            if (bmp != null) {
                canvas.drawBitmap(bmp, null, tmpRect, bitmapPaint)
            } else {
                canvas.drawCircle(tmpRect.exactCenterX(), tmpRect.exactCenterY(), cell * 0.42f, placeholderPaint)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> pressSpring.animateTo(PRESSED_SCALE)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> pressSpring.animateTo(1f)
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pressSpring.snapTo(1f)
    }

    companion object {
        const val PRESSED_SCALE = 0.9f
        const val LABEL_ON_WALLPAPER = 0xFFFFFFFF.toInt()
    }
}
