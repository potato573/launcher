package io.github.potato573.aura.ui

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import io.github.potato573.aura.R
import io.github.potato573.aura.util.Fonts
import io.github.potato573.aura.util.dp
import io.github.potato573.aura.util.roundRect
import io.github.potato573.aura.util.systemBarsCompat

/** Small builder for AURA's settings screens: flat rows, clear hierarchy, one accent. */
class SettingsUi(private val activity: Activity, val palette: Palette) {

    fun applyWindow(root: View) {
        val w = activity.window
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false)
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            w.insetsController?.setSystemBarsAppearance(if (palette.isDark) 0 else mask, mask)
        } else {
            @Suppress("DEPRECATION")
            var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            @Suppress("DEPRECATION")
            if (!palette.isDark) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            @Suppress("DEPRECATION")
            w.decorView.systemUiVisibility = flags
        }
        w.decorView.setBackgroundColor(palette.surface)
        root.setOnApplyWindowInsetsListener { v, insets ->
            val b = insets.systemBarsCompat()
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
    }

    fun title(text: CharSequence, subtitle: CharSequence? = null) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(40), dp(24), dp(20))
        addView(TextView(activity).apply {
            this.text = text
            setTextColor(palette.textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
            typeface = Fonts.sans(400)
        })
        if (subtitle != null) addView(TextView(activity).apply {
            this.text = subtitle
            setTextColor(palette.textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Fonts.sans(400)
            setPadding(0, dp(6), 0, 0)
        })
    }

    fun section(text: CharSequence) = TextView(activity).apply {
        this.text = text
        setTextColor(palette.accentOnSurface)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
        typeface = Fonts.sans(600)
        setPadding(dp(24), dp(28), dp(24), dp(8))
    }

    private fun pressable(): Drawable = RippleDrawable(ColorStateList.valueOf(palette.pressed), null, null)

    private fun texts(title: CharSequence, summary: CharSequence?): Pair<LinearLayout, TextView> {
        val summaryView = TextView(activity).apply {
            setTextColor(palette.textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Fonts.sans(400)
            this.text = summary
            visibility = if (summary.isNullOrEmpty()) View.GONE else View.VISIBLE
            setPadding(0, dp(2), 0, 0)
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(activity).apply {
                text = title
                setTextColor(palette.textPrimary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                typeface = Fonts.sans(450)
            })
            addView(summaryView)
        }
        return box to summaryView
    }

    private fun row(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(64)
        setPadding(dp(24), dp(10), dp(20), dp(10))
    }

    /** Tappable row. Returns the summary view so callers can update it. */
    fun action(title: CharSequence, summary: CharSequence? = null, onClick: () -> Unit): Pair<View, TextView> {
        val r = row()
        val (box, sum) = texts(title, summary)
        r.addView(box, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        r.addView(ImageView(activity).apply {
            setImageResource(R.drawable.ic_chevron)
            setColorFilter(palette.textSecondary)
        }, LinearLayout.LayoutParams(dp(20), dp(20)))
        r.background = pressable()
        r.isClickable = true
        r.setOnClickListener { onClick() }
        return r to sum
    }

    fun toggle(title: CharSequence, summary: CharSequence? = null, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val r = row()
        val (box, _) = texts(title, summary)
        r.addView(box, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        @Suppress("UseSwitchCompatOrMaterialCode")
        val sw = Switch(activity).apply {
            isChecked = checked
            thumbTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(palette.accent, if (palette.isDark) 0xFFB9BDC4.toInt() else 0xFFFFFFFF.toInt()),
            )
            trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Palette.withAlpha(palette.accent, 0.5f), Palette.withAlpha(palette.textSecondary, 0.4f)),
            )
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        r.addView(sw)
        r.background = pressable()
        r.isClickable = true
        r.contentDescription = title
        r.setOnClickListener {
            sw.isChecked = !sw.isChecked
            onChange(sw.isChecked)
        }
        r.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.isCheckable = true
                info.isChecked = sw.isChecked
            }
        }
        return r
    }

    /** "−  4  +" control for small integer ranges. */
    fun stepper(title: CharSequence, value: Int, min: Int, max: Int, onChange: (Int) -> Unit): View {
        val r = row()
        val (box, _) = texts(title, null)
        r.addView(box, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        var current = value
        val valueView = TextView(activity).apply {
            setTextColor(palette.textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Fonts.sans(600)
            gravity = Gravity.CENTER
            text = current.toString()
        }
        lateinit var minus: ImageView
        lateinit var plus: ImageView
        fun refresh() {
            valueView.text = current.toString()
            minus.isEnabled = current > min
            plus.isEnabled = current < max
            minus.alpha = if (current > min) 1f else 0.3f
            plus.alpha = if (current < max) 1f else 0.3f
        }
        fun button(icon: Int, desc: Int, delta: Int) = ImageView(activity).apply {
            setImageResource(icon)
            setColorFilter(palette.textPrimary)
            scaleType = ImageView.ScaleType.CENTER
            background = RippleDrawable(ColorStateList.valueOf(palette.pressed), roundRect(palette.field, dp(18f)), null)
            contentDescription = activity.getString(desc)
            setOnClickListener {
                val next = (current + delta).coerceIn(min, max)
                if (next != current) {
                    current = next
                    refresh()
                    onChange(next)
                }
            }
        }
        minus = button(R.drawable.ic_remove, R.string.decrease, -1)
        plus = button(R.drawable.ic_add, R.string.increase, +1)
        r.addView(minus, LinearLayout.LayoutParams(dp(36), dp(36)))
        r.addView(valueView, LinearLayout.LayoutParams(dp(40), LinearLayout.LayoutParams.WRAP_CONTENT))
        r.addView(plus, LinearLayout.LayoutParams(dp(36), dp(36)))
        refresh()
        return r
    }

    fun slider(title: CharSequence, value: Int, min: Int, max: Int, step: Int, format: (Int) -> String, onChange: (Int) -> Unit): View {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(14), dp(24), dp(8))
        }
        val header = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val (texts, _) = texts(title, null)
        val valueView = TextView(activity).apply {
            setTextColor(palette.textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Fonts.sans(500)
            text = format(value)
        }
        header.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(valueView)
        box.addView(header)
        val bar = SeekBar(activity).apply {
            this.max = (max - min) / step
            progress = (value - min) / step
            progressTintList = ColorStateList.valueOf(palette.accent)
            thumbTintList = ColorStateList.valueOf(palette.accent)
            progressBackgroundTintList = ColorStateList.valueOf(palette.textSecondary)
            contentDescription = title
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    valueView.text = format(min + progress * step)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                // Commit on release: every change re-renders icons, no need to do it per pixel.
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    onChange(min + (seekBar?.progress ?: 0) * step)
                }
            })
        }
        box.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)).apply {
            setMargins(-dp(12), dp(4), -dp(12), 0)
        })
        return box
    }

    /** Segmented choice (e.g. Auto / Light / Dark). */
    fun segmented(title: CharSequence, options: List<CharSequence>, selected: Int, onSelect: (Int) -> Unit): View {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(14), dp(24), dp(12))
        }
        box.addView(texts(title, null).first)
        val group = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundRect(palette.field, dp(22f))
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val views = ArrayList<TextView>()
        fun style(i: Int, sel: Int) {
            val v = views[i]
            val on = i == sel
            v.background = if (on) roundRect(palette.surfaceRaised, dp(18f)) else null
            v.elevation = if (on) dp(1f) else 0f
            v.setTextColor(if (on) palette.textPrimary else palette.textSecondary)
            v.isSelected = on
        }
        options.forEachIndexed { i, label ->
            val v = TextView(activity).apply {
                text = label
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Fonts.sans(550)
                isClickable = true
                setOnClickListener {
                    for (j in views.indices) style(j, i)
                    onSelect(i)
                }
            }
            views += v
            group.addView(v, LinearLayout.LayoutParams(0, dp(36), 1f))
        }
        for (j in views.indices) style(j, selected)
        box.addView(group, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })
        return box
    }

    fun swatches(title: CharSequence, colors: List<Int>, selected: Int, onSelect: (Int) -> Unit): View {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(14), dp(24), dp(12))
        }
        box.addView(texts(title, null).first)
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val checks = ArrayList<ImageView>()
        colors.forEachIndexed { i, c ->
            val cell = FrameLayout(activity).apply {
                isClickable = true
                contentDescription = activity.getString(R.string.accent_option, i + 1)
            }
            val dot = View(activity).apply { background = roundRect(c, dp(16f)) }
            val check = ImageView(activity).apply {
                setImageResource(R.drawable.ic_check)
                setColorFilter(0xFFFFFFFF.toInt())
                scaleType = ImageView.ScaleType.CENTER
                visibility = if (c == selected) View.VISIBLE else View.INVISIBLE
            }
            checks += check
            cell.addView(dot, FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER))
            cell.addView(check, FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER))
            cell.setOnClickListener {
                checks.forEachIndexed { j, v -> v.visibility = if (j == i) View.VISIBLE else View.INVISIBLE }
                onSelect(c)
            }
            row.addView(cell, LinearLayout.LayoutParams(0, dp(44), 1f))
        }
        box.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10)
            marginStart = -dp(6)
            marginEnd = -dp(6)
        })
        return box
    }

    fun footnote(text: CharSequence) = TextView(activity).apply {
        this.text = text
        setTextColor(palette.textSecondary)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = Fonts.sans(400)
        setPadding(dp(24), dp(24), dp(24), dp(32))
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun dp(v: Int) = activity.dp(v)
    private fun dp(v: Float) = activity.dp(v)
}
