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
