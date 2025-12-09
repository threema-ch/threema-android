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

import java.io.EOFException
import java.io.IOException
import java.io.InputStream

fun InputStream?.orEmpty(): InputStream =
    this ?: emptyInputStream()

fun emptyInputStream(): InputStream =
    emptyByteArray().inputStream()

/**
 * Compares the content of the input stream with the provided [byteArray]. Note that the input stream is read until the first byte that does not match
 * the byte array.
 *
 * @return true if both the input stream and the byte array are null or both contain the exact same bytes
 */
fun InputStream?.contentEquals(byteArray: ByteArray?): Boolean {
    if (this == null && byteArray == null) {
        return true
    }
    if (this == null || byteArray == null) {
        return false
    }
    use { input ->
        var index = 0
        var next: Int
        while (input.read().also { next = it } != -1) {
            // Abort if the provided byte array is shorter than the input stream
            if (index >= byteArray.size) {
                return false
            }
            // Abort if the next byte does not match the byte array's byte at this position
            if (next.toByte() != byteArray[index]) {
                return false
            }
            index++
        }
        return index == byteArray.size
    }
}

@Throws(IOException::class)
fun InputStream.readLittleEndianInt(): Int {
    val byte1 = readOrThrow()
    val byte2 = readOrThrow()
    val byte3 = readOrThrow()
    val byte4 = readOrThrow()
    return ((byte1 and 0xff) shl 0) +
        ((byte2 and 0xff) shl 8) +
        ((byte3 and 0xff) shl 16) +
        ((byte4 and 0xff) shl 24)
}

@Throws(IOException::class)
fun InputStream.readLittleEndianShort(): Short {
    val byte1 = readOrThrow()
    val byte2 = readOrThrow()
    return (
        ((byte1 and 0xff) shl 0) +
            ((byte2 and 0xff) shl 8)
        ).toShort()
}

@Throws(IOException::class)
private fun InputStream.readOrThrow(): Int {
    val value = read()
    if (value == -1) {
        throw EOFException()
    }
    return value
}
