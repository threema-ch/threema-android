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

import ch.threema.common.models.CryptographicByteArray
import ch.threema.localcrypto.MasterKeyTestData.MASTER_KEY
import ch.threema.localcrypto.exceptions.CryptoException
import ch.threema.localcrypto.models.MasterKeyData
import ch.threema.localcrypto.models.MasterKeyState
import ch.threema.localcrypto.models.Version2MasterKeyStorageInnerData
import ch.threema.localcrypto.models.Version2MasterKeyStorageOuterData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MasterKeyCryptoTest {
    @Test
    fun `verify passphrase version 1`() {
        val verificationMock = mockk<CryptographicByteArray>()
        val protectionMock = mockk<MasterKeyState.WithPassphrase.PassphraseProtection.Version1> {
            every { verification } returns verificationMock
        }
        val crypto = MasterKeyCrypto(
            version1Crypto = mockk {
                every {
                    decryptPassphraseProtectedMasterKey(protectionMock, PASSPHRASE)
                } returns MasterKeyData(MASTER_KEY)
                every {
                    checkVerification(MasterKeyData(MASTER_KEY), verificationMock)
                } returns true
            },
            converter = mockk(),
            version2Crypto = mockk(),
        )

        val verified = crypto.verifyPassphrase(
            keyState = MasterKeyState.WithPassphrase(
                protection = protectionMock,
            ),
            passphrase = PASSPHRASE,
        )

        assertTrue(verified)
    }

    @Test
    fun `verify passphrase version 2`() {
        val outerDataMock = mockk<Version2MasterKeyStorageOuterData.PassphraseProtected>()
        val crypto = MasterKeyCrypto(
            version2Crypto = mockk {
                every { decryptWithPassphrase(outerDataMock, PASSPHRASE) } returns mockk()
            },
            converter = mockk(),
            version1Crypto = mockk(),
        )

        val verified = crypto.verifyPassphrase(
            keyState = MasterKeyState.WithPassphrase(
                protection = mockk<MasterKeyState.WithPassphrase.PassphraseProtection.Version2> {
                    every { toOuterData() } returns outerDataMock
                },
            ),
            passphrase = PASSPHRASE,
        )

        assertTrue(verified)
    }

    @Test
    fun `verify invalid passphrase version 2`() {
        val outerDataMock = mockk<Version2MasterKeyStorageOuterData.PassphraseProtected>()
        val crypto = MasterKeyCrypto(
            version2Crypto = mockk {
                every {
                    decryptWithPassphrase(outerDataMock, PASSPHRASE)
                } answers { throw CryptoException() }
            },
            converter = mockk(),
            version1Crypto = mockk(),
        )

        val verified = crypto.verifyPassphrase(
            keyState = MasterKeyState.WithPassphrase(
                protection = mockk<MasterKeyState.WithPassphrase.PassphraseProtection.Version2> {
                    every { toOuterData() } returns outerDataMock
                },
            ),
            passphrase = PASSPHRASE,
        )

        assertFalse(verified)
    }

    @Test
    fun `decrypt with passphrase version 1`() {
        val verificationMock = mockk<CryptographicByteArray>()
        val protectionMock = mockk<MasterKeyState.WithPassphrase.PassphraseProtection.Version1> {
            every { verification } returns verificationMock
        }
        val masterKeyData = MasterKeyData(MASTER_KEY)
        val version1CryptoMock = mockk<Version1MasterKeyCrypto> {
            every {
                checkVerification(
                    masterKeyData = masterKeyData,
                    verification = verificationMock,
                )
            } returns true
            every {
                decryptPassphraseProtectedMasterKey(protectionMock, PASSPHRASE)
            } returns masterKeyData
        }
        val crypto = MasterKeyCrypto(
            version1Crypto = version1CryptoMock,
            converter = mockk(),
            version2Crypto = mockk(),
        )

        val withoutPassphrase = crypto.decryptWithPassphrase(
            keyState = MasterKeyState.WithPassphrase(
                protection = protectionMock,
            ),
            passphrase = PASSPHRASE,
        )

        assertEquals(MasterKeyState.Plain(masterKeyData, wasMigrated = true), withoutPassphrase)
        verify(exactly = 1) {
            version1CryptoMock.checkVerification(masterKeyData, verificationMock)
        }
    }

    @Test
    fun `decrypt with passphrase version 2`() {
        val plainMock = mockk<MasterKeyState.Plain>()
        val innerDataMock = mockk<Version2MasterKeyStorageInnerData.Unprotected>()
        val outerDataMock = mockk<Version2MasterKeyStorageOuterData.PassphraseProtected>()
        val crypto = MasterKeyCrypto(
            version2Crypto = mockk {
                every { decryptWithPassphrase(outerDataMock, PASSPHRASE) } returns innerDataMock
            },
            converter = mockk {
                every { toKeyState(innerDataMock) } returns plainMock
            },
            version1Crypto = mockk(),
        )

        val withoutPassphrase = crypto.decryptWithPassphrase(
            keyState = MasterKeyState.WithPassphrase(
                protection = mockk<MasterKeyState.WithPassphrase.PassphraseProtection.Version2> {
                    every { toOuterData() } returns outerDataMock
                },
            ),
            passphrase = PASSPHRASE,
        )

        assertEquals(plainMock, withoutPassphrase)
    }

    companion object {
        private val PASSPHRASE = "superSecretPassword".toCharArray()
    }
}
