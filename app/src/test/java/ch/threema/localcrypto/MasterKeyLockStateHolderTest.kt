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
import ch.threema.localcrypto.MasterKeyTestData.MASTER_KEY
import ch.threema.localcrypto.models.MasterKeyState
import ch.threema.localcrypto.models.RemoteSecretParameters
import ch.threema.testhelpers.assertSuspendsForever
import ch.threema.testhelpers.expectItem
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class MasterKeyLockStateHolderTest {

    @Test
    fun `initial lock state is unknown and can not be checked`() {
        val masterKeyLockStateHolder = MasterKeyLockStateHolder()

        assertFailsWith<IllegalStateException> {
            assertFalse(masterKeyLockStateHolder.isLockedWithPassphrase())
        }
        assertNull(masterKeyLockStateHolder.masterKeyFlow.value)
        assertNull(masterKeyLockStateHolder.getMasterKey())
        assertNull(masterKeyLockStateHolder.remoteSecretParametersFlow.value)
        assertFalse(masterKeyLockStateHolder.passphraseLockedFlow.value)
    }

    @Test
    fun `locking with passphrase`() = runTest {
        val withPassphraseMock = mockk<MasterKeyState.WithPassphrase>()
        val masterKeyLockStateHolder = MasterKeyLockStateHolder()
        masterKeyLockStateHolder.setLockedWithPassphrase(lockData = withPassphraseMock)

        assertTrue(masterKeyLockStateHolder.isLockedWithPassphrase())
        assertSuspendsForever {
            masterKeyLockStateHolder.isLockedWithRemoteSecret()
        }
        assertEquals(withPassphraseMock, masterKeyLockStateHolder.getPassphraseLock())
        assertNull(masterKeyLockStateHolder.masterKeyFlow.value)
        assertNull(masterKeyLockStateHolder.getMasterKey())
        assertNull(masterKeyLockStateHolder.remoteSecretParametersFlow.value)
        assertTrue(masterKeyLockStateHolder.passphraseLockedFlow.value)
    }

    @Test
    fun `locking with remote secret`() = runTest {
        val parametersMock = mockk<RemoteSecretParameters>()
        val masterKeyLockStateHolder = MasterKeyLockStateHolder()
        masterKeyLockStateHolder.setLockedWithRemoteSecret(
            lockData = MasterKeyState.WithRemoteSecret(
                parameters = parametersMock,
                encryptedData = mockk(),
            ),
        )

        assertFalse(masterKeyLockStateHolder.isLockedWithPassphrase())
        assertTrue(masterKeyLockStateHolder.isLockedWithRemoteSecret())
        assertNull(masterKeyLockStateHolder.getPassphraseLock())
        assertNull(masterKeyLockStateHolder.masterKeyFlow.value)
        assertNull(masterKeyLockStateHolder.getMasterKey())
        assertEquals(parametersMock, masterKeyLockStateHolder.remoteSecretParametersFlow.value)
        assertFalse(masterKeyLockStateHolder.passphraseLockedFlow.value)
    }

    @Test
    fun `unlocking with master key and no remote secret lock data`() = runTest {
        val masterKey = mockk<InvalidateableMasterKey>()
        val masterKeyLockStateHolder = MasterKeyLockStateHolder()
        masterKeyLockStateHolder.setUnlocked(masterKey, remoteSecretLockData = null)

        assertFalse(masterKeyLockStateHolder.isLockedWithPassphrase())
        assertFalse(masterKeyLockStateHolder.isLockedWithRemoteSecret())
        assertNull(masterKeyLockStateHolder.getPassphraseLock())
        assertSame(masterKey, masterKeyLockStateHolder.masterKeyFlow.value)
        assertSame(masterKey, masterKeyLockStateHolder.getMasterKey())
        assertNull(masterKeyLockStateHolder.remoteSecretParametersFlow.value)
        assertFalse(masterKeyLockStateHolder.passphraseLockedFlow.value)
    }

    @Test
    fun `unlocking with master key and remote secret lock data`() = runTest {
        val masterKey = mockk<InvalidateableMasterKey>()
        val masterKeyLockStateHolder = MasterKeyLockStateHolder()
        val withRemoteSecret = MasterKeyState.WithRemoteSecret(
            parameters = mockk(),
            encryptedData = mockk(),
        )
        masterKeyLockStateHolder.setUnlocked(masterKey, remoteSecretLockData = withRemoteSecret)

        assertFalse(masterKeyLockStateHolder.isLockedWithPassphrase())
        assertFalse(masterKeyLockStateHolder.isLockedWithRemoteSecret())
        assertNull(masterKeyLockStateHolder.getPassphraseLock())
        assertSame(masterKey, masterKeyLockStateHolder.masterKeyFlow.value)
        assertSame(masterKey, masterKeyLockStateHolder.getMasterKey())
        assertEquals(withRemoteSecret.parameters, masterKeyLockStateHolder.remoteSecretParametersFlow.value)
        assertFalse(masterKeyLockStateHolder.passphraseLockedFlow.value)
    }

    @Test
    fun `master key is returned when unlocked and invalidated when locked`() = runTest {
        val masterKey = MasterKeyImpl(MASTER_KEY.copyOf())
        val masterKeyLockStateHolder = MasterKeyLockStateHolder()

        masterKeyLockStateHolder.masterKeyFlow.test {
            expectItem(null)

            masterKeyLockStateHolder.setLockedWithPassphrase(lockData = mockk<MasterKeyState.WithPassphrase>())
            expectNoEvents()

            masterKeyLockStateHolder.setUnlocked(masterKey, remoteSecretLockData = null)
            val receivedMasterKey = awaitItem()
            assertContentEquals(masterKey.value, receivedMasterKey!!.value)

            masterKeyLockStateHolder.setLockedWithPassphrase(lockData = mockk<MasterKeyState.WithPassphrase>())
            expectItem(null)
            assertFalse(masterKey.isValid())
        }
    }

    @Test
    fun `permanently locking, can not be unlocked`() {
        val masterKey = MasterKeyImpl(MASTER_KEY.copyOf())
        val masterKeyLockStateHolder = MasterKeyLockStateHolder()
        masterKeyLockStateHolder.setUnlocked(masterKey, remoteSecretLockData = null)

        masterKeyLockStateHolder.setPermanentlyLocked()

        assertNull(masterKeyLockStateHolder.masterKeyFlow.value)

        assertFailsWith<IllegalStateException> {
            masterKeyLockStateHolder.setUnlocked(mockk(), remoteSecretLockData = null)
        }
    }

    @Test
    fun `await remote secret lock suspends if not unlocked`() = runTest {
        val masterKeyLockStateHolder = MasterKeyLockStateHolder()

        assertSuspendsForever {
            masterKeyLockStateHolder.awaitRemoteSecretLockState()
        }
    }

    @Test
    fun `await remote secret lock suspends if locked with passphrase`() = runTest {
        val masterKeyLockStateHolder = MasterKeyLockStateHolder()
        masterKeyLockStateHolder.setLockedWithPassphrase(mockk<MasterKeyState.WithPassphrase>())

        assertSuspendsForever {
            masterKeyLockStateHolder.awaitRemoteSecretLockState()
        }
    }

    @Test
    fun `await remote secret lock data returns null if unlocked and no remote secret is used`() = runTest {
        val masterKeyLockStateHolder = MasterKeyLockStateHolder()
        masterKeyLockStateHolder.setUnlocked(mockk(), remoteSecretLockData = null)

        assertNull(masterKeyLockStateHolder.awaitRemoteSecretLockState().remoteSecretLockData)
    }

    @Test
    fun `await remote secret lock returns remote secret lock when locked with remote secret`() = runTest {
        val withRemoteSecretMock = mockk<MasterKeyState.WithRemoteSecret>()
        val masterKeyLockStateHolder = MasterKeyLockStateHolder()
        masterKeyLockStateHolder.setLockedWithRemoteSecret(withRemoteSecretMock)

        assertEquals(withRemoteSecretMock, masterKeyLockStateHolder.awaitRemoteSecretLockState().remoteSecretLockData)
    }

    @Test
    fun `await remote secret lock returns remote secret lock when unlocked and remote secret is used`() = runTest {
        val withRemoteSecretMock = mockk<MasterKeyState.WithRemoteSecret>()
        val masterKeyLockStateHolder = MasterKeyLockStateHolder()
        masterKeyLockStateHolder.setUnlocked(mockk(), remoteSecretLockData = withRemoteSecretMock)

        assertEquals(withRemoteSecretMock, masterKeyLockStateHolder.awaitRemoteSecretLockState().remoteSecretLockData)
    }
}
