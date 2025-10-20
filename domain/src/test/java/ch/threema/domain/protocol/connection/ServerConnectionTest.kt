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

package ch.threema.domain.protocol.connection

import ch.threema.base.crypto.NaCl
import ch.threema.base.crypto.NonceCounter
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.libthreema.blake2bMac256
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest

internal abstract class ServerConnectionTest {
    private companion object {
        private val skPublicPrimary = ByteArray(NaCl.PUBLIC_KEY_BYTES)
        private val skSecretPrimary = ByteArray(NaCl.SECRET_KEY_BYTES)
        private val skPublicAlt = ByteArray(NaCl.PUBLIC_KEY_BYTES)
        private val skSecretAlt = ByteArray(NaCl.SECRET_KEY_BYTES)
        private val random = SecureRandom()

        init {
            NaCl.generateKeypairInPlace(skPublicPrimary, skSecretPrimary)
            NaCl.generateKeypairInPlace(skPublicAlt, skSecretAlt)
            NaCl.generateKeypairInPlace(TestIdentityStore.ckPublic, TestIdentityStore.ckSecret)
        }
    }

    protected lateinit var testSocket: TestSocket
    protected val serverAddressProvider = TestServerAddressProvider(skPublicPrimary, skPublicAlt)

    private lateinit var skSecret: ByteArray
    private lateinit var skPublic: ByteArray

    private lateinit var tskSecret: ByteArray
    private lateinit var tskPublic: ByteArray
    private lateinit var sck: ByteArray
    private lateinit var serverNonce: NonceCounter

    private lateinit var tckPublic: ByteArray
    private lateinit var cck: ByteArray
    private lateinit var clientNonce: NonceCounter

    private lateinit var kClientServer: NaCl

    /**
     * Create a [ServerConnection] which would be used to connect to the chatserver and uses [testSocket] as the server Socket.
     * [serverAddressProvider] must be used as the [ServerAddressProvider].
     */
    abstract fun createChatServerConnection(): ServerConnection

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        testSocket = TestSocket()
    }

    @Test
    fun `test initial connection state is DISCONNECTED`() {
        val connection = createChatServerConnection()
        assertEquals(ConnectionState.DISCONNECTED, connection.connectionState)
    }

    @Test
    fun testChatServerConnection() = runTest(timeout = 1.seconds) {
        prepareServerKeys(skPublicPrimary, skSecretPrimary)

        val connection = createChatServerConnection()
        val connectionStates = observeConnectionStates(connection)
        connection.start()

        assertHandshake()

        // stop the connection
        connection.stop()

        // assert expected states
        assertEquals(1, serverAddressProvider.keyFetchCount)
        assertEquals(0, serverAddressProvider.altKeyFetchCount)
        assertEquals(4, connectionStates.size)
        assertEquals(ConnectionState.CONNECTING, connectionStates[0])
        assertEquals(ConnectionState.CONNECTED, connectionStates[1])
        assertEquals(ConnectionState.LOGGEDIN, connectionStates[2])
        assertEquals(ConnectionState.DISCONNECTED, connectionStates[3])
    }

    @Test
    fun testChatServerConnectionAltKey() {
        prepareServerKeys(skPublicAlt, skSecretAlt)

        val connection = createChatServerConnection()
        val connectionStates = observeConnectionStates(connection)
        connection.start()

        assertHandshake()

        // stop the connection
        connection.stop()

        // assert expected states
        assertEquals(1, serverAddressProvider.keyFetchCount)
        assertEquals(1, serverAddressProvider.altKeyFetchCount)
        assertEquals(4, connectionStates.size)
        assertEquals(ConnectionState.CONNECTING, connectionStates[0])
        assertEquals(ConnectionState.CONNECTED, connectionStates[1])
        assertEquals(ConnectionState.LOGGEDIN, connectionStates[2])
        assertEquals(ConnectionState.DISCONNECTED, connectionStates[3])
    }

    private fun assertHandshake() {
        expectClientHello()
        sendServerHello()

        expectClientLogin()
        sendLoginAck()

        // Wait some time until the connection has processed the reservedBox response
        Thread.sleep(100)
    }

    private fun expectClientHello() {
        tckPublic = testSocket.read(32)
        cck = testSocket.read(16)
        clientNonce = NonceCounter(cck)
        kClientServer = NaCl(tskSecret, tckPublic)
    }

    private fun sendServerHello() {
        val serverHelloNaCl = NaCl(skSecret, tckPublic)
        val serverHello = serverHelloNaCl.encrypt(
            data = tskPublic + cck,
            nonce = serverNonce.nextNonce(),
        )
        testSocket.write(sck + serverHello)
    }

    private fun expectClientLogin() {
        val extensionLength = expectLoginBox()
        expectExtensionBox(extensionLength)
    }

    private fun expectLoginBox(): Int {
        // expect `login`
        val loginBox = testSocket.read(144)
        val loginBoxDecrypted = kClientServer.decrypt(
            data = loginBox,
            nonce = clientNonce.nextNonce(),
        )
        assertNotNull(loginBoxDecrypted)
        val decryptedLoginStream = ByteArrayInputStream(loginBoxDecrypted)

        // read identity
        val clientIdentity = decryptedLoginStream.readNBytes(8).decodeToString()
        assertEquals(TestIdentityStore.CLIENT_IDENTITY, clientIdentity)

        // read extension-indicator
        val extensionIndicator = decryptedLoginStream.readNBytes(30).decodeToString()
        assertEquals("threema-clever-extension-field", extensionIndicator)
        val bb = ByteBuffer.wrap(decryptedLoginStream.readNBytes(2))
        bb.order(ByteOrder.LITTLE_ENDIAN)
        val extensionLength = bb.short.toUShort().toInt()

        // read repeated sck
        val repeatedSck = decryptedLoginStream.readNBytes(16)
        assertContentEquals(sck, repeatedSck)

        // read reserved1
        val reserved1 = decryptedLoginStream.readNBytes(24)
        assertContentEquals(ByteArray(24), reserved1)

        // read vouch
        val ss1 = NaCl(skSecret, TestIdentityStore.ckPublic).sharedSecret
        val ss2 = NaCl(tskSecret, TestIdentityStore.ckPublic).sharedSecret
        val vouchKey = blake2bMac256(
            key = ss1 + ss2,
            personal = "3ma-csp".encodeToByteArray(),
            salt = "v2".encodeToByteArray(),
            data = byteArrayOf(),
        )
        val expectedVouch = blake2bMac256(
            key = vouchKey,
            personal = byteArrayOf(),
            salt = byteArrayOf(),
            data = sck + tckPublic,
        )
        val vouch = decryptedLoginStream.readNBytes(32)
        assertContentEquals(expectedVouch, vouch)

        // read reserved2
        val reserved2 = decryptedLoginStream.readNBytes(16)
        assertContentEquals(ByteArray(16), reserved2)

        // assert login data consumed
        assertEquals(0, decryptedLoginStream.available())

        return extensionLength
    }

    private fun expectExtensionBox(length: Int) {
        // read extension box
        val extensionBox = testSocket.read(length)
        val extensionBoxDecrypted = kClientServer.decrypt(
            data = extensionBox,
            nonce = clientNonce.nextNonce(),
        )
        assertNotNull(extensionBoxDecrypted)
    }

    private fun sendLoginAck() {
        val reservedBox = kClientServer.encrypt(
            data = ByteArray(16),
            nonce = serverNonce.nextNonce(),
        )
        testSocket.write(reservedBox)
    }

    private fun prepareServerKeys(skPublic: ByteArray, skSecret: ByteArray) {
        this.skPublic = skPublic
        this.skSecret = skSecret
        sck = ByteArray(16)
        random.nextBytes(sck)
        tskPublic = ByteArray(NaCl.PUBLIC_KEY_BYTES)
        tskSecret = ByteArray(NaCl.SECRET_KEY_BYTES)
        NaCl.generateKeypairInPlace(tskPublic, tskSecret)
        serverNonce = NonceCounter(sck)
    }

    /**
     * Prepare the connection to collect the different connection states during the test and return a list of these states which
     * can be evaluated at the end of the test.
     */
    private fun observeConnectionStates(connection: ServerConnection): List<ConnectionState> {
        val connectionStates = mutableListOf<ConnectionState>()
        connection.addConnectionStateListener { connectionState ->
            connectionStates.add(
                connectionState,
            )
        }
        return connectionStates
    }
}
