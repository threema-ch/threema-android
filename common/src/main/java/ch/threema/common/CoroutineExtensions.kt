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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

suspend fun <T> Flow<T>.await(item: T) {
    first { it == item }
}

/**
 * Creates a [StateFlow] which will always hold the same [value].
 */
fun <T> stateFlowOf(value: T): StateFlow<T> = MutableStateFlow(value)

/**
 * Similar to [combine], but produces a [StateFlow] instead of a regular [Flow].
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
