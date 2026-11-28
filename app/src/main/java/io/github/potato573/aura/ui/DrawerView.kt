package io.github.potato573.aura.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.potato573.aura.R
import io.github.potato573.aura.data.AppEntry
import io.github.potato573.aura.data.IconCache
import io.github.potato573.aura.search.AppSearch
import io.github.potato573.aura.util.Fonts
import io.github.potato573.aura.util.dp
import io.github.potato573.aura.util.roundRect

/**
 * All-apps sheet: a search field and an alphabetical grid. Filtering runs on every
 * keystroke against pre-normalised labels (see [AppSearch.Index]); with a few hundred apps
 * this takes well under a millisecond, so results are truly instant.
 */
@SuppressLint("ViewConstructor")
class DrawerView(context: Context, private val icons: IconCache) : FrameLayout(context) {

    var onLaunch: ((AppEntry, View) -> Unit)? = null
    var onLongPress: ((AppEntry, IconView) -> Unit)? = null

    private var allApps: List<AppEntry> = emptyList()
    private var searchableApps: List<AppEntry> = emptyList()
    private var shown: List<AppEntry> = emptyList()
    private var palette: Palette? = null
    private var iconSize = dp(54)

    private val sheetPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheetRect = android.graphics.RectF()

    /** Top corner radius of the sheet; shrinks to 0 as the drawer reaches full screen. */
    var cornerRadius = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    /** 0..1, how opaque the sheet background is; driven by the open progress. */
    var sheetAlpha = 1f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    val search = EditText(context).apply {
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Fonts.sans(420)
        hint = context.getString(R.string.search_hint)
        compoundDrawablePadding = dp(12)
        setPadding(dp(18), 0, dp(48), 0)
        gravity = Gravity.CENTER_VERTICAL
        background = null
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
    }

    private val clear = ImageView(context).apply {
        setImageResource(R.drawable.ic_close)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = context.getString(android.R.string.cancel)
        visibility = GONE
        setOnClickListener { search.setText("") }
    }

    private val searchBox = FrameLayout(context).apply {
        addView(search, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(clear, LayoutParams(dp(48), LayoutParams.MATCH_PARENT, Gravity.END))
    }

    val grid = GridView(context).apply {
        numColumns = 4
        stretchMode = GridView.STRETCH_COLUMN_WIDTH
        selector = ColorDrawable(Color.TRANSPARENT)
        isVerticalScrollBarEnabled = false
        clipToPadding = false
        overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        verticalSpacing = dp(4)
        isFocusable = false
        cacheColorHint = Color.TRANSPARENT
    }

    private val empty = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Fonts.sans(400)
        visibility = GONE
        setPadding(dp(32), dp(48), dp(32), dp(16))
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val v = (convertView as? IconView) ?: IconView(context).apply {
                layoutParams = AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, AbsListView.LayoutParams.WRAP_CONTENT)
                setOnClickListener { (item as? AppEntry)?.let { app -> onLaunch?.invoke(app, this) } }
                setOnLongClickListener {
                    (item as? AppEntry)?.let { app -> onLongPress?.invoke(app, this) }
                    true
                }
            }
            val app = shown[position]
            val p = palette
            if (p != null) v.setColors(p.textPrimary, onWallpaper = false)
            v.iconSize = iconSize
            v.item = app
            v.bindApp(app, icons)
            v.highlightColor = p?.accentOnSurface ?: 0
            v.highlighted = position == 0 && isSearching()
            return v
        }
    }

    init {
        setWillNotDraw(false)
        val searchH = dp(52)
        content.addView(searchBox, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, searchH).apply {
            setMargins(dp(20), dp(12), dp(20), dp(12))
        })
        content.addView(empty, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        content.addView(grid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        grid.adapter = adapter

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = refilter()
        })
        search.setOnEditorActionListener { v, actionId, event ->
            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                val first = shown.firstOrNull()
                if (first != null && isSearching()) {
                    onLaunch?.invoke(first, grid.getChildAt(0) ?: v)
                }
                true
            } else {
                false
            }
        }
        grid.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {
                // Scrolling means browsing: get the keyboard out of the way.
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) hideKeyboard()
            }
            override fun onScroll(view: AbsListView?, first: Int, visible: Int, total: Int) = Unit
        })
    }

    fun applyPalette(p: Palette) {
        palette = p
        search.setTextColor(p.textPrimary)
        search.setHintTextColor(p.textSecondary)
        searchBox.background = roundRect(p.field, dp(26f))
        val icon = context.getDrawable(R.drawable.ic_search)?.mutate()
        icon?.setTint(p.textSecondary)
        search.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
        clear.setColorFilter(p.textSecondary)
        empty.setTextColor(p.textSecondary)
        if (Build.VERSION.SDK_INT >= 29) {
            search.textCursorDrawable?.mutate()?.setTint(p.accentOnSurface)
            grid.setEdgeEffectColor(p.accent)
        }
        search.highlightColor = p.accentSoft
        adapter.notifyDataSetChanged()
        invalidate()
    }

    fun setColumns(columns: Int) {
        grid.numColumns = columns
    }

    fun setIconSize(px: Int) {
        if (iconSize != px) {
            iconSize = px
            adapter.notifyDataSetChanged()
        }
    }

    /**
     * [visible] apps make up the browsable grid; [all] (which also includes hidden apps) is
     * what search looks through, so a hidden app is still one query away.
     */
    fun setApps(visible: List<AppEntry>, all: List<AppEntry>) {
        allApps = visible
        searchableApps = all
        refilter()
    }

    fun isSearching() = search.text.isNotBlank()

    private fun refilter() {
        val q = search.text.toString()
        shown = if (q.isBlank()) allApps else AppSearch.filter(searchableApps, q) { it.search }
        adapter.notifyDataSetChanged()
        clear.visibility = if (q.isEmpty()) GONE else VISIBLE
        val searching = q.isNotBlank()
        val none = searching && shown.isEmpty()
        empty.visibility = if (none) VISIBLE else GONE
        if (none) empty.text = context.getString(R.string.no_results, q.trim())
        if (searching) grid.setSelection(0)
    }

    /** Resets the drawer to its browsing state (top of list, no query). */
    fun reset() {
        if (search.text.isNotEmpty()) search.setText("")
        search.clearFocus()
        grid.setSelection(0)
    }

    fun focusSearch(showKeyboard: Boolean) {
        search.requestFocus()
        if (showKeyboard) {
            search.post {
                context.getSystemService(InputMethodManager::class.java)
                    ?.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    fun hideKeyboard() {
        context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(windowToken, 0)
    }

    fun isAtTop(): Boolean = !grid.canScrollVertically(-1)

    fun setInsets(top: Int, bottom: Int) {
        content.setPadding(0, top, 0, 0)
        grid.setPadding(dp(8), dp(4), dp(8), bottom + dp(16))
    }

    override fun onDraw(canvas: Canvas) {
        if (sheetAlpha <= 0f) return
        val base = palette?.sheet ?: return
        val a = (Color.alpha(base) * sheetAlpha).toInt()
        sheetPaint.color = (base and 0x00FFFFFF) or (a shl 24)
        if (cornerRadius <= 0.5f) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), sheetPaint)
        } else {
            // Extend below the bottom edge so only the top corners are rounded.
            sheetRect.set(0f, 0f, width.toFloat(), height + cornerRadius)
            canvas.drawRoundRect(sheetRect, cornerRadius, cornerRadius, sheetPaint)
        }
    }
}
