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

package ch.threema.app.files

import ch.threema.common.files.FileHandle
import ch.threema.localcrypto.MasterKeyProvider
import java.io.IOException
import java.io.OutputStream

class EncryptedFileHandle(
    private val masterKeyProvider: MasterKeyProvider,
    private val file: FileHandle,
) : FileHandle by file {
    @Throws(IOException::class)
    override fun read() =
        file.read()?.let { inputStream ->
            val masterKey = masterKeyProvider.getMasterKey()
            masterKey.decrypt(inputStream)
        }

    @Throws(IOException::class)
    override fun write(): OutputStream =
        masterKeyProvider.getMasterKey().encrypt(file.write())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EncryptedFileHandle
        if (masterKeyProvider != other.masterKeyProvider) return false
        if (file != other.file) return false
        return true
    }

    override fun hashCode(): Int {
        var result = masterKeyProvider.hashCode()
        result = 31 * result + file.hashCode()
        return result
    }

    override fun toString() = "Encrypted[$file]"
}
