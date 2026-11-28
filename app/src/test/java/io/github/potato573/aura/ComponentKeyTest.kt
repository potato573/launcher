package io.github.potato573.aura

import io.github.potato573.aura.model.ComponentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComponentKeyTest {
    @Test fun roundTrip() {
        val k = ComponentKey("com.example", "com.example.Main", 10L)
        assertEquals(k, ComponentKey.parse(k.serialize()))
    }

    @Test fun shortClassFormExpands() {
        assertEquals(ComponentKey("com.a", "com.a.Main", 0), ComponentKey.parse("com.a/.Main"))
    }

    @Test fun malformedReturnsNull() {
        assertNull(ComponentKey.parse(""))
        assertNull(ComponentKey.parse(null))
        assertNull(ComponentKey.parse("noslash"))
        assertNull(ComponentKey.parse("/cls"))
        assertNull(ComponentKey.parse("pkg/"))
        assertNull(ComponentKey.parse("pkg/cls#notanumber"))
    }
}
