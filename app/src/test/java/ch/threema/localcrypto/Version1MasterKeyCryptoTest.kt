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

import ch.threema.common.toCryptographicByteArray
import ch.threema.localcrypto.MasterKeyTestData.MASTER_KEY
import ch.threema.localcrypto.models.MasterKeyData
import ch.threema.localcrypto.models.MasterKeyState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Version1MasterKeyCryptoTest {
    @Test
    fun `check verification, is equal`() {
        assertTrue(
            Version1MasterKeyCrypto().checkVerification(
                masterKeyData = MasterKeyData(MASTER_KEY),
                verification = MasterKeyTestData.Version1.VERIFICATION.toCryptographicByteArray(),
            ),
        )
    }

    @Test
    fun `check verification, is not equal`() {
        val invalid = MasterKeyTestData.Version1.VERIFICATION.copyOf()
        invalid[3] = 123
        assertFalse(
            Version1MasterKeyCrypto().checkVerification(
                masterKeyData = MasterKeyData(MASTER_KEY),
                verification = invalid.toCryptographicByteArray(),
            ),
        )
    }

    @Test
    fun `decrypt passphrase`() {
        val masterKeyData = Version1MasterKeyCrypto().decryptPassphraseProtectedMasterKey(
            protection = MasterKeyState.WithPassphrase.PassphraseProtection.Version1(
                protectedKey = MasterKeyTestData.Version1.PROTECTED_KEY.toCryptographicByteArray(),
                salt = MasterKeyTestData.Version1.SALT.toCryptographicByteArray(),
                verification = MasterKeyTestData.Version1.VERIFICATION.toCryptographicByteArray(),
            ),
            passphrase = "superSecretPassword".toCharArray(),
        )

        assertEquals(MasterKeyData(MASTER_KEY), masterKeyData)
    }
}
