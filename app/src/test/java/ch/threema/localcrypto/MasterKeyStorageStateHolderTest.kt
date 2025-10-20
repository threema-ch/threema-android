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
import ch.threema.localcrypto.exceptions.PassphraseRequiredException
import ch.threema.localcrypto.models.MasterKeyData
import ch.threema.localcrypto.models.MasterKeyState
import ch.threema.localcrypto.models.RemoteSecret
import ch.threema.localcrypto.models.RemoteSecretParameters
import ch.threema.testhelpers.assertSuspendsForever
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class MasterKeyStorageStateHolderTest {
    @Test
    fun `cannot access storage data before init`() {
        val storageStateHolder = MasterKeyStorageStateHolder(
            crypto = mockk(),
        )

        assertFailsWith<IllegalStateException> {
            storageStateHolder.getStorageState()
        }
    }

    @Test
    fun `init with unprotected `() {
        val keyState = MasterKeyState.Plain(
            masterKeyData = MasterKeyData(MasterKeyTestData.MASTER_KEY),
        )
        val storageStateHolder = MasterKeyStorageStateHolder(
            crypto = mockk(),
        )

        storageStateHolder.init(keyState)

        assertEquals(keyState, storageStateHolder.getStorageState())
    }

    @Test
    fun `isProtected suspends if init not called`() = runTest {
        val storageStateHolder = MasterKeyStorageStateHolder(
            crypto = mockk(),
        )

        assertSuspendsForever {
            storageStateHolder.isProtected()
        }
    }

    @Test
    fun `isProtected returns false if no protection is used`() = runTest {
        val storageStateHolder = MasterKeyStorageStateHolder(
            crypto = mockk(),
        )

        storageStateHolder.init(mockk<MasterKeyState.Plain>())

        assertFalse(storageStateHolder.isProtected())
    }

    @Test
    fun `isProtected returns true if passphrase is used`() = runTest {
        val storageStateHolder = MasterKeyStorageStateHolder(
            crypto = mockk(),
        )

        storageStateHolder.init(mockk<MasterKeyState.WithPassphrase>())

        assertTrue(storageStateHolder.isProtected())
    }

    @Test
    fun `adding passphrase protection`() = runTest {
        val plainMock = mockk<MasterKeyState.Plain>()
        val withPassphraseMock = mockk<MasterKeyState.WithPassphrase>()
        val storageStateHolder = MasterKeyStorageStateHolder(
            crypto = mockk {
                every {
                    encryptWithPassphrase(
                        keyState = plainMock,
                        passphrase = PASSPHRASE,
                    )
                } returns withPassphraseMock
            },
        )
        storageStateHolder.init(plainMock)

        storageStateHolder.addPassphraseProtection(PASSPHRASE)

        assertEquals(withPassphraseMock, storageStateHolder.getStorageState())
        assertTrue(storageStateHolder.isProtected())
    }

    @Test
    fun `cannot add passphrase protection if already passphrase protected`() = runTest {
        val storageStateHolder = MasterKeyStorageStateHolder(
            crypto = mockk(),
        )
        storageStateHolder.init(mockk<MasterKeyState.WithPassphrase>())

        assertFailsWith<IllegalStateException> {
            storageStateHolder.addPassphraseProtection(PASSPHRASE)
        }
    }

    @Test
    fun `removing version 2 passphrase protection`() = runTest {
        val plainMock = mockk<MasterKeyState.Plain>()
        val withPassphraseMock = mockk<MasterKeyState.WithPassphrase>()
        val storageStateHolder = MasterKeyStorageStateHolder(
            crypto = mockk {
                every {
                    decryptWithPassphrase(
                        keyState = withPassphraseMock,
                        passphrase = PASSPHRASE,
                    )
                } returns plainMock
            },
        )
        storageStateHolder.init(withPassphraseMock)

        storageStateHolder.removePassphraseProtection(PASSPHRASE)

        assertEquals(plainMock, storageStateHolder.getStorageState())
        assertFalse(storageStateHolder.isProtected())
    }

    @Test
    fun `adding remote secret protection to unprotected key`() = runTest {
        val plain = MasterKeyState.Plain(MasterKeyData(MASTER_KEY))
        val withRemoteSecretMock = mockk<MasterKeyState.WithRemoteSecret>()
        val parametersMock = mockk<RemoteSecretParameters>()
        val storageStateHolder = MasterKeyStorageStateHolder(
            crypto = mockk {
                every {
                    encryptWithRemoteSecret(
                        keyState = plain,
                        remoteSecret = REMOTE_SECRET,
                        parameters = parametersMock,
                    )
                } returns withRemoteSecretMock
            },
        )
        storageStateHolder.init(plain)

        storageStateHolder.setStateWithRemoteSecretProtection(
            masterKeyData = MasterKeyData(MASTER_KEY),
            passphrase = null,
            remoteSecret = REMOTE_SECRET,
            parameters = parametersMock,
        )

        assertEquals(withRemoteSecretMock, storageStateHolder.getStorageState())
        assertTrue(storageStateHolder.isProtected())
    }

    @Test
    fun `adding remote secret protection to passphrase protection`() = runTest {
        val withPassphrase1Mock = mockk<MasterKeyState.WithPassphrase>()
        val withPassphrase2Mock = mockk<MasterKeyState.WithPassphrase>()
        val withRemoteSecretMock = mockk<MasterKeyState.WithRemoteSecret>()
        val parametersMock = mockk<RemoteSecretParameters>()
        val plain = MasterKeyState.Plain(MasterKeyData(MASTER_KEY))
        val storageStateHolder = MasterKeyStorageStateHolder(
            crypto = mockk {
                every {
                    encryptWithRemoteSecret(
                        keyState = plain,
                        remoteSecret = REMOTE_SECRET,
                        parameters = parametersMock,
                    )
                } returns withRemoteSecretMock
                every {
                    encryptWithPassphrase(
                        keyState = withRemoteSecretMock,
                        passphrase = PASSPHRASE,
                    )
                } returns withPassphrase2Mock
            },
        )
        storageStateHolder.init(withPassphrase1Mock)

        storageStateHolder.setStateWithRemoteSecretProtection(
            masterKeyData = MasterKeyData(MASTER_KEY),
            passphrase = PASSPHRASE,
            remoteSecret = REMOTE_SECRET,
            parameters = parametersMock,
        )

        assertEquals(withPassphrase2Mock, storageStateHolder.getStorageState())
        assertTrue(storageStateHolder.isProtected())
    }

    @Test
    fun `cannot add remote secret protection without providing passphrase when passphrase protection is active`() = runTest {
        val storageStateHolder = MasterKeyStorageStateHolder(
            crypto = mockk(),
        )
        storageStateHolder.init(mockk<MasterKeyState.WithPassphrase>())

        assertFailsWith<PassphraseRequiredException> {
            storageStateHolder.setStateWithRemoteSecretProtection(
                masterKeyData = MasterKeyData(MASTER_KEY),
                passphrase = null,
                remoteSecret = REMOTE_SECRET,
                parameters = mockk(),
            )
        }
    }

    @Test
    fun `removing remote secret protection from not-passphrase protected key`() = runTest {
        val plain = MasterKeyState.Plain(MasterKeyData(MASTER_KEY))
        val withRemoteSecretMock = mockk<MasterKeyState.WithRemoteSecret>()
        val storageStateHolder = MasterKeyStorageStateHolder(
            crypto = mockk {
                every {
                    decryptWithRemoteSecret(
                        keyState = withRemoteSecretMock,
                        remoteSecret = REMOTE_SECRET,
                    )
                } returns plain
            },
        )
        storageStateHolder.init(withRemoteSecretMock)

        storageStateHolder.setStateWithoutRemoteSecretProtection(
            masterKeyData = MasterKeyData(MASTER_KEY),
            passphrase = null,
        )

        assertEquals(plain, storageStateHolder.getStorageState())
        assertFalse(storageStateHolder.isProtected())
    }

    @Test
    fun `removing remote secret protection from passphrase protected key`() = runTest {
        val withPassphrase1Mock = mockk<MasterKeyState.WithPassphrase>()
        val withPassphrase2Mock = mockk<MasterKeyState.WithPassphrase>()
        val plain = MasterKeyState.Plain(MasterKeyData(MASTER_KEY))
        val storageStateHolder = MasterKeyStorageStateHolder(
            crypto = mockk {
                every {
                    encryptWithPassphrase(
                        keyState = plain,
                        passphrase = PASSPHRASE,
                    )
                } returns withPassphrase2Mock
            },
        )
        storageStateHolder.init(withPassphrase1Mock)

        storageStateHolder.setStateWithoutRemoteSecretProtection(
            masterKeyData = MasterKeyData(MASTER_KEY),
            passphrase = PASSPHRASE,
        )

        assertEquals(withPassphrase2Mock, storageStateHolder.getStorageState())
        assertTrue(storageStateHolder.isProtected())
    }

    @Test
    fun `cannot remove remote secret without providing passphrase when passphrase protection is active`() = runTest {
        val storageStateHolder = MasterKeyStorageStateHolder(
            crypto = mockk(),
        )
        storageStateHolder.init(mockk<MasterKeyState.WithPassphrase>())

        assertFailsWith<PassphraseRequiredException> {
            storageStateHolder.setStateWithoutRemoteSecretProtection(
                masterKeyData = MasterKeyData(MASTER_KEY),
                passphrase = null,
            )
        }
    }

    companion object {
        private val PASSPHRASE = "passphrase".toCharArray()
        private val REMOTE_SECRET = RemoteSecret("remote-secret".toByteArray())
    }
}
