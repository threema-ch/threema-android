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

package ch.threema.domain.protocol.connection.csp

import ch.threema.base.crypto.NonceCounter
import ch.threema.base.crypto.ThreemaKDF
import ch.threema.base.utils.TimeMeasureUtil
import ch.threema.base.utils.toHexString
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.domain.protocol.Version
import ch.threema.domain.protocol.connection.BaseServerConnectionConfiguration
import ch.threema.domain.protocol.connection.InputPipe
import ch.threema.domain.protocol.connection.ServerConnectionDispatcher
import ch.threema.domain.protocol.connection.ServerConnectionException
import ch.threema.domain.protocol.connection.d2m.D2mConnectionConfiguration
import ch.threema.domain.protocol.connection.data.CspContainer
import ch.threema.domain.protocol.connection.data.CspFrame
import ch.threema.domain.protocol.connection.data.CspLoginMessage
import ch.threema.domain.protocol.connection.data.DeviceId
import ch.threema.domain.protocol.connection.data.leBytes
import ch.threema.domain.protocol.connection.util.ConnectionLoggingUtil
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.stores.IdentityStoreInterface
import com.neilalexander.jnacl.NaCl
import org.apache.commons.io.EndianUtils
import ove.crypto.digest.Blake2b
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.SecureRandom

private val logger = ConnectionLoggingUtil.getConnectionLogger("CspSession")

interface CspSessionState {
    val isLoginDone: Boolean
}

internal class CspSession(
    private val configuration: BaseServerConnectionConfiguration,
    private val dispatcher: ServerConnectionDispatcher
) : CspSessionState {
    private enum class LoginState {
        IDLE,
        AWAIT_HELLO,
        AWAIT_LOGIN_ACK,
        DONE
    }

    private val timeMeasureUtil = TimeMeasureUtil()

    private val version: Version
        get() = configuration.version
    private val identityStore: IdentityStoreInterface
        get() = configuration.identityStore
    private val cspDeviceId: DeviceId?
        get() = when (configuration) {
            is D2mConnectionConfiguration -> configuration.multiDevicePropertyProvider.get().cspDeviceId
            else -> null
        }
    private val serverAddressProvider: ServerAddressProvider
        get() = configuration.serverAddressProvider
    private val deviceCookieManager: DeviceCookieManager
        get() = configuration.deviceCookieManager

    private var loginState = LoginState.IDLE

    private lateinit var clientCookie: ByteArray
    private lateinit var serverCookie: ByteArray

    private lateinit var clientTempKeyPub: ByteArray
    private lateinit var clientTempKeySec: ByteArray

    private lateinit var serverPubKeyPerm: ByteArray

    private lateinit var kClientTempServerTemp: NaCl

    private lateinit var serverNonce: NonceCounter
    private lateinit var clientNonce: NonceCounter

    override val isLoginDone: Boolean
        get() = loginState == LoginState.DONE

    fun startLogin(outbound: InputPipe<in CspLoginMessage>) {
        dispatcher.assertDispatcherContext()
        logger.debug("Start csp login")

        createTemporaryKeypair()

        sendClientHello(outbound)
        loginState = LoginState.AWAIT_HELLO
    }

    fun handleLoginMessage(message: CspLoginMessage, outbound: InputPipe<in CspLoginMessage>) {
        dispatcher.assertDispatcherContext()

        loginState = when (loginState) {
            LoginState.AWAIT_HELLO -> {
                timeMeasureUtil.stop()
                val serverTempKeyPub = processServerHello(message)
                sendClientLogin(serverTempKeyPub, outbound)
                LoginState.AWAIT_LOGIN_ACK
            }

            LoginState.AWAIT_LOGIN_ACK -> {
                timeMeasureUtil.stop()
                processServerLoginAck(message)
                LoginState.DONE
            }

            else -> throw ServerConnectionException("Unexpected CspLoginMessage message in login state $loginState")
        }
    }

    fun encryptContainer(container: CspContainer): CspFrame {
        dispatcher.assertDispatcherContext()

        if (loginState != LoginState.DONE) {
            throw ServerConnectionException("Message cannot be encrypted; login not completed")
        }
        val payload = byteArrayOf(container.payloadType.toByte()) +
            ByteArray(3) +
            container.data

        logger.trace(
            "Encrypt CspContainer with {} data bytes (payload size: {} bytes)",
            container.data.size,
            payload.size
        )
        return CspFrame(kClientTempServerTemp.encrypt(payload, clientNonce.nextNonce()))
    }

    fun decryptBox(frame: CspFrame): CspContainer {
        dispatcher.assertDispatcherContext()

        if (loginState != LoginState.DONE) {
            throw ServerConnectionException("CspFrame cannot be decrypted; login not completed")
        }
        val decrypted = kClientTempServerTemp.decrypt(frame.box, serverNonce.nextNonce())
            ?: throw ServerConnectionException("Payload decryption failed")
        val payloadType = decrypted[0].toUByte()
        val dataLength = decrypted.size - 4
        val data = ByteArray(dataLength)
        System.arraycopy(decrypted, 4, data, 0, dataLength)
        return CspContainer(payloadType, data)
    }

    private fun createTemporaryKeypair() {
        clientTempKeyPub = ByteArray(NaCl.PUBLICKEYBYTES)
        clientTempKeySec = ByteArray(NaCl.SECRETKEYBYTES)
        NaCl.genkeypair(clientTempKeyPub, clientTempKeySec)
    }

    /**
     * @return The server's temporary public key
     */
    private fun processServerHello(message: CspLoginMessage): ByteArray {
        dispatcher.assertDispatcherContext()

        if (message.bytes.size != ProtocolDefines.SERVER_HELLO_LEN) {
            throw ServerConnectionException("Server hello has invalid length")
        }

        try {
            ByteArrayInputStream(message.bytes).use {
                readServerCookie(it)
                val serverHello = decryptServerHello(it)
                assertCorrectClientCookie(serverHello)
                val serverTempKeyPub = processServerHello(serverHello)
                logger.info("Server hello successful (rtt: {} ms)", timeMeasureUtil.elapsedTime)
                return serverTempKeyPub
            }
        } catch (e: IOException) {
            throw ServerConnectionException("Exception while handling server-hello", e)
        }
    }

    private fun readServerCookie(input: InputStream) {
        dispatcher.assertDispatcherContext()

        serverCookie = readNBytes(input, ProtocolDefines.COOKIE_LEN)
        if (logger.isDebugEnabled) {
            logger.debug("Server cookie = {}", NaCl.asHex(serverCookie))
        }
        serverNonce = NonceCounter(serverCookie)
    }

    private fun decryptServerHello(input: InputStream): ByteArray {
        dispatcher.assertDispatcherContext()

        val serverHelloBox = readNBytes(input, ProtocolDefines.SERVER_HELLO_BOXLEN)
        val nonce = serverNonce.nextNonce()
        if (logger.isDebugEnabled) {
            logger.debug("Server nonce = {}", NaCl.asHex(nonce))
        }

        // Note that the public key of the server is checked here in our custom chat server
        // protocol. This gives us the same security protections as certificate pinning in the tls
        // context.
        serverPubKeyPerm = serverAddressProvider.chatServerPublicKey
        var kClientTempServerPerm = NaCl(clientTempKeySec, serverPubKeyPerm)
        var serverHello = kClientTempServerPerm.decrypt(serverHelloBox, nonce)

        if (serverHello == null) {
            /* Try again with alternate key */
            serverPubKeyPerm = serverAddressProvider.chatServerPublicKeyAlt
            kClientTempServerPerm = NaCl(clientTempKeySec, serverPubKeyPerm)
            serverHello = kClientTempServerPerm.decrypt(serverHelloBox, nonce)
            if (serverHello == null) {
                throw ServerConnectionException("Decryption of server hello box failed")
            }
        }
        return serverHello
    }

    /**
     * Assert that the client cookie from server hello matches the local clientCookie
     */
    private fun assertCorrectClientCookie(serverHello: ByteArray) {
        val clientCookieFromServer = ByteArray(ProtocolDefines.COOKIE_LEN)
        System.arraycopy(
            serverHello,
            NaCl.PUBLICKEYBYTES,
            clientCookieFromServer,
            0,
            ProtocolDefines.COOKIE_LEN
        )

        if (!clientCookieFromServer.contentEquals(clientCookie)) {
            throw ServerConnectionException("Client cookie mismatch")
        }
    }

    /**
     * Extract server tempkey from server hello.
     *
     * @return The server's temporary public key
     */
    private fun processServerHello(serverHello: ByteArray): ByteArray {
        dispatcher.assertDispatcherContext()

        val serverTempKeyPub = ByteArray(NaCl.PUBLICKEYBYTES)
        System.arraycopy(serverHello, 0, serverTempKeyPub, 0, NaCl.PUBLICKEYBYTES)

        kClientTempServerTemp = NaCl(clientTempKeySec, serverTempKeyPub)

        return serverTempKeyPub
    }

    private fun readNBytes(input: InputStream, n: Int): ByteArray {
        dispatcher.assertDispatcherContext()

        val data = ByteArray(n)
        if (input.read(data) != n) {
            throw ServerConnectionException("Could not read $n bytes from input")
        }
        return data
    }

    /**
     * This will create a client cookie and initalize the clientnonce.
     *
     * The clientCookie is then sent to the server as client-hello
     */
    private fun sendClientHello(outbound: InputPipe<in CspLoginMessage>) {
        dispatcher.assertDispatcherContext()

        clientCookie = ByteArray(ProtocolDefines.COOKIE_LEN)
        SecureRandom().nextBytes(clientCookie)
        if (logger.isDebugEnabled) {
            logger.debug("Client cookie = {}", NaCl.asHex(clientCookie))
        }
        clientNonce = NonceCounter(clientCookie)
        val clientHello = clientTempKeyPub + clientCookie
        timeMeasureUtil.start()
        outbound.send(CspLoginMessage(clientHello))
    }

    private fun sendClientLogin(
        serverTempKeyPub: ByteArray,
        outbound: InputPipe<in CspLoginMessage>
    ) {
        dispatcher.assertDispatcherContext()

        val loginNonce = clientNonce.nextNonce()
        val extensionsNonce = clientNonce.nextNonce()

        val extensionsBox = kClientTempServerTemp.encrypt(createExtensions(), extensionsNonce)
        val vouch = createVouch(serverTempKeyPub)

        val login = identityStore.identity.encodeToByteArray() +
            createExtensionIndicator(extensionsBox.size) +
            serverCookie +
            ByteArray(ProtocolDefines.RESERVED1_LEN) +
            vouch +
            ByteArray(ProtocolDefines.RESERVED2_LEN)

        if (login.size != ProtocolDefines.LOGIN_LEN) {
            throw ServerConnectionException("Invalid login packet length")
        }

        val loginBox = kClientTempServerTemp.encrypt(login, loginNonce)
        timeMeasureUtil.start()
        outbound.send(CspLoginMessage(loginBox + extensionsBox))
        logger.debug("Sent login packet")
    }

    private fun createExtensions(): ByteArray {
        /* Client info (0x00) */
        val clientInfo = ProtocolExtension(
            ProtocolExtension.CLIENT_INFO_TYPE,
            version.fullVersionString.encodeToByteArray()
        )

        /* Csp device id (0x01) if multi device is active, omit if md is not active */
        val cspDeviceIdBytes = cspDeviceId
            ?.let { ProtocolExtension(ProtocolExtension.CSP_DEVICE_ID_TYPE, it.leBytes()).bytes }
            ?: ByteArray(0)
        logger.trace("Csp  device id bytes {}", cspDeviceIdBytes.toHexString())

        /* Message payload version (0x02) */
        val messagePayloadVersion = ProtocolExtension(
            ProtocolExtension.MESSAGE_PAYLOAD_VERSION_TYPE,
            byteArrayOf(ProtocolExtension.MESSAGE_PAYLOAD_VERSION.toByte())
        )

        /* Device cookie extension (0x03) */
        val deviceCookie = ProtocolExtension(
            ProtocolExtension.DEVICE_COOKIE_TYPE,
            deviceCookieManager.obtainDeviceCookie()
        )

        return clientInfo.bytes + cspDeviceIdBytes + messagePayloadVersion.bytes + deviceCookie.bytes
    }

    private fun createExtensionIndicator(extensionsBoxLength: Int): ByteArray {
        if (extensionsBoxLength > UShort.MAX_VALUE.toInt()) {
            throw ServerConnectionException("Extensions box is too long")
        }
        val bos = ByteArrayOutputStream(ProtocolDefines.EXTENSION_INDICATOR_LEN)
        bos.write(ProtocolExtension.VERSION_MAGIC_STRING.encodeToByteArray())
        EndianUtils.writeSwappedShort(bos, extensionsBoxLength.toShort())
        return bos.toByteArray()
    }

    private fun createVouch(serverTempKeyPub: ByteArray): ByteArray {
        val kdf = ThreemaKDF("3ma-csp")
        val sharedSecrets =
            identityStore.calcSharedSecret(serverPubKeyPerm) + identityStore.calcSharedSecret(
                serverTempKeyPub
            )
        val vouchKey = kdf.deriveKey("v2", sharedSecrets)
        val input = serverCookie + clientTempKeyPub
        return Blake2b.Mac.newInstance(vouchKey, ProtocolDefines.VOUCH_LEN).digest(input)
    }

    private fun processServerLoginAck(message: CspLoginMessage) {
        dispatcher.assertDispatcherContext()

        if (message.bytes.size != ProtocolDefines.SERVER_LOGIN_ACK_LEN) {
            throw ServerConnectionException("Login ack has invalid length")
        }

        kClientTempServerTemp.decrypt(message.bytes, serverNonce.nextNonce())
            ?: throw ServerConnectionException("Decryption of login ack box failed")
        logger.info("Login ack received (rtt: {} ms)", timeMeasureUtil.elapsedTime)
    }
}
