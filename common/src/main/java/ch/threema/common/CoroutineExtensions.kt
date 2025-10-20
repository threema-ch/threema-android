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

import kotlin.reflect.KProperty
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull

suspend fun <T : Any> Flow<T>.await(item: T) {
    first { it == item }
}

suspend fun <T : Any> Flow<T?>.awaitNonNull(): T =
    filterNotNull().first()

suspend fun <T : Any> Flow<T?>.awaitNull() {
    first { it == null }
}

suspend inline fun waitAtMost(timeout: Duration, noinline block: suspend () -> Unit) {
    withTimeoutOrNull(timeout) { block() }
}

/**
 * Creates a [StateFlow] which will always hold the same [value].
 */
fun <T> stateFlowOf(value: T): StateFlow<T> = MutableStateFlow(value)

/**
 * Similar to [combine], but produces a [StateFlow] instead of a regular [Flow].
 *
 * Note: This should be used sparingly and only with very simple and cheap transformations, as the transformation
 * is run for every collector every time a new value is emitted from any of the source flows, as well as every time
 * [StateFlow.value] is called on the resulting [StateFlow]. It is mainly viable in cases where at most one collector
 * will be active at a time and `value` is never called. In most cases, it is better to use [combine] in combination with [Flow.stateIn].
 */
fun <T1, T2, R> combineStates(
    stateFlow1: StateFlow<T1>,
    stateFlow2: StateFlow<T2>,
    transformation: (T1, T2) -> R,
): StateFlow<R> =
    DerivedStateFlow(
        getValue = {
            transformation(stateFlow1.value, stateFlow2.value)
        },
        flow = combine(stateFlow1, stateFlow2, transformation),
    )

/**
 * Similar to [combine], but produces a [StateFlow] instead of a regular [Flow].
 *
 * Note: This should be used sparingly and only with very simple and cheap transformations, as the transformation
 * is run for every collector every time a new value is emitted from any of the source flows, as well as every time
 * [StateFlow.value] is called on the resulting [StateFlow]. It is mainly viable in cases where at most one collector
 * will be active at a time and `value` is never called. In most cases, it is better to use [combine] in combination with [Flow.stateIn].
 */
fun <T1, T2, T3, R> combineStates(
    stateFlow1: StateFlow<T1>,
    stateFlow2: StateFlow<T2>,
    stateFlow3: StateFlow<T3>,
    transformation: (T1, T2, T3) -> R,
): StateFlow<R> =
    DerivedStateFlow(
        getValue = {
            transformation(stateFlow1.value, stateFlow2.value, stateFlow3.value)
        },
        flow = combine(stateFlow1, stateFlow2, stateFlow3, transformation),
    )

/**
 * Similar to [map], but produces a [StateFlow] instead of a regular [Flow].
 *
 * Note: This should be used sparingly and only with very simple and cheap transformations, as the transformation
 * is run for every collector every time a new value is emitted from the source flow, as well as every time
 * [StateFlow.value] is called on the resulting [StateFlow]. It is mainly viable in cases where at most one collector
 * will be active at a time and `value` is never called. In most cases, it is better to use [map] in combination with [Flow.stateIn].
 */
fun <T, R> StateFlow<T>.mapState(transformation: (T) -> R): StateFlow<R> =
    DerivedStateFlow(
        getValue = {
            transformation(value)
        },
        flow = map(transformation),
    )

operator fun <T> StateFlow<T>.getValue(receiver: Any, property: KProperty<*>): T = value

operator fun <T> MutableStateFlow<T>.setValue(receiver: Any, property: KProperty<*>, t: T) {
    this.value = t
}

suspend inline fun <T> MutableSharedFlow<T>.awaitAtLeastOneSubscriber() {
    subscriptionCount.first { it > 0 }
}
