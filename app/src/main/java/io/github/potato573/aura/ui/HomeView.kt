package io.github.potato573.aura.ui

import android.annotation.SuppressLint
import android.content.Context
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import io.github.potato573.aura.R
import io.github.potato573.aura.util.Fonts
import io.github.potato573.aura.util.dp
import io.github.potato573.aura.util.roundRect
import java.util.Locale

/**
 * Static structure of the home screen, top to bottom:
 * clock and date, paged favourites, page dots, (optional) default-launcher card, dock,
 * search pill. Content is bound by [LauncherActivity].
 */
@SuppressLint("ViewConstructor")
class HomeView(context: Context) : LinearLayout(context) {

    val time = TextClock(context).apply {
        format12Hour = "h:mm"
        format24Hour = "HH:mm"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 64f)
        typeface = Fonts.sans(250)
        letterSpacing = -0.02f
        includeFontPadding = false
        isClickable = true
        isFocusable = true
    }

    val date = TextClock(context).apply {
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM")
        format12Hour = pattern
        format24Hour = pattern
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Fonts.sans(450)
        isClickable = true
        isFocusable = true
        setPadding(0, dp(6), 0, dp(4))
    }

    val clockBlock = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(dp(28), dp(36), dp(28), dp(8))
        addView(time, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(date, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    val workspace = Workspace(context)
    val indicator = PageIndicator(context)
    val dock = DockView(context)

    val promptText = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Fonts.sans(500)
        text = context.getString(R.string.default_prompt_title)
    }
    val promptAction = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Fonts.sans(650)
        text = context.getString(R.string.default_prompt_action)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        isClickable = true
        isFocusable = true
    }
    val promptClose = ImageView(context).apply {
        setImageResource(R.drawable.ic_close)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = context.getString(R.string.dismiss)
        isClickable = true
        isFocusable = true
    }
    val prompt = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(18), 0, dp(4), 0)
        minimumHeight = dp(52)
        addView(promptText, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(promptAction, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(promptClose, LayoutParams(dp(44), dp(44)))
        visibility = GONE
    }

    val searchText = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Fonts.sans(450)
        text = context.getString(R.string.search_hint)
        gravity = Gravity.CENTER_VERTICAL
        compoundDrawablePadding = dp(12)
    }
    val searchPill = FrameLayout(context).apply {
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(R.string.search_hint)
        setPadding(dp(20), 0, dp(20), 0)
        addView(searchText, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
        addView(clockBlock, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(workspace, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(indicator, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dp(4), 0, dp(10))
        })
        addView(prompt, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(16), 0, dp(16), dp(12))
        })
        addView(dock, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(12), 0, dp(12), dp(12))
        })
        addView(searchPill, LayoutParams(LayoutParams.MATCH_PARENT, dp(50)).apply {
            setMargins(dp(16), 0, dp(16), dp(12))
        })
    }

    /**
     * [darkText] is true for light wallpapers. Text then switches to near-black with no
     * shadow; on dark wallpapers it is white with a soft shadow for legibility.
     */
    fun applyWallpaperContrast(darkText: Boolean, palette: Palette) {
        val fg = if (darkText) 0xFF15161A.toInt() else 0xFFFFFFFF.toInt()
        val sub = if (darkText) 0xCC15161A.toInt() else 0xE6FFFFFF.toInt()
        for (tv in listOf(time, date)) {
            tv.setTextColor(if (tv === time) fg else sub)
            if (darkText) tv.setShadowLayer(0f, 0f, 0f, 0) else tv.setShadowLayer(dp(6f), 0f, dp(1f), 0x40000000)
        }
        indicator.color = fg
        dock.setSurface(darkText, palette.opacity)
        val (pillBg, pillStroke) = DockView.glassColors(darkText, palette.opacity)
        searchPill.background = roundRect(pillBg, dp(25f), pillStroke, dp(1))
        searchText.setTextColor(sub)
        val icon = context.getDrawable(R.drawable.ic_search)?.mutate()
        icon?.setTint(sub)
        searchText.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)

        prompt.background = roundRect(palette.surfaceRaised, dp(22f), palette.glassStroke, dp(1))
        prompt.elevation = dp(4f)
        promptText.setTextColor(palette.textPrimary)
        promptAction.setTextColor(palette.accentOnSurface)
        promptClose.setColorFilter(palette.textSecondary)
    }

    fun setSections(clock: Boolean, search: Boolean, dockVisible: Boolean) {
        clockBlock.visibility = if (clock) View.VISIBLE else View.GONE
        searchPill.visibility = if (search) View.VISIBLE else View.GONE
        dock.visibility = if (dockVisible) View.VISIBLE else View.GONE
    }
}
