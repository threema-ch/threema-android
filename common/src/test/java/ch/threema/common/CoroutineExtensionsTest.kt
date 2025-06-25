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
import ch.threema.testhelpers.expectItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
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
}
