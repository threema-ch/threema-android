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
