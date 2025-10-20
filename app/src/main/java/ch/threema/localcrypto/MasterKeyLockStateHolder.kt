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

import ch.threema.common.mapState
import ch.threema.localcrypto.models.MasterKeyState
import ch.threema.localcrypto.models.RemoteSecretParameters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/**
 * The [MasterKeyLockStateHolder] keeps track of if and how the master key is currently locked.
 */
class MasterKeyLockStateHolder {
    private val lockStateFlow = MutableStateFlow<LockState>(LockState.Unknown)
    private var lockState: LockState
        get() = lockStateFlow.value
        set(newState) {
            lockStateFlow.update { previousState ->
                if (previousState is LockState.PermanentlyLocked) {
                    error("Cannot unlock, permanently locked")
                }

                // wipe the master key from memory by filling it with zeroes
                (previousState as? LockState.Unlocked)?.masterKey?.let { previousMasterKey ->
                    if ((newState as? LockState.Unlocked)?.masterKey != previousMasterKey) {
                        previousMasterKey.invalidate()
                    }
                }

                newState
            }
        }

    val masterKeyFlow: StateFlow<MasterKey?> =
        lockStateFlow.mapState { lockState ->
            (lockState as? LockState.Unlocked)?.masterKey
        }

    val remoteSecretParametersFlow: StateFlow<RemoteSecretParameters?> =
        lockStateFlow.mapState { lockState ->
            (lockState as? RemoteSecretLockState)?.remoteSecretLockData?.parameters
        }

    val passphraseLockedFlow: StateFlow<Boolean> =
        lockStateFlow.mapState { lockState ->
            lockState is LockState.LockedWithPassphrase
        }

    fun setLockedWithPassphrase(lockData: MasterKeyState.WithPassphrase) {
        lockState = LockState.LockedWithPassphrase(lockData)
    }

    fun setLockedWithRemoteSecret(lockData: MasterKeyState.WithRemoteSecret) {
        lockState = LockState.LockedWithRemoteSecret(lockData)
    }

    fun setUnlocked(masterKey: InvalidateableMasterKey, remoteSecretLockData: MasterKeyState.WithRemoteSecret?) {
        lockState = LockState.Unlocked(masterKey, remoteSecretLockData)
    }

    fun setPermanentlyLocked() {
        lockState = LockState.PermanentlyLocked
    }

    fun isLockedWithPassphrase() =
        when (lockState) {
            LockState.Unknown -> error("Lock state not yet known")
            is LockState.LockedWithPassphrase -> true
            else -> false
        }

    suspend fun isLockedWithRemoteSecret() =
        awaitRemoteSecretLockState() is LockState.LockedWithRemoteSecret

    suspend fun awaitRemoteSecretLockState(): RemoteSecretLockState =
        lockStateFlow
            .filterIsInstance<RemoteSecretLockState>()
            .first()

    fun getRemoteSecretLockState(): RemoteSecretLockState? =
        lockState as? RemoteSecretLockState

    fun getMasterKey(): MasterKey? =
        (lockState as? LockState.Unlocked)?.masterKey

    fun getPassphraseLock(): MasterKeyState.WithPassphrase? =
        (lockState as? LockState.LockedWithPassphrase)?.lockData

    private sealed class LockState {
        data object Unknown : LockState()

        data class LockedWithPassphrase(
            val lockData: MasterKeyState.WithPassphrase,
        ) : LockState()

        data class LockedWithRemoteSecret(
            val lockData: MasterKeyState.WithRemoteSecret,
        ) : LockState(), RemoteSecretLockState {
            override val remoteSecretLockData: MasterKeyState.WithRemoteSecret
                get() = lockData
        }

        data object PermanentlyLocked : LockState()

        class Unlocked(
            val masterKey: InvalidateableMasterKey,
            override val remoteSecretLockData: MasterKeyState.WithRemoteSecret?,
        ) : LockState(), RemoteSecretLockState
    }

    sealed interface RemoteSecretLockState {
        /**
         * If this is non-null, it indicates that the master key is protected by a remote secret, but not necessarily locked with it.
         * I.e., it means that this lock state is a remote secret lock itself or was reached by unlocking a remote secret lock.
         * When a lock state with remote secret lock data becomes active, its parameters must be used to monitor the remote secret.
         */
        val remoteSecretLockData: MasterKeyState.WithRemoteSecret?
    }
}
