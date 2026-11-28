package io.github.potato573.aura.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.potato573.aura.anim.Spring
import io.github.potato573.aura.anim.SpringAnimator
import io.github.potato573.aura.util.Fonts
import io.github.potato573.aura.util.dp
import io.github.potato573.aura.util.roundRect

/**
 * Long-press menu: app shortcuts as rows, system actions as a compact row of buttons.
 * Lives inside the launcher window (not a PopupWindow) so it can grow out of the icon with
 * a spring and be dismissed by any touch outside it.
 */
@SuppressLint("ViewConstructor")
class PopupMenuView(context: Context, private val palette: Palette) : FrameLayout(context) {

    class Entry(val icon: Drawable?, val label: CharSequence, val tintIcon: Boolean = true, val onClick: () -> Unit)

    var onDismissed: (() -> Unit)? = null

    /** Open amount, 0..1, every frame (drives the background blur). */
    var onProgress: ((Float) -> Unit)? = null

    private val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = roundRect(palette.glassPanel, dp(24f), palette.glassStroke, dp(1))
        // No shadow: under a translucent card it would show through as a grey smudge.
        clipToOutline = true
        setPadding(0, dp(6), 0, dp(6))
    }
    private var dismissing = false

    private val anim = SpringAnimator(Spring.STIFFNESS_MEDIUM, 0.78f, 0.002f, this) { p ->
        card.alpha = p.coerceIn(0f, 1f)
        val s = 0.86f + 0.14f * p
        card.scaleX = s
        card.scaleY = s
        onProgress?.invoke(p.coerceIn(0f, 1f))
    }

    init {
        isClickable = true
        isFocusable = true
        addView(card, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    /** Shows the menu near [anchor] (in [root] coordinates). */
    fun show(root: ViewGroup, anchor: Rect, shortcuts: List<Entry>, actions: List<Entry>, minTop: Int = 0) {
        val width = dp(248)
        shortcuts.forEach { card.addView(shortcutRow(it), LinearLayout.LayoutParams(width, dp(48))) }
        if (shortcuts.isNotEmpty() && actions.isNotEmpty()) {
            card.addView(View(context).apply { setBackgroundColor(palette.divider) },
                LinearLayout.LayoutParams(width, dp(1)).apply { setMargins(0, dp(6), 0, dp(6)) })
        }
        if (actions.isNotEmpty()) {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            actions.forEach { row.addView(actionButton(it), LinearLayout.LayoutParams(0, dp(64), 1f)) }
            card.addView(row, LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(4), 0, dp(4), 0)
            })
        }
        root.addView(this, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        card.measure(
            MeasureSpec.makeMeasureSpec(root.width, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(root.height, MeasureSpec.AT_MOST),
        )
        val cw = card.measuredWidth
        val ch = card.measuredHeight
        val margin = dp(12)
        val gap = dp(8)
        val x = (anchor.centerX() - cw / 2).coerceIn(margin, (root.width - cw - margin).coerceAtLeast(margin))
        val above = anchor.top - gap - ch
        val y = if (above >= minTop + margin) above else (anchor.bottom + gap).coerceAtMost(root.height - ch - margin)
        (card.layoutParams as LayoutParams).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = x
            topMargin = y
        }
        card.pivotX = (anchor.centerX() - x).toFloat().coerceIn(0f, cw.toFloat())
        card.pivotY = if (y < anchor.top) ch.toFloat() else 0f
        anim.snapTo(0f)
        anim.animateTo(1f)
    }

    private fun shortcutRow(e: Entry) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), 0, dp(16), 0)
        background = pressable()
        isClickable = true
        addView(ImageView(context).apply {
            setImageDrawable(e.icon)
            if (e.tintIcon) setColorFilter(palette.textSecondary)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(24), dp(24)))
        addView(TextView(context).apply {
            text = e.label
            setTextColor(palette.textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Fonts.sans(450)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14) })
        setOnClickListener { choose(e) }
    }

    private fun actionButton(e: Entry) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = pressable()
        isClickable = true
        contentDescription = e.label
        addView(ImageView(context).apply {
            setImageDrawable(e.icon)
            setColorFilter(palette.textPrimary)
        }, LinearLayout.LayoutParams(dp(22), dp(22)))
        addView(TextView(context).apply {
            text = e.label
            setTextColor(palette.textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Fonts.sans(500)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
        })
        setOnClickListener { choose(e) }
    }

    private fun pressable() = android.graphics.drawable.RippleDrawable(
        android.content.res.ColorStateList.valueOf(palette.pressed), null,
        roundRect(0xFF000000.toInt(), dp(16f)),
    )

    private fun choose(e: Entry) {
        if (dismissing) return
        dismiss()
        e.onClick()
    }

    fun dismiss() {
        if (dismissing) return
        dismissing = true
        anim.spring.dampingRatio = 1f
        anim.spring.stiffness = Spring.STIFFNESS_HIGH
        anim.animateTo(0f) {
            (parent as? ViewGroup)?.removeView(this)
            onDismissed?.invoke()
        }
    }

    val isDismissing: Boolean get() = dismissing

    /** Removes the menu without animation (a drag is starting from the same icon). */
    fun dismissNow() {
        if (dismissing && parent == null) return
        dismissing = true
        anim.cancel()
        (parent as? ViewGroup)?.removeView(this)
        onDismissed?.invoke()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) dismiss()
        return true
    }
}
