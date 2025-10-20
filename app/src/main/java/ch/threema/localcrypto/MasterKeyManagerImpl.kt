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
import ch.threema.base.utils.LoggingUtil
import ch.threema.common.combineStates
import ch.threema.localcrypto.exceptions.BlockedByAdminException
import ch.threema.localcrypto.exceptions.CryptoException
import ch.threema.localcrypto.exceptions.InvalidCredentialsException
import ch.threema.localcrypto.exceptions.MasterKeyLockedException
import ch.threema.localcrypto.exceptions.PassphraseRequiredException
import ch.threema.localcrypto.exceptions.RemoteSecretMonitorException
import ch.threema.localcrypto.models.MasterKeyData
import ch.threema.localcrypto.models.MasterKeyEvent
import ch.threema.localcrypto.models.MasterKeyState
import ch.threema.localcrypto.models.PassphraseLockState
import ch.threema.localcrypto.models.RemoteSecretAuthenticationToken
import ch.threema.localcrypto.models.RemoteSecretCheckType
import ch.threema.localcrypto.models.RemoteSecretClientParameters
import ch.threema.localcrypto.models.RemoteSecretProtectionCheckResult
import java.io.IOException
import java.security.SecureRandom
import kotlin.Throws
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val logger = LoggingUtil.getThreemaLogger("MasterKeyManagerImpl")

class MasterKeyManagerImpl(
    private val keyStorageManager: MasterKeyStorageManager,
    private val random: SecureRandom = SecureRandom(),
    private val keyGenerator: MasterKeyGenerator = MasterKeyGenerator(random),
    private val lockStateHolder: MasterKeyLockStateHolder = MasterKeyLockStateHolder(),
    private val crypto: MasterKeyCrypto = MasterKeyCrypto(),
    private val storageStateHolder: MasterKeyStorageStateHolder = MasterKeyStorageStateHolder(crypto),
    private val passphraseStore: PassphraseStore = PassphraseStore(),
    private val remoteSecretManager: RemoteSecretManager,
) : MasterKeyManager {

    private var keyNeedsWriting = false

    private val remoteSecretMutex = Mutex()

    /**
     * Tracks whether Remote Secret protection can be activated or deactivated during app runtime, i.e., after the app's normal startup sequence.
     * Set to false initially to avoid interference with the app startup sequence, and set to false while RS protection is being activated
     * or deactivated.
     */
    private var remoteSecretProtectionUpdateAllowedAtRuntime = false

    private val _events = Channel<MasterKeyEvent>(capacity = Channel.UNLIMITED)
    override val events: Flow<MasterKeyEvent> = _events.receiveAsFlow()

    override val masterKeyProvider = MasterKeyProvider(lockStateHolder.masterKeyFlow)

    override val passphraseLockState: StateFlow<PassphraseLockState>
        get() = combineStates(
            storageStateHolder.observeStorageState(),
            lockStateHolder.passphraseLockedFlow,
        ) { storageState, isLockedWithPassphrase ->
            when (storageState) {
                is MasterKeyState.WithPassphrase -> {
                    if (isLockedWithPassphrase) {
                        PassphraseLockState.LOCKED
                    } else {
                        PassphraseLockState.UNLOCKED
                    }
                }
                is MasterKeyState.WithoutPassphrase,
                null,
                -> {
                    PassphraseLockState.NO_PASSPHRASE
                }
            }
        }

    /**
     * Checks if a master key exists in storage and will read it.
     * If it is unprotected and of an old version, it will be migrated to the latest version.
     * If it is protected, the migration will happen later once it is unlocked.
     * If no key exists yet, a new unprotected one is generated, but not yet written to storage until [persistKeyDataIfNeeded] is called.
     */
    @Throws(IOException::class)
    @WorkerThread
    fun readOrGenerateKey() {
        if (keyStorageManager.keyExists()) {
            logger.info("Master key exists")
            readKey()
        } else {
            logger.info("Master key does not exist, generating new one")
            generateKey()
        }
    }

    @Throws(IOException::class)
    @WorkerThread
    private fun readKey() {
        val storageState = keyStorageManager.readKey()

        when (storageState) {
            is MasterKeyState.WithPassphrase -> {
                lockStateHolder.setLockedWithPassphrase(storageState)
            }
            is MasterKeyState.WithRemoteSecret -> {
                lockStateHolder.setLockedWithRemoteSecret(storageState)
            }
            is MasterKeyState.Plain -> {
                lockStateHolder.setUnlocked(storageState.masterKeyData)
                if (storageState.wasMigrated) {
                    storageStateHolder.init(storageState)
                    persistKeyData()
                    return
                }
            }
        }

        storageStateHolder.init(storageState)
    }

    private fun MasterKeyLockStateHolder.setUnlocked(
        masterKeyData: MasterKeyData,
        remoteSecretLockData: MasterKeyState.WithRemoteSecret? = null,
    ) {
        setUnlocked(
            MasterKeyImpl(
                value = masterKeyData.value,
                random = random,
            ),
            remoteSecretLockData,
        )
    }

    private fun generateKey() {
        val masterKeyData = keyGenerator.generate()
        lockStateHolder.setUnlocked(masterKeyData)
        storageStateHolder.init(MasterKeyState.Plain(masterKeyData))
        keyNeedsWriting = true
    }

    override fun isProtectedWithPassphrase() =
        storageStateHolder.getStorageState() is MasterKeyState.WithPassphrase

    override fun isProtectedWithRemoteSecret() =
        when (storageStateHolder.getStorageState()) {
            is MasterKeyState.WithRemoteSecret -> true
            is MasterKeyState.Plain -> false
            is MasterKeyState.WithPassphrase -> {
                lockStateHolder.getRemoteSecretLockState()
                    ?.let { lockState -> lockState.remoteSecretLockData != null }
            }
        }

    override suspend fun isProtected() =
        storageStateHolder.isProtected()

    override fun isLockedWithPassphrase() =
        lockStateHolder.isLockedWithPassphrase()

    override suspend fun isLockedWithRemoteSecret() =
        lockStateHolder.isLockedWithRemoteSecret()

    override fun isLocked(): Boolean =
        masterKeyProvider.isLocked()

    override fun lockWithPassphrase() {
        logger.info("locking with passphrase")
        passphraseStore.passphrase = null
        when (val storageState = storageStateHolder.getStorageState()) {
            is MasterKeyState.WithPassphrase -> {
                lockStateHolder.setLockedWithPassphrase(storageState)
            }
            else -> {
                logger.warn("Tried to lock with passphrase, but no passphrase protection is active. ignoring")
            }
        }
    }

    @Synchronized
    override fun checkPassphrase(passphrase: CharArray): Boolean =
        when (val storageState = storageStateHolder.getStorageState()) {
            is MasterKeyState.WithPassphrase -> crypto.verifyPassphrase(storageState, passphrase)
            else -> {
                logger.warn("Tried to check a passphrase, but no passphrase protection is active")
                true
            }
        }

    @Throws(IOException::class)
    @WorkerThread
    @Synchronized
    override fun unlockWithPassphrase(passphrase: CharArray): Boolean {
        val lockData = lockStateHolder.getPassphraseLock()
            ?: return true

        logger.info("Unlocking with passphrase")
        val newLockData = try {
            crypto.decryptWithPassphrase(lockData, passphrase)
        } catch (e: CryptoException) {
            logger.warn("Incorrect passphrase entered", e)
            return false
        }

        // Check if we will need to activate or deactivate remote secret protection after unlocking, and if so
        // keep the passphrase in memory until that is completed
        when (remoteSecretManager.checkRemoteSecretProtection(newLockData as? MasterKeyState.WithRemoteSecret)) {
            RemoteSecretProtectionCheckResult.SHOULD_ACTIVATE,
            RemoteSecretProtectionCheckResult.SHOULD_DEACTIVATE,
            -> {
                passphraseStore.passphrase = passphrase
            }
            RemoteSecretProtectionCheckResult.NO_CHANGE_NEEDED -> Unit
        }

        when (newLockData) {
            is MasterKeyState.WithRemoteSecret -> {
                lockStateHolder.setLockedWithRemoteSecret(newLockData)
            }
            is MasterKeyState.Plain -> {
                lockStateHolder.setUnlocked(newLockData.masterKeyData)
                if (newLockData.wasMigrated) {
                    // We call setPassphrase here to start the migration from version 1 to version 2
                    setPassphrase(passphrase, oldPassphrase = passphrase)
                }
            }
        }

        return true
    }

    @Throws(IOException::class, CryptoException::class)
    @WorkerThread
    @Synchronized
    override fun setPassphrase(passphrase: CharArray, oldPassphrase: CharArray?) {
        if (oldPassphrase != null) {
            unlockWithPassphrase(oldPassphrase)
        }
        lockStateHolder.getMasterKey() ?: throw MasterKeyLockedException()

        if (oldPassphrase != null) {
            storageStateHolder.removePassphraseProtection(oldPassphrase)
        }

        passphraseStore.passphrase = null
        storageStateHolder.addPassphraseProtection(passphrase)
        persistKeyData()
    }

    @Throws(IOException::class)
    @WorkerThread
    @Synchronized
    override fun removePassphrase(passphrase: CharArray) {
        if (!isProtectedWithPassphrase()) {
            return
        }
        logger.info("Removing passphrase")
        unlockWithPassphrase(passphrase)
        storageStateHolder.removePassphraseProtection(passphrase)
        passphraseStore.passphrase = null
        persistKeyData()
    }

    @Throws(CryptoException::class)
    override suspend fun unlockWithRemoteSecret() {
        // Wait until the master key is unlocked or only locked with a remote secret,
        // or do nothing if no remote secret protection is in use.
        val lockData = lockStateHolder.awaitRemoteSecretLockState()
            .remoteSecretLockData
            ?: return

        logger.info("Awaiting remote secret")
        val remoteSecret = remoteSecretManager.awaitRemoteSecretAndClear()

        logger.info("Unlocking with remote secret")
        val newLockData = crypto.decryptWithRemoteSecret(lockData, remoteSecret)
        lockStateHolder.setUnlocked(newLockData.masterKeyData, remoteSecretLockData = lockData)
    }

    override fun lockWithRemoteSecret() {
        passphraseStore.passphrase = null
        val remoteSecretLockData = lockStateHolder.getRemoteSecretLockState()?.remoteSecretLockData
            ?: run {
                logger.error("Failed to lock with remote secret, no remote secret lock data available")
                return
            }
        logger.info("Locking with remote secret")
        lockStateHolder.setLockedWithRemoteSecret(remoteSecretLockData)
    }

    @Throws(RemoteSecretMonitorException::class, BlockedByAdminException::class)
    override suspend fun monitorRemoteSecret() = coroutineScope {
        lockStateHolder.remoteSecretParametersFlow.collectLatest { parameters ->
            if (parameters != null) {
                remoteSecretManager.monitorRemoteSecret(parameters)
            }
        }
    }

    override fun lockPermanently() {
        passphraseStore.passphrase = null
        lockStateHolder.setPermanentlyLocked()
    }

    override fun shouldUpdateRemoteSecretProtectionState(checkType: RemoteSecretCheckType): Boolean {
        if (checkType == RemoteSecretCheckType.APP_RUNTIME && !remoteSecretProtectionUpdateAllowedAtRuntime) {
            return false
        }
        val remoteSecretLockState = lockStateHolder.getRemoteSecretLockState()
            ?: run {
                logger.warn("shouldUpdateRemoteSecretProtectionState called while RS lock state not yet known")
                return false
            }
        return when (remoteSecretManager.checkRemoteSecretProtection(remoteSecretLockState.remoteSecretLockData)) {
            RemoteSecretProtectionCheckResult.NO_CHANGE_NEEDED -> {
                remoteSecretProtectionUpdateAllowedAtRuntime = true
                false
            }
            RemoteSecretProtectionCheckResult.SHOULD_ACTIVATE,
            RemoteSecretProtectionCheckResult.SHOULD_DEACTIVATE,
            -> true
        }
    }

    override fun getRemoteSecretProtectionState(): RemoteSecretProtectionCheckResult? {
        val remoteSecretLockState = lockStateHolder.getRemoteSecretLockState()
            ?: return null
        return remoteSecretManager.checkRemoteSecretProtection(remoteSecretLockState.remoteSecretLockData)
    }

    @Throws(ThreemaException::class, InvalidCredentialsException::class, IOException::class, PassphraseRequiredException::class)
    override suspend fun updateRemoteSecretProtectionStateIfNeeded(
        clientParameters: RemoteSecretClientParameters,
    ): Unit = remoteSecretMutex.withLock {
        remoteSecretProtectionUpdateAllowedAtRuntime = false

        // Wait until the master key is unlocked or only locked with a remote secret
        val remoteSecretLockData = lockStateHolder.awaitRemoteSecretLockState()
            .remoteSecretLockData
        val passphrase = passphraseStore.passphrase

        when (remoteSecretManager.checkRemoteSecretProtection(remoteSecretLockData)) {
            RemoteSecretProtectionCheckResult.NO_CHANGE_NEEDED -> {
                logger.debug("Nothing needs to be done with remote secrets")
            }

            RemoteSecretProtectionCheckResult.SHOULD_ACTIVATE -> {
                logger.info("Adding remote secret protection")
                if (isProtectedWithPassphrase() && passphrase == null) {
                    // If we need the passphrase but don't have it, we stop here to avoid
                    // unnecessarily creating a remote secret that won't be used.
                    throw PassphraseRequiredException()
                }
                val masterKeyData = awaitMasterKeyData()
                val result = remoteSecretManager.createRemoteSecret(clientParameters)
                storageStateHolder.setStateWithRemoteSecretProtection(
                    masterKeyData = masterKeyData,
                    passphrase = passphrase,
                    remoteSecret = result.remoteSecret,
                    parameters = result.parameters,
                )
                persistKeyData()
                lockStateHolder.setUnlocked(
                    masterKeyData = masterKeyData,
                    remoteSecretLockData = crypto.encryptWithRemoteSecret(
                        keyState = MasterKeyState.Plain(masterKeyData),
                        remoteSecret = result.remoteSecret,
                        parameters = result.parameters,
                    ),
                )
                _events.send(MasterKeyEvent.RemoteSecretActivated)
            }

            RemoteSecretProtectionCheckResult.SHOULD_DEACTIVATE -> {
                logger.info("Removing remote secret protection")
                val masterKeyData = awaitMasterKeyData()
                storageStateHolder.setStateWithoutRemoteSecretProtection(
                    masterKeyData = masterKeyData,
                    passphrase = passphrase,
                )
                persistKeyData()
                lockStateHolder.setUnlocked(
                    masterKeyData = masterKeyData,
                    remoteSecretLockData = null,
                )
                remoteSecretLockData?.parameters?.run {
                    _events.send(
                        MasterKeyEvent.RemoteSecretDeactivated(
                            remoteSecretAuthenticationToken = authenticationToken,
                        ),
                    )
                }
            }
        }

        passphraseStore.passphrase = null
        remoteSecretProtectionUpdateAllowedAtRuntime = true
    }

    private suspend fun awaitMasterKeyData() =
        MasterKeyData(masterKeyProvider.awaitUnlocked().value)

    @Throws(ThreemaException::class, InvalidCredentialsException::class, IOException::class)
    override suspend fun deleteRemoteSecret(clientParameters: RemoteSecretClientParameters, authenticationToken: RemoteSecretAuthenticationToken) {
        remoteSecretManager.deleteRemoteSecret(clientParameters, authenticationToken)
    }

    @Throws(IOException::class)
    @WorkerThread
    private fun persistKeyData() {
        val masterKeyState = storageStateHolder.getStorageState()
        when (masterKeyState) {
            is MasterKeyState.Plain -> logger.info("Persisting plain master key")
            is MasterKeyState.WithPassphrase -> logger.info("Persisting passphrase protected master key")
            is MasterKeyState.WithRemoteSecret -> logger.info("Persisting remote secret protected master key")
        }
        keyStorageManager.writeKey(masterKeyState)
        keyNeedsWriting = false
    }

    @Throws(IOException::class)
    @WorkerThread
    @Synchronized
    override fun persistKeyDataIfNeeded() {
        if (keyNeedsWriting) {
            persistKeyData()
        }
    }
}
