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

package ch.threema.localcrypto.models

import ch.threema.common.models.CryptographicByteArray

/**
 * Represents the data stored in the master key file version 1,
 * i.e., the version used prior to introduction of the Remote Secrets feature in app version 6.2.0.
 */
sealed class Version1MasterKeyStorageData {
    /**
     * @param protectedKey The master key encrypted with the passphrase
     */
    data class PassphraseProtected(
        val protectedKey: CryptographicByteArray,
        val salt: CryptographicByteArray,
        val verification: CryptographicByteArray,
    ) : Version1MasterKeyStorageData()

    data class Unprotected(
        val masterKeyData: MasterKeyData,
        val verification: CryptographicByteArray,
    ) : Version1MasterKeyStorageData()
}
