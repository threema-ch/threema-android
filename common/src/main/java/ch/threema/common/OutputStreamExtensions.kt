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

import java.io.IOException
import java.io.OutputStream

@Throws(IOException::class)
fun OutputStream.writeLittleEndianShort(value: Short) {
    write(((value.toInt() shr 0) and 0xff).toByte().toInt())
    write(((value.toInt() shr 8) and 0xff).toByte().toInt())
}

@Throws(IOException::class)
fun OutputStream.writeLittleEndianInt(value: Int) {
    write(((value shr 0) and 0xff).toByte().toInt())
    write(((value shr 8) and 0xff).toByte().toInt())
    write(((value shr 16) and 0xff).toByte().toInt())
    write(((value shr 24) and 0xff).toByte().toInt())
}
