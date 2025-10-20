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

package ch.threema.common

import ch.threema.common.models.CryptographicByteArray
import java.security.MessageDigest
import kotlin.experimental.xor
import kotlin.text.toHexString as toHexStringKotlinStdLib

fun emptyByteArray() = ByteArray(0)

infix fun ByteArray.xor(other: ByteArray): ByteArray {
    require(size == other.size)
    val processedData = ByteArray(size)
    for (i in indices) {
        processedData[i] = this[i] xor other[i]
    }
    return processedData
}

fun ByteArray.secureContentEquals(other: ByteArray) =
    MessageDigest.isEqual(this, other)

fun ByteArray.toCryptographicByteArray() = CryptographicByteArray(this)

fun ByteArray.chunked(size: Int): List<ByteArray> = asIterable()
    .chunked(size)
    .map { it.toByteArray() }

/**
 * Returns a string representation of this [ByteArray] with lowercase hex characters.
 *
 * @param maxBytes if set to a positive value smaller than the size of the array only [maxBytes] bytes
 *  will be visible in the string representation followed by an ellipsis character.
 */
@OptIn(ExperimentalStdlibApi::class)
fun ByteArray.toHexString(maxBytes: Int = 0): String =
    if (maxBytes in 1 until size) {
        val bytes = copyOfRange(0, maxBytes)
        "${bytes.toHexStringKotlinStdLib()}$HORIZONTAL_ELLIPSIS"
    } else {
        toHexStringKotlinStdLib()
    }

private const val HORIZONTAL_ELLIPSIS = 0x2026.toChar()
