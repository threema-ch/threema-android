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

package ch.threema.app.utils

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.slf4j.Logger

private class ActiveScreenLogger(private val logger: Logger) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        logger.info("Now visible")
    }

    override fun onStop(owner: LifecycleOwner) {
        logger.info("No longer visible")
    }
}

fun LifecycleOwner.logScreenVisibility(logger: Logger) {
    lifecycle.addObserver(ActiveScreenLogger(logger))
}
