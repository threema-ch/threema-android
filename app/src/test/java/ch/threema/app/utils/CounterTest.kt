package ch.threema.app.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CounterTest {
    @Test
    fun `initial counter value must be zero`() {
        val counter = Counter()
        counter.assertCountAndStringRepresentation(0L)
    }

    @Test
    fun `count must increment the counter by one for each call`() {
        val counter = Counter()
        counter.assertCountAndStringRepresentation(0)

        repeat(1000) { round ->
            counter.count()
            counter.assertCountAndStringRepresentation(round + 1L)
        }
    }

    @Test
    fun `step sizes smaller or equal zero must throw an exception`() {
        assertFailsWith<IllegalArgumentException> { Counter(0) }
        assertFailsWith<IllegalArgumentException> { Counter(-1) }
        assertFailsWith<IllegalArgumentException> { Counter(Long.MIN_VALUE) }
    }

    @Test
    fun `default step size must be one`() {
        val counter = Counter()
        counter.assertCountAndSteps(0, 0)

        repeat(1000) { round ->
            counter.count()
            val expected = round + 1L
            counter.assertCountAndSteps(expected, expected)
        }
    }

    @Test
    fun `steps must be counted based on the step size`() {
        val counter = Counter(10)
        counter.assertCountAndSteps(0, 0)

        repeat(10) { counter.count() }
        counter.assertCountAndSteps(10, 1)

        repeat(1000) { counter.count() }
        counter.assertCountAndSteps(1010, 101)
    }

    @Test
    fun `threshold must be respected when querying steps`() {
        val counter = Counter(10)
        counter.assertCountAndSteps(0, 0)

        repeat(10) { counter.count() }
        assertEquals(0, counter.getAndResetSteps(5))
        counter.assertCountAndSteps(10, 1)

        repeat(30) { counter.count() }
        assertEquals(0, counter.getAndResetSteps(5))
        counter.assertCountAndSteps(40, 4)

        repeat(10) { counter.count() }
        assertEquals(5, counter.getAndResetSteps(5))
        // steps must be reset after they are queried
        counter.assertCountAndSteps(50, 0)

        repeat(60) { counter.count() }
        assertEquals(6, counter.getAndResetSteps(5))
        counter.assertCountAndSteps(110, 0)
    }

    @Test
    fun `partial steps must not be affected when step threshold is met`() {
        val counter = Counter(10)
        counter.assertCountAndSteps(0, 0)

        repeat(59) { counter.count() }
        assertEquals(5, counter.getAndResetSteps(5))
        counter.assertCountAndSteps(59, 0)

        counter.count()
        counter.assertCountAndSteps(60, 1)
    }

    private fun Counter.assertCountAndStringRepresentation(expectedCount: Long) {
        assertEquals(expectedCount, count)
        assertEquals("$expectedCount", toString())
    }

    private fun Counter.assertCountAndSteps(expectedCount: Long, expectedSteps: Long) {
        assertCountAndStringRepresentation(expectedCount)
        assertEquals(expectedSteps, steps)
    }
}
