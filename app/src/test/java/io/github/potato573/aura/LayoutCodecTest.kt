package io.github.potato573.aura

import io.github.potato573.aura.data.LayoutCodec
import io.github.potato573.aura.model.Cell
import io.github.potato573.aura.model.ComponentKey
import io.github.potato573.aura.model.HomeLayout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LayoutCodecTest {
    private fun k(n: Int) = ComponentKey("p$n", "p$n.Main", n.toLong())

    private fun sample(): HomeLayout {
        var l = HomeLayout().addApp(k(1), 4, 4).addApp(k(2), 4, 4).addApp(k(3), 4, 4).addToDock(k(4), 5)
        val (a, b) = l.items.map { it.id }
        l = l.combine(b, a, "Work")
        return l.addAppAt(k(5), Cell(1, 3, 3))
    }

    @Test fun layoutRoundTrip() {
        val l = sample()
        assertEquals(l, LayoutCodec.decode(JSONObject(LayoutCodec.encode(l).toString())))
    }

    @Test fun backupRoundTrip() {
        val backup = LayoutCodec.Backup(sample(), mapOf("theme" to "dark", "icon_scale" to 110, "home_labels" to false), setOf(k(9)))
        val decoded = LayoutCodec.decodeBackup(LayoutCodec.encodeBackup(backup, 1234L))
        assertEquals(backup, decoded)
    }

    @Test fun rejectsForeignFiles() {
        for (text in listOf("not json", "{}", "{\"format\":\"aura-backup\",\"version\":99}")) {
            try {
                LayoutCodec.decodeBackup(text)
                fail("accepted: $text")
            } catch (e: LayoutCodec.InvalidBackupException) {
            }
        }
    }

    @Test fun skipsDamagedEntriesAndDuplicates() {
        val json = JSONObject(
            """
            {"dock":["a/a.M#0","a/a.M#0","bad"],
             "items":[
               {"id":1,"type":"app","page":0,"x":0,"y":0,"key":"b/b.M#0"},
               {"id":2,"type":"app","page":0,"x":0,"y":0,"key":"c/c.M#0"},
               {"id":3,"type":"app","page":0,"x":1,"y":0,"key":"a/a.M#0"},
               {"id":4,"type":"folder","page":0,"x":2,"y":0,"title":"x","apps":["d/d.M#0"]},
               {"id":5,"type":"mystery","page":0,"x":3,"y":0},
               {"type":"app","page":0,"x":3,"y":1,"key":"e/e.M#0"}
             ]}
            """.trimIndent()
        )
        val l = LayoutCodec.decode(json)
        assertEquals(1, l.dock.size)
        assertEquals(2, l.items.size) // b and the single-app folder converted to d
        assertTrue(l.nextId > 4)
    }
}
