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

package ch.threema.base.utils

import androidx.annotation.AnyThread
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.slf4j.Logger

val logger: Logger = LoggingUtil.getThreemaLogger("CoroutinesExtension")

/**
 *  Merges all exceptions from the default completed exceptionally state and the ones that could be thrown by [Deferred.getCompleted].
 *
 *  Note: If this [Deferred] was cancelled, [onCompletedExceptionally] will get called with the cancellation exception.
 */
@OptIn(ExperimentalCoroutinesApi::class)
inline fun <R> Deferred<R>.onCompleted(
    @AnyThread crossinline onCompletedExceptionally: (throwable: Throwable) -> Unit,
    @AnyThread crossinline onCompletedNormally: (value: R) -> Unit,
) {
    invokeOnCompletion { throwable: Throwable? ->
        throwable?.let {
            onCompletedExceptionally(throwable)
            return@invokeOnCompletion
        }
        val completedValue = try {
            getCompleted()
        } catch (exception: Exception) {
            logger.error("Failed to complete deferred", exception)
            onCompletedExceptionally(exception)
            return@invokeOnCompletion
        }
        onCompletedNormally(completedValue)
    }
}
