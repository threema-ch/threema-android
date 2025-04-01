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

package ch.threema.domain.protocol.rendezvous

import ch.threema.base.utils.LoggingUtil
import ch.threema.base.utils.chunked
import ch.threema.libthreema.PathProcessResult
import ch.threema.libthreema.PathStateUpdate
import ch.threema.libthreema.RendezvousProtocol
import ch.threema.protobuf.d2d.join.MdD2DJoin.NdToEd
import ch.threema.protobuf.d2d.rendezvous.MdD2DRendezvous.RendezvousInit
import kotlinx.coroutines.Deferred
import okhttp3.OkHttpClient

private val logger = LoggingUtil.getThreemaLogger("DeviceJoin.RendezvousConnection")

class RendezvousConnection private constructor(
    val rph: ByteArray,
    private val protocol: RendezvousProtocol,
    private val rendezvousPath: RendezvousPath
) {
    val closedSignal: Deferred<Unit> = rendezvousPath.closedSignal

    /**
     * @throws RendezvousException if the [DeviceJoinMessage] does not result in a valid outgoingFrame
     * @throws java.io.IOException if writing to the underlying [RendezvousPath] failed
     */
    suspend fun write(message: DeviceJoinMessage) {
        logger.debug("Sending ULP data (length={})", message.bytes.size)
        val result = protocol.createUlpFrame(message.bytes)

        if (result.stateUpdate != null) {
            logger.warn("Ignore unexpected state update")
        }

        if (result.incomingUlpData != null) {
            logger.warn("Ignore unexpected incoming ulp data")
        }

        if (result.outgoingFrame == null) {
            throw RendezvousException("Outgoing frame is missing")
        }

        result.outgoingFrame.chunked(1024 * 1024).forEach {
            rendezvousPath.write(it)
        }
    }

    /**
     * @throws java.io.IOException if reading from the underlying [RendezvousPath] failed.
     */
    suspend fun read(): DeviceJoinMessage {
        while (true) {
            val pid = rendezvousPath.pid
            protocol.addChunks(pid, listOf(rendezvousPath.read()))
            val result = protocol.processFrame(pid) ?: continue

            if (result.stateUpdate != null) {
                logger.warn("Ignore unexpected state update")
            }

            if (result.outgoingFrame != null) {
                logger.warn("Ignore unexpected outgoing frame")
            }

            if (result.incomingUlpData != null) {
                return result.incomingUlpData.decodeUlpData()
            }
        }
    }

    /**
     * Close the connection. If the connection has already been closed, this method has no effect.
     */
    fun close() {
        rendezvousPath.close()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RendezvousConnection) return false

        if (!rph.contentEquals(other.rph)) return false
        return rendezvousPath == other.rendezvousPath
    }

    override fun hashCode(): Int {
        var result = rph.contentHashCode()
        result = 31 * result + rendezvousPath.hashCode()
        return result
    }

    // TODO(ANDR-2696): Also decode EdToNd messages
    private fun ByteArray.decodeUlpData(): DeviceJoinMessage {
        try {
            val message = NdToEd.parseFrom(this)
            return when (message.contentCase!!) {
                NdToEd.ContentCase.REGISTERED -> DeviceJoinMessage.Registered()
                NdToEd.ContentCase.CONTENT_NOT_SET -> throw RendezvousException("NdToEd message has no content")
            }
        } catch (e: Exception) {
            throw RendezvousException("Cannot parse ulp data", e)
        }
    }

    companion object {
        private class DefaultRendezvousPathProvider(private val okHttpClient: OkHttpClient) :
            RendezvousPathProvider {
            override fun getPaths(rendezvousInit: RendezvousInit): Map<UInt, RendezvousPath> {
                return getPaths(okHttpClient, rendezvousInit)
            }

            private fun getPaths(
                okHttpClient: OkHttpClient,
                rendezvousInit: RendezvousInit
            ): Map<UInt, RendezvousPath> {
                if (rendezvousInit.hasDirectTcpServer()) {
                    logger.info("Ignore unsupported direct tcp server")
                    logger.debug("Ignored direct tcp server: {}", rendezvousInit.directTcpServer)
                }
                if (!rendezvousInit.hasRelayedWebSocket()) {
                    throw RendezvousException("No relayed web socket provided")
                }
                return mapOf(
                    rendezvousInit.relayedWebSocket.let {
                        it.pathId.toUInt() to WebSocketRendezvousPath(
                            it.pathId.toUInt(),
                            okHttpClient,
                            it.url
                        )
                    }
                )
            }
        }

        suspend fun connect(
            okHttpClient: OkHttpClient,
            rendezvousInit: RendezvousInit
        ): RendezvousConnection {
            return connect(DefaultRendezvousPathProvider(okHttpClient), rendezvousInit)
        }

        private suspend fun connect(
            rendezvousPathProvider: RendezvousPathProvider,
            rendezvousInit: RendezvousInit
        ): RendezvousConnection {
            val paths = rendezvousPathProvider.getPaths(rendezvousInit)

            val protocol = RendezvousProtocol.newAsRrd(
                true,
                rendezvousInit.ak.toByteArray(),
                paths.keys.toList()
            )

            val multiplexedPath = paths.toMultiplexedPath()
            return try {
                multiplexedPath.connect()
                logger.debug("Connected")

                // Send initial frames
                protocol.initialOutgoingFrames()?.let { frames ->
                    logger.debug("Send {} initial outgoing frames", frames.size)
                    frames.forEach { frame ->
                        multiplexedPath.write(frame.pid to frame.frame)
                    }
                }

                runNominationLoop(protocol, multiplexedPath)
            } catch (e: Exception) {
                logger.warn("Rendezvous connection failed. Close all possible paths.")
                multiplexedPath.closeAll()
                throw e
            }
        }

        /**
         * Run the nomination loop where we run the handshakes simultaneously over all
         * available paths until we have nominated one path.
         */
        private suspend fun runNominationLoop(
            protocol: RendezvousProtocol,
            multiplexedPath: MultiplexedRendezvousPath
        ): RendezvousConnection {
            logger.info("Entering nomination loop")
            while (true) {
                val (pid, incomingFrame) = multiplexedPath.read()
                protocol.addChunks(pid, listOf(incomingFrame))
                var result: PathProcessResult? = protocol.processFrame(pid)

                while (result != null) {
                    if (result.incomingUlpData != null) {
                        logger.warn("Unexpected incoming ULP data in nomination loop")
                    }

                    result.outgoingFrame?.let {
                        multiplexedPath.write(pid to it)
                    }

                    when (val update = result.stateUpdate) {
                        is PathStateUpdate.AwaitingNominate -> {
                            // Check if we should nominate the path
                            // TODO(ANDR-2691): Choose the _best_ path based on the measured RTT
                            logger.debug(
                                "Path ready to nominate (measuredRttMs={})",
                                update.measuredRttMs
                            )
                            result = if (protocol.isNominator()) {
                                try {
                                    protocol.nominatePath(pid)
                                } catch (e: Exception) {
                                    multiplexedPath.closeAll()
                                    throw RendezvousException("Unable to nominate path", e)
                                }
                            } else {
                                null
                            }
                        }

                        is PathStateUpdate.Nominated -> {
                            val nominated = multiplexedPath.nominate(pid)
                            logger.info("Nomination complete, rendezvous connection established")
                            return RendezvousConnection(
                                update.rph,
                                protocol,
                                nominated
                            )
                        }

                        null -> result = null
                    }
                }
            }
        }
    }
}
