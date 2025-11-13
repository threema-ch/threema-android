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

import androidx.core.util.AtomicFile
import ch.threema.common.toCryptographicByteArray
import ch.threema.common.xor
import ch.threema.localcrypto.models.MasterKeyData
import ch.threema.localcrypto.models.MasterKeyStorageData
import ch.threema.localcrypto.models.Version1MasterKeyStorageData
import java.io.DataInputStream
import java.io.File
import java.io.IOException

/**
 * Handles reading from version 1 master key file,
 * i.e., the format used prior to app version 6.2.0. It become obsolete with the introduction of the
 * Remote Secrets feature.
 *
 * Key file format:
 *
 * - protection type (1 byte)
 *    - 0 = unprotected
 *    - 1 = protected with PBKDF2 passphrase (no longer supported)
 *    - 2 = protected with Scrypt passphrase
 * - key (32 bytes)
 * - salt (8 bytes)
 * - verification (4 bytes = start of SHA1 hash of master key)
 * </ul>
 */
class Version1MasterKeyFileManager(
    private val keyFile: File,
) {
    fun keyFileExists() = keyFile.exists()

    @Throws(IOException::class)
    fun readKeyFile(): MasterKeyStorageData.Version1 {
        val atomicKeyFile = AtomicFile(keyFile)

        DataInputStream(atomicKeyFile.openRead()).use { dis ->
            val protectionTypeCode = dis.readUnsignedByte()
            val obfuscatedKey = ByteArray(MasterKeyConfig.KEY_LENGTH)
            dis.readFully(obfuscatedKey)

            val keyData = obfuscatedKey xor DEPRECATED_OBFUSCATION_KEY

            val salt = ByteArray(SALT_LENGTH)
            dis.readFully(salt)

            val verification = ByteArray(MasterKeyConfig.VERSION1_VERIFICATION_LENGTH)
            dis.readFully(verification)

            return when (protectionTypeCode) {
                PROTECTION_CODE_UNPROTECTED -> MasterKeyStorageData.Version1(
                    data = Version1MasterKeyStorageData.Unprotected(
                        masterKeyData = MasterKeyData(keyData),
                        verification = verification.toCryptographicByteArray(),
                    ),
                )
                PROTECTION_CODE_SCRYPT -> MasterKeyStorageData.Version1(
                    data = Version1MasterKeyStorageData.PassphraseProtected(
                        protectedKey = keyData.toCryptographicByteArray(),
                        salt = salt.toCryptographicByteArray(),
                        verification = verification.toCryptographicByteArray(),
                    ),
                )
                else -> error("Unsupported protection type $protectionTypeCode")
            }
        }
    }

    fun deleteFile() {
        if (keyFile.exists() && !keyFile.delete()) {
            error("Failed to delete version 1 master key file")
        }
    }

    companion object {
        private const val SALT_LENGTH = 8

        private const val PROTECTION_CODE_UNPROTECTED = 0
        private const val PROTECTION_CODE_SCRYPT = 2

        /**
         * Static key used for obfuscating the stored master key.
         *
         * Introduced in really old app versions (long before APIs like the Android keystore system
         * existed) back when the app was closed source and retained for compatibility; of course pretty
         * pointless in an open source app, but does no harm either)
         */
        private val DEPRECATED_OBFUSCATION_KEY = byteArrayOf(
            0x95.toByte(), 0x0d.toByte(), 0x26.toByte(), 0x7a.toByte(), 0x88.toByte(), 0xea.toByte(), 0x77.toByte(), 0x10.toByte(),
            0x9c.toByte(), 0x50.toByte(), 0xe7.toByte(), 0x3f.toByte(), 0x47.toByte(), 0xe0.toByte(), 0x69.toByte(), 0x72.toByte(),
            0xda.toByte(), 0xc4.toByte(), 0x39.toByte(), 0x7c.toByte(), 0x99.toByte(), 0xea.toByte(), 0x7e.toByte(), 0x67.toByte(),
            0xaf.toByte(), 0xfd.toByte(), 0xdd.toByte(), 0x32.toByte(), 0xda.toByte(), 0x35.toByte(), 0xf7.toByte(), 0x0c.toByte(),
        )
    }
}
