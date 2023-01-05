/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2023 Threema GmbH
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

package ch.threema.app.voip.groupcall.sfu

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import ch.threema.app.voip.groupcall.GroupCallDescription
import ch.threema.app.voip.groupcall.GroupCallThreadUtil
import ch.threema.app.voip.groupcall.sfu.messages.P2PMessageContent
import ch.threema.app.voip.groupcall.sfu.messages.P2POuterEnvelope
import ch.threema.app.voip.groupcall.sfu.messages.P2SMessage
import ch.threema.app.voip.groupcall.sfu.webrtc.ConnectionCtx
import ch.threema.app.voip.groupcall.sfu.webrtc.ParticipantCallMediaKeyState
import ch.threema.base.utils.LoggingUtil
import ch.threema.base.utils.Utils
import ch.threema.protobuf.groupcall.CallState
import ch.threema.protobuf.groupcall.ParticipantToParticipant
import com.neilalexander.jnacl.NaCl
import org.apache.commons.io.EndianUtils
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val logger = LoggingUtil.getThreemaLogger("GroupCallContext")

@WorkerThread
internal class GroupCallContext(
    val connectionCtx: ConnectionCtx,
    private val localParticipant: LocalParticipant
) {

    private val p2pHandshakes: MutableMap<ParticipantId, P2PHandshake> = mutableMapOf()

    @AnyThread
    fun sendMessageToSfu(provider: ConnectionCtx.P2SMessageProvider) {
        connectionCtx.sendMessageToSfu(provider)
    }

    /**
     * Set the [P2PHandshake] for a participant. If there already is a handshake for this [ParticipantId],
     * it will be removed and cancelled before adding the provided handshake.
     */
    @WorkerThread
    fun setHandshake(participantId: ParticipantId, handshake: P2PHandshake) {
        GroupCallThreadUtil.assertDispatcherThread()

        synchronized(p2pHandshakes) {
            logger.info("Set handshake for {}", participantId)
            p2pHandshakes.remove(participantId)?.cancel()
            p2pHandshakes[participantId] = handshake
        }
    }

    @WorkerThread
    fun removeParticipant(participantId: ParticipantId): NormalRemoteParticipant? {
        GroupCallThreadUtil.assertDispatcherThread()

        return synchronized(p2pHandshakes) {
            logger.info("Remove and cancel handshake with {}", participantId)
            p2pHandshakes.remove(participantId)?.also { it.cancel() }
                ?.let {
                    if (it.isDone) {
                        it.p2pContexts.remote.participant
                    } else {
                        null
                    }
                }
        }
    }

    /**
     * Send a [P2PMessageContent] to all participants
     */
    @AnyThread
    fun sendBroadcast(message: P2PMessageContent) {
        val p2pContexts = getP2PContexts()
            .toSet()

        logger.info("Send P2P broadcast to {} participants", p2pContexts.size)

        p2pContexts.forEach { sendMessageToSfu { it.createP2PMessage(message) } }
    }

    @WorkerThread
    fun sendRekeyBroadcast(state: ParticipantCallMediaKeyState) {
        GroupCallThreadUtil.assertDispatcherThread()

        val message = P2PMessageContent.MediaKey.fromState(state)
        synchronized(p2pHandshakes) { p2pHandshakes.values.asSequence() }
            .forEach {
                if (it.isDone) {
                    sendMessageToSfu {
                        logger.debug("Sending Rekey {}", state)
                        it.p2pContexts.createP2PMessage(message)
                    }
                } else {
                    it.queueRekey(message)
                }
            }
    }

    @WorkerThread
    fun getHandshake(remoteParticipantId: ParticipantId): P2PHandshake? {
        GroupCallThreadUtil.assertDispatcherThread()

        return synchronized(p2pHandshakes) { p2pHandshakes[remoteParticipantId] }
    }

    @WorkerThread
    fun sendCallStateUpdateToSfu(callDescription: GroupCallDescription) {
        GroupCallThreadUtil.assertDispatcherThread()

        logger.info("Create call state update and send to sfu")
        val callState = createCallState()
        val encryptedState = callDescription.encryptWithGcsk(callState.toByteArray())
        P2SMessage.UpdateCallState(encryptedState)
        sendMessageToSfu { P2SMessage.UpdateCallState(encryptedState) }
    }

    @WorkerThread
    fun isDesignatedToUpdateCallState(): Boolean {
        GroupCallThreadUtil.assertDispatcherThread()

        val participants = getAllCallParticipants()
            .groupBy { it is NormalParticipant }

        if (participants[false]?.isNotEmpty() == true) {
            logger.warn("Guest participants is not empty but not supported yet")
        }

        val candidates = participants[true] ?: participants[false] ?: emptyList()

        if (candidates.isEmpty()) {
            logger.warn("No candidates for call state update")
        }

        return candidates.minByOrNull { it.id.id } == localParticipant
    }

    @WorkerThread
    private fun createCallState(): CallState {
        return GroupCallState(
            localParticipant.id,
            Date().time.toULong(),
            getAllCallParticipants()
        ).toProtobuf()
    }

    @AnyThread
    private fun getAllCallParticipants(): Set<Participant> {
        return getP2PContexts()
            .map { it.remote.participant }
            .toSet() + localParticipant
    }

    @AnyThread
    private fun getP2PContexts(): Sequence<P2PContexts> {
        return synchronized(p2pHandshakes) { p2pHandshakes.values.asSequence() }
            .filter { it.isDone }
            .map { it.p2pContexts }
    }
}

data class P2PContexts(
    val local: LocalP2PContext,
    val remote: RemoteP2PContext,
) {
    private val naCl: NaCl by lazy { NaCl(local.pckPrivate, remote.pckPublic) }

    @WorkerThread
    fun decryptMessage(messageContent: ByteArray): P2PMessageContent? {
        GroupCallThreadUtil.assertDispatcherThread()

        val envelope = naCl.decrypt(messageContent, remote.nextPcckNonce())?.let { decrypted ->
            ParticipantToParticipant.Envelope.parseFrom(decrypted)
        }
        return when {
            envelope == null -> null
            envelope.hasCaptureState() -> P2PMessageContent.CaptureState.fromProtobuf(envelope.captureState)
            envelope.hasRekey() -> P2PMessageContent.MediaKey.fromProtobuf(envelope.rekey)
            else -> {
                logger.warn("Cannot map P2P message")
                null
            }
        }
    }

    @WorkerThread
    fun createP2PMessage(message: P2PMessageContent): P2POuterEnvelope {
        GroupCallThreadUtil.assertDispatcherThread()

        val encrypted = encryptMessage(message)
        return P2POuterEnvelope(local.participant.id, remote.participant.id, encrypted)
    }

    private fun encryptMessage(message: P2PMessageContent): ByteArray {
        val envelopeBytes = message.toProtobufEnvelope().toByteArray()
        return naCl.encrypt(envelopeBytes, local.nextPcckNonce())
    }
}

sealed class P2PContext(val pckPublic: ByteArray, val pcck: ByteArray) {
    // The first used sequence number is 1
    private var pcsn: ULong = 1UL
    private val pcsnLock = ReentrantLock()

    @WorkerThread
    fun nextPcckNonce(): ByteArray {
        GroupCallThreadUtil.assertDispatcherThread()

        // Read and increment pcsn
        val currentPcsn = pcsnLock.withLock { pcsn++ }
        // Multibyte data (such as long) is always stored in big-endian order on the jvm
        // (https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html).
        // Therefore we need to swap endianness as the protocol specifies a little endian sequence number
        val swappedPcsn = EndianUtils.swapLong(currentPcsn.toLong())
        return pcck + Utils.longToByteArray(swappedPcsn)
    }
}

class RemoteP2PContext(
    val participant: NormalRemoteParticipant,
    pckPublic: ByteArray,
    pcck: ByteArray
) : P2PContext(pckPublic, pcck)

class LocalP2PContext(
    val participant: LocalParticipant,
    val pckPrivate: ByteArray,
    pcck: ByteArray
) : P2PContext(NaCl.derivePublicKey(pckPrivate), pcck)
