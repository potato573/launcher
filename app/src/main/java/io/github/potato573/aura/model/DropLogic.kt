package io.github.potato573.aura.model

/**
 * Pure rules for drag and drop, kept out of the UI so they are unit-tested.
 * A drop is "detach the dragged thing from where it was, then attach it at the target";
 * if the target cannot take it (full dock, folder onto a folder, …) the drop is rejected
 * and the layout is left untouched.
 */
object DropLogic {

    sealed class Source {
        /** An item on the home grid (app or folder). */
        data class Grid(val id: Long) : Source()
        data class Dock(val key: ComponentKey) : Source()
        data class InFolder(val folderId: Long, val key: ComponentKey) : Source()
        /** From the app drawer: not on the layout yet. */
        data class External(val key: ComponentKey) : Source()
    }

    sealed class Target {
        data class OnCell(val cell: Cell) : Target()
        data class DockSlot(val index: Int) : Target()
    }

    /** Layout with the source removed, plus the app key being carried (null for a folder). */
    fun detach(layout: HomeLayout, source: Source, cols: Int, rows: Int): Pair<HomeLayout, ComponentKey?> = when (source) {
        is Source.Grid -> when (val item = layout.item(source.id)) {
            is AppItem -> layout.remove(item.id) to item.key
            is FolderItem -> layout.remove(item.id) to null
            null -> layout to null
        }
        is Source.Dock -> layout.removeFromDock(source.key) to source.key
        is Source.InFolder -> layout.removeFromFolder(source.folderId, source.key, cols, rows, toGrid = false) to source.key
        is Source.External -> layout to source.key
    }

    /** What would be under the finger for [target] once the source is lifted. */
    fun occupant(layout: HomeLayout, source: Source, target: Target, cols: Int, rows: Int): HomeItem? {
        val cell = (target as? Target.OnCell)?.cell ?: return null
        return detach(layout, source, cols, rows).first.itemAt(cell)
    }

    /** Returns the new layout, or null when the drop is not possible. */
    fun apply(
        layout: HomeLayout,
        source: Source,
        target: Target,
        cols: Int,
        rows: Int,
        folderTitle: String,
    ): HomeLayout? {
        if (target is Target.OnCell) {
            val c = target.cell
            if (c.page < 0 || c.x !in 0 until cols || c.y !in 0 until rows) return null
        }
        // Folders move as a unit and only onto empty cells.
        if (source is Source.Grid) {
            val folder = layout.item(source.id) as? FolderItem
            if (folder != null) {
                val cell = (target as? Target.OnCell)?.cell ?: return null
                val occ = layout.itemAt(cell)
                if (occ != null && occ.id != folder.id) return null
                return layout.move(folder.id, cell)
            }
        }
        if (source is Source.External && layout.contains(source.key)) return null
        val (detached, key) = detach(layout, source, cols, rows)
        key ?: return null
        val result = when (target) {
            is Target.OnCell -> {
                val occ = detached.itemAt(target.cell)
                if (occ == null) detached.addAppAt(key, target.cell) else detached.combineKey(key, occ.id, folderTitle)
            }
            is Target.DockSlot -> detached.insertIntoDock(key, target.index, HomeLayout.MAX_DOCK)
        }
        // Unchanged means the attach step refused (e.g. dock full): reject the whole drop.
        return if (result == detached) null else result
    }
}
