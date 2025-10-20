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

/**
 * Represents the master key related data that is stored locally, including its different versions and the hierarchy in which their data is stored.
 * It does not reflect whether the master key is locked, only whether and how it is protected.
 */
sealed class MasterKeyStorageData {
    /**
     * Version 2 was introduced in app version 6.2.0. Primarily, it adds support for Remote Secrets.
     */
    data class Version2(
        val outerData: Version2MasterKeyStorageOuterData,
    ) : MasterKeyStorageData()

    /**
     * Version 1 was used prior to app version 6.2.0. It supports protecting the master key with an optional passphrase.
     */
    data class Version1(
        val data: Version1MasterKeyStorageData,
    ) : MasterKeyStorageData()
}
