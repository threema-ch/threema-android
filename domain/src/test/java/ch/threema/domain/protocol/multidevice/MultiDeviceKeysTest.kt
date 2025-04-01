/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

package ch.threema.domain.protocol.multidevice

import ch.threema.base.utils.Utils
import ch.threema.domain.protocol.connection.data.D2dMessage
import ch.threema.protobuf.d2d.MdD2D.TransactionScope
import ch.threema.protobuf.d2d.transactionScope
import com.neilalexander.jnacl.NaCl
import org.junit.Assert.*
import org.junit.Test

/**
 * Test vectors according to the `multidevice-kdf` repository
 */
class MultiDeviceKeysTest {
    private companion object {
        private val DGK1 =
            Utils.hexStringToByteArray("1b35ed7e1ba9993171fe4a7eed30c2831905c3a583616d61e93782da900bf8ba")
        private val DGPK1 =
            Utils.hexStringToByteArray("60c0ed9098902c0b6093be4a819bf344900bc7473504ccdb61004b1b58aa2233")
        private val DGRK1 =
            Utils.hexStringToByteArray("a4bf34ff67ed3b731a2aa6e023335f7eba6c914e877da3d15bff41d84f7f75f8")
        private val DGDIK1 =
            Utils.hexStringToByteArray("5953fb9775d8c23fae573e245534dac2c7aa16b62ea73b954ea4177d192e8c50")
        private val DGSDDK1 =
            Utils.hexStringToByteArray("7a91ad36bc9537ccfd6fd76b23d7f5319e506e6a5294c988c9a75c8e34a3c9dd")
        private val DGTSK1 =
            Utils.hexStringToByteArray("7c6b94affb171c564bfd375d50c4781d72ab2671ae4035ab4307dedce67ef30e")

        private val DGK2 =
            Utils.hexStringToByteArray("7cf1c4847fb32d6c3702747018d0cccdc2f724c115bfca1036ae6208d2b7c68c")
        private val DGPK2 =
            Utils.hexStringToByteArray("2b59e83e5a93ce4a09eeb4db91ec30e63cc3f173385742dd4a27ef83e5bef4f3")
        private val DGRK2 =
            Utils.hexStringToByteArray("fa33785f528439c2bfd9b7220ff03ebca919c1c127aca91878f2038b65a79c73")
        private val DGDIK2 =
            Utils.hexStringToByteArray("8a8199023dfeda5793e06552fb968282d7e7e29452c1229dcb212fb7d48f1e6a")
        private val DGSDDK2 =
            Utils.hexStringToByteArray("bd96dd0f700c5d77da666990854eb4287d5f20a9be0deda6d88b875963c39b3f")
        private val DGTSK2 =
            Utils.hexStringToByteArray("351e81590fb0115f8d1ab604c7eaf6996f3e079ea0a591db62caea4dcd7c6bda")

        private val DGK3 =
            Utils.hexStringToByteArray("0000000000000000000000000000000000000000000000000000000000000000")
        private val DGPK3 =
            Utils.hexStringToByteArray("4b464e5d33debe0d3f9be535b9a1449f79caac615c852da734b47ef3a23e14ca")
        private val DGRK3 =
            Utils.hexStringToByteArray("d597f6380d1ecf6f1a7fd265c49bc53cff04c0efc1236a542dae338bba4bc6e9")
        private val DGDIK3 =
            Utils.hexStringToByteArray("8bb45615b2982e8d6aefcfbe46a44bcea0df048df7a05d9d50afd01911ed10d8")
        private val DGSDDK3 =
            Utils.hexStringToByteArray("86fa7962c7d85e01876de9a90cad95cf83b4605d40b83b6580b80618f56fa4d4")
        private val DGTSK3 =
            Utils.hexStringToByteArray("7ab0a7c3239323e1b9f697eb59196d888747f027df356a305f58f55ebb8c23aa")
    }

    @Test
    fun testKeyDerivationDgk1() {
        val keys = MultiDeviceKeys(DGK1)

        assertArrayEquals(DGPK1, keys.dgpk)
        assertArrayEquals(DGRK1, keys.dgrk)
        assertArrayEquals(DGDIK1, keys.dgdik)
        assertArrayEquals(DGSDDK1, keys.dgsddk)
        assertArrayEquals(DGTSK1, keys.dgtsk)
    }

    @Test
    fun testKeyDerivationDgk2() {
        val keys = MultiDeviceKeys(DGK2)

        assertArrayEquals(DGPK2, keys.dgpk)
        assertArrayEquals(DGRK2, keys.dgrk)
        assertArrayEquals(DGDIK2, keys.dgdik)
        assertArrayEquals(DGSDDK2, keys.dgsddk)
        assertArrayEquals(DGTSK2, keys.dgtsk)
    }

    @Test
    fun testKeyDerivationDgk3() {
        val keys = MultiDeviceKeys(DGK3)

        assertArrayEquals(DGPK3, keys.dgpk)
        assertArrayEquals(DGRK3, keys.dgrk)
        assertArrayEquals(DGDIK3, keys.dgdik)
        assertArrayEquals(DGSDDK3, keys.dgsddk)
        assertArrayEquals(DGTSK3, keys.dgtsk)
    }

    @Test
    fun testEncryptDecryptDeviceInfo() {
        val keys = MultiDeviceKeys(DGK1)
        val deviceInfo = D2dMessage.DeviceInfo(
            D2dMessage.DeviceInfo.Platform.ANDROID,
            "Unit Test",
            "1.2.3",
            "Test Client"
        )

        val encrypted = keys.encryptDeviceInfo(deviceInfo)
        val decrypted = keys.decryptDeviceInfo(encrypted)
        assertEquals(deviceInfo, decrypted)
    }

    @Test
    fun testEncryptTransactionScope() {
        val keys = MultiDeviceKeys(DGK1)

        listOf(
            // TODO(ANDR-2699): This leads to an empty array which can not be encrypted at the moment
            // TransactionScope.Scope.USER_PROFILE_SYNC,
            TransactionScope.Scope.CONTACT_SYNC,
            TransactionScope.Scope.GROUP_SYNC,
            TransactionScope.Scope.DISTRIBUTION_LIST_SYNC,
            TransactionScope.Scope.SETTINGS_SYNC,
            TransactionScope.Scope.MDM_PARAMETER_SYNC,
            TransactionScope.Scope.NEW_DEVICE_SYNC
        ).forEach {
            val expected = transactionScope {
                scope = it
            }
            val encrypted = keys.encryptTransactionScope(it)
            val nonce = encrypted.copyOfRange(0, NaCl.NONCEBYTES)
            val data = encrypted.copyOfRange(NaCl.NONCEBYTES, encrypted.size)
            val decrypted = NaCl.symmetricDecryptData(data, keys.dgtsk, nonce)
            assertEquals(expected, TransactionScope.parseFrom(decrypted))
        }
    }

    @Test
    fun testDecryptTransactionScope() {
        val keys = MultiDeviceKeys(DGK1)

        listOf(
            // TODO(ANDR-2699): This leads to an empty array which can not be encrypted at the moment
            // TransactionScope.Scope.USER_PROFILE_SYNC,
            TransactionScope.Scope.CONTACT_SYNC,
            TransactionScope.Scope.GROUP_SYNC,
            TransactionScope.Scope.DISTRIBUTION_LIST_SYNC,
            TransactionScope.Scope.SETTINGS_SYNC,
            TransactionScope.Scope.MDM_PARAMETER_SYNC,
            TransactionScope.Scope.NEW_DEVICE_SYNC
        ).forEach { expectedScope ->
            val bytes = transactionScope {
                scope = expectedScope
            }.toByteArray()
            val nonce = ByteArray(24) { it.toByte() }
            val encrypted = nonce + NaCl.symmetricEncryptData(bytes, keys.dgtsk, nonce)
            val decrypted = keys.decryptTransactionScope(encrypted)
            assertEquals(expectedScope, decrypted)
        }
    }
}
