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

package ch.threema.localcrypto

import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * @param value the raw master key, used for encrypting the user's locally stored data, such as the database, files, or preferences.
 */
interface MasterKey {
    val value: ByteArray

    /**
     * Checks whether this key is still valid. The key becomes invalid if it gets locked, e.g. with a passphrase, at which point the
     * underlying byte array is filled with all zeroes. Once a [MasterKey] instance is invalid, it will remain invalid, and
     * [decrypt] and [encrypt] will always fail when called on an invalid [MasterKey].
     */
    fun isValid(): Boolean

    /**
     * Wrap an input stream (most commonly a [FileInputStream]) with a decryption operation
     * under this master key.
     *
     * @param inputStream the raw ciphertext input stream
     * @return an input stream for reading plaintext data
     * @throws IOException
     */
    @Throws(IOException::class)
    fun decrypt(inputStream: InputStream): InputStream

    /**
     * Wrap an output stream (most commonly a [FileOutputStream]) with an encryption operation
     * under this master key.
     *
     * @param outputStream the raw ciphertext output stream
     * @return an output stream for writing plaintext data
     * @throws IOException
     */
    @Throws(IOException::class)
    fun encrypt(outputStream: OutputStream): OutputStream
}
