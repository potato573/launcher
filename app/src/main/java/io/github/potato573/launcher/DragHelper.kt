package io.github.potato573.launcher

import android.view.HapticFeedbackConstants
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * Tekan lama lalu geser untuk memindah ikon.
 * Tekan lama tanpa menggeser = tampilkan menu.
 */
class DragHelper(
    private val adapter: AppAdapter,
    dragDirs: Int,
    private val onDragStateChanged: (Boolean) -> Unit,
    private val onOrderChanged: () -> Unit,
    private val onTapHold: (AppInfo, RecyclerView.ViewHolder) -> Unit,
) : ItemTouchHelper.SimpleCallback(dragDirs, 0) {

    private var moved = false

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean {
        adapter.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
        moved = true
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

    override fun isLongPressDragEnabled() = true

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
            moved = false
            onDragStateChanged(true)
            viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            viewHolder.itemView.animate().scaleX(1.12f).scaleY(1.12f).setDuration(120).start()
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        viewHolder.itemView.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        onDragStateChanged(false)
        if (moved) {
            onOrderChanged()
        } else {
            val pos = viewHolder.bindingAdapterPosition
            if (pos in adapter.items.indices) onTapHold(adapter.items[pos], viewHolder)
        }
        moved = false
    }
}
