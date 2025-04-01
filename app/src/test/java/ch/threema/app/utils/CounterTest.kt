/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.utils

import org.junit.Test
import org.junit.Assert.assertThrows
import kotlin.test.assertEquals

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
        assertThrows(IllegalArgumentException::class.java) { Counter(0) }
        assertThrows(IllegalArgumentException::class.java) { Counter(-1) }
        assertThrows(IllegalArgumentException::class.java) { Counter(Long.MIN_VALUE) }
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
