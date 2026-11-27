package io.github.potato573.aura

import io.github.potato573.aura.search.AppSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchTest {
    private val labels = listOf("Calculator", "Calendar", "Camera", "Café Finder", "Google Maps", "Gmail", "WhatsApp", "YouTube Music")
    private fun search(q: String) = AppSearch.filter(labels, q) { AppSearch.Index(it) }

    @Test fun emptyQueryKeepsOrder() = assertEquals(labels, search("  "))

    @Test fun prefixRanksFirst() {
        val r = search("cal")
        assertEquals(listOf("Calendar", "Calculator").toSet(), r.take(2).toSet())
        assertTrue("Camera" !in r)
    }

    @Test fun shorterPrefixMatchWins() = assertEquals("Calendar", search("cal").first())

    @Test fun accentsAndCaseIgnored() = assertEquals("Café Finder", search("CAFE").first())

    @Test fun wordPrefix() = assertEquals("Google Maps", search("maps").first())

    @Test fun initials() = assertEquals("YouTube Music", search("ytm").first())

    @Test fun labelPrefixBeatsInitials() = assertEquals(listOf("Gmail", "Google Maps"), search("gm"))

    @Test fun camelCaseWords() = assertEquals("WhatsApp", search("app").first())

    @Test fun subsequenceForTypos() = assertEquals(listOf("WhatsApp"), search("whtsp"))

    @Test fun exactBeatsEverything() = assertEquals("Gmail", search("gmail").first())

    @Test fun noMatch() = assertTrue(search("zzz").isEmpty())
}
