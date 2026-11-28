package io.github.potato573.aura.search

import java.text.Normalizer
import java.util.Locale

/**
 * Ranks app labels against a query. Pure Kotlin so it is unit-tested on the JVM.
 *
 * Matching ignores case and accents ("cafe" finds "Café") and accepts, from best to
 * weakest: an exact label, a label prefix, a word prefix ("map" in "Google Maps"),
 * initials ("gm" for "Google Maps"), a substring, and finally an in-order subsequence
 * ("whtsp" for "WhatsApp") so small typos of omission still find the app.
 */
object AppSearch {

    private val MARKS = Regex("\\p{Mn}+")
    private val SEPARATORS = Regex("[\\s\\-_.:()&+]+")
    private val CAMEL = Regex("(?<=[a-z])(?=[A-Z])")

    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(MARKS, "").lowercase(Locale.ROOT).trim()

    /** Pre-computed searchable form of a label; build it once per app, not per keystroke. */
    class Index(label: String) {
        val text: String = normalize(label)
        val words: List<String> = label.split(SEPARATORS)
            .flatMap { it.split(CAMEL) }
            .map { normalize(it) }
            .filter { it.isNotEmpty() }
        val initials: String = words.joinToString("") { it.take(1) }
    }

    /** Score of [index] for [query]; 0 means no match. Higher is better. */
    fun score(index: Index, query: String): Int {
        val q = normalize(query)
        if (q.isEmpty()) return 1
        val t = index.text
        return when {
            t == q -> 1000
            t.startsWith(q) -> 900 - (t.length - q.length).coerceAtMost(99)
            index.words.any { it.startsWith(q) } -> 700 - (t.length - q.length).coerceAtMost(99)
            q.length >= 2 && index.initials.startsWith(q) -> 600
            t.contains(q) -> 400 - t.indexOf(q).coerceAtMost(99)
            q.length >= 3 && isSubsequence(q, t.replace(" ", "")) -> 100
            else -> 0
        }
    }

    private fun isSubsequence(q: String, t: String): Boolean {
        var i = 0
        for (c in t) {
            if (i < q.length && c == q[i]) i++
        }
        return i == q.length
    }

    /**
     * Filters and orders [items] for [query]. Ties keep the input order, so callers
     * should pass items already sorted alphabetically.
     */
    fun <T> filter(items: List<T>, query: String, index: (T) -> Index): List<T> {
        if (normalize(query).isEmpty()) return items
        return items.asSequence()
            .mapIndexedNotNull { i, item -> score(index(item), query).takeIf { it > 0 }?.let { Triple(item, it, i) } }
            .sortedWith(compareByDescending<Triple<T, Int, Int>> { it.second }.thenBy { it.third })
            .map { it.first }
            .toList()
    }
}
