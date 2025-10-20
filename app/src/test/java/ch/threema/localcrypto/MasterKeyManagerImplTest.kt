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

import app.cash.turbine.test
import ch.threema.common.stateFlowOf
import ch.threema.common.toCryptographicByteArray
import ch.threema.localcrypto.MasterKeyTestData.MASTER_KEY
import ch.threema.localcrypto.exceptions.MasterKeyLockedException
import ch.threema.localcrypto.models.Argon2Version
import ch.threema.localcrypto.models.MasterKeyData
import ch.threema.localcrypto.models.MasterKeyState
import ch.threema.localcrypto.models.PassphraseLockState
import ch.threema.localcrypto.models.RemoteSecretProtectionCheckResult
import ch.threema.testhelpers.assertSuspendsForever
import ch.threema.testhelpers.expectItem
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class MasterKeyManagerImplTest {

    private lateinit var masterKeyStorageManagerMock: MasterKeyStorageManager
    private lateinit var masterKeyGeneratorMock: MasterKeyGenerator
    private lateinit var masterKeyManager: MasterKeyManagerImpl
    private lateinit var passphraseStore: PassphraseStore
    private lateinit var cryptoMock: MasterKeyCrypto
    private lateinit var lockStateHolder: MasterKeyLockStateHolder

    @BeforeTest
    fun setUp() {
        masterKeyStorageManagerMock = mockk {
            every { writeKey(any()) } just runs
        }
        cryptoMock = mockk()
        every { cryptoMock.verifyPassphrase(any(), any()) } returns true
        masterKeyGeneratorMock = mockk()
        lockStateHolder = spyk(MasterKeyLockStateHolder())
        passphraseStore = spyk(PassphraseStore())
        masterKeyManager = MasterKeyManagerImpl(
            keyStorageManager = masterKeyStorageManagerMock,
            keyGenerator = masterKeyGeneratorMock,
            lockStateHolder = lockStateHolder,
            passphraseStore = passphraseStore,
            crypto = cryptoMock,
            remoteSecretManager = mockk(),
            random = mockk(),
        )
    }

    @Test
    fun `init from existing version 2 key file without passphrase`() = runTest {
        val masterKeyData = MasterKeyData(ByteArray(MasterKeyConfig.KEY_LENGTH))
        every { masterKeyStorageManagerMock.keyExists() } returns true
        every { masterKeyStorageManagerMock.readKey() } returns MasterKeyState.Plain(
            masterKeyData = masterKeyData,
        )

        masterKeyManager.readOrGenerateKey()
        masterKeyManager.persistKeyDataIfNeeded()

        assertFalse(masterKeyManager.isProtected())
        assertFalse(masterKeyManager.isProtectedWithPassphrase())
        assertFalse(masterKeyManager.isLockedWithPassphrase())
        assertFalse(masterKeyManager.isLockedWithRemoteSecret())
        assertContentEquals(masterKeyData.value, masterKeyManager.masterKeyProvider.getMasterKey().value)
        verify(exactly = 0) { masterKeyStorageManagerMock.writeKey(any()) }
    }

    @Test
    fun `init from existing version 1 key file without passphrase`() = runTest {
        val masterKeyData = MasterKeyData(ByteArray(MasterKeyConfig.KEY_LENGTH))
        every { masterKeyStorageManagerMock.keyExists() } returns true
        every { masterKeyStorageManagerMock.readKey() } returns MasterKeyState.Plain(
            masterKeyData = masterKeyData,
            wasMigrated = true,
        )

        masterKeyManager.readOrGenerateKey()
        masterKeyManager.persistKeyDataIfNeeded()

        assertFalse(masterKeyManager.isProtectedWithPassphrase())
        assertFalse(masterKeyManager.isLockedWithPassphrase())
        assertFalse(masterKeyManager.isLockedWithRemoteSecret())
        assertContentEquals(masterKeyData.value, masterKeyManager.masterKeyProvider.getMasterKey().value)

        // Migration to version 2
        verify(exactly = 1) {
            masterKeyStorageManagerMock.writeKey(MasterKeyState.Plain(masterKeyData, wasMigrated = true))
        }
    }

    @Test
    fun `init from existing version 2 key file with passphrase`() = runTest {
        every { masterKeyStorageManagerMock.keyExists() } returns true
        every { masterKeyStorageManagerMock.readKey() } returns MasterKeyState.WithPassphrase(
            protection = MasterKeyState.WithPassphrase.PassphraseProtection.Version2(
                encryptedData = ByteArray(123).toCryptographicByteArray(),
                nonce = ByteArray(MasterKeyConfig.NONCE_LENGTH).toCryptographicByteArray(),
                argonVersion = Argon2Version.VERSION_1_3,
                salt = ByteArray(MasterKeyConfig.ARGON2_SALT_LENGTH).toCryptographicByteArray(),
                memoryBytes = MasterKeyConfig.ARGON2_MEMORY_BYTES,
                iterations = MasterKeyConfig.ARGON2_ITERATIONS,
                parallelism = MasterKeyConfig.ARGON2_PARALLELIZATION,
            ),
        )

        masterKeyManager.readOrGenerateKey()
        masterKeyManager.persistKeyDataIfNeeded()

        assertTrue(masterKeyManager.isProtectedWithPassphrase())
        assertTrue(masterKeyManager.isLockedWithPassphrase())
        assertSuspendsForever {
            masterKeyManager.isLockedWithRemoteSecret()
        }
        assertNull(masterKeyManager.masterKeyProvider.getMasterKeyOrNull())
        verify(exactly = 0) { masterKeyStorageManagerMock.writeKey(any()) }
    }

    @Test
    fun `init from existing version 1 key file with passphrase`() = runTest {
        val masterKeyData = ByteArray(MasterKeyConfig.KEY_LENGTH)
        every { masterKeyStorageManagerMock.keyExists() } returns true
        every { masterKeyStorageManagerMock.readKey() } returns MasterKeyState.WithPassphrase(
            protection = MasterKeyState.WithPassphrase.PassphraseProtection.Version1(
                protectedKey = masterKeyData.toCryptographicByteArray(),
                salt = ByteArray(8).toCryptographicByteArray(),
                verification = ByteArray(MasterKeyConfig.VERSION1_VERIFICATION_LENGTH).toCryptographicByteArray(),
            ),
        )

        masterKeyManager.readOrGenerateKey()
        masterKeyManager.persistKeyDataIfNeeded()

        assertTrue(masterKeyManager.isProtected())
        assertTrue(masterKeyManager.isProtectedWithPassphrase())
        assertTrue(masterKeyManager.isLockedWithPassphrase())
        assertSuspendsForever {
            masterKeyManager.isLockedWithRemoteSecret()
        }
        assertNull(masterKeyManager.masterKeyProvider.getMasterKeyOrNull())
        verify(exactly = 0) { masterKeyStorageManagerMock.writeKey(any()) }
    }

    @Test
    fun `init generates new key when no key file exists`() {
        val masterKeyData = ByteArray(MasterKeyConfig.KEY_LENGTH)
        every { masterKeyGeneratorMock.generate() } returns MasterKeyData(ByteArray(MasterKeyConfig.KEY_LENGTH))
        every { masterKeyStorageManagerMock.keyExists() } returns false

        masterKeyManager.readOrGenerateKey()
        masterKeyManager.persistKeyDataIfNeeded()

        assertFalse(masterKeyManager.isProtectedWithPassphrase())
        assertFalse(masterKeyManager.isLockedWithPassphrase())
        assertContentEquals(masterKeyData, masterKeyManager.masterKeyProvider.getMasterKey().value)
        verify(exactly = 1) { masterKeyStorageManagerMock.writeKey(any()) }
    }

    @Test
    fun `newly generated key is not written if not requested`() {
        every { masterKeyGeneratorMock.generate() } returns MasterKeyData(ByteArray(MasterKeyConfig.KEY_LENGTH))
        every { masterKeyStorageManagerMock.keyExists() } returns false
        every { masterKeyStorageManagerMock.writeKey(any()) } just runs

        masterKeyManager.readOrGenerateKey()

        verify(exactly = 0) { masterKeyStorageManagerMock.writeKey(any()) }
    }

    @Test
    fun `passphrase handling`() {
        val keyStorageManagerMock = mockk<MasterKeyStorageManager> {
            every { keyExists() } returns true
            every { readKey() } returns MasterKeyState.Plain(
                masterKeyData = MasterKeyData(MASTER_KEY.copyOf()),
            )
            every { writeKey(any()) } just runs
        }
        var storedPassphrase: CharArray? = null
        val passphraseStoreMock = mockk<PassphraseStore> {
            every { passphrase } answers { storedPassphrase }
            every { passphrase = any() } answers { storedPassphrase = firstArg() }
        }
        val masterKeyManager = MasterKeyManagerImpl(
            keyStorageManager = keyStorageManagerMock,
            remoteSecretManager = mockk {
                every { checkRemoteSecretProtection(any()) } returns RemoteSecretProtectionCheckResult.NO_CHANGE_NEEDED
            },
            passphraseStore = passphraseStoreMock,
        )
        masterKeyManager.readOrGenerateKey()

        // all passphrases are valid when there is no passphrase
        assertTrue(masterKeyManager.checkPassphrase("hello".toCharArray()))

        // locking does nothing when there is no passphrase set
        masterKeyManager.lockWithPassphrase()
        assertFalse(masterKeyManager.isLockedWithPassphrase())

        // setting a passphrase does not lock
        masterKeyManager.setPassphrase("hello".toCharArray(), oldPassphrase = null)
        assertFalse(masterKeyManager.isLockedWithPassphrase())
        assertFalse(masterKeyManager.masterKeyProvider.isLocked())
        verify(exactly = 1) {
            keyStorageManagerMock.writeKey(
                match { it is MasterKeyState.WithPassphrase },
            )
        }

        // locking when a passphrase is set causes the master key to be locked
        masterKeyManager.lockWithPassphrase()
        assertTrue(masterKeyManager.isLockedWithPassphrase())
        assertTrue(masterKeyManager.masterKeyProvider.isLocked())

        // the passphrase can not be changed when locked and old passphrase not provided
        assertFailsWith<MasterKeyLockedException> {
            masterKeyManager.setPassphrase("HELLO".toCharArray(), oldPassphrase = null)
        }

        // checking the passphrase does not unlock the master key
        assertTrue(masterKeyManager.checkPassphrase("hello".toCharArray()))
        assertTrue(masterKeyManager.isLockedWithPassphrase())
        assertFalse(masterKeyManager.checkPassphrase("HELLO".toCharArray()))

        // unlocking with the wrong passphrase fails
        assertFalse(masterKeyManager.unlockWithPassphrase("HELLO".toCharArray()))
        assertTrue(masterKeyManager.isLockedWithPassphrase())
        assertNull(storedPassphrase)

        // unlocking with the correct passphrase works
        assertTrue(masterKeyManager.unlockWithPassphrase("hello".toCharArray()))
        assertFalse(masterKeyManager.isLockedWithPassphrase())
        assertFalse(masterKeyManager.masterKeyProvider.isLocked())
        val masterKey = masterKeyManager.masterKeyProvider.getMasterKey()
        assertContentEquals(MASTER_KEY, masterKey.value)
        assertNull(storedPassphrase)

        // cannot change passphrase without providing old passphrase
        assertFails {
            masterKeyManager.setPassphrase("HELLO".toCharArray(), oldPassphrase = null)
        }

        // cannot change passphrase when wrong old passphrase is provided
        assertFails {
            masterKeyManager.setPassphrase("HELLO".toCharArray(), oldPassphrase = "wrong".toCharArray())
        }

        // changing the passphrase updates the key file but does not change the master key
        masterKeyManager.setPassphrase("HELLO".toCharArray(), oldPassphrase = "hello".toCharArray())
        val masterKey2 = masterKeyManager.masterKeyProvider.getMasterKey()
        assertEquals(masterKey2, masterKey)
        verify(exactly = 2) {
            keyStorageManagerMock.writeKey(
                match { it is MasterKeyState.WithPassphrase },
            )
        }
        assertNull(storedPassphrase)

        // locking with passphrase wipes the master key and passphrase from memory
        masterKeyManager.lockWithPassphrase()
        assertTrue(masterKeyManager.isLockedWithPassphrase())
        assertNull(masterKeyManager.masterKeyProvider.getMasterKeyOrNull())
        assertContentEquals(ByteArray(MasterKeyConfig.KEY_LENGTH), masterKey.value)
        assertNull(storedPassphrase)

        // the passphrase can be removed when unlocked
        assertTrue(masterKeyManager.unlockWithPassphrase("HELLO".toCharArray()))
        masterKeyManager.removePassphrase("HELLO".toCharArray())
        verify(exactly = 1) {
            keyStorageManagerMock.writeKey(MasterKeyState.Plain(MasterKeyData(MASTER_KEY)))
        }
        assertFalse(masterKeyManager.isProtectedWithPassphrase())
        assertNull(storedPassphrase)
    }

    @Test
    fun `passphrase is kept in store when remote secret needs to be activated`() {
        val keyStorageManagerMock = mockk<MasterKeyStorageManager> {
            every { keyExists() } returns true
            every { readKey() } returns MasterKeyState.Plain(
                masterKeyData = MasterKeyData(MASTER_KEY.copyOf()),
            )
            every { writeKey(any()) } just runs
        }
        var storedPassphrase: CharArray? = null
        val passphraseStoreMock = mockk<PassphraseStore> {
            every { passphrase } answers { storedPassphrase }
            every { passphrase = any() } answers { storedPassphrase = firstArg() }
        }
        val masterKeyManager = MasterKeyManagerImpl(
            keyStorageManager = keyStorageManagerMock,
            remoteSecretManager = mockk {
                every { checkRemoteSecretProtection(any()) } returns RemoteSecretProtectionCheckResult.SHOULD_ACTIVATE
            },
            passphraseStore = passphraseStoreMock,
        )
        masterKeyManager.readOrGenerateKey()
        masterKeyManager.setPassphrase("hello".toCharArray(), oldPassphrase = null)
        masterKeyManager.lockWithPassphrase()

        masterKeyManager.unlockWithPassphrase("hello".toCharArray())

        assertContentEquals("hello".toCharArray(), storedPassphrase)
    }

    @Test
    fun `key is migrated from version 1 to version 2 after passphrase unlocked`() {
        val passphrase = "passphrase".toCharArray()
        val passphraseVersion1 = MasterKeyState.WithPassphrase(
            protection = mockk<MasterKeyState.WithPassphrase.PassphraseProtection.Version1>(),
        )
        val plain = MasterKeyState.Plain(
            masterKeyData = MasterKeyData(MASTER_KEY),
            wasMigrated = true,
        )
        val passphraseVersion2 = mockk<MasterKeyState.WithPassphrase>()
        val masterKeyManager = MasterKeyManagerImpl(
            keyStorageManager = masterKeyStorageManagerMock,
            crypto = mockk<MasterKeyCrypto> {
                every { decryptWithPassphrase(keyState = passphraseVersion1, passphrase) } returns plain
                every { encryptWithPassphrase(keyState = plain, passphrase) } returns passphraseVersion2
                every { verifyPassphrase(any(), any()) } returns true
            },
            remoteSecretManager = mockk {
                every { checkRemoteSecretProtection(any()) } returns RemoteSecretProtectionCheckResult.NO_CHANGE_NEEDED
            },
            passphraseStore = mockk(relaxed = true),
        )
        every { masterKeyStorageManagerMock.keyExists() } returns true
        every { masterKeyStorageManagerMock.readKey() } returns passphraseVersion1

        masterKeyManager.readOrGenerateKey()
        masterKeyManager.unlockWithPassphrase(passphrase)

        verify(exactly = 1) {
            masterKeyStorageManagerMock.writeKey(passphraseVersion2)
        }
    }

    @Test
    fun `isProtected can be called before key is loaded`() = runTest(timeout = 3.seconds) {
        every { masterKeyStorageManagerMock.keyExists() } returns true
        every { masterKeyStorageManagerMock.readKey() } returns MasterKeyState.Plain(
            masterKeyData = MasterKeyData(ByteArray(MasterKeyConfig.KEY_LENGTH)),
        )

        val deferred = async {
            masterKeyManager.isProtected()
        }
        advanceUntilIdle()
        masterKeyManager.readOrGenerateKey()

        assertFalse(deferred.await())
    }

    @Test
    fun `locking permanently clears the passphrase and sets the lock state`() {
        masterKeyManager.lockPermanently()

        verify { lockStateHolder.setPermanentlyLocked() }
        verify { passphraseStore.passphrase = null }
    }

    @Test
    fun `observe passphrase lock state`() = runTest {
        val storageStateFlow = MutableStateFlow<MasterKeyState?>(null)
        val isLockedWithPassphraseFlow = MutableStateFlow(true)
        val masterKeyManager = MasterKeyManagerImpl(
            keyStorageManager = mockk(),
            remoteSecretManager = mockk(),
            storageStateHolder = mockk {
                every { observeStorageState() } returns storageStateFlow
            },
            lockStateHolder = mockk {
                every { masterKeyFlow } returns stateFlowOf(null)
                every { passphraseLockedFlow } returns isLockedWithPassphraseFlow
            },
        )

        masterKeyManager.passphraseLockState.test {
            // Initially, there is no passphrase lock known
            expectItem(PassphraseLockState.NO_PASSPHRASE)

            // passphrase protection is active, still locked
            storageStateFlow.value = mockk<MasterKeyState.WithPassphrase>()
            expectItem(PassphraseLockState.LOCKED)

            // passphrase is unlocked
            isLockedWithPassphraseFlow.value = false
            expectItem(PassphraseLockState.UNLOCKED)

            // passphrase is locked
            isLockedWithPassphraseFlow.value = true
            expectItem(PassphraseLockState.LOCKED)

            // passphrase is unlocked again
            isLockedWithPassphraseFlow.value = false
            expectItem(PassphraseLockState.UNLOCKED)

            // passphrase protection is removed
            storageStateFlow.value = mockk<MasterKeyState.Plain>()
            expectItem(PassphraseLockState.NO_PASSPHRASE)

            // adding remote secret protection does not change the passphrase lock state
            storageStateFlow.value = mockk<MasterKeyState.WithRemoteSecret>()
            expectNoEvents()
        }
    }
}
