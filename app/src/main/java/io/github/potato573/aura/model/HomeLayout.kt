package io.github.potato573.aura.model

/** A position on the home screen: page index plus column/row inside that page. */
data class Cell(val page: Int, val x: Int, val y: Int)

sealed class HomeItem {
    abstract val id: Long
    abstract val cell: Cell
    abstract fun at(cell: Cell): HomeItem
}

data class AppItem(
    override val id: Long,
    override val cell: Cell,
    val key: ComponentKey,
) : HomeItem() {
    override fun at(cell: Cell) = copy(cell = cell)
}

data class FolderItem(
    override val id: Long,
    override val cell: Cell,
    val title: String,
    val apps: List<ComponentKey>,
) : HomeItem() {
    override fun at(cell: Cell) = copy(cell = cell)
}

/**
 * Immutable model of everything the user arranged: home pages, folders and the dock.
 *
 * Invariant: an app appears at most once across the home grid, every folder and the dock.
 * All operations return a new instance and never throw for unknown ids, so the UI can
 * apply them without defensive checks.
 */
data class HomeLayout(
    val items: List<HomeItem> = emptyList(),
    val dock: List<ComponentKey> = emptyList(),
    val pageCount: Int = 1,
    val nextId: Long = 1L,
) {

    fun itemAt(cell: Cell): HomeItem? = items.firstOrNull { it.cell == cell }

    fun item(id: Long): HomeItem? = items.firstOrNull { it.id == id }

    fun itemsOn(page: Int): List<HomeItem> = items.filter { it.cell.page == page }

    /** True when [key] is already placed anywhere (grid, a folder or the dock). */
    fun contains(key: ComponentKey): Boolean =
        key in dock || items.any { it is AppItem && it.key == key || it is FolderItem && key in it.apps }

    /** All apps referenced by this layout. */
    fun allKeys(): Set<ComponentKey> = buildSet {
        addAll(dock)
        for (item in items) when (item) {
            is AppItem -> add(item.key)
            is FolderItem -> addAll(item.apps)
        }
    }

    /**
     * First empty cell scanning pages from [startPage], row-major. When every page is full a
     * cell on a new trailing page is returned (the page is created by the caller's add).
     */
    fun firstFreeCell(cols: Int, rows: Int, startPage: Int = 0): Cell {
        val occupied = items.mapTo(HashSet()) { it.cell }
        val first = startPage.coerceIn(0, pageCount - 1)
        val order = (first until pageCount) + (0 until first)
        for (page in order) {
            for (y in 0 until rows) for (x in 0 until cols) {
                val c = Cell(page, x, y)
                if (c !in occupied) return c
            }
        }
        return Cell(pageCount, 0, 0)
    }

    fun addApp(key: ComponentKey, cols: Int, rows: Int, startPage: Int = 0): HomeLayout {
        if (contains(key)) return this
        return place(AppItem(nextId, firstFreeCell(cols, rows, startPage), key))
    }

    fun addAppAt(key: ComponentKey, cell: Cell): HomeLayout {
        if (contains(key) || itemAt(cell) != null) return this
        return place(AppItem(nextId, cell, key))
    }

    private fun place(item: HomeItem): HomeLayout = copy(
        items = items + item,
        pageCount = maxOf(pageCount, item.cell.page + 1),
        nextId = maxOf(nextId, item.id + 1),
    )

    fun remove(id: Long): HomeLayout = copy(items = items.filterNot { it.id == id })

    /** Removes an app from every place it may appear (used when a package is uninstalled). */
    fun removeApp(key: ComponentKey): HomeLayout = removeApps(setOf(key))

    fun removeApps(keys: Set<ComponentKey>): HomeLayout {
        if (keys.isEmpty()) return this
        val newItems = items.mapNotNull { item ->
            when (item) {
                is AppItem -> item.takeUnless { it.key in keys }
                is FolderItem -> normalizeFolder(item.copy(apps = item.apps.filterNot { it in keys }))
            }
        }
        return copy(items = newItems, dock = dock.filterNot { it in keys })
    }

    /** Keeps only apps contained in [installed]. */
    fun retainApps(installed: Set<ComponentKey>): HomeLayout = removeApps(allKeys() - installed)

    /**
     * Moves an item to [target]. If another item occupies the target the two swap places,
     * which keeps drag-and-drop predictable: nothing is ever pushed off the grid.
     */
    fun move(id: Long, target: Cell): HomeLayout {
        val moving = item(id) ?: return this
        if (moving.cell == target) return this
        val occupant = itemAt(target)
        val newItems = items.map {
            when (it.id) {
                id -> it.at(target)
                occupant?.id -> it.at(moving.cell)
                else -> it
            }
        }
        return copy(items = newItems, pageCount = maxOf(pageCount, target.page + 1))
    }

    /** Drops item [dragId] onto app/folder [targetId], creating or extending a folder. */
    fun combine(dragId: Long, targetId: Long, defaultTitle: String): HomeLayout {
        if (dragId == targetId) return this
        val drag = item(dragId) as? AppItem ?: return this
        if (item(targetId) == null) return this
        return remove(dragId).combineKey(drag.key, targetId, defaultTitle)
    }

    /**
     * Puts [key] (an app not placed anywhere yet) into the item [targetId]: onto an app it
     * creates a folder in that cell, onto a folder it appends.
     */
    fun combineKey(key: ComponentKey, targetId: Long, defaultTitle: String): HomeLayout {
        if (contains(key)) return this
        return when (val target = item(targetId)) {
            is AppItem -> {
                val folder = FolderItem(nextId, target.cell, defaultTitle, listOf(target.key, key))
                copy(items = items.filterNot { it.id == targetId } + folder, nextId = nextId + 1)
            }
            is FolderItem -> copy(
                items = items.map { if (it.id == targetId) target.copy(apps = target.apps + key) else it },
            )
            null -> this
        }
    }

    /** Inserts [key] (not placed anywhere yet) into the dock at [index]. */
    fun insertIntoDock(key: ComponentKey, index: Int, maxDock: Int): HomeLayout {
        if (contains(key) || dock.size >= maxDock) return this
        return copy(dock = dock.toMutableList().apply { add(index.coerceIn(0, size), key) })
    }

    /** Moves a dock app onto the grid. */
    fun dockToCell(key: ComponentKey, target: Cell): HomeLayout {
        if (key !in dock || itemAt(target) != null) return this
        return copy(dock = dock - key).place(AppItem(nextId, target, key))
    }

    /** Moves a grid app into the dock at [index]. */
    fun cellToDock(id: Long, index: Int, maxDock: Int): HomeLayout {
        val app = item(id) as? AppItem ?: return this
        if (dock.size >= maxDock) return this
        val newDock = dock.toMutableList().apply { add(index.coerceIn(0, size), app.key) }
        return copy(items = items.filterNot { it.id == id }, dock = newDock)
    }

    fun addToDock(key: ComponentKey, maxDock: Int): HomeLayout {
        if (contains(key) || dock.size >= maxDock) return this
        return copy(dock = dock + key)
    }

    fun removeFromDock(key: ComponentKey): HomeLayout = copy(dock = dock - key)

    fun moveInDock(from: Int, to: Int): HomeLayout {
        if (from !in dock.indices || to !in dock.indices || from == to) return this
        val list = dock.toMutableList()
        list.add(to, list.removeAt(from))
        return copy(dock = list)
    }

    fun renameFolder(id: Long, title: String): HomeLayout = copy(
        items = items.map { if (it.id == id && it is FolderItem) it.copy(title = title) else it },
    )

    fun reorderFolder(id: Long, from: Int, to: Int): HomeLayout = copy(
        items = items.map {
            if (it.id == id && it is FolderItem && from in it.apps.indices && to in it.apps.indices) {
                it.copy(apps = it.apps.toMutableList().apply { add(to, removeAt(from)) })
            } else {
                it
            }
        },
    )

    /**
     * Takes [key] out of folder [folderId] and places it on the grid. A folder left with a
     * single app turns back into that app; an empty folder disappears.
     */
    fun removeFromFolder(folderId: Long, key: ComponentKey, cols: Int, rows: Int, toGrid: Boolean = true): HomeLayout {
        val folder = item(folderId) as? FolderItem ?: return this
        if (key !in folder.apps) return this
        val withoutFolder = copy(items = items.filterNot { it.id == folderId })
        val remaining = normalizeFolder(folder.copy(apps = folder.apps - key))
        val base = if (remaining != null) {
            withoutFolder.copy(items = withoutFolder.items + remaining)
        } else {
            withoutFolder
        }
        return if (toGrid) base.addApp(key, cols, rows, folder.cell.page) else base
    }

    private fun normalizeFolder(folder: FolderItem): HomeItem? = when (folder.apps.size) {
        0 -> null
        1 -> AppItem(folder.id, folder.cell, folder.apps.first())
        else -> folder
    }

    /**
     * Makes the layout valid for a [cols] x [rows] grid: items outside the grid move to the
     * first free cell (adding pages if needed), overlapping items are separated and empty
     * trailing pages are dropped.
     */
    fun reflow(cols: Int, rows: Int): HomeLayout {
        val sorted = items.sortedWith(compareBy({ it.cell.page }, { it.cell.y }, { it.cell.x }))
        var result = copy(items = emptyList(), pageCount = maxOf(1, pageCount))
        val displaced = ArrayList<HomeItem>()
        for (item in sorted) {
            val c = item.cell
            val inBounds = c.page >= 0 && c.x in 0 until cols && c.y in 0 until rows
            if (inBounds && result.itemAt(c) == null) {
                result = result.copy(items = result.items + item)
            } else {
                displaced += item
            }
        }
        for (item in displaced) {
            val cell = result.firstFreeCell(cols, rows, item.cell.page.coerceAtLeast(0))
            result = result.copy(
                items = result.items + item.at(cell),
                pageCount = maxOf(result.pageCount, cell.page + 1),
            )
        }
        return result.trimPages()
    }

    /** Removes empty pages (keeping at least one) and shifts later pages down. */
    fun trimPages(): HomeLayout {
        val used = items.map { it.cell.page }.toSortedSet()
        if (used.isEmpty()) return copy(pageCount = 1)
        val mapping = used.withIndex().associate { (newIndex, old) -> old to newIndex }
        return copy(
            items = items.map { it.at(it.cell.copy(page = mapping.getValue(it.cell.page))) },
            pageCount = used.size,
        )
    }

    fun addPage(): HomeLayout = copy(pageCount = pageCount + 1)

    companion object {
        const val MAX_DOCK = 5
    }
}
