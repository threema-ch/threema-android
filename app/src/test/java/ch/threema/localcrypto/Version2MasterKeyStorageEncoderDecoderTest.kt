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
import ch.threema.localcrypto.MasterKeyTestData.AUTH_TOKEN
import ch.threema.localcrypto.MasterKeyTestData.MASTER_KEY
import ch.threema.localcrypto.MasterKeyTestData.REMOTE_SECRET_HASH
import ch.threema.localcrypto.models.Argon2Version
import ch.threema.localcrypto.models.MasterKeyData
import ch.threema.localcrypto.models.MasterKeyStorageData
import ch.threema.localcrypto.models.RemoteSecretParameters
import ch.threema.localcrypto.models.Version2MasterKeyStorageInnerData
import ch.threema.localcrypto.models.Version2MasterKeyStorageOuterData
import ch.threema.testhelpers.cryptographicByteArrayOf
import java.io.DataInputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class Version2MasterKeyStorageEncoderDecoderTest {
    @Test
    fun `unprotected master key can be encoded and decoded`() {
        val unprotected = MasterKeyStorageData.Version2(
            outerData = Version2MasterKeyStorageOuterData.NotPassphraseProtected(
                innerData = Version2MasterKeyStorageInnerData.Unprotected(
                    masterKeyData = MasterKeyData(MASTER_KEY),
                ),
            ),
        )
        val encoder = Version2MasterKeyStorageEncoder()
        val encoded = encoder.encodeMasterKeyStorageData(unprotected)

        val decoder = Version2MasterKeyStorageDecoder()
        val decoded = decoder.decodeOuterKeyStorage(DataInputStream(encoded.toByteArray().inputStream()))

        assertEquals(unprotected, decoded)
    }

    @Test
    fun `remote secret protected master key can be encoded and decoded`() {
        val unprotected = MasterKeyStorageData.Version2(
            outerData = Version2MasterKeyStorageOuterData.NotPassphraseProtected(
                innerData = Version2MasterKeyStorageInnerData.RemoteSecretProtected(
                    parameters = RemoteSecretParameters(
                        authenticationToken = AUTH_TOKEN,
                        remoteSecretHash = REMOTE_SECRET_HASH,
                    ),
                    encryptedData = cryptographicByteArrayOf(1, 2, 3),
                ),
            ),
        )
        val encoder = Version2MasterKeyStorageEncoder()
        val encoded = encoder.encodeMasterKeyStorageData(unprotected)

        val decoder = Version2MasterKeyStorageDecoder()
        val decoded = decoder.decodeOuterKeyStorage(DataInputStream(encoded.toByteArray().inputStream()))

        assertEquals(unprotected, decoded)
    }

    @Test
    fun `passphrase protected master key can be encoded and decoded`() {
        val unprotected = MasterKeyStorageData.Version2(
            outerData = Version2MasterKeyStorageOuterData.PassphraseProtected(
                argonVersion = Argon2Version.VERSION_1_3,
                encryptedData = cryptographicByteArrayOf(1, 2, 3, 4, 5),
                nonce = ByteArray(MasterKeyConfig.NONCE_LENGTH) { it.toByte() }.toCryptographicByteArray(),
                memoryBytes = MasterKeyConfig.ARGON2_MEMORY_BYTES,
                salt = MasterKeyTestData.SALT.toCryptographicByteArray(),
                iterations = MasterKeyConfig.ARGON2_ITERATIONS,
                parallelism = MasterKeyConfig.ARGON2_PARALLELIZATION,
            ),
        )
        val encoder = Version2MasterKeyStorageEncoder()
        val encoded = encoder.encodeMasterKeyStorageData(unprotected)

        val decoder = Version2MasterKeyStorageDecoder()
        val decoded = decoder.decodeOuterKeyStorage(DataInputStream(encoded.toByteArray().inputStream()))

        assertEquals(unprotected, decoded)
    }
}
