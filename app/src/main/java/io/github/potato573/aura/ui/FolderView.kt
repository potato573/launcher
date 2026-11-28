package io.github.potato573.aura.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.potato573.aura.R
import io.github.potato573.aura.anim.Spring
import io.github.potato573.aura.anim.SpringAnimator
import io.github.potato573.aura.data.AppEntry
import io.github.potato573.aura.data.IconCache
import io.github.potato573.aura.model.FolderItem
import io.github.potato573.aura.util.Fonts
import io.github.potato573.aura.util.dp
import io.github.potato573.aura.util.roundRect
import kotlin.math.ceil

/**
 * An open folder: a card that grows out of the folder icon, with an editable name and the
 * folder's apps. Apps can be launched, long-pressed for their menu, or dragged back out.
 */
@SuppressLint("ViewConstructor")
class FolderView(
    context: Context,
    private val palette: Palette,
    private val icons: IconCache,
    val folder: FolderItem,
    private val apps: List<AppEntry>,
    private val iconSize: Int,
) : FrameLayout(context) {

    var onLaunch: ((AppEntry, IconView) -> Unit)? = null
    var onLongPress: ((AppEntry, IconView) -> Unit)? = null
    var onRename: ((String) -> Unit)? = null
    var onClosed: (() -> Unit)? = null

    /** Open amount, 0..1, every frame (drives the background blur). */
    var onProgress: ((Float) -> Unit)? = null

    private val title = EditText(context).apply {
        setText(folder.title)
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        typeface = Fonts.sans(500)
        setTextColor(palette.textPrimary)
        setHintTextColor(palette.textSecondary)
        hint = context.getString(R.string.folder_name_hint)
        gravity = Gravity.CENTER
        background = null
        importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO
        setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commitTitle()
                v.clearFocus()
                hideKeyboard()
                true
            } else {
                false
            }
        }
    }

    private val grid = CellLayout(context)
    private val columns = when {
        apps.size <= 4 -> 2
        apps.size <= 9 -> 3
        else -> 4
    }
    private val gridScroll = android.widget.ScrollView(context).apply {
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
    }
    private val rows = ceil(apps.size / columns.toFloat()).toInt().coerceAtLeast(1)
    private val cellW = iconSize + dp(36)
    private val cellH = iconSize + dp(40)

    val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = roundRect(palette.glassPanel, dp(32f), palette.glassStroke, dp(1))
        setPadding(dp(12), dp(16), dp(12), dp(16))
    }

    private var closing = false
    private val anim = SpringAnimator(Spring.STIFFNESS_MEDIUM, 0.82f, 0.002f, this) { p ->
        card.alpha = p.coerceIn(0f, 1f)
        val s = 0.6f + 0.4f * p
        card.scaleX = s
        card.scaleY = s
        // With real blur behind, a light dim is enough; without it the dim does more work.
        val dim = if (palette.blur) 0.12f else 0.3f
        setBackgroundColor(Palette.withAlpha(0xFF000000.toInt(), dim * p.coerceIn(0f, 1f)))
        onProgress?.invoke(p.coerceIn(0f, 1f))
    }

    init {
        isClickable = true
        grid.columns = columns
        grid.rows = rows
        apps.forEachIndexed { i, app ->
            val v = IconView(context)
            v.iconSize = iconSize
            v.setColors(palette.textPrimary, onWallpaper = false)
            v.item = app
            v.bindApp(app, icons)
            v.setOnClickListener { onLaunch?.invoke(app, v) }
            v.setOnLongClickListener { onLongPress?.invoke(app, v); true }
            grid.addView(v, CellLayout.LayoutParams(i % columns, i / columns))
        }
        card.addView(title, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)))
        gridScroll.addView(grid, LayoutParams(cellW * columns, cellH * rows))
        card.addView(gridScroll, LinearLayout.LayoutParams(cellW * columns, cellH * rows).apply { topMargin = dp(8) })
        addView(card, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    /** Opens centred on screen, growing from [anchor] (root coordinates). */
    fun open(root: ViewGroup, anchor: Rect) {
        root.addView(this, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // Large folders scroll inside the card instead of running off screen.
        val maxGrid = (root.height * 0.6f).toInt()
        (gridScroll.layoutParams as LinearLayout.LayoutParams).height = minOf(cellH * rows, maxGrid)
        card.measure(
            MeasureSpec.makeMeasureSpec(root.width, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(root.height, MeasureSpec.AT_MOST),
        )
        val w = card.measuredWidth
        val h = card.measuredHeight
        val x = ((root.width - w) / 2).coerceAtLeast(0)
        val y = ((root.height - h) / 2 - dp(24)).coerceAtLeast(root.paddingTop)
        (card.layoutParams as LayoutParams).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = x
            topMargin = y
        }
        card.pivotX = (anchor.centerX() - x).toFloat()
        card.pivotY = (anchor.centerY() - y).toFloat()
        anim.snapTo(0f)
        anim.animateTo(1f)
    }

    private fun commitTitle() {
        val t = title.text.toString().trim()
        if (t != folder.title) onRename?.invoke(t)
    }

    private fun hideKeyboard() {
        context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(windowToken, 0)
    }

    /** Closes the folder; [animate] = false removes it at once (e.g. when a drag starts). */
    fun close(animate: Boolean = true) {
        if (closing) return
        closing = true
        commitTitle()
        hideKeyboard()
        if (!animate) {
            anim.cancel()
            (parent as? ViewGroup)?.removeView(this)
            onClosed?.invoke()
            return
        }
        anim.spring.dampingRatio = 1f
        anim.spring.stiffness = Spring.STIFFNESS_HIGH
        anim.animateTo(0f) {
            (parent as? ViewGroup)?.removeView(this)
            onClosed?.invoke()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Touch on the dimmed area outside the card closes the folder.
        if (event.actionMasked == MotionEvent.ACTION_DOWN) close()
        return true
    }
}
