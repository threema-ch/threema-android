package ch.threema.common

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * A [DelegateStateFlow] can be used to wrap another [StateFlow] as its [delegate].
 * The delegate can be swapped out at runtime, such that the [DelegateStateFlow] will always reflect the latest
 * value of the currently used delegate.
 */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
class DelegateStateFlow<T>(
    delegate: StateFlow<T>,
) : StateFlow<T> {
    private val delegateFlow = MutableStateFlow(delegate)

    var delegate: StateFlow<T>
        get() = delegateFlow.value
        set(value) {
            delegateFlow.value = value
        }

    override val value: T
        get() = delegate.value

    override val replayCache: List<T>
        get() = listOf(delegate.value)

    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        channelFlow {
            delegateFlow.collectLatest { delegate ->
                delegate.collect {
                    send(it)
                }
            }
        }
            .distinctUntilChanged()
            .collect(collector)
        error("should never get here")
    }
}
