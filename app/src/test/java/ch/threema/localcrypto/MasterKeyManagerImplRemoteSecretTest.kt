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

import ch.threema.localcrypto.MasterKeyTestData.MASTER_KEY
import ch.threema.localcrypto.models.MasterKeyData
import ch.threema.localcrypto.models.MasterKeyEvent
import ch.threema.localcrypto.models.MasterKeyState
import ch.threema.localcrypto.models.RemoteSecret
import ch.threema.localcrypto.models.RemoteSecretAuthenticationToken
import ch.threema.localcrypto.models.RemoteSecretClientParameters
import ch.threema.localcrypto.models.RemoteSecretCreationResult
import ch.threema.localcrypto.models.RemoteSecretParameters
import ch.threema.localcrypto.models.RemoteSecretProtectionCheckResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class MasterKeyManagerImplRemoteSecretTest {

    private lateinit var storageManagerMock: MasterKeyStorageManager
    private lateinit var lockStateHolderMock: MasterKeyLockStateHolder
    private lateinit var storageStateHolderMock: MasterKeyStorageStateHolder
    private lateinit var remoteSecretManagerMock: RemoteSecretManager
    private lateinit var passphraseStoreMock: PassphraseStore
    private lateinit var crypto: MasterKeyCrypto
    private lateinit var masterKeyFlow: MutableStateFlow<MasterKey?>
    private lateinit var masterKeyManager: MasterKeyManagerImpl

    @BeforeTest
    fun setUp() {
        storageManagerMock = mockk {
            every { keyExists() } returns true
            every { readKey() } returns MasterKeyState.Plain(
                masterKeyData = MasterKeyData(MASTER_KEY),
            )
            every { writeKey(any()) } just runs
        }
        remoteSecretManagerMock = mockk {
            coEvery { deleteRemoteSecret(any(), any()) } just runs
        }
        storageStateHolderMock = mockk(relaxed = true)
        masterKeyFlow = MutableStateFlow(null)
        lockStateHolderMock = mockk(relaxed = true) {
            every { masterKeyFlow } returns this@MasterKeyManagerImplRemoteSecretTest.masterKeyFlow
        }
        passphraseStoreMock = mockk(relaxed = true) {
            every { passphrase } returns PASSPHRASE
        }
        crypto = spyk(MasterKeyCrypto())
        masterKeyManager = MasterKeyManagerImpl(
            keyStorageManager = storageManagerMock,
            lockStateHolder = lockStateHolderMock,
            remoteSecretManager = remoteSecretManagerMock,
            storageStateHolder = storageStateHolderMock,
            passphraseStore = passphraseStoreMock,
            crypto = crypto,
            random = mockk(),
        )
        masterKeyManager.readOrGenerateKey()
    }

    @Test
    fun `activate remote secret`() = runTest {
        val plain = MasterKeyState.Plain(MasterKeyData(MASTER_KEY))
        val withRemoteSecretMock = mockk<MasterKeyState.WithRemoteSecret>()
        val clientParametersMock = mockk<RemoteSecretClientParameters>()
        val parametersMock = mockk<RemoteSecretParameters>()
        masterKeyFlow.value = MasterKeyImpl(MASTER_KEY)
        coEvery { lockStateHolderMock.awaitRemoteSecretLockState() } returns mockk {
            every { remoteSecretLockData } returns null
        }
        every {
            remoteSecretManagerMock.checkRemoteSecretProtection(null)
        } returns RemoteSecretProtectionCheckResult.SHOULD_ACTIVATE
        coEvery {
            remoteSecretManagerMock.createRemoteSecret(clientParametersMock)
        } returns RemoteSecretCreationResult(
            remoteSecret = REMOTE_SECRET,
            parameters = parametersMock,
        )
        every {
            crypto.encryptWithRemoteSecret(plain, REMOTE_SECRET, parametersMock)
        } returns withRemoteSecretMock

        val deferredEvent = async(UnconfinedTestDispatcher()) {
            masterKeyManager.events.first()
        }

        masterKeyManager.updateRemoteSecretProtectionStateIfNeeded(clientParametersMock)

        verify(exactly = 1) {
            storageStateHolderMock.setStateWithRemoteSecretProtection(
                masterKeyData = MasterKeyData(MASTER_KEY),
                passphrase = PASSPHRASE,
                remoteSecret = REMOTE_SECRET,
                parameters = parametersMock,
            )
        }
        verify(exactly = 1) {
            lockStateHolderMock.setUnlocked(
                masterKey = match { it.value.contentEquals(MASTER_KEY) },
                remoteSecretLockData = withRemoteSecretMock,
            )
        }
        verify(exactly = 1) {
            storageManagerMock.writeKey(any())
        }
        verify(exactly = 1) {
            passphraseStoreMock.passphrase = null
        }
        assertEquals(MasterKeyEvent.RemoteSecretActivated, deferredEvent.await())
    }

    @Test
    fun `deactivate remote secret`() = runTest {
        val clientParametersMock = mockk<RemoteSecretClientParameters>()
        val authenticationTokenMock = mockk<RemoteSecretAuthenticationToken>()
        val withRemoteSecretMock = mockk<MasterKeyState.WithRemoteSecret> {
            every { parameters } returns mockk {
                every { authenticationToken } returns authenticationTokenMock
            }
        }
        masterKeyFlow.value = MasterKeyImpl(MASTER_KEY)
        coEvery { lockStateHolderMock.awaitRemoteSecretLockState() } returns mockk {
            every { remoteSecretLockData } returns withRemoteSecretMock
        }
        every {
            remoteSecretManagerMock.checkRemoteSecretProtection(withRemoteSecretMock)
        } returns RemoteSecretProtectionCheckResult.SHOULD_DEACTIVATE
        coEvery { remoteSecretManagerMock.awaitRemoteSecretAndClear() } returns REMOTE_SECRET

        every { storageStateHolderMock.getStorageState() } returns MasterKeyState.Plain(MasterKeyData(MASTER_KEY))

        val deferredEvent = async(UnconfinedTestDispatcher()) {
            masterKeyManager.events.first()
        }

        masterKeyManager.updateRemoteSecretProtectionStateIfNeeded(clientParametersMock)

        coVerifyOrder {
            storageStateHolderMock.setStateWithoutRemoteSecretProtection(
                masterKeyData = MasterKeyData(MASTER_KEY),
                passphrase = PASSPHRASE,
            )
            storageManagerMock.writeKey(any())
            lockStateHolderMock.setUnlocked(
                masterKey = match { it.value.contentEquals(MASTER_KEY) },
                remoteSecretLockData = null,
            )
        }
        verify(exactly = 1) {
            passphraseStoreMock.passphrase = null
        }
        assertEquals(MasterKeyEvent.RemoteSecretDeactivated(authenticationTokenMock), deferredEvent.await())
    }

    @Test
    fun `nothing happens when no change is needed`() = runTest {
        coEvery { lockStateHolderMock.awaitRemoteSecretLockState() } returns mockk {
            every { remoteSecretLockData } returns null
        }
        every {
            remoteSecretManagerMock.checkRemoteSecretProtection(any())
        } returns RemoteSecretProtectionCheckResult.NO_CHANGE_NEEDED

        verify(exactly = 1) {
            // setUnlocked is called once initially
            lockStateHolderMock.setUnlocked(any(), remoteSecretLockData = null)
        }
        verify(exactly = 0) {
            storageStateHolderMock.setStateWithRemoteSecretProtection(any(), any(), any(), any())
        }
        verify(exactly = 0) {
            storageStateHolderMock.setStateWithoutRemoteSecretProtection(any(), any())
        }
        verify(exactly = 0) {
            storageManagerMock.writeKey(any())
        }
    }

    @Test
    fun `unlocking with remote secret`() = runTest {
        val parametersMock = mockk<RemoteSecretParameters>()
        val remoteSecretMock = mockk<RemoteSecret>()
        val lockData = MasterKeyState.WithRemoteSecret(
            parameters = parametersMock,
            encryptedData = mockk(),
        )
        coEvery {
            lockStateHolderMock.awaitRemoteSecretLockState()
        } returns mockk {
            every { remoteSecretLockData } returns lockData
        }
        coEvery { remoteSecretManagerMock.awaitRemoteSecretAndClear() } returns remoteSecretMock
        every { crypto.decryptWithRemoteSecret(lockData, remoteSecretMock) } returns MasterKeyState.Plain(
            masterKeyData = MasterKeyData(MASTER_KEY),
        )

        masterKeyManager.unlockWithRemoteSecret()

        verify {
            lockStateHolderMock.setUnlocked(
                masterKey = MasterKeyImpl(MASTER_KEY),
                remoteSecretLockData = lockData,
            )
        }
    }

    @Test
    fun `nothing happens when trying to unlock with remote secret but no remote secret is in use`() = runTest {
        coEvery { lockStateHolderMock.awaitRemoteSecretLockState() } returns mockk {
            every { remoteSecretLockData } returns null
        }

        masterKeyManager.unlockWithRemoteSecret()

        coVerify(exactly = 0) { remoteSecretManagerMock.awaitRemoteSecretAndClear() }
    }

    @Test
    fun `monitoring remote secret`() = runTest {
        val remoteSecretParametersFlow = MutableStateFlow<RemoteSecretParameters?>(null)
        every { lockStateHolderMock.remoteSecretParametersFlow } returns remoteSecretParametersFlow
        coEvery { remoteSecretManagerMock.monitorRemoteSecret(any()) } coAnswers { awaitCancellation() }

        val job = launch {
            masterKeyManager.monitorRemoteSecret()
        }

        val parametersMock1 = mockk<RemoteSecretParameters>("mock1")
        remoteSecretParametersFlow.value = parametersMock1
        advanceUntilIdle()
        val parametersMock2 = mockk<RemoteSecretParameters>("mock2")
        remoteSecretParametersFlow.value = parametersMock2
        advanceUntilIdle()
        remoteSecretParametersFlow.value = null
        advanceUntilIdle()
        val parametersMock3 = mockk<RemoteSecretParameters>("mock3")
        remoteSecretParametersFlow.value = parametersMock3
        val parametersMock4 = mockk<RemoteSecretParameters>("mock4")
        remoteSecretParametersFlow.value = parametersMock4
        advanceUntilIdle()

        coVerifyOrder {
            remoteSecretManagerMock.monitorRemoteSecret(parametersMock1)
            remoteSecretManagerMock.monitorRemoteSecret(parametersMock2)
            remoteSecretManagerMock.monitorRemoteSecret(parametersMock4)
        }
        coVerify(exactly = 0) {
            remoteSecretManagerMock.monitorRemoteSecret(parametersMock3)
        }

        job.cancel()
    }

    @Test
    fun `locking with remote secret when no passphrase is set`() {
        val remoteSecretLockDataMock = mockk<MasterKeyState.WithRemoteSecret>()
        val lockStateHolder = mockk<MasterKeyLockStateHolder>(relaxed = true) {
            every { getRemoteSecretLockState() } returns mockk {
                every { remoteSecretLockData } returns remoteSecretLockDataMock
            }
        }
        val masterKeyManager = createMasterKeyManagerWithStorageState(
            lockStateHolder = lockStateHolder,
            storageState = remoteSecretLockDataMock,
        )

        masterKeyManager.lockWithRemoteSecret()

        verify { lockStateHolder.setLockedWithRemoteSecret(remoteSecretLockDataMock) }
    }

    @Test
    fun `locking with remote secret when a passphrase is set`() {
        val remoteSecretLockDataMock = mockk<MasterKeyState.WithRemoteSecret>()
        val lockStateHolder = mockk<MasterKeyLockStateHolder>(relaxed = true) {
            every { getRemoteSecretLockState() } returns mockk {
                every { remoteSecretLockData } returns remoteSecretLockDataMock
            }
        }
        val lockData = mockk<MasterKeyState.WithPassphrase>()
        val passphraseStore = mockk<PassphraseStore>(relaxed = true)
        val masterKeyManager = createMasterKeyManagerWithStorageState(
            lockStateHolder = lockStateHolder,
            passphraseStore = passphraseStore,
            storageState = lockData,
        )

        masterKeyManager.lockWithRemoteSecret()

        verify { lockStateHolder.setLockedWithRemoteSecret(remoteSecretLockDataMock) }
        verify { passphraseStore.passphrase = null }
    }

    private fun createMasterKeyManagerWithStorageState(
        lockStateHolder: MasterKeyLockStateHolder,
        passphraseStore: PassphraseStore = PassphraseStore(),
        storageState: MasterKeyState,
    ): MasterKeyManagerImpl {
        return MasterKeyManagerImpl(
            lockStateHolder = lockStateHolder,
            keyStorageManager = mockk(),
            remoteSecretManager = mockk(),
            passphraseStore = passphraseStore,
            storageStateHolder = mockk {
                every { getStorageState() } returns storageState
            },
        )
    }

    companion object {
        private val REMOTE_SECRET = mockk<RemoteSecret>()
        private val PASSPHRASE = "passphrase".toCharArray()
    }
}
