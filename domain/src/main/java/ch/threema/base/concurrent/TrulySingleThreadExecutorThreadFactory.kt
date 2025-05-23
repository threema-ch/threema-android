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

package ch.threema.base.concurrent

import ch.threema.base.utils.LoggingUtil
import java.util.concurrent.ThreadFactory

private val logger = LoggingUtil.getThreemaLogger("TrulySingleThreadExecutorThreadFactory")

class TrulySingleThreadExecutorThreadFactory(
    val name: String,
    val created: (Thread) -> Unit,
) : ThreadFactory {
    var thread: Thread? = null

    override fun newThread(runnable: Runnable): Thread {
        thread?.also {
            logger.error("Thread '{}' was already created", it.name)
        }
        return Thread(runnable, name).also {
            thread = it
            created(it)
        }
    }
}
