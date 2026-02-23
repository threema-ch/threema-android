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
