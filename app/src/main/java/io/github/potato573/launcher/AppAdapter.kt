package io.github.potato573.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.github.potato573.launcher.databinding.ItemAppBinding

class AppAdapter(
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: ((AppInfo, View) -> Unit)? = null,
) : RecyclerView.Adapter<AppAdapter.Holder>() {

    val items = mutableListOf<AppInfo>()
    var showLabels = true
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    fun submit(list: List<AppInfo>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun move(from: Int, to: Int) {
        if (from == to || from !in items.indices || to !in items.indices) return
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
    }

    class Holder(val b: ItemAppBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val app = items[position]
        holder.b.icon.setImageDrawable(app.icon)
        holder.b.label.text = app.label
        holder.b.label.visibility = if (showLabels) View.VISIBLE else View.GONE
        holder.b.root.contentDescription = app.label
        holder.b.root.setOnClickListener { onClick(app) }
        if (onLongClick != null) {
            holder.b.root.setOnLongClickListener { onLongClick.invoke(app, it); true }
        } else {
            holder.b.root.setOnLongClickListener(null)
            holder.b.root.isLongClickable = false
        }
    }
}
