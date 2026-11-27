package io.github.potato573.aura.ui

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import io.github.potato573.aura.AuraApp
import io.github.potato573.aura.R
import io.github.potato573.aura.data.AppEntry
import io.github.potato573.aura.util.Fonts
import io.github.potato573.aura.util.dp

/** Choose which apps stay out of the drawer. Changes apply immediately. */
class HiddenAppsActivity : Activity() {

    private val app by lazy { AuraApp.of(this) }
    private var apps: List<AppEntry> = emptyList()
    private var hidden: MutableSet<io.github.potato573.aura.model.ComponentKey> = HashSet()
    private lateinit var palette: Palette
    private lateinit var adapter: BaseAdapter
    private val appsListener: (List<AppEntry>) -> Unit = { list ->
        apps = list
        if (::adapter.isInitialized) adapter.notifyDataSetChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = Palette.from(this, app.settings)
        hidden = app.settings.hiddenApps.toMutableSet()
        val ui = SettingsUi(this, palette)

        val list = ListView(this).apply {
            id = R.id.hidden_list
            divider = null
            selector = ColorDrawable(Color.TRANSPARENT)
            clipToPadding = false
            cacheColorHint = Color.TRANSPARENT
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ui.title(getString(R.string.hidden_apps)))
            addView(TextView(this@HiddenAppsActivity).apply {
                text = getString(R.string.hidden_apps_help)
                setTextColor(palette.textSecondary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Fonts.sans(400)
                setPadding(dp(24), 0, dp(24), dp(16))
            })
        }
        list.addHeaderView(header, null, false)
        adapter = object : BaseAdapter() {
            override fun getCount() = apps.size
            override fun getItem(position: Int) = apps[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val row = (convertView as? Row) ?: Row()
                row.bind(apps[position])
                return row
            }
        }
        list.adapter = adapter
        ui.applyWindow(list)
        setContentView(list)
        app.apps.addListener(appsListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        app.apps.removeListener(appsListener)
    }

    private inner class Row : LinearLayout(this@HiddenAppsActivity) {
        private val icon = ImageView(context)
        private val label = TextView(context).apply {
            setTextColor(palette.textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Fonts.sans(450)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        private val check = CheckBox(context).apply {
            buttonTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(palette.accent, palette.textSecondary),
            )
            isClickable = false
            isFocusable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        private var entry: AppEntry? = null
        private var token = 0

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(60)
            setPadding(dp(24), dp(6), dp(16), dp(6))
            layoutParams = AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, AbsListView.LayoutParams.WRAP_CONTENT)
            background = RippleDrawable(ColorStateList.valueOf(palette.pressed), null, ColorDrawable(Color.WHITE))
            addView(icon, LayoutParams(dp(36), dp(36)))
            addView(label, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(16) })
            addView(check)
            setOnClickListener {
                val e = entry ?: return@setOnClickListener
                val now = e.key !in hidden
                if (now) hidden += e.key else hidden -= e.key
                check.isChecked = now
                app.settings.setHidden(e.key, now)
            }
        }

        fun bind(e: AppEntry) {
            entry = e
            val t = ++token
            label.text = e.label
            contentDescription = e.label
            check.isChecked = e.key in hidden
            icon.setImageBitmap(app.icons.get(e) { bmp -> if (t == token) icon.setImageBitmap(bmp) })
        }

        override fun onInitializeAccessibilityNodeInfo(info: android.view.accessibility.AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.isCheckable = true
            info.isChecked = check.isChecked
        }
    }
}
