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

import ch.threema.base.crypto.Nonce
import ch.threema.base.crypto.ThreemaKDF
import ch.threema.base.utils.SecureRandomUtil.generateRandomBytes
import ch.threema.domain.protocol.connection.data.D2dMessage
import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.connection.data.D2mProtocolException
import ch.threema.protobuf.d2d.MdD2D
import ch.threema.protobuf.d2d.MdD2D.DeviceInfo
import ch.threema.protobuf.d2d.MdD2D.Envelope
import ch.threema.protobuf.d2d.MdD2D.TransactionScope
import ch.threema.protobuf.d2d.MdD2D.TransactionScope.Scope
import ch.threema.protobuf.d2d.transactionScope
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

    fun decryptDeviceInfo(deviceInfo: ByteArray): D2dMessage.DeviceInfo {
        val nonce = deviceInfo.copyOfRange(0, NaCl.NONCEBYTES)
        val data = deviceInfo.copyOfRange(nonce.size, deviceInfo.size)
        val decrypted = NaCl.symmetricDecryptData(data, dgdik, nonce)
        return DeviceInfo.parseFrom(decrypted).let { D2dMessage.DeviceInfo.fromProtobuf(it) }
    }

    fun encryptTransactionScope(scope: Scope): ByteArray {
        val nonce = createNonce()
        val bytes = transactionScope {
            this.scope = scope
        }.toByteArray()
        val encrypted = NaCl.symmetricEncryptData(bytes, dgtsk, nonce)
        return nonce + encrypted
    }

    fun decryptTransactionScope(encryptedTransactionScope: ByteArray): Scope {
        val nonce = encryptedTransactionScope.copyOfRange(0, NaCl.NONCEBYTES)
        val data = encryptedTransactionScope.copyOfRange(nonce.size, encryptedTransactionScope.size)
        val decrypted = NaCl.symmetricDecryptData(data, dgtsk, nonce)
        return TransactionScope.parseFrom(decrypted).scope
    }

    fun encryptEnvelope(envelope: Envelope): EncryptedEnvelopeResult {
        val nonceBytes = createNonce()
        val encryptedEnvelope =
            nonceBytes + NaCl.symmetricEncryptData(envelope.toByteArray(), dgrk, nonceBytes)
        return EncryptedEnvelopeResult(
            encryptedEnvelope = encryptedEnvelope,
            nonce = Nonce(nonceBytes),
            debugInfo = EncryptedEnvelopeResult.DebugInfo(
                protoContentCaseName = envelope.contentCase.name,
                rawEnvelopeContent = envelope.toString()
            )
        )
    }

    fun decryptEnvelope(encryptedEnvelopeBytes: ByteArray): Pair<Nonce, Envelope> {
        val nonce = encryptedEnvelopeBytes.copyOfRange(0, NaCl.NONCEBYTES)
        val data = encryptedEnvelopeBytes.copyOfRange(nonce.size, encryptedEnvelopeBytes.size)
        val decrypted = NaCl.symmetricDecryptData(data, dgrk, nonce)
        return Nonce(nonce) to Envelope.parseFrom(decrypted)
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

    /**
     * @param encryptedEnvelope Encrypted envelope bytes
     * @param debugInfo         Only used for debugging. Contains the unencrypted contents of [MdD2D.Envelope.toString]
     */
    data class EncryptedEnvelopeResult(
        val encryptedEnvelope: ByteArray,
        val nonce: Nonce,
        val debugInfo: DebugInfo
    ) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as EncryptedEnvelopeResult
            if (!encryptedEnvelope.contentEquals(other.encryptedEnvelope)) return false
            if (nonce != other.nonce) return false
            if (debugInfo != other.debugInfo) return false
            return true
        }

        override fun hashCode(): Int {
            var result = encryptedEnvelope.contentHashCode()
            result = 31 * result + nonce.hashCode()
            result = 31 * result + debugInfo.hashCode()
            return result
        }

        override fun toString(): String {
            return "EncryptedEnvelopeResult(encryptedEnvelope: ${encryptedEnvelope.contentToString()}, nonce: ***, debugInfo: $debugInfo"
        }

        /**
         * @param protoContentCaseName Is the value of [Envelope.getContentCase]
         * @param rawEnvelopeContent   Contains the whole proto message contents in an **unencrypted** form. Never send this
         * and only log it on debug level!
         */
        data class DebugInfo(
            val protoContentCaseName: String,
            val rawEnvelopeContent: String
        ) {

            override fun toString(): String {
                return "EncryptedEnvelopeResult(protoContentCaseName: $protoContentCaseName, rawEnvelopeContent: ***)"
            }
        }
    }
}
