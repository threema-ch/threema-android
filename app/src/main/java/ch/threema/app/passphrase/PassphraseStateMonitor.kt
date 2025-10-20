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

package ch.threema.app.passphrase

import android.content.Context
import ch.threema.app.services.PassphraseService
import ch.threema.localcrypto.MasterKeyManager
import ch.threema.localcrypto.models.PassphraseLockState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.map

class PassphraseStateMonitor(
    private val appContext: Context,
    private val masterKeyManager: MasterKeyManager,
) {
    suspend fun monitorPassphraseLock() {
        masterKeyManager.passphraseLockState
            .map { lockState ->
                when (lockState) {
                    PassphraseLockState.NO_PASSPHRASE,
                    PassphraseLockState.LOCKED,
                    -> false
                    PassphraseLockState.UNLOCKED -> true
                }
            }
            .distinctUntilChanged()
            .dropWhile { isUnlocked -> !isUnlocked }
            .collect { serviceNeeded ->
                if (serviceNeeded) {
                    PassphraseService.start(appContext)
                } else {
                    PassphraseService.stop(appContext)
                }
            }
    }
}
