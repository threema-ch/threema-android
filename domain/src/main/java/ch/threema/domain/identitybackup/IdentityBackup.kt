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

package ch.threema.domain.identitybackup

import ch.threema.base.ThreemaException
import ch.threema.base.crypto.NaCl
import ch.threema.common.secureContentEquals
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.types.Identity
import ch.threema.libthreema.IdentityBackupData
import ch.threema.libthreema.IdentityBackupException
import ch.threema.libthreema.decryptIdentityBackup
import ch.threema.libthreema.encryptIdentityBackup

object IdentityBackup {
    private const val PASSWORD_MIN_LEN = 8

    /**
     * Generate a Threema identity backup by encrypting the identity and private key
     * using the given password (minimum [PASSWORD_MIN_LEN] characters).
     * <p>
     * The backup will be returned in ASCII format and consists of 20 groups of 4
     * uppercase characters and digits separated by '-'.
     *
     * @throws IllegalArgumentException If the threema ID or the client key has an invalid length,
     *         or the password has fewer than [PASSWORD_MIN_LEN] characters
     * @throws ThreemaException If the encryption of the backup failed
     */
    @JvmStatic
    @Throws(IllegalArgumentException::class, ThreemaException::class)
    fun encryptIdentityBackup(password: String, backupData: PlainBackupData): EncryptedIdentityBackup {
        require(backupData.threemaId.length == ProtocolDefines.IDENTITY_LEN) {
            "Identity has invalid length"
        }

        require(backupData.clientKey.size == NaCl.SECRET_KEY_BYTES) {
            "Private key has invalid length"
        }

        require(password.length >= PASSWORD_MIN_LEN) {
            "Password is too short"
        }

        try {
            val encryptedBackup = encryptIdentityBackup(
                password,
                backupData.toIdentityBackupData(),
            )
            return EncryptedIdentityBackup(encryptedBackup)
        } catch (e: IdentityBackupException) {
            throw ThreemaException("Backup encryption failed", e)
        }
    }

    /**
     * Decode an identity backup using the given password.
     *
     * @param password password that was used to encrypt the backup (min. 6 characters)
     * @param encryptedBackup The encrypted identity backup
     *
     * @return The decrypted [PlainBackupData] of the identity backup containing the threema ID and the client key
     *
     * @throws ThreemaException If the backup could not be decrypted
     */
    @JvmStatic
    @Throws(ThreemaException::class)
    fun decryptIdentityBackup(password: String, encryptedBackup: EncryptedIdentityBackup): PlainBackupData {
        try {
            return decryptIdentityBackup(
                password,
                encryptedBackup.data,
            ).toPlainBackupData()
        } catch (e: IdentityBackupException) {
            throw ThreemaException("Identity backup decryption failed", e)
        }
    }

    /**
     * A wrapper class for the identity backup string used to ensure the password and the
     * identity backup do not get mixed up.
     */
    data class EncryptedIdentityBackup(
        val data: String,
    )

    /**
     * A wrapper for the unencrypted identity backup data.
     * This serves both as an abstraction for the libthreema datatype and to
     * help avoid mixing up password and threema identity.
     */
    data class PlainBackupData(
        val threemaId: Identity,
        val clientKey: ByteArray,
    ) {
        fun toIdentityBackupData() = IdentityBackupData(
            threemaId,
            clientKey,
        )

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as PlainBackupData

            if (threemaId != other.threemaId) return false
            if (!clientKey.secureContentEquals(other.clientKey)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = threemaId.hashCode()
            result = 31 * result + clientKey.contentHashCode()
            return result
        }
    }

    private fun IdentityBackupData.toPlainBackupData() = PlainBackupData(
        threemaId,
        clientKey,
    )
}
