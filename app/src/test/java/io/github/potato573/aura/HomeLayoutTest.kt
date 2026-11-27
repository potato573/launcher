package io.github.potato573.aura

import io.github.potato573.aura.model.AppItem
import io.github.potato573.aura.model.Cell
import io.github.potato573.aura.model.ComponentKey
import io.github.potato573.aura.model.FolderItem
import io.github.potato573.aura.model.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {
    private fun k(n: Int) = ComponentKey("p$n", "p$n.Main")

    @Test fun addFillsRowMajorAndCreatesPages() {
        var l = HomeLayout()
        repeat(5) { l = l.addApp(k(it), cols = 2, rows = 2) }
        assertEquals(2, l.pageCount)
        assertEquals(Cell(0, 1, 0), (l.items[1]).cell)
        assertEquals(Cell(1, 0, 0), (l.items[4]).cell)
    }

    @Test fun noDuplicatesAnywhere() {
        var l = HomeLayout().addToDock(k(1), 5)
        l = l.addApp(k(1), 4, 4)
        assertTrue(l.items.isEmpty())
        l = l.addApp(k(2), 4, 4).addToDock(k(2), 5)
        assertEquals(1, l.dock.size)
    }

    @Test fun moveSwapsWithOccupant() {
        val l = HomeLayout().addApp(k(1), 4, 4).addApp(k(2), 4, 4)
        val a = l.items[0].id
        val moved = l.move(a, Cell(0, 1, 0))
        assertEquals(Cell(0, 1, 0), moved.item(a)!!.cell)
        assertEquals(Cell(0, 0, 0), moved.items.first { it.id != a }.cell)
    }

    @Test fun combineCreatesAndExtendsFolder() {
        var l = HomeLayout().addApp(k(1), 4, 4).addApp(k(2), 4, 4).addApp(k(3), 4, 4)
        val (a, b, c) = l.items.map { it.id }
        l = l.combine(b, a, "Folder")
        val folder = l.items.single { it is FolderItem } as FolderItem
        assertEquals(listOf(k(1), k(2)), folder.apps)
        assertEquals(Cell(0, 0, 0), folder.cell)
        l = l.combine(c, folder.id, "x")
        assertEquals(3, (l.item(folder.id) as FolderItem).apps.size)
        assertEquals(1, l.items.size)
        assertTrue(l.contains(k(3)))
    }

    @Test fun folderWithOneAppDissolves() {
        var l = HomeLayout().addApp(k(1), 4, 4).addApp(k(2), 4, 4)
        val (a, b) = l.items.map { it.id }
        l = l.combine(b, a, "F")
        val f = l.items.single().id
        l = l.removeFromFolder(f, k(1), 4, 4)
        assertEquals(2, l.items.size)
        val remaining = l.item(f) as AppItem
        assertEquals(k(2), remaining.key)
        assertTrue(l.contains(k(1)))
    }

    @Test fun removeAppsCleansFoldersAndDock() {
        var l = HomeLayout().addApp(k(1), 4, 4).addApp(k(2), 4, 4).addApp(k(3), 4, 4).addToDock(k(4), 5)
        val (a, b) = l.items.map { it.id }
        l = l.combine(b, a, "F")
        l = l.removeApps(setOf(k(1), k(4)))
        assertFalse(l.contains(k(1)))
        assertFalse(l.contains(k(4)))
        assertTrue(l.items.none { it is FolderItem })
        assertTrue(l.contains(k(2)))
    }

    @Test fun reflowMovesOutOfBoundsItems() {
        var l = HomeLayout()
        repeat(6) { l = l.addApp(k(it), cols = 6, rows = 1) }
        val r = l.reflow(cols = 4, rows = 1)
        assertEquals(6, r.items.size)
        assertEquals(2, r.pageCount)
        assertEquals(r.items.size, r.items.map { it.cell }.toSet().size)
        assertTrue(r.items.all { it.cell.x < 4 && it.cell.y < 1 })
    }

    @Test fun trimRemovesEmptyPages() {
        val l = HomeLayout().addAppAt(k(1), Cell(2, 0, 0))
        assertEquals(3, l.pageCount)
        val t = l.trimPages()
        assertEquals(1, t.pageCount)
        assertEquals(0, t.items.single().cell.page)
    }

    @Test fun dockTransfers() {
        var l = HomeLayout().addApp(k(1), 4, 4)
        val id = l.items.single().id
        l = l.cellToDock(id, 0, 5)
        assertTrue(l.items.isEmpty())
        assertEquals(listOf(k(1)), l.dock)
        l = l.dockToCell(k(1), Cell(0, 2, 2))
        assertTrue(l.dock.isEmpty())
        assertEquals(Cell(0, 2, 2), l.items.single().cell)
    }

    @Test fun dockRespectsLimitAndReorders() {
        var l = HomeLayout()
        repeat(7) { l = l.addToDock(k(it), 5) }
        assertEquals(5, l.dock.size)
        l = l.moveInDock(0, 4)
        assertEquals(k(0), l.dock[4])
        assertEquals(k(1), l.dock[0])
    }

    @Test fun unknownIdsAreNoOps() {
        val l = HomeLayout().addApp(k(1), 4, 4)
        assertEquals(l, l.move(999, Cell(0, 1, 1)))
        assertEquals(l, l.combine(1, 999, "x"))
        assertNull(l.item(42))
    }

    @Test fun combineKeyAndInsertIntoDock() {
        var l = HomeLayout().addApp(k(1), 4, 4)
        val target = l.items.single().id
        l = l.combineKey(k(2), target, "F")
        assertEquals(listOf(k(1), k(2)), (l.items.single() as FolderItem).apps)
        assertEquals(l, l.combineKey(k(2), target, "F")) // already placed
        l = l.insertIntoDock(k(3), 0, 5).insertIntoDock(k(4), 0, 5)
        assertEquals(listOf(k(4), k(3)), l.dock)
    }

    @Test fun removeFromFolderWithoutPlacing() {
        var l = HomeLayout().addApp(k(1), 4, 4).addApp(k(2), 4, 4).addApp(k(3), 4, 4)
        val (a, b, c) = l.items.map { it.id }
        l = l.combine(b, a, "F")
        l = l.combine(c, l.items.first { it is FolderItem }.id, "F")
        val f = l.items.single().id
        l = l.removeFromFolder(f, k(3), 4, 4, toGrid = false)
        assertFalse(l.contains(k(3)))
        assertEquals(2, (l.item(f) as FolderItem).apps.size)
    }
}
