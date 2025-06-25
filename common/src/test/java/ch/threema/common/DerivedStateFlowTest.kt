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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest

class DerivedStateFlowTest {
    @Test
    fun `values are emitted from flow and read from getValue`() = runTest {
        var value = 0

        val derivedStateFlow = DerivedStateFlow(
            getValue = {
                value
            },
            flow = flow {
                emit(0)
                delay(100)

                value = 1
                emit(1)
                delay(100)

                value = 2
                emit(2)
                delay(100)

                value = 2
                emit(2)
                delay(100)

                value = 1
                emit(1)
            },
        )

        derivedStateFlow.test {
            expectItem(0)
            assertEquals(0, derivedStateFlow.value)

            expectItem(1)
            assertEquals(1, derivedStateFlow.value)

            expectItem(2)
            assertEquals(2, derivedStateFlow.value)

            expectItem(1)
            assertEquals(1, derivedStateFlow.value)
        }
    }
}
