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

class DelegateStateFlowTest {
    @Test
    fun `values always flow from delegate`() = runTest {
        val flow1 = MutableStateFlow(0)
        val flow2 = MutableStateFlow(1)

        val delegateStateFlow = DelegateStateFlow(flow1)

        delegateStateFlow.test {
            expectItem(0)
            assertEquals(0, delegateStateFlow.value)

            flow1.value = 1
            expectItem(1)
            assertEquals(1, delegateStateFlow.value)

            flow1.value = 2
            expectItem(2)
            assertEquals(2, delegateStateFlow.value)

            delegateStateFlow.delegate = flow2
            expectItem(1)
            assertEquals(1, delegateStateFlow.value)

            flow1.value = 4
            expectNoEvents()

            flow2.value = 3
            expectItem(3)
            assertEquals(3, delegateStateFlow.value)
        }
    }
}
