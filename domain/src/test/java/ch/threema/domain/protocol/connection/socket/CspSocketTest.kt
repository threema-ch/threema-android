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

package ch.threema.domain.protocol.connection.socket

import ch.threema.domain.protocol.connection.SingleThreadedServerConnectionDispatcher
import ch.threema.domain.protocol.connection.TestChatServerAddressProvider
import ch.threema.domain.protocol.connection.TestSocket
import ch.threema.domain.protocol.connection.csp.socket.CspSocket
import ch.threema.domain.protocol.connection.csp.socket.SocketFactory
import ch.threema.domain.protocol.csp.ProtocolDefines
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.Timeout

class CspSocketTest {
    private lateinit var testSocket: TestSocket

    // /* in order to debug the tests the timeout should be disabled
    @Rule
    @JvmField
    val timeout: Timeout = Timeout.seconds(5)
    // */

    @BeforeTest
    fun setUp() {
        testSocket = TestSocket()
    }

    @Test
    fun testSocketCloseWithoutIoProcessing() = runTest {
        val socket = createSocket()
        assertFalse(testSocket.closed)
        socket.connect()
        withContext(Dispatchers.Default) { delay(200) }
        socket.close(ServerSocketCloseReason("Close"))
        assertTrue(testSocket.closed)
    }

    @Test
    fun `test handling of handshake messages`() = test {
        // The handshake messages from the server have a fixed length.
        // Therefore the length is not prepended to the messages.
        // Make sure they are processed as separate messages with the correct length
        assertServerHello(it)
        assertServerLoginAck(it)
    }

    @Test
    fun `test correct handling of received frames with prepended length`() = test {
        simulateServerLoginMessages(it)

        // shortest valid message
        testDataFrame(it, 20u)

        // maximum allowed frame length according to protocol (8192)
        testDataFrame(it, ProtocolDefines.MAX_PKT_LEN.toUShort())

        // maximum length (with two bytes for length indication)
        testDataFrame(it, 0xffffu)

        testDataFrame(it, 0xffu) // 255u
        testDataFrame(it, 0x100u) // 256u
        testDataFrame(it, 0xff00u) // 65280
    }

    /**
     * Set the receiving part of the socket to the logged in state
     * by simulating server login messages.
     */
    private suspend fun simulateServerLoginMessages(socket: CspSocket) {
        testSocket.write(ByteArray(ProtocolDefines.SERVER_HELLO_LEN))
        receiveMessageAsync(socket).await()
        testSocket.write(ByteArray(ProtocolDefines.SERVER_LOGIN_ACK_LEN))
        receiveMessageAsync(socket).await()
    }

    private suspend fun testDataFrame(socket: CspSocket, length: UShort) {
        val msg = receiveMessageAsync(socket)
        testSocket.write(createFrame(length))

        val expected = ByteArray(length.toInt()) { length.toByte() }
        assertContentEquals(expected, msg.await())
    }

    private suspend fun assertServerHello(socket: CspSocket) {
        val serverHello = ByteArray(ProtocolDefines.SERVER_HELLO_LEN) { 0x1F }
        val msg = receiveMessageAsync(socket)
        testSocket.write(serverHello)
        val bytes = msg.await()
        assertContentEquals(serverHello, bytes)
    }

    private suspend fun assertServerLoginAck(socket: CspSocket) {
        val serverLoginAck = ByteArray(ProtocolDefines.SERVER_LOGIN_ACK_LEN) { 0x1F }
        val msg = receiveMessageAsync(socket)
        testSocket.write(serverLoginAck)
        assertContentEquals(serverLoginAck, msg.await())
    }

    private fun receiveMessageAsync(socket: CspSocket): Deferred<ByteArray?> {
        val frame = CompletableDeferred<ByteArray>()
        socket.source.setHandler { msg -> frame.complete(msg) }
        return frame
    }

    private fun createFrame(length: UShort): ByteArray {
        val lengthBytes = ByteBuffer.wrap(ByteArray(2))
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(length.toShort())
            .array()
        val data = ByteArray(length.toInt()) { length.toByte() }
        return lengthBytes + data
    }

    private fun test(testBody: suspend (CspSocket) -> Unit) = runTest {
        val socket = createSocket()
        assertFalse(testSocket.closed)
        socket.connect()
        launch(Dispatchers.Default) {
            testBody(socket)
            delay(100)
            socket.close(ServerSocketCloseReason("Close"))
            assertTrue(testSocket.closed)
        }
        // start processing io; the test will only complete _after_ this method returns
        // which must happen, when the socket has been closed.
        socket.processIo()
    }

    private fun createSocket(): CspSocket {
        val socketFactory = SocketFactory { testSocket }
        val addressProvider = TestChatServerAddressProvider()
        val dispatcher = SingleThreadedServerConnectionDispatcher(true)
        return CspSocket(
            socketFactory,
            addressProvider,
            CompletableDeferred(),
            dispatcher.coroutineContext,
        )
    }
}
