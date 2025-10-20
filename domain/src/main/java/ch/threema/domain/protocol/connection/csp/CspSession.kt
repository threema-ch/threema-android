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

package ch.threema.domain.protocol.connection.csp

import ch.threema.base.crypto.KeyPair
import ch.threema.base.crypto.NaCl
import ch.threema.base.crypto.NonceCounter
import ch.threema.base.utils.SecureRandomUtil
import ch.threema.base.utils.TimeMeasureUtil
import ch.threema.common.toHexString
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
import ch.threema.domain.stores.IdentityStore
import ch.threema.libthreema.CryptoException
import ch.threema.libthreema.blake2bMac256
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import org.apache.commons.io.EndianUtils

private val logger = ConnectionLoggingUtil.getConnectionLogger("CspSession")

interface CspSessionState {
    val isLoginDone: Boolean
}

internal class CspSession(
    private val configuration: BaseServerConnectionConfiguration,
    private val dispatcher: ServerConnectionDispatcher,
) : CspSessionState {
    private enum class LoginState {
        IDLE,
        AWAIT_HELLO,
        AWAIT_LOGIN_ACK,
        DONE,
    }

    private val timeMeasureUtil = TimeMeasureUtil()

    private val version: Version
        get() = configuration.version
    private val identityStore: IdentityStore
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

    private lateinit var clientTempKeypair: KeyPair

    private lateinit var serverPubKeyPerm: ByteArray

    private lateinit var kClientTempServerTemp: NaCl

    private lateinit var serverNonce: NonceCounter
    private lateinit var clientNonce: NonceCounter

    override val isLoginDone: Boolean
        get() = loginState == LoginState.DONE

    fun startLogin(outbound: InputPipe<in CspLoginMessage, Unit>) {
        dispatcher.assertDispatcherContext()
        logger.debug("Start csp login")

        clientTempKeypair = try {
            NaCl.generateKeypair()
        } catch (exception: Exception) {
            throw ServerConnectionException("Failed to generate keypair", exception)
        }

        sendClientHello(outbound)
        loginState = LoginState.AWAIT_HELLO
    }

    fun handleLoginMessage(
        message: CspLoginMessage,
        outbound: InputPipe<in CspLoginMessage, Unit>,
    ) {
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
            payload.size,
        )
        return CspFrame(
            kClientTempServerTemp.encrypt(
                data = payload,
                nonce = clientNonce.nextNonce(),
            ),
        )
    }

    fun decryptBox(frame: CspFrame): CspContainer {
        dispatcher.assertDispatcherContext()

        if (loginState != LoginState.DONE) {
            throw ServerConnectionException("CspFrame cannot be decrypted; login not completed")
        }
        val decrypted = try {
            kClientTempServerTemp.decrypt(
                data = frame.box,
                nonce = serverNonce.nextNonce(),
            )
        } catch (cryptoException: CryptoException) {
            throw ServerConnectionException("Payload decryption failed", cryptoException)
        }
        val payloadType = decrypted[0].toUByte()
        val dataLength = decrypted.size - 4
        val data = ByteArray(dataLength)
        System.arraycopy(decrypted, 4, data, 0, dataLength)
        return CspContainer(payloadType, data)
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
                assertClientCookieAndServerCookieNotEqual()
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
            logger.debug("Server cookie = {}", serverCookie.toHexString())
        }
        serverNonce = NonceCounter(serverCookie)
    }

    private fun decryptServerHello(input: InputStream): ByteArray {
        dispatcher.assertDispatcherContext()

        val serverHelloBox = readNBytes(input, ProtocolDefines.SERVER_HELLO_BOXLEN)
        val nonce = serverNonce.nextNonce()
        if (logger.isDebugEnabled) {
            logger.debug("Server nonce = {}", nonce.toHexString())
        }

        // Note that the public key of the server is checked here in our custom chat server
        // protocol. This gives us the same security protections as certificate pinning in the tls
        // context.
        serverPubKeyPerm = serverAddressProvider.getChatServerPublicKey()
        var kClientTempServerPerm = NaCl(clientTempKeypair.privateKey, serverPubKeyPerm)
        var serverHello = try {
            kClientTempServerPerm.decrypt(
                data = serverHelloBox,
                nonce = nonce,
            )
        } catch (cryptoException: CryptoException) {
            logger.error("Failed to decrypt server hello. Trying again with alternate key", cryptoException)
            null
        }

        if (serverHello == null) {
            /* Try again with alternate key */
            serverPubKeyPerm = serverAddressProvider.getChatServerPublicKeyAlt()
            kClientTempServerPerm = NaCl(clientTempKeypair.privateKey, serverPubKeyPerm)
            serverHello = try {
                kClientTempServerPerm.decrypt(
                    data = serverHelloBox,
                    nonce = nonce,
                )
            } catch (cryptoException: CryptoException) {
                throw ServerConnectionException("Decryption of server hello box failed", cryptoException)
            }
        }

        if (serverHello != null) {
            return serverHello
        } else {
            throw ServerConnectionException("Decryption of server hello box failed")
        }
    }

    private fun assertClientCookieAndServerCookieNotEqual() {
        if (clientCookie.contentEquals(serverCookie)) {
            throw ServerConnectionException("CCK and SCK must not be equal")
        }
    }

    /**
     * Assert that the client cookie from server hello matches the local clientCookie
     */
    private fun assertCorrectClientCookie(serverHello: ByteArray) {
        val clientCookieFromServer = ByteArray(ProtocolDefines.COOKIE_LEN)
        System.arraycopy(
            serverHello,
            NaCl.PUBLIC_KEY_BYTES,
            clientCookieFromServer,
            0,
            ProtocolDefines.COOKIE_LEN,
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

        val serverTempKeyPub = ByteArray(NaCl.PUBLIC_KEY_BYTES)
        System.arraycopy(serverHello, 0, serverTempKeyPub, 0, NaCl.PUBLIC_KEY_BYTES)

        kClientTempServerTemp = NaCl(clientTempKeypair.privateKey, serverTempKeyPub)

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
     * This will create a client cookie and initialize the client nonce.
     *
     * The clientCookie is then sent to the server as client-hello
     */
    private fun sendClientHello(outbound: InputPipe<in CspLoginMessage, Unit>) {
        dispatcher.assertDispatcherContext()

        clientCookie = SecureRandomUtil.generateRandomBytes(ProtocolDefines.COOKIE_LEN)
        if (logger.isDebugEnabled) {
            logger.debug("Client cookie = {}", clientCookie.toHexString())
        }
        clientNonce = NonceCounter(clientCookie)
        val clientHello = clientTempKeypair.publicKey + clientCookie
        timeMeasureUtil.start()
        outbound.send(CspLoginMessage(clientHello))
    }

    @Throws(ServerConnectionException::class)
    private fun sendClientLogin(
        serverTempKeyPub: ByteArray,
        outbound: InputPipe<in CspLoginMessage, Unit>,
    ) {
        dispatcher.assertDispatcherContext()

        val loginNonce = clientNonce.nextNonce()
        val extensionsNonce = clientNonce.nextNonce()

        val extensionsBox = kClientTempServerTemp.encrypt(
            data = createExtensions(),
            nonce = extensionsNonce,
        )
        val vouch = createVouch(serverTempKeyPub)

        val login = identityStore.getIdentity()!!.encodeToByteArray() +
            createExtensionIndicator(extensionsBox.size) +
            serverCookie +
            ByteArray(ProtocolDefines.RESERVED1_LEN) +
            vouch +
            ByteArray(ProtocolDefines.RESERVED2_LEN)

        if (login.size != ProtocolDefines.LOGIN_LEN) {
            throw ServerConnectionException("Invalid login packet length")
        }

        val loginBox = kClientTempServerTemp.encrypt(
            data = login,
            nonce = loginNonce,
        )
        timeMeasureUtil.start()
        outbound.send(CspLoginMessage(loginBox + extensionsBox))
        logger.debug("Sent login packet")
    }

    private fun createExtensions(): ByteArray {
        /* Client info (0x00) */
        val clientInfo = ProtocolExtension(
            ProtocolExtension.CLIENT_INFO_TYPE,
            version.fullVersionString.encodeToByteArray(),
        )

        /* Csp device id (0x01) if multi device is active, omit if md is not active */
        val cspDeviceIdBytes = cspDeviceId
            ?.let { ProtocolExtension(ProtocolExtension.CSP_DEVICE_ID_TYPE, it.leBytes()).bytes }
            ?: ByteArray(0)
        logger.trace("Csp  device id bytes {}", cspDeviceIdBytes.toHexString())

        /* Supported features extension (0x02) */
        val supportedFeatures =
            ProtocolExtension.SUPPORTS_MESSAGE_WITH_METADATA_PAYLOAD or ProtocolExtension.SUPPORTS_RECEIVING_ECHO_REQUEST
        val supportedFeaturesExtension = ProtocolExtension(
            ProtocolExtension.SUPPORTED_FEATURES_TYPE,
            byteArrayOf(supportedFeatures.toByte()),
        )

        /* Device cookie extension (0x03) */
        val deviceCookie = ProtocolExtension(
            ProtocolExtension.DEVICE_COOKIE_TYPE,
            deviceCookieManager.obtainDeviceCookie(),
        )

        return clientInfo.bytes + cspDeviceIdBytes + supportedFeaturesExtension.bytes + deviceCookie.bytes
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

    @Throws(ServerConnectionException::class)
    private fun createVouch(serverTempKeyPub: ByteArray): ByteArray {
        val sharedSecrets = identityStore.calcSharedSecret(serverPubKeyPerm) + identityStore.calcSharedSecret(serverTempKeyPub)
        val input = serverCookie + clientTempKeypair.publicKey
        return try {
            val vouchKey = blake2bMac256(
                key = sharedSecrets,
                personal = "3ma-csp".encodeToByteArray(),
                salt = "v2".encodeToByteArray(),
                data = byteArrayOf(),
            )
            blake2bMac256(
                key = vouchKey,
                personal = byteArrayOf(),
                salt = byteArrayOf(),
                data = input,
            )
        } catch (cryptoException: CryptoException.InvalidParameter) {
            logger.error("Failed to compute blake2b hash", cryptoException)
            throw ServerConnectionException("Failed to compute blake2b hash", cryptoException)
        }
    }

    private fun processServerLoginAck(message: CspLoginMessage) {
        dispatcher.assertDispatcherContext()

        if (message.bytes.size != ProtocolDefines.SERVER_LOGIN_ACK_LEN) {
            throw ServerConnectionException("Login ack has invalid length")
        }

        try {
            kClientTempServerTemp.decrypt(
                data = message.bytes,
                nonce = serverNonce.nextNonce(),
            )
        } catch (cryptoException: CryptoException) {
            throw ServerConnectionException("Decryption of login ack box failed", cryptoException)
        }
        logger.info("Login ack received (rtt: {} ms)", timeMeasureUtil.elapsedTime)
    }
}
