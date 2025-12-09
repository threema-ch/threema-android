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

import ch.threema.common.secureContentEquals
import ch.threema.localcrypto.models.MasterKeyState
import ch.threema.localcrypto.models.MasterKeyStorageData
import ch.threema.localcrypto.models.Version1MasterKeyStorageData
import java.io.IOException
import kotlin.Throws

/**
 * Provides access to the master key storage, independently of the storage version.
 */
class MasterKeyStorageManager(
    private val version2KeyFileManager: Version2MasterKeyFileManager,
    private val version1KeyFileManager: Version1MasterKeyFileManager,
    private val storageStateConverter: MasterKeyStorageStateConverter,
) {
    fun keyExists() = version2KeyFileManager.keyFileExists() || version1KeyFileManager.keyFileExists()

    @Throws(IOException::class)
    fun readKey(): MasterKeyState =
        storageStateConverter.toKeyState(readStorageData())

    private fun readStorageData(): MasterKeyStorageData {
        if (version2KeyFileManager.keyFileExists()) {
            return version2KeyFileManager.readKeyFile()
        }
        return version1KeyFileManager.readKeyFile()
    }

    @Throws(IOException::class)
    fun writeKey(data: MasterKeyState) {
        if (version1KeyFileManager.keyFileExists() && data is MasterKeyState.Plain) {
            val oldKeyData = version1KeyFileManager.readKeyFile().data
            if (oldKeyData is Version1MasterKeyStorageData.Unprotected) {
                if (!oldKeyData.masterKeyData.value.secureContentEquals(data.masterKeyData.value)) {
                    error("Migrated master key differs from original master key")
                }
            }
        }

        version2KeyFileManager.writeKeyFile(storageStateConverter.toStorageData(data))
        version1KeyFileManager.deleteFile()
    }
}
