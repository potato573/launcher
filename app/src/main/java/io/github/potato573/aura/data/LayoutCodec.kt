package io.github.potato573.aura.data

import io.github.potato573.aura.model.AppItem
import io.github.potato573.aura.model.Cell
import io.github.potato573.aura.model.ComponentKey
import io.github.potato573.aura.model.FolderItem
import io.github.potato573.aura.model.HomeItem
import io.github.potato573.aura.model.HomeLayout
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * JSON form of the home layout and of full backups. Decoding is tolerant: unknown fields are
 * ignored and malformed entries are skipped, so a partially damaged file still restores
 * everything that is readable. A wrong format or a newer, incompatible version is rejected.
 */
object LayoutCodec {

    const val FORMAT = "aura-backup"
    const val VERSION = 1

    fun encode(layout: HomeLayout): JSONObject = JSONObject().apply {
        put("pageCount", layout.pageCount)
        put("nextId", layout.nextId)
        put("dock", JSONArray().apply { layout.dock.forEach { put(it.serialize()) } })
        put("items", JSONArray().apply { layout.items.forEach { put(encodeItem(it)) } })
    }

    private fun encodeItem(item: HomeItem): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("page", item.cell.page)
        put("x", item.cell.x)
        put("y", item.cell.y)
        when (item) {
            is AppItem -> {
                put("type", "app")
                put("key", item.key.serialize())
            }
            is FolderItem -> {
                put("type", "folder")
                put("title", item.title)
                put("apps", JSONArray().apply { item.apps.forEach { put(it.serialize()) } })
            }
        }
    }

    fun decode(json: JSONObject): HomeLayout {
        val seen = HashSet<ComponentKey>()
        val dock = keys(json.optJSONArray("dock")).filter { seen.add(it) }.take(HomeLayout.MAX_DOCK)
        val items = ArrayList<HomeItem>()
        val usedIds = HashSet<Long>()
        val usedCells = HashSet<Cell>()
        val array = json.optJSONArray("items") ?: JSONArray()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = o.optLong("id", -1L)
            val cell = Cell(o.optInt("page", -1), o.optInt("x", -1), o.optInt("y", -1))
            if (id < 0 || cell.page < 0 || cell.x < 0 || cell.y < 0) continue
            if (!usedIds.add(id) || !usedCells.add(cell)) continue
            when (o.optString("type")) {
                "app" -> {
                    val key = ComponentKey.parse(o.optString("key")) ?: continue
                    if (seen.add(key)) items += AppItem(id, cell, key)
                }
                "folder" -> {
                    val apps = keys(o.optJSONArray("apps")).filter { seen.add(it) }
                    when {
                        apps.size >= 2 -> items += FolderItem(id, cell, o.optString("title"), apps)
                        apps.size == 1 -> items += AppItem(id, cell, apps.first())
                    }
                }
            }
        }
        val maxPage = items.maxOfOrNull { it.cell.page } ?: 0
        val maxId = items.maxOfOrNull { it.id } ?: 0L
        return HomeLayout(
            items = items,
            dock = dock,
            pageCount = maxOf(1, json.optInt("pageCount", 1), maxPage + 1),
            nextId = maxOf(json.optLong("nextId", 1L), maxId + 1),
        )
    }

    private fun keys(array: JSONArray?): List<ComponentKey> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { ComponentKey.parse(array.optString(it)) }
    }

    /** A complete, portable backup: layout, settings and hidden apps. */
    data class Backup(
        val layout: HomeLayout,
        val settings: Map<String, Any>,
        val hidden: Set<ComponentKey>,
    )

    fun encodeBackup(backup: Backup, createdAtMillis: Long): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("createdAt", createdAtMillis)
        put("layout", encode(backup.layout))
        put("settings", JSONObject().apply {
            for ((k, v) in backup.settings) put(k, v)
        })
        put("hidden", JSONArray().apply { backup.hidden.sorted().forEach { put(it.serialize()) } })
    }.toString(2)

    class InvalidBackupException(message: String) : Exception(message)

    @Throws(InvalidBackupException::class)
    fun decodeBackup(text: String): Backup {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw InvalidBackupException("not_json")
        }
        if (root.optString("format") != FORMAT) throw InvalidBackupException("wrong_format")
        val version = root.optInt("version", -1)
        if (version < 1 || version > VERSION) throw InvalidBackupException("unsupported_version")
        val layout = root.optJSONObject("layout")?.let { decode(it) } ?: HomeLayout()
        val settings = LinkedHashMap<String, Any>()
        root.optJSONObject("settings")?.let { s ->
            val it = s.keys()
            while (it.hasNext()) {
                val k = it.next()
                when (val v = s.get(k)) {
                    is Boolean, is Int, is String -> settings[k] = v
                    is Long -> if (v in Int.MIN_VALUE..Int.MAX_VALUE) settings[k] = v.toInt()
                    is Double -> settings[k] = v.toInt()
                }
            }
        }
        return Backup(layout, settings, keys(root.optJSONArray("hidden")).toSet())
    }
}
