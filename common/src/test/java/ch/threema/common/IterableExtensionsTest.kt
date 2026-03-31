package ch.threema.common

import kotlin.test.Test
import kotlin.test.assertEquals

class IterableExtensionsTest {
    @Test
    fun `associate with not null keeps the order`() {
        // Arrange
        val numbers = (0..9)

        // Act
        val evenNumbers = numbers.associateWithNotNull { number ->
            if (number % 2 == 0) {
                number
            } else {
                null
            }
        }

        // Assert. Note that we convert the entries to a list to check that the order is preserved
        val expected = (0..9 step 2).associateWith { it }.toList()
        assertEquals(expected, evenNumbers.toList())
    }

    @Test
    fun `associate with not null keeps last element`() {
        // Arrange
        val numbers = listOf(0, 1, 0)

        // Act
        var mappedValue = 0
        val associated = numbers.associateWith { mappedValue++ }

        // Assert
        val expected = mapOf(
            0 to 2,
            1 to 1,
        )

        assertEquals(expected, associated)
    }
}
