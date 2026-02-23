package ch.threema.common

import kotlin.test.Test
import kotlin.test.assertEquals

class CollectionExtensionsTest {
    @Test
    fun `adding item to set with toggle`() {
        val mySet = setOf(1, 2, 3)
        assertEquals(
            setOf(1, 2, 3, 4),
            mySet.toggle(4),
        )
    }

    @Test
    fun `removing item from set with toggle`() {
        val mySet = setOf(1, 2, 3)
        assertEquals(
            setOf(1, 3),
            mySet.toggle(2),
        )
    }
}
