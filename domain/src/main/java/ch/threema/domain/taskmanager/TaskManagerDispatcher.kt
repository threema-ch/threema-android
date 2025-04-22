/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

package ch.threema.domain.taskmanager

import ch.threema.base.concurrent.TrulySingleThreadExecutorThreadFactory
import ch.threema.base.utils.LoggingUtil
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

private val logger = LoggingUtil.getThreemaLogger("TaskManagerDispatcher")

internal interface TaskManagerDispatcherAsserter {
    fun assertDispatcherContext()
}

internal interface TaskManagerDispatcher : TaskManagerDispatcherAsserter {
    val coroutineContext: CoroutineContext
}

internal class SingleThreadedTaskManagerDispatcher(
    private val assertContext: Boolean,
    threadName: String,
) : TaskManagerDispatcher {
    private lateinit var thread: Thread

    private val dispatcher: ExecutorCoroutineDispatcher
    override val coroutineContext: CoroutineContext

    init {
        val factory = TrulySingleThreadExecutorThreadFactory(threadName) {
            thread = it
        }

        dispatcher = Executors.newSingleThreadExecutor(factory).asCoroutineDispatcher()

        coroutineContext = dispatcher
    }

    override fun assertDispatcherContext() {
        if (assertContext) {
            val actual = Thread.currentThread()
            if (actual !== thread) {
                val msg = "Thread mismatch, expected '${thread.name}', got '${actual.name}'"
                logger.error(msg)
                throw Error(msg)
            }
        }
    }
}
