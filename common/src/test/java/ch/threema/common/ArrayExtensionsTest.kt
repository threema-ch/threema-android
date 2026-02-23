package ch.threema.common

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArrayExtensionsTest {
    @Test
    fun `comparing arrays ignoring order`() {
        assertTrue(arrayOf(1, 2, 3).equalsIgnoreOrder(arrayOf(1, 2, 3)))
        assertTrue(arrayOf(1, 2, 3).equalsIgnoreOrder(arrayOf(2, 1, 3)))
        assertTrue(arrayOf(1, 2, 3, 2).equalsIgnoreOrder(arrayOf(2, 1, 3, 2)))
        assertFalse(arrayOf(1, 2, 3).equalsIgnoreOrder(arrayOf(1, 2)))
        assertFalse(arrayOf(1, 2, 3).equalsIgnoreOrder(arrayOf(1, 2, 4)))
    }
}
