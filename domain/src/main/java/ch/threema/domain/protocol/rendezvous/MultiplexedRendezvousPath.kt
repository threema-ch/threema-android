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

import ch.threema.base.utils.LoggingUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import java.io.IOException

typealias MultiplexedBytes = Pair<UInt, ByteArray>

private val logger = LoggingUtil.getThreemaLogger("MultiplexedRendezvousPath")

internal class MultiplexedRendezvousPath(
    paths: Map<UInt, RendezvousPath>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val paths = paths.toMutableMap()
    private val readBytes = Channel<MultiplexedBytes>(Channel.UNLIMITED)

    private val readJobs: MutableMap<UInt, Job> = mutableMapOf()

    /**
     * @throws IOException if there are no paths to connect to
     */
    suspend fun connect() {
        if (paths.isEmpty()) {
            throw IOException("Cannot connect as there are no paths")
        }
        paths.values.forEach { path ->
            setupReading(path)
        }
    }

    /**
     * Close _all_ paths contained in this multiplexed path.
     * If all but a single path should be closed, use [nominate]
     */
    fun closeAll() {
        closePaths(paths.values)
    }

    /**
     * Get a single path contained in this multiplexed path and close all other paths.
     * Reading of _all_ paths will be stopped. For further reading the nominated paths'
     * [RendezvousPath.close] method should be used.
     *
     * If no path with the provided [pid] is found, all paths will be closed and and exception is thrown.
     */
    suspend fun nominate(pid: UInt): RendezvousPath {
        val otherPaths = paths.entries
            .filter { it.key != pid }
            .map { it.value }
        closePaths(otherPaths)
        logger.trace("Cancel reading jobs")
        readJobs.values.forEach { it.cancelAndJoin() }
        readJobs.clear()
        val nominatedPath = paths[pid] ?: throw RendezvousException("No path with pid=`$pid` found")
        paths.clear()
        return nominatedPath
    }

    /**
     * Write [data] to this [MultiplexedRendezvousPath].
     * Note: If no path with the requested pid is found, but there are remaining open paths
     * [data] is silently ignored.
     *
     * @throws IOException if no open paths are available
     */
    suspend fun write(data: MultiplexedBytes) {
        val (pid, bytes) = data
        val path = getPath(pid)
        if (path != null) {
            path.write(bytes)
        } else if (paths.isEmpty()) {
            throw IOException("No paths available")
        }
    }

    /**
     * Read the next bytes that are received by the multiplexed path.
     *
     * @throws IOException if there are no underlying [RendezvousPath]s.
     */
    suspend fun read(): MultiplexedBytes = try {
        readBytes.receive()
    } catch (e: ClosedReceiveChannelException) {
        throw IOException(e)
    }

    private fun closePaths(pathsToClose: Collection<RendezvousPath>) {
        // Use a defensive copy of the paths because it might be linked
        // to the [paths] map, which would lead to a ConcurrentModificationException
        pathsToClose.toList().forEach {
            it.close()
            removePath(it)
        }
    }

    private fun getPath(pid: UInt): RendezvousPath? {
        return paths[pid].also {
            if (it == null) {
                logger.warn("Attempt to access an unknown path (pid=$pid)")
            }
        }
    }

    private suspend fun setupReading(path: RendezvousPath) {
        val pid = path.pid
        logger.trace("Setup reading for pid={}", pid)
        if (readJobs.containsKey(pid)) {
            logger.warn("There is already a reading job for pid={}", pid)
            return
        }
        path.connect()
        readJobs[pid] = CoroutineScope(ioDispatcher).launch {
            try {
                while (true) {
                    readBytes.send(pid to path.read())
                }
            } catch (e: IOException) {
                logger.error("Path with pid={} was closed while reading; remove path", pid)
                removePath(path)
            }
        }
    }

    private fun removePath(path: RendezvousPath) {
        logger.info("Remove path with pid={}", path.pid)
        paths.remove(path.pid)
        if (paths.isEmpty()) {
            logger.info("No remaining paths. Close readBytes channel.")
            readBytes.close()
        }
    }
}

internal fun Map<UInt, RendezvousPath>.toMultiplexedPath(): MultiplexedRendezvousPath {
    return MultiplexedRendezvousPath(this)
}
