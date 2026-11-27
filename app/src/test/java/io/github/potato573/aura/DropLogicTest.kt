package io.github.potato573.aura

import io.github.potato573.aura.model.AppItem
import io.github.potato573.aura.model.Cell
import io.github.potato573.aura.model.ComponentKey
import io.github.potato573.aura.model.DropLogic
import io.github.potato573.aura.model.DropLogic.Source
import io.github.potato573.aura.model.DropLogic.Target
import io.github.potato573.aura.model.FolderItem
import io.github.potato573.aura.model.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DropLogicTest {
    private fun k(n: Int) = ComponentKey("p$n", "p$n.Main")
    private fun drop(l: HomeLayout, s: Source, t: Target) = DropLogic.apply(l, s, t, 4, 4, "Folder")

    @Test fun moveToEmptyCell() {
        val l = HomeLayout().addApp(k(1), 4, 4)
        val r = drop(l, Source.Grid(l.items.single().id), Target.OnCell(Cell(0, 3, 3)))!!
        assertEquals(Cell(0, 3, 3), r.items.single().cell)
    }

    @Test fun dropOnOwnCellIsAllowed() {
        val l = HomeLayout().addApp(k(1), 4, 4)
        val r = drop(l, Source.Grid(l.items.single().id), Target.OnCell(Cell(0, 0, 0)))
        assertNotNull(r)
        assertEquals(Cell(0, 0, 0), r!!.items.single().cell)
    }

    @Test fun dropOnAppMakesFolder() {
        val l = HomeLayout().addApp(k(1), 4, 4).addApp(k(2), 4, 4)
        val r = drop(l, Source.Grid(l.items[1].id), Target.OnCell(Cell(0, 0, 0)))!!
        val f = r.items.single() as FolderItem
        assertEquals(listOf(k(1), k(2)), f.apps)
    }

    @Test fun drawerAppToCellDockAndFolder() {
        var l = HomeLayout().addApp(k(1), 4, 4)
        l = drop(l, Source.External(k(2)), Target.OnCell(Cell(0, 2, 0)))!!
        assertTrue(l.items.any { it is AppItem && it.key == k(2) })
        l = drop(l, Source.External(k(3)), Target.DockSlot(0))!!
        assertEquals(listOf(k(3)), l.dock)
        l = drop(l, Source.External(k(4)), Target.OnCell(Cell(0, 0, 0)))!!
        assertTrue(l.items.any { it is FolderItem && k(4) in it.apps })
    }

    @Test fun duplicatesRejected() {
        val l = HomeLayout().addApp(k(1), 4, 4)
        assertNull(drop(l, Source.External(k(1)), Target.OnCell(Cell(0, 1, 0))))
    }

    @Test fun fullDockRejectsAndKeepsLayout() {
        var l = HomeLayout()
        repeat(5) { l = l.addToDock(k(10 + it), 5) }
        l = l.addApp(k(1), 4, 4)
        assertNull(drop(l, Source.Grid(l.items.single().id), Target.DockSlot(2)))
    }

    @Test fun dockReorderAndDockToGrid() {
        var l = HomeLayout().addToDock(k(1), 5).addToDock(k(2), 5).addToDock(k(3), 5)
        l = drop(l, Source.Dock(k(1)), Target.DockSlot(2))!!
        assertEquals(listOf(k(2), k(3), k(1)), l.dock)
        l = drop(l, Source.Dock(k(3)), Target.OnCell(Cell(0, 1, 1)))!!
        assertEquals(listOf(k(2), k(1)), l.dock)
        assertEquals(Cell(0, 1, 1), l.items.single().cell)
    }

    @Test fun dragOutOfFolderDissolvesIt() {
        var l = HomeLayout().addApp(k(1), 4, 4).addApp(k(2), 4, 4)
        l = l.combine(l.items[1].id, l.items[0].id, "F")
        val f = l.items.single() as FolderItem
        l = drop(l, Source.InFolder(f.id, k(2)), Target.OnCell(Cell(0, 3, 0)))!!
        assertEquals(2, l.items.size)
        assertTrue(l.items.none { it is FolderItem })
    }

    @Test fun foldersMoveOnlyToEmptyCells() {
        var l = HomeLayout().addApp(k(1), 4, 4).addApp(k(2), 4, 4).addApp(k(3), 4, 4)
        l = l.combine(l.items[1].id, l.items[0].id, "F")
        val f = l.items.first { it is FolderItem }
        assertNull(drop(l, Source.Grid(f.id), Target.OnCell(l.items.first { it is AppItem }.cell)))
        assertNull(drop(l, Source.Grid(f.id), Target.DockSlot(0)))
        assertEquals(Cell(0, 3, 3), drop(l, Source.Grid(f.id), Target.OnCell(Cell(0, 3, 3)))!!.item(f.id)!!.cell)
    }

    @Test fun outOfBoundsRejected() {
        val l = HomeLayout()
        assertNull(drop(l, Source.External(k(1)), Target.OnCell(Cell(0, 4, 0))))
    }
}
