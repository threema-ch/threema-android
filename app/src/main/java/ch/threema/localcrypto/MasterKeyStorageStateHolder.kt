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

import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.awaitNonNull
import ch.threema.localcrypto.exceptions.CryptoException
import ch.threema.localcrypto.exceptions.PassphraseRequiredException
import ch.threema.localcrypto.models.MasterKeyData
import ch.threema.localcrypto.models.MasterKeyState
import ch.threema.localcrypto.models.RemoteSecret
import ch.threema.localcrypto.models.RemoteSecretParameters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

private val logger = getThreemaLogger("MasterKeyStorageStateHolder")

/**
 * The [MasterKeyStorageStateHolder] keeps a representation of how the master key should be stored,
 * i.e., only considering its protection but not whether it is unlocked.
 * It also takes care of migrating from version 1 to version 2 if needed by transforming the state.
 * Note that this class does NOT persist any data.
 */
class MasterKeyStorageStateHolder(
    private val crypto: MasterKeyCrypto,
) {
    private val storageStateFlow = MutableStateFlow<MasterKeyState?>(null)

    fun init(keyState: MasterKeyState) {
        storageStateFlow.update { previousState ->
            if (previousState != null) {
                error("already initialized")
            }
            keyState
        }
    }

    suspend fun isProtected() =
        storageStateFlow.awaitNonNull() !is MasterKeyState.Plain

    fun getStorageState(): MasterKeyState =
        storageStateFlow.value ?: error("master key data not yet loaded or generated")

    fun observeStorageState(): StateFlow<MasterKeyState?> =
        storageStateFlow

    @Throws(CryptoException::class)
    fun addPassphraseProtection(passphrase: CharArray) {
        updateState { previousState ->
            when (previousState) {
                is MasterKeyState.WithPassphrase -> {
                    error("Previous passphrase needs to be removed first")
                }
                is MasterKeyState.WithoutPassphrase -> {
                    logger.info("Adding passphrase protection")
                    crypto.encryptWithPassphrase(previousState, passphrase)
                }
            }
        }
    }

    @Throws(CryptoException::class)
    fun removePassphraseProtection(passphrase: CharArray) {
        updateState { previousState ->
            when (previousState) {
                is MasterKeyState.WithoutPassphrase -> {
                    logger.info("Already not passphrase protected")
                    previousState
                }
                is MasterKeyState.WithPassphrase -> {
                    logger.info("Removing passphrase protection")
                    crypto.decryptWithPassphrase(previousState, passphrase)
                }
            }
        }
    }

    @Throws(CryptoException::class, PassphraseRequiredException::class)
    fun setStateWithRemoteSecretProtection(
        masterKeyData: MasterKeyData,
        passphrase: CharArray?,
        remoteSecret: RemoteSecret,
        parameters: RemoteSecretParameters,
    ) {
        updateState { previousState ->
            if (previousState is MasterKeyState.WithPassphrase && passphrase == null) {
                throw PassphraseRequiredException()
            }
            val plain = MasterKeyState.Plain(masterKeyData)
            val withRemoteSecret = crypto.encryptWithRemoteSecret(plain, remoteSecret, parameters)
            if (passphrase != null) {
                crypto.encryptWithPassphrase(withRemoteSecret, passphrase)
            } else {
                withRemoteSecret
            }
        }
    }

    @Throws(CryptoException::class, PassphraseRequiredException::class)
    fun setStateWithoutRemoteSecretProtection(masterKeyData: MasterKeyData, passphrase: CharArray?) {
        updateState { previousState ->
            if (previousState is MasterKeyState.WithPassphrase && passphrase == null) {
                throw PassphraseRequiredException()
            }
            val plain = MasterKeyState.Plain(masterKeyData)
            if (passphrase != null) {
                crypto.encryptWithPassphrase(plain, passphrase)
            } else {
                plain
            }
        }
    }

    private fun updateState(transformation: (previousState: MasterKeyState) -> MasterKeyState) {
        storageStateFlow.update { previousState ->
            if (previousState == null) {
                error("master key data not yet loaded or generated")
            }
            transformation(previousState)
        }
    }
}
