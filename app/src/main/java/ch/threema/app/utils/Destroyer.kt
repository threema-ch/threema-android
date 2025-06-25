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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import ch.threema.annotation.SameThread

/**
 * A [Destroyer] can be used to collect all the code that needs to run when
 * the host component (e.g. an activity or fragment) is destroyed.
 */
@SameThread
class Destroyer private constructor() : DefaultLifecycleObserver {
    private val destroyables = mutableListOf<Destroyable>()

    fun <T> register(create: () -> T, destroy: Destroyable): T {
        val item = create()
        own(destroy)
        return item
    }

    /**
     * Registers a [Destroyable] callback, to be run when the Destroyer itself is destroyed.
     */
    fun own(destroyable: Destroyable) {
        destroyables.add(destroyable)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        destroyables.forEach { destroyable ->
            destroyable.destroy()
        }
        destroyables.clear()
    }

    companion object {
        @JvmStatic
        fun LifecycleOwner.createDestroyer(): Destroyer =
            lifecycle.createDestroyer()

        @JvmStatic
        fun Lifecycle.createDestroyer(): Destroyer {
            val destroyer = Destroyer()
            addObserver(destroyer)
            return destroyer
        }
    }
}

fun interface Destroyable {
    fun destroy()
}

fun <T : Destroyable> T.ownedBy(destroyer: Destroyer) = also {
    destroyer.own(this)
}
