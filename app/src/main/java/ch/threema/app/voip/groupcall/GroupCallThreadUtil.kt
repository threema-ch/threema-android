/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2023 Threema GmbH
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
import ch.threema.base.utils.LoggingUtil
import kotlinx.coroutines.*
import java.lang.Runnable
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

private val logger = LoggingUtil.getThreemaLogger("GroupCallThreadUtil")

class TrulySingleThreadExecutorThreadFactory(
    val name: String,
    val created: ((thread: Thread) -> Unit),
) : ThreadFactory {
    var thread: Thread? = null

    override fun newThread(runnable: Runnable?): Thread {
        thread?.also {
            logger.error("Thread '{}' was already created", it.name)
        }
        return Thread(runnable, name).also {
            thread = it
            created(it)
        }
    }
}

class GroupCallThreadUtil {
    companion object {
        val DISPATCHER: ExecutorCoroutineDispatcher
        lateinit var THREAD: Thread

        init {
            val factory = TrulySingleThreadExecutorThreadFactory("GroupCallWorker") {
                THREAD = it
            }
            DISPATCHER = Executors.newSingleThreadExecutor(factory).asCoroutineDispatcher()
        }

        fun assertDispatcherThread() {
            if (BuildConfig.DEBUG) {
                val actual = Thread.currentThread()
                if (actual !== THREAD) {
                    throw Error("Thread mismatch, expected '${THREAD.name}', got '${actual.name}'")
                }
            }
        }
    }
}
