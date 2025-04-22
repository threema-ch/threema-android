/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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

package ch.threema.app.voip.groupcall

import ch.threema.app.BuildConfig
import ch.threema.base.concurrent.TrulySingleThreadExecutorThreadFactory
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*

class GroupCallThreadUtil {
    interface ExceptionHandler {
        fun handle(t: Throwable)
    }

    companion object {
        var exceptionHandler: ExceptionHandler? = null
        val dispatcher: CoroutineContext
        lateinit var thread: Thread

        init {
            val factory = TrulySingleThreadExecutorThreadFactory("GroupCallWorker") {
                thread = it
            }
            val handler = CoroutineExceptionHandler { _, exception ->
                exceptionHandler?.handle(exception) ?: throw exception
            }
            dispatcher = Executors.newSingleThreadExecutor(factory).asCoroutineDispatcher().plus(handler)
        }

        fun assertDispatcherThread() {
            if (BuildConfig.DEBUG) {
                val actual = Thread.currentThread()
                if (actual !== thread) {
                    throw Error("Thread mismatch, expected '${thread.name}', got '${actual.name}'")
                }
            }
        }
    }
}
