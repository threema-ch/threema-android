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

import ch.threema.common.awaitNonNull
import ch.threema.common.awaitNull
import ch.threema.localcrypto.exceptions.MasterKeyLockedException
import kotlinx.coroutines.flow.StateFlow

/**
 * Provides read-only access to the [MasterKey], if it is unlocked.
 * The [MasterKeyProvider] is agnostic to the types of locks, e.g., it does not know
 * if a passphrase or remote secret is used.
 */
class MasterKeyProvider(
    private val masterKeyFlow: StateFlow<MasterKey?>,
) {
    fun getMasterKeyOrNull(): MasterKey? =
        masterKeyFlow.value

    @Throws(MasterKeyLockedException::class)
    fun getMasterKey(): MasterKey =
        getMasterKeyOrNull()
            ?: throw MasterKeyLockedException()

    fun isLocked(): Boolean =
        getMasterKeyOrNull() == null

    suspend fun awaitUnlocked(): MasterKey =
        masterKeyFlow.awaitNonNull()

    suspend fun awaitLocked() {
        masterKeyFlow.awaitNull()
    }
}
