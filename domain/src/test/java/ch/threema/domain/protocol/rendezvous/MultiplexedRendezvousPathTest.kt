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

package ch.threema.domain.protocol.rendezvous

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.lang.RuntimeException

class MultiplexedRendezvousPathTest {

    @Test
    fun testConnect() {
        val paths = TestRendezvousPath.createPaths(5)

        val path = MultiplexedRendezvousPath(paths)

        paths.values.forEach {
            assertFalse(it.connected)
            assertFalse(it.closed)
        }

        runBlocking {
            path.connect()
        }

        paths.values.forEach {
            assertTrue(it.connected)
            assertFalse(it.closed)
        }
    }

    @Test
    fun testConnectAndCloseAll() {
        val paths = TestRendezvousPath.createPaths(5)

        val path = MultiplexedRendezvousPath(paths)

        paths.values.forEach {
            assertFalse(it.connected)
            assertFalse(it.closed)
        }

        runBlocking {
            path.connect()
        }

        paths.values.forEach {
            assertTrue(it.connected)
            assertFalse(it.closed)
        }

        runBlocking {
            path.closeAll()
        }

        paths.values.forEach {
            assertFalse(it.connected)
            assertTrue(it.closed)
        }
    }

    @Test
    fun testNominate() {
        val paths = TestRendezvousPath.createPaths(5)

        val path = MultiplexedRendezvousPath(paths)

        paths.values.forEach {
            assertFalse(it.connected)
            assertFalse(it.closed)
        }

        runBlocking {
            path.connect()
        }

        paths.values.forEach {
            assertTrue(it.connected)
            assertFalse(it.closed)
        }

        val expected = paths[3U]!!
        val nominated = runBlocking {
            path.nominate(expected.pid)
        }

        // Verify the correct path is nominated and not closed

        assertEquals(expected, nominated)
        assertEquals(3U, nominated.pid)
        assertTrue(expected.connected)
        assertFalse(expected.closed)

        // Verify all other paths are closed
        paths.values
            .filter { it.pid != 3U }
            .forEach {
                assertTrue(it.closed)
                assertFalse(it.connected)
            }

    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test(timeout = 1000)
    fun testWrite() {
        val paths = TestRendezvousPath.createPaths(5)

        val path = MultiplexedRendezvousPath(paths)

        paths.values.forEach {
            assertFalse(it.connected)
            assertFalse(it.closed)
        }

        runBlocking {
            path.connect()
        }

        paths.values.forEach {
            assertTrue(it.connected)
            assertFalse(it.closed)
        }

        runBlocking {
            paths.keys.forEach {
                path.write(it to byteArrayOf(it.toByte()))
            }

            paths.values.forEach {
                val bytes = it.writtenBytes.receive()
                assertArrayEquals(byteArrayOf(it.pid.toByte()), bytes)
                assertTrue(it.writtenBytes.isEmpty)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test(timeout = 1000)
    fun testRead() {
        val paths = TestRendezvousPath.createPaths(5)

        val path = MultiplexedRendezvousPath(paths)

        paths.values.forEach {
            assertFalse(it.connected)
            assertFalse(it.closed)
        }

        runBlocking {
            path.connect()
        }

        paths.values.forEach {
            assertTrue(it.connected)
            assertFalse(it.closed)
        }

        runBlocking {
            paths.values.forEach {
                it.readableBytes.send(byteArrayOf(it.pid.toByte()))
            }


            val readBytes = paths.map { path.read() }
                .associate { it }

            paths.values.forEach {
                assertArrayEquals(byteArrayOf(it.pid.toByte()), readBytes[it.pid])
                assertTrue(it.readableBytes.isEmpty)
            }
        }
    }

    @Test(expected = IOException::class)
    fun testNoPathsConnect() {
        val path = MultiplexedRendezvousPath(mapOf())

        runBlocking {
            path.connect()
        }
    }

    @Test
    fun testReadWithoutOpenPaths() {
        val pid = 0U
        val closedPath = TestClosedRendezvousPath(pid, CompletableDeferred())

        val path = MultiplexedRendezvousPath(mapOf(closedPath.pid to closedPath))

        runBlocking {
            path.connect()
        }

        assertThrows(IOException::class.java) {
            runBlocking {
                path.read()
            }
        }
    }

    @Test
    fun testWriteWithoutOpenPaths() {
        val pid = 0U
        val closedPath = TestClosedRendezvousPath(pid, CompletableDeferred())

        val path = MultiplexedRendezvousPath(mapOf(closedPath.pid to closedPath))

        runBlocking {
            path.connect()
        }

        assertThrows(IOException::class.java) {
            runBlocking {
                path.write(pid to ByteArray(32))
            }
        }
    }
}

private class TestClosedRendezvousPath(override val pid: UInt, override val closedSignal: CompletableDeferred<Unit>) : RendezvousPath {
    init {
        closedSignal.complete(Unit)
    }

    override suspend fun connect() {
        // noop
    }

    override fun close() {
        // noop
    }

    override suspend fun write(bytes: ByteArray) {
        throw IOException()
    }

    override suspend fun read(): ByteArray {
        throw IOException()
    }
}

private class TestRendezvousPath(override val pid: UInt, override val closedSignal: CompletableDeferred<Unit>) : RendezvousPath {
    var connected = false
    var closed = false
    val writtenBytes = Channel<ByteArray>(capacity = Channel.UNLIMITED, onBufferOverflow = BufferOverflow.SUSPEND)
    val readableBytes = Channel<ByteArray>(capacity = Channel.UNLIMITED, onBufferOverflow = BufferOverflow.SUSPEND)

    override suspend fun connect() {
        if (closed) {
            throw RuntimeException("Path has been closed")
        }
        connected = true
    }

    override fun close() {
        connected = false
        closed = true
        writtenBytes.close()
        readableBytes.close()
    }

    override suspend fun write(bytes: ByteArray) {
        writtenBytes.send(bytes)

    }

    override suspend fun read(): ByteArray {
        return readableBytes.receive()
    }

    companion object {
        fun createPaths(count: Int): Map<UInt, TestRendezvousPath> = (0 until count)
            .map { TestRendezvousPath(it.toUInt(), CompletableDeferred()) }
            .associateBy { it.pid }
    }
}
