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

import ch.threema.base.crypto.NonceCounter
import ch.threema.base.crypto.ThreemaKDF
import ch.threema.domain.protocol.ServerAddressProvider
import com.neilalexander.jnacl.NaCl
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import ove.crypto.digest.Blake2b
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom

internal abstract class ServerConnectionTest {
    private companion object {
        private val skPublicPrimary = ByteArray(NaCl.PUBLICKEYBYTES)
        private val skSecretPrimary = ByteArray(NaCl.SECRETKEYBYTES)
        private val skPublicAlt = ByteArray(NaCl.PUBLICKEYBYTES)
        private val skSecretAlt = ByteArray(NaCl.SECRETKEYBYTES)
        private val random = SecureRandom()

        init {
            NaCl.genkeypair(skPublicPrimary, skSecretPrimary)
            NaCl.genkeypair(skPublicAlt, skSecretAlt)
            NaCl.genkeypair(TestIdentityStore.ckPublic, TestIdentityStore.ckSecret)
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

    @Before
    @Throws(Exception::class)
    fun setUp() {
        testSocket = TestSocket()
    }

    @Test
    fun `test initial connection state is DISCONNECTED`() {
        val connection = createChatServerConnection()
        Assert.assertEquals(ConnectionState.DISCONNECTED, connection.connectionState)
    }

    @Test(timeout = 1000L)
    fun testChatServerConnection() {
        prepareServerKeys(skPublicPrimary, skSecretPrimary)

        val connection = createChatServerConnection()
        val connectionStates = observeConnectionStates(connection)
        connection.start()

        assertHandshake()

        // stop the connection
        connection.stop()

        // assert expected states
        Assert.assertEquals(1, serverAddressProvider.keyFetchCount)
        Assert.assertEquals(0, serverAddressProvider.altKeyFetchCount)
        Assert.assertEquals(4, connectionStates.size)
        Assert.assertEquals(ConnectionState.CONNECTING, connectionStates[0])
        Assert.assertEquals(ConnectionState.CONNECTED, connectionStates[1])
        Assert.assertEquals(ConnectionState.LOGGEDIN, connectionStates[2])
        Assert.assertEquals(ConnectionState.DISCONNECTED, connectionStates[3])
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
        Assert.assertEquals(1, serverAddressProvider.keyFetchCount)
        Assert.assertEquals(1, serverAddressProvider.altKeyFetchCount)
        Assert.assertEquals(4, connectionStates.size)
        Assert.assertEquals(ConnectionState.CONNECTING, connectionStates[0])
        Assert.assertEquals(ConnectionState.CONNECTED, connectionStates[1])
        Assert.assertEquals(ConnectionState.LOGGEDIN, connectionStates[2])
        Assert.assertEquals(ConnectionState.DISCONNECTED, connectionStates[3])
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
        val serverHello = serverHelloNaCl.encrypt(tskPublic + cck, serverNonce.nextNonce())
        testSocket.write(sck + serverHello)
    }

    private fun expectClientLogin() {
        val extensionLength = expectLoginBox()
        expectExtensionBox(extensionLength)
    }

    private fun expectLoginBox(): Int {
        // expect `login`
        val loginBox = testSocket.read(144)
        val loginBoxDecrypted = kClientServer.decrypt(loginBox, clientNonce.nextNonce())
        Assert.assertNotNull(loginBoxDecrypted)
        val decryptedLoginStream = ByteArrayInputStream(loginBoxDecrypted)

        // read identity
        val clientIdentity = decryptedLoginStream.readNBytes(8).decodeToString()
        Assert.assertEquals(TestIdentityStore.CLIENT_IDENTITY, clientIdentity)

        // read extension-indicator
        val extensionIndicator = decryptedLoginStream.readNBytes(30).decodeToString()
        Assert.assertEquals("threema-clever-extension-field", extensionIndicator)
        val bb = ByteBuffer.wrap(decryptedLoginStream.readNBytes(2))
        bb.order(ByteOrder.LITTLE_ENDIAN)
        val extensionLength = bb.short.toUShort().toInt()

        // read repeated sck
        val repeatedSck = decryptedLoginStream.readNBytes(16)
        Assert.assertArrayEquals(sck, repeatedSck)

        // read reserved1
        val reserved1 = decryptedLoginStream.readNBytes(24)
        Assert.assertArrayEquals(ByteArray(24), reserved1)

        // read vouch
        val ss1 = NaCl(skSecret, TestIdentityStore.ckPublic).precomputed
        val ss2 = NaCl(tskSecret, TestIdentityStore.ckPublic).precomputed
        val vouchKey = ThreemaKDF("3ma-csp").deriveKey("v2", ss1 + ss2)
        val expectedVouch = Blake2b.Mac.newInstance(vouchKey, 32).digest(sck + tckPublic)
        val vouch = decryptedLoginStream.readNBytes(32)
        Assert.assertArrayEquals(expectedVouch, vouch)

        // read reserved2
        val reserved2 = decryptedLoginStream.readNBytes(16)
        Assert.assertArrayEquals(ByteArray(16), reserved2)

        // assert login data consumed
        Assert.assertEquals(0, decryptedLoginStream.available())

        return extensionLength
    }

    private fun expectExtensionBox(length: Int) {
        // read extension box
        val extensionBox = testSocket.read(length)
        val extensionBoxDecrypted = kClientServer.decrypt(extensionBox, clientNonce.nextNonce())
        Assert.assertNotNull(extensionBoxDecrypted)
    }

    private fun sendLoginAck() {
        val reservedBox = kClientServer.encrypt(ByteArray(16), serverNonce.nextNonce())
        testSocket.write(reservedBox)
    }

    private fun prepareServerKeys(skPublic: ByteArray, skSecret: ByteArray) {
        this.skPublic = skPublic
        this.skSecret = skSecret
        sck = ByteArray(16)
        random.nextBytes(sck)
        tskPublic = ByteArray(NaCl.PUBLICKEYBYTES)
        tskSecret = ByteArray(NaCl.SECRETKEYBYTES)
        NaCl.genkeypair(tskPublic, tskSecret)
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
                connectionState
            )
        }
        return connectionStates
    }
}
