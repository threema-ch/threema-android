package ch.threema.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IntTrieTest {
    @Test
    fun insert() {
        val trie = IntTrie<String>()
        trie.insert(intArrayOf(1, 2, 3), "Yes")

        assertTrue(trie.contains(intArrayOf(1, 2, 3)))
        assertEquals("Yes", trie.get(intArrayOf(1, 2, 3))!!.getValue())
    }

    @Test
    fun `contains and get`() {
        val trie = IntTrie<String>()
        trie.insert(intArrayOf(1, 2, 3), "Hello")

        // Contains the inserted value
        assertTrue(trie.contains(intArrayOf(1, 2, 3)))
        assertEquals("Hello", trie.get(intArrayOf(1, 2, 3))!!.getValue())

        // Does not contain longer values
        assertFalse(trie.contains(intArrayOf(1, 2, 3, 4)))
        assertEquals(null, trie.get(intArrayOf(1, 2, 3, 4)))

        // Does not contain shorter values
        assertFalse(trie.contains(intArrayOf(1, 2)))

        // Does not contain other values
        assertFalse(trie.contains(intArrayOf(1, 3, 4)))

        // Now we'll insert that other value
        trie.insert(intArrayOf(1, 3, 4), "Goodbye")
        assertTrue(trie.contains(intArrayOf(1, 3, 4)))
        assertEquals("Goodbye", trie.get(intArrayOf(1, 3, 4))!!.getValue())

        // It should still contain the first inserted value
        assertTrue(trie.contains(intArrayOf(1, 2, 3)))

        // Now we'll insert a prefix of the first value
        trie.insert(intArrayOf(1, 2), "Foo")
        assertFalse(trie.contains(intArrayOf(1)))
        assertTrue(trie.contains(intArrayOf(1, 2)))
        assertTrue(trie.contains(intArrayOf(1, 2, 3)))
    }

    @Test
    fun `contains empty`() {
        val trie = IntTrie<Int>()
        assertFalse(trie.contains(intArrayOf(1)))
        assertFalse(trie.contains(intArrayOf()))
        trie.insert(intArrayOf(), 12345)
        assertFalse(trie.contains(intArrayOf(1)))
        assertFalse(trie.contains(intArrayOf()))
    }

    @Test
    fun `is leaf`() {
        val trie = IntTrie<String>()

        trie.insert(intArrayOf(1, 2, 3), "A")
        assertTrue(trie.get(intArrayOf(1, 2, 3))!!.isLeaf)

        trie.insert(intArrayOf(1, 2), "B")
        assertTrue(trie.get(intArrayOf(1, 2, 3))!!.isLeaf)

        trie.insert(intArrayOf(1, 2, 3, 4, 5), "C")
        assertFalse(trie.get(intArrayOf(1, 2, 3))!!.isLeaf)
    }

    @Test
    fun `get null vs get empty value`() {
        val trie = IntTrie<String>()
        trie.insert(intArrayOf(1, 2, 3), "Bonjour")

        // An element that does not exist returns null
        assertNull(trie.get(intArrayOf(2, 3)))

        // If the path is valid but doesn't lead to a valid node, an empty Value is returned.
        val value: IntTrie.Value<*>? = trie.get(intArrayOf(1, 2))
        assertNotNull(value)
        assertNull(value.getValue())
        assertFalse(value.isLeaf)
    }
}
