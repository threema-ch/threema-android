/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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

/**
 * Returns a string representation of this [ByteArray] with lowercase hex characters.
 *
 * @param maxBytes if set to a positive value smaller than the size of the array only [maxBytes] bytes
 *  will be visible in the string representation followed by an ellipse character.
 */
fun ByteArray.toHexString(maxBytes: Int = 0): String {
    return if (maxBytes in 1 until size) {
        val bytes = copyOfRange(0, maxBytes)
        // 0x2026: Horizontal ellipsis
        "${Utils.byteArrayToHexString(bytes)}${Char(0x2026)}"
    } else {
        Utils.byteArrayToHexString(this)
    }
}

fun ByteArray.chunked(size: Int): List<ByteArray> = asIterable()
    .chunked(size)
    .map { it.toByteArray() }
