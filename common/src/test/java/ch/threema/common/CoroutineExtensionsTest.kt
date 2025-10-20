/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.common

import app.cash.turbine.test
import ch.threema.testhelpers.assertSuspendsForever
import ch.threema.testhelpers.expectItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class CoroutineExtensionsTest {
    @Test
    fun `combine two StateFlows into one`() = runTest {
        val stateFlow1 = MutableStateFlow("A")
        val stateFlow2 = MutableStateFlow("B")

        val combinedFlow = combineStates(
            stateFlow1,
            stateFlow2,
        ) { state1, state2 ->
            state1 + state2
        }

        combinedFlow.test {
            expectItem("AB")
            assertEquals("AB", combinedFlow.value)

            stateFlow1.value = "X"
            expectItem("XB")
            assertEquals("XB", combinedFlow.value)

            stateFlow2.value = "Y"
            expectItem("XY")
            assertEquals("XY", combinedFlow.value)
        }
    }

    @Test
    fun `combine three StateFlows into one`() = runTest {
        val stateFlow1 = MutableStateFlow("A")
        val stateFlow2 = MutableStateFlow("B")
        val stateFlow3 = MutableStateFlow("C")

        val combinedFlow = combineStates(
            stateFlow1,
            stateFlow2,
            stateFlow3,
        ) { state1, state2, state3 ->
            state1 + state2 + state3
        }

        combinedFlow.test {
            expectItem("ABC")
            assertEquals("ABC", combinedFlow.value)

            stateFlow1.value = "X"
            expectItem("XBC")
            assertEquals("XBC", combinedFlow.value)

            stateFlow2.value = "Y"
            expectItem("XYC")
            assertEquals("XYC", combinedFlow.value)

            stateFlow3.value = "Z"
            expectItem("XYZ")
            assertEquals("XYZ", combinedFlow.value)
        }
    }

    @Test
    fun `map state`() = runTest {
        val stateFlow = MutableStateFlow(1)

        val mappedStateFlow = stateFlow.mapState { it * 2 }

        assertEquals(2, mappedStateFlow.value)

        mappedStateFlow.test {
            expectItem(2)

            stateFlow.value = 2
            expectItem(4)
            assertEquals(4, mappedStateFlow.value)
        }
    }

    @Test
    fun `wait at most within the timeout`() = runTest {
        var finished = false
        waitAtMost(3.seconds) {
            delay(2.seconds)
            finished = true
        }
        assertTrue(finished)
    }

    @Test
    fun `wait at most reaches the timeout`() = runTest {
        var finished = false
        waitAtMost(3.seconds) {
            delay(5.seconds)
            finished = true
        }
        assertFalse(finished)
    }

    @Test
    fun `awaitAtLeastOneSubscriber suspends forever if no subscriber is present`() = runTest {
        val flow = MutableSharedFlow<Int>()

        assertSuspendsForever {
            flow.awaitAtLeastOneSubscriber()
        }
    }

    @Test
    fun `awaitAtLeastOneSubscriber suspends until a subscriber is present`() = runTest(timeout = 10.seconds) {
        val flow = MutableSharedFlow<Int>()

        val collectionJob = launch {
            delay(3.seconds)
            flow.collect()
        }

        flow.awaitAtLeastOneSubscriber()

        collectionJob.cancel()
    }
}
