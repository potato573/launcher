package io.github.potato573.aura.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.potato573.aura.anim.Spring
import io.github.potato573.aura.anim.SpringAnimator
import io.github.potato573.aura.util.Fonts
import io.github.potato573.aura.util.dp
import io.github.potato573.aura.util.roundRect

/** A short message with an optional action, rising from the bottom edge. One at a time. */
@SuppressLint("ViewConstructor")
class Snackbar private constructor(context: Context, palette: Palette) : LinearLayout(context) {

    private val message = TextView(context).apply {
        setTextColor(palette.textPrimary)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Fonts.sans(450)
        maxLines = 3
    }
    private val action = TextView(context).apply {
        setTextColor(palette.accentOnSurface)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Fonts.sans(600)
        setPadding(dp(16), dp(10), dp(4), dp(10))
        isClickable = true
    }
    private val anim = SpringAnimator(Spring.STIFFNESS_MEDIUM, 0.85f, 0.002f, this) { p ->
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * dp(24f)
    }
    private val hideRunnable = Runnable { hide() }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = roundRect(palette.surfaceRaised, dp(18f), palette.divider, dp(1))
        elevation = dp(8f)
        setPadding(dp(18), dp(6), dp(12), dp(6))
        minimumHeight = dp(52)
        addView(message, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(action, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    fun hide() {
        removeCallbacks(hideRunnable)
        anim.animateTo(0f) { (parent as? ViewGroup)?.removeView(this) }
        if (current === this) current = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(hideRunnable)
        anim.cancel()
        if (current === this) current = null
    }

    companion object {
        private var current: Snackbar? = null

        fun show(
            root: FrameLayout,
            palette: Palette,
            text: CharSequence,
            bottomInset: Int,
            actionLabel: CharSequence? = null,
            onAction: (() -> Unit)? = null,
        ) {
            current?.hide()
            val bar = Snackbar(root.context, palette)
            bar.message.text = text
            if (actionLabel != null && onAction != null) {
                bar.action.text = actionLabel
                bar.action.setOnClickListener {
                    onAction()
                    bar.hide()
                }
            } else {
                bar.action.visibility = GONE
            }
            val m = bar.dp(16)
            root.addView(bar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM).apply { setMargins(m, 0, m, bottomInset + m) })
            bar.anim.snapTo(0f)
            bar.anim.animateTo(1f)
            bar.postDelayed(bar.hideRunnable, if (onAction != null) 5000L else 2800L)
            current = bar
        }

        fun dismissCurrent() {
            current?.hide()
        }
    }
}
