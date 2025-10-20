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

import androidx.annotation.WorkerThread
import ch.threema.base.ThreemaException
import ch.threema.localcrypto.exceptions.BlockedByAdminException
import ch.threema.localcrypto.exceptions.CryptoException
import ch.threema.localcrypto.exceptions.InvalidCredentialsException
import ch.threema.localcrypto.exceptions.PassphraseRequiredException
import ch.threema.localcrypto.exceptions.RemoteSecretMonitorException
import ch.threema.localcrypto.models.MasterKeyEvent
import ch.threema.localcrypto.models.PassphraseLockState
import ch.threema.localcrypto.models.RemoteSecretAuthenticationToken
import ch.threema.localcrypto.models.RemoteSecretCheckType
import ch.threema.localcrypto.models.RemoteSecretClientParameters
import ch.threema.localcrypto.models.RemoteSecretProtectionCheckResult
import java.io.IOException
import kotlin.Throws
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The master key is used to encrypt locally stored application data.
 *
 * It has a length of 32 bytes. It is generated randomly the first time the
 * application is started and never changes (not even if the user changes the passphrase or if a remote secret is applied)
 * to prevent the need to re-encrypt all stored data.
 *
 * The master key is stored on the device and may be protected with a passphrase and/or a remote secret.
 */
interface MasterKeyManager {
    val masterKeyProvider: MasterKeyProvider

    val events: Flow<MasterKeyEvent>

    val passphraseLockState: StateFlow<PassphraseLockState>

    /**
     * Returns whether the master key is protected by any means, meaning that it will
     * first need to be unlocked before it can be accessed. Will suspend until the master key is loaded from storage,
     * as it is not possible to tell before whether it is protected.
     */
    suspend fun isProtected(): Boolean

    /**
     * Returns whether the master key is protected (but not necessarily locked) with a passphrase
     */
    fun isProtectedWithPassphrase(): Boolean

    /**
     * Returns whether the master key is protected (but not necessarily locked) with a remote secret,
     * or `null` if the master key is protected with a passphrase and has never been unlocked, as it is not possible to tell in that case.
     */
    fun isProtectedWithRemoteSecret(): Boolean?

    /**
     * Return whether the master key is currently locked with a passphrase.
     * @throws IllegalStateException if called before the master key is loaded. Call [isProtected] first to ensure that it is loaded.
     */
    fun isLockedWithPassphrase(): Boolean

    /**
     * Return whether the master key is currently locked with a remote secret. If the master key is also locked with a passphrase, this will
     * suspend until the passphrase is entered correctly.
     */
    suspend fun isLockedWithRemoteSecret(): Boolean

    /**
     * Return whether the master key is currently locked by any means.
     */
    fun isLocked(): Boolean

    /**
     * Locks the master key, if a passphrase is set
     */
    fun lockWithPassphrase()

    /**
     * Check if the supplied passphrase is correct. This can be called regardless of
     * whether the master key is currently unlocked, and will not change the lock state.
     * If no passphrase is set, returns true.
     *
     * @param passphrase passphrase to be checked
     * @return true on success or if the key is currently not protected with a passphrase, false if the passphrase is wrong or an error occurred
     */
    fun checkPassphrase(passphrase: CharArray): Boolean

    /**
     * Unlock the master key with the supplied passphrase.
     *
     * @param passphrase passphrase for unlocking
     * @return true on success or if the key was not actually locked with a passphrase, or false if the passphrase is wrong or an error occurred
     *
     * @throws IOException thrown when writing the master key data fails.
     * This is only relevant when the master key needs to be migrated to a newer format. Otherwise, nothing needs to be written.
     */
    @Throws(IOException::class)
    @WorkerThread
    fun unlockWithPassphrase(passphrase: CharArray): Boolean

    /**
     * Set or change the passphrase of the master key.
     *
     * @param passphrase the new passphrase
     *
     * @throws IOException thrown when writing the master key data fails
     * @throws CryptoException thrown when removing the previous passphrase failed
     */
    @Throws(IOException::class, CryptoException::class)
    @WorkerThread
    fun setPassphrase(passphrase: CharArray, oldPassphrase: CharArray?)

    /**
     * Remove passphrase of the master key.
     *
     * @throws IOException thrown when writing the master key data fails
     * @throws CryptoException thrown when removing the passphrase failed
     */
    @Throws(IOException::class, CryptoException::class)
    @WorkerThread
    fun removePassphrase(passphrase: CharArray)

    /**
     * Checks whether the master key should be protected with a remote secret, or whether the existing remote secret protection should be removed.
     * This must NOT be called when the master key is still locked.
     */
    fun shouldUpdateRemoteSecretProtectionState(checkType: RemoteSecretCheckType): Boolean

    /**
     * Checks whether the master key should be protected with a remote secret, or whether the existing remote secret protection should be removed,
     * or whether no change needs to be made.
     *
     * @return the type of change needed, or `null` if this information is not available, e.g. because the master key is locked with a passphrase.
     */
    fun getRemoteSecretProtectionState(): RemoteSecretProtectionCheckResult?

    /**
     * Enables or disables remote secrets based on the mdm parameter.
     * @throws IOException thrown when a (temporary) error occurs when communicating with the server
     * @throws InvalidCredentialsException thrown when the server rejects the work credentials for being invalid
     * @throws ThreemaException thrown when one of the services from service manager can not be obtained
     * @throws PassphraseRequiredException thrown when the passphrase is required to re-encrypt the master key. If this is thrown,
     * [lockWithPassphrase] should be called, such that the user is forced to re-enter the passphrase. After that, the passphrase is
     * kept in memory until [updateRemoteSecretProtectionStateIfNeeded] is called again.
     */
    @Throws(ThreemaException::class, InvalidCredentialsException::class, IOException::class, PassphraseRequiredException::class)
    suspend fun updateRemoteSecretProtectionStateIfNeeded(clientParameters: RemoteSecretClientParameters)

    /**
     * Deletes the remote secret from the server.
     * @throws IOException thrown when a (temporary) error occurs when communicating with the server
     * * @throws InvalidCredentialsException thrown when the server rejects the work credentials for being invalid
     * @throws ThreemaException thrown when one of the services from service manager can not be obtained
     */
    @Throws(ThreemaException::class, InvalidCredentialsException::class, IOException::class)
    suspend fun deleteRemoteSecret(clientParameters: RemoteSecretClientParameters, authenticationToken: RemoteSecretAuthenticationToken)

    fun lockWithRemoteSecret()

    /**
     * Checks if the master key needs to be unlocked with a remote secret. Will suspend if the master key is still
     * locked with a passphrase, and will return immediately if the master key is already unlocked.
     * Otherwise, it will suspend until the remote secret is available and then use it to unlock the master key.
     *
     * @throws CryptoException If decrypting the master key with the remote secret fails
     */
    @Throws(CryptoException::class)
    suspend fun unlockWithRemoteSecret()

    /**
     * Monitors the remote secret. Will suspend until an error occurs, in which case the monitoring stops and an exception is thrown.
     * Monitoring means that the remote secret is fetched from the server if needed and then periodically checked for validity.
     */
    @Throws(RemoteSecretMonitorException::class, BlockedByAdminException::class)
    suspend fun monitorRemoteSecret()

    /**
     * Locks the master key with a virtual lock that can not be unlocked. Once in this state, the app needs to be restarted to become usable again.
     */
    fun lockPermanently()

    /**
     * Writes the master key data to storage, if the master key was newly created and has not yet been persisted.
     * This should only be called when the app is fully set up,
     * i.e., once the user's identity is stored and remote secrets have been applied if needed,
     * to avoid that the master key is written to disk unencrypted when it shouldn't be.
     *
     * @throws IOException thrown when writing the master key data fails
     */
    @Throws(IOException::class)
    @WorkerThread
    fun persistKeyDataIfNeeded()
}
