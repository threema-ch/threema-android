/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

fun <T : Comparable<T>> Array<out T>.equalsIgnoreOrder(other: Array<out T>): Boolean {
    if (this.size != other.size) {
        return false
    }
    return this.sortedArray().contentEquals(other.sortedArray())
}

fun <T : Comparable<T>> Array<out T>.deepEqualsIgnoreOrder(other: Array<out T>): Boolean {
    if (this.size != other.size) {
        return false
    }
    return this.sortedArray().contentDeepEquals(other.sortedArray())
}
