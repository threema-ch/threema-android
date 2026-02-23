package ch.threema.data

import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class WeakValueMapTest {
    @Test
    fun testReferences() {
        val map = WeakValueMap<String, Date>()
        val date1 = map.getOrCreate("hello") { Date() }
        val date2 = map.getOrCreate("hello") { Date() }
        val date3 = map.getOrCreate("world") { Date() }
        assertSame(date1, date2)
        assertNotSame(date1, date3)
        assertSame(date1, map.get("hello"))
        assertSame(date3, map.get("world"))
        assertNull(map.get("something-else"))
    }

    @Test
    fun testMissNull() {
        val map = WeakValueMap<String, String>()
        val string1 = map.getOrCreate("hello") { "guten tag!" }
        val string2 = map.getOrCreate("not-found") { null }
        assertEquals("guten tag!", string1)
        assertNull(string2)
    }
}
