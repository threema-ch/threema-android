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

package ch.threema.app.utils

import java8.util.function.Supplier

/**
 * Intended to be used in java code to fill the missing gap of lazy initialized properties.
 *
 * This uses the kotlin [lazy] initializer internally when the value is accessed. Subsequent access
 * to the value will always return the same instance of the same value.
 *
 * @param supplier The supplier that will be utilized to initialise the value.
 */
class LazyProperty<T>(private val supplier: Supplier<T>) : Supplier<T> {
    private val value: T by lazy { supplier.get() }

    /**
     * Get the value provided by this [Supplier]. If the value is not yet initialized it will
     * be created using the provided [supplier].
     *
     * Subsequent calls will always return the value created on initialisation.
     */
    override fun get() = value
}
