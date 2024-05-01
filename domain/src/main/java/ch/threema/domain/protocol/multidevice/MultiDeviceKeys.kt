/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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

import ch.threema.base.crypto.ThreemaKDF
import ch.threema.base.utils.SecureRandomUtil.generateRandomBytes
import ch.threema.domain.protocol.connection.data.D2dMessage
import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.connection.data.D2mProtocolException
import com.neilalexander.jnacl.NaCl

data class MultiDeviceKeys(val dgk: ByteArray) {
    companion object {
        private val KDF: ThreemaKDF by lazy { ThreemaKDF("3ma-mdev") }
        private const val SALT_DGPK = "p"
        private const val SALT_DGRK = "r"
        private const val SALT_DGDIK = "di"
        private const val SALT_DGSDDK = "sdd"
        private const val SALT_DGTSK = "ts"

        private fun createNonce(): ByteArray {
            return generateRandomBytes(NaCl.NONCEBYTES)
        }
    }

    init {
        // assert correct length of dgk
        if (dgk.size != 32) {
            throw D2mProtocolException("Invalid length of key material. Expected 32, actual ${dgk.size}")
        }
    }

    internal val dgpk: ByteArray by lazy { KDF.deriveKey(SALT_DGPK, dgk) }
    internal val dgid: ByteArray by lazy { NaCl.derivePublicKey(dgpk) }

    internal val dgrk: ByteArray by lazy { KDF.deriveKey(SALT_DGRK, dgk) }
    internal val dgdik: ByteArray by lazy { KDF.deriveKey(SALT_DGDIK, dgk) }
    internal val dgsddk: ByteArray by lazy { KDF.deriveKey(SALT_DGSDDK, dgk) }
    internal val dgtsk: ByteArray by lazy { KDF.deriveKey(SALT_DGTSK, dgk) }

    internal fun createServerHelloResponse(serverHello: InboundD2mMessage.ServerHello): ByteArray {
        val naCl = NaCl(dgpk, serverHello.esk)
        val nonce = createNonce()
        return nonce + naCl.encrypt(serverHello.challenge, nonce)
    }

    fun encryptDeviceInfo(deviceInfo: D2dMessage.DeviceInfo): ByteArray {
        val nonce = createNonce()
        return nonce + NaCl.symmetricEncryptData(deviceInfo.bytes, dgdik, nonce)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MultiDeviceKeys) return false

        if (!dgk.contentEquals(other.dgk)) return false

        return true
    }

    override fun hashCode(): Int {
        return dgk.contentHashCode()
    }

    override fun toString(): String {
        return "MultiDeviceKeys(dgk=********)"
    }


}
