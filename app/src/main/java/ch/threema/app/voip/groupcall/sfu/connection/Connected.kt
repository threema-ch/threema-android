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

package ch.threema.app.voip.groupcall.sfu.connection

import androidx.annotation.WorkerThread
import ch.threema.app.voip.groupcall.GroupCallException
import ch.threema.app.voip.groupcall.GroupCallThreadUtil
import ch.threema.app.voip.groupcall.sfu.*
import ch.threema.app.voip.groupcall.sfu.messages.P2PMessageContent
import ch.threema.app.voip.groupcall.sfu.messages.P2POuterEnvelope
import ch.threema.app.voip.groupcall.sfu.messages.P2SMessage
import ch.threema.app.voip.groupcall.sfu.messages.S2PMessage
import ch.threema.app.voip.groupcall.sfu.webrtc.ConnectionCtx
import ch.threema.app.webrtc.PeerConnectionObserver
import ch.threema.app.webrtc.SaneDataChannelObserver
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.ProtocolDefines
import com.google.protobuf.InvalidProtocolBufferException
import java8.util.function.Function
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.webrtc.DataChannel

private val logger = LoggingUtil.getThreemaLogger("GroupCallConnectionState.Connected")

class Connected internal constructor(
    call: GroupCall,
    private val participant: LocalParticipant
) : GroupCallConnectionState(StateName.CONNECTED, call) {
    private val stopCallSignal: CompletableDeferred<GroupCallConnectionState> = CompletableDeferred()

    private val ctx = call.context.connectionCtx
    private val updateCallMutex = Mutex()

    private val p2pHandshakeFactory = P2PHandshake.P2PHandshakeFactory(participant, call)

    private var callStateUpdateJob: Job? = null
    private var pendingPcmkJob: Job? = null

    @WorkerThread
    override fun getStateProviders() = listOf(
        this::observeCallEnd,
        this::getNextState
    )

    @WorkerThread
    private suspend fun getNextState(): GroupCallConnectionState {
        GroupCallThreadUtil.assertDispatcherThread()

        call.addTeardownRoutine {
            removeP2sDataChannelObserver()
            stopCallStateUpdateInterval()
            cancelPendingPcmkReplacement()
        }

        // Set initial (remote) participants
        call.updateParticipants(GroupCall.ParticipantsUpdate.empty())

        logger.trace("Replace peer connection observer")
        ctx.pc.observer.replace(PeerConnectionObserver(
            addTransceiver = ctx.pc::addTransceiverFromEvent,
            failedSignal = stopCallSignal,
        ))

        logger.trace("Set data channel observer")
        setP2sDataChannelObserver()

        // Update the call
        logger.trace("Update call initiated")
        val (_, participantIds) = call.connectedSignal.await()
        if (participantIds.isNotEmpty()) {
            logger.info("Adding initial participants {} to call", participantIds.map { it.id })
            updateCallMutex.withLock {
                ctx.updateCall(call, remove = mutableSetOf(), add = participantIds.toMutableSet())
                performInitialHandshakes(participantIds)
            }
        } else {
            // nobody else is in the call, start sending call state updates
            restartCallStateUpdateInterval()
        }

        return stopCallSignal.await()
    }

    @WorkerThread
    private fun setP2sDataChannelObserver() {
        GroupCallThreadUtil.assertDispatcherThread()

        ctx.p2s.observer.replace(object : SaneDataChannelObserver {
            override fun onStateChange(state: DataChannel.State) {
                // Note: Not dispatching here because no thread unsafe variables are accessed and
                //       we need to know ASAP in case the data channel has been closed.

                logger.debug("P2S data channel state: {}", state.name)
                when (state) {
                    DataChannel.State.CLOSING, DataChannel.State.CLOSED ->
                        stopCallSignal.completeExceptionally(GroupCallException("P2S data channel closed"))
                    else -> {
                        // noop
                    }
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                logger.trace("P2S data channel incoming message (length={}, binary={})",
                    buffer.data.remaining(), buffer.binary)

                if (call.callLeftSignal.isCompleted) {
                    logger.debug("Call already left, ignore incoming P2S message")
                    return
                }

                val message = try {
                    S2PMessage.decode(buffer)
                } catch (e: InvalidProtocolBufferException) {
                    logger.warn("Invalid S2P message, could not decode protobuf", e)
                    return
                }

                // Note: Dispatching here is required to keep the decryption sequence numbers
                //       guarded by the dispatcher thread.
                CoroutineScope(GroupCallThreadUtil.DISPATCHER).launch {
                    when (message) {
                        is P2POuterEnvelope -> handleP2PMessage(message)
                        is S2PMessage.SfuParticipantJoined -> handleJoinMessage(message)
                        is S2PMessage.SfuParticipantLeft -> handleLeaveMessage(message)
                        is S2PMessage.SfuHello -> logger.warn("Unexpected SfuHello")
                    }
                }
            }
        })
    }

    @WorkerThread
    private fun removeP2sDataChannelObserver() {
        GroupCallThreadUtil.assertDispatcherThread()

        logger.debug("Remove P2S data channel observer")
        ctx.p2s.observer.replace(null)
    }

    /**
     * Create a new P2PHandshake state machine for the provided participant.
     * If there already is a handshake for this [ParticipantId] it will be
     * removed and cancelled prior to creating a new handshake.
     */
    @WorkerThread
    private fun createHandshake(remoteParticipantId: ParticipantId, supplier: Function<ParticipantId, P2PHandshake>): P2PHandshake {
        GroupCallThreadUtil.assertDispatcherThread()

        return supplier.apply(remoteParticipantId).also {
            call.context.setHandshake(remoteParticipantId, it)
        }
    }

    @WorkerThread
    private fun handleP2PMessage(message: P2POuterEnvelope) {
        GroupCallThreadUtil.assertDispatcherThread()

        if (message.receiverId != participant.id) {
            logger.warn("Received P2P message for wrong participant {}", message.receiverId)
        } else {
            val handshake = call.context.getHandshake(message.senderId)
            if (handshake == null) {
                logger.info("Ignore P2P message from unknown sender {}", message.senderId)
            } else if (handshake.isDone) {
                logger.info("P2P non handshake message from {} to {}", message.senderId, message.receiverId)
                val contexts = handshake.p2pContexts
                val decryptedMessage = contexts.decryptMessage(message.encryptedData)
                if (decryptedMessage == null) {
                    logger.warn("Failed to decrypt p2p-message")
                } else {
                    handleP2PMessageContent(contexts.remote.participant, decryptedMessage)
                }
            } else {
                logger.trace("Handle P2P handshake message")
                handshake.handleMessage(message)
            }
        }
    }

    @WorkerThread
    private fun handleP2PMessageContent(
        sender: NormalRemoteParticipant,
        message: P2PMessageContent
    ) {
        GroupCallThreadUtil.assertDispatcherThread()

        logger.trace("handle P2P message {}", message)
        when (message) {
            is P2PMessageContent.CaptureState -> handleCaptureState(sender, message)
            is P2PMessageContent.MediaKey -> handleRekey(sender, message)
        }
    }

    @WorkerThread
    private fun handleCaptureState(sender: NormalRemoteParticipant, captureState: P2PMessageContent.CaptureState) {
        GroupCallThreadUtil.assertDispatcherThread()

        logger.debug("Received capture state from {}: {}", sender.id, captureState)
        when (captureState) {
            is P2PMessageContent.CaptureState.Camera -> sender.cameraActive = captureState.active
            is P2PMessageContent.CaptureState.Microphone -> sender.microphoneActive = captureState.active
        }
        call.updateCaptureStates()
    }

    @WorkerThread
    private fun handleRekey(sender: NormalRemoteParticipant, rekey: P2PMessageContent.MediaKey) {
        GroupCallThreadUtil.assertDispatcherThread()

        logger.debug("Received rekey from {}: {}", sender.id, rekey)
        addDecryptorPcmks(sender.id, listOf(rekey))
    }

    @WorkerThread
    private suspend fun handleJoinMessage(message: S2PMessage.SfuParticipantJoined) {
        GroupCallThreadUtil.assertDispatcherThread()

        logger.info("Scheduling to add participant '{}' to call", message.participantId)

        // Other participants could send handshake messages before the mutex lock can be acquired.
        // Therefore we prepare the handshake right away, before updating the call.
        val handshake = createHandshakeForNewParticipant(message.participantId)

        updateCallMutex.withLock {
            addParticipantToCall(message.participantId, handshake)
        }
    }

    @WorkerThread
    private suspend fun handleLeaveMessage(message: S2PMessage.SfuParticipantLeft) {
        GroupCallThreadUtil.assertDispatcherThread()

        logger.info("Scheduling to remove participant '{}' from call", message.participantId)
        updateCallMutex.withLock {
            removeParticipantFromCall(message.participantId)
        }
    }

    @WorkerThread
    private fun performInitialHandshakes(participantIds: Collection<ParticipantId>) {
        GroupCallThreadUtil.assertDispatcherThread()

        logger.debug("Perform handshakes for initial participants {}", participantIds)
        participantIds
            .map { createHandshake(it, p2pHandshakeFactory::initHandshakeWithExistingParticipant) }
            .forEach { runPostHandshakeStepsOnHandshakeComplete(it) }
    }

    @WorkerThread
    private fun createHandshakeForNewParticipant(participantId: ParticipantId): P2PHandshake {
        GroupCallThreadUtil.assertDispatcherThread()

        return createHandshake(participantId, p2pHandshakeFactory::createForNewParticipant)
    }

    @WorkerThread
    private fun runPostHandshakeStepsOnHandshakeComplete(
        handshake: P2PHandshake,
    ) {
        CoroutineScope(GroupCallThreadUtil.DISPATCHER).launch {
            waitForHandshakeComplete(handshake)?.also { (contexts, mediaKeys) ->
                performPostHandshakeSteps(contexts, mediaKeys)
            }
        }
    }

    @WorkerThread
    private suspend fun waitForHandshakeComplete(handshake: P2PHandshake): Pair<P2PContexts, List<P2PMessageContent.MediaKey>>? {
        GroupCallThreadUtil.assertDispatcherThread()

        return try {
            handshake.handshakeCompletedSignal.await()
        } catch (e: Exception) {
            logger.warn("Handshake with {} failed", handshake.receiverId, e)
            null
        }
    }

    @WorkerThread
    private fun performPostHandshakeSteps(contexts: P2PContexts, mediaKeys: List<P2PMessageContent.MediaKey>) {
        GroupCallThreadUtil.assertDispatcherThread()

        addDecryptorPcmks(contexts.remote.participant.id, mediaKeys)
        refreshCallStateUpdateInterval()
        call.updateParticipants(GroupCall.ParticipantsUpdate.addParticipant(contexts.remote.participant))
        subscribeMicrophone(contexts)
        sendCurrentCaptureStates(contexts)
    }

    @WorkerThread
    private fun subscribeMicrophone(contexts: P2PContexts) {
        GroupCallThreadUtil.assertDispatcherThread()

        call.context.sendMessageToSfu { P2SMessage.SubscribeParticipantMicrophone(contexts.remote.participant.id) }
    }

    @WorkerThread
    private fun sendCurrentCaptureStates(contexts: P2PContexts) {
        GroupCallThreadUtil.assertDispatcherThread()

        if (participant.cameraActive) {
            val cameraState = P2PMessageContent.CaptureState.Camera(true)
            sendP2PMessage { contexts.createP2PMessage(cameraState) }
        }
        if (participant.microphoneActive) {
            val microphoneState = P2PMessageContent.CaptureState.Microphone(true)
            sendP2PMessage { contexts.createP2PMessage(microphoneState) }
        }
    }

    @WorkerThread
    private fun sendP2PMessage(provider: ConnectionCtx.P2SMessageProvider) {
        GroupCallThreadUtil.assertDispatcherThread()

        call.context.sendMessageToSfu(provider)
    }

    @WorkerThread
    private fun addDecryptorPcmks(participantId: ParticipantId, mediaKeys: List<P2PMessageContent.MediaKey>) {
        GroupCallThreadUtil.assertDispatcherThread()

        // Add decryptor PCMK (for inbound media frames)
        val decryptor = ctx.frameCrypto.getDecryptor(participantId.id.toShort())!!
        mediaKeys.forEach {
            decryptor.addPcmk(it.pcmk, it.epoch.toShort(), it.ratchetCounter.toShort())
        }
    }

    /** May only be called with `updateCallMutex` held! */
    @WorkerThread
    private suspend fun addParticipantToCall(participantId: ParticipantId, handshake: P2PHandshake) {
        GroupCallThreadUtil.assertDispatcherThread()

        logger.info("Adding participant '{}' to call", participantId)
        increaseRatchetCounter()
        ctx.updateCall(call, add = mutableSetOf(participantId), remove = mutableSetOf())
        runPostHandshakeStepsOnHandshakeComplete(handshake)
    }

    /** May only be called with `updateCallMutex` held! */
    @WorkerThread
    private suspend fun removeParticipantFromCall(participantId: ParticipantId) {
        GroupCallThreadUtil.assertDispatcherThread()

        logger.info("Removing participant '{}' from call", participantId)
        call.context.removeParticipant(participantId)?.let {
            call.updateParticipants(GroupCall.ParticipantsUpdate.removeParticipant(it))
            ctx.updateCall(call, remove = mutableSetOf(it.id), add = mutableSetOf())
            increaseEpoch()
        }
        refreshCallStateUpdateInterval()
    }

    /** May only be called with `updateCallMutex` held! */
    @WorkerThread
    private fun increaseEpoch() {
        GroupCallThreadUtil.assertDispatcherThread()

        // Start the process of replacing the PCMK (for outbound media frames)
        // with a new epoch and random PCMK.
        val currentEpoch = ctx.pcmk.current.epoch
        val pending = ctx.pcmk.nextEpoch()
        if (pending.stale) {
            // The pending PCMK is marked _stale_. We don't need to do anything,
            // a subsequent call to this function will be made once the pending
            // PCMK has been applied.
            logger.debug("Pending PCMK now marked as stale (epoch $currentEpoch -> ${pending.state.epoch})")
            return
        }
        logger.debug("Replacing PCMK (epoch $currentEpoch -> ${pending.state.epoch})")

        // Distribute the new PCMK to all existing participants, excluding the
        // one that has been removed.
        call.context.sendRekeyBroadcast(pending.state)

        // Delay applying the PCMK by 2s to hopefully prevent our media
        // frames from being dropped when another participant receives
        // it because the keys had not yet been received.
        //
        pendingPcmkJob = pendingPcmkJob.let { job ->
            // Note: Considered unreachable, guarded by the 'stale' property
            job?.cancel("Another PCMK replacement was still pending")

            // Replace in 2s
            CoroutineScope(GroupCallThreadUtil.DISPATCHER).launch {
                delay(2_000)
                pendingPcmkJob = null

                // Apply it
                pending.state.also {
                    ctx.frameCrypto.encryptor.setPcmk(it.pcmk, it.epoch.toShort(), it.ratchetCounter.toShort())
                }
                pending.applied()
                logger.debug("PCMK was replaced (epoch $currentEpoch -> ${pending.state.epoch})")

                // If it was marked as _stale_, rerun the whole process.
                if (pending.stale) {
                    logger.debug("Replaced PCMK was stale, rerunning the process")
                    increaseEpoch()
                }
            }
        }
    }

    /** May only be called with `updateCallMutex` held! */
    @WorkerThread
    private fun increaseRatchetCounter() {
        GroupCallThreadUtil.assertDispatcherThread()

        // Update PCMK (for outbound media frames) with a new ratchet
        // round and apply it immediately.
        //
        // Note: The other participants will automatically notice since
        //       the ratchet counter is sent with each media frame.
        val previousRatchetCounter = ctx.pcmk.current.ratchetCounter
        val state = ctx.pcmk.nextRatchetCounter()
        logger.debug("Applied PCMK ratchet (ratchet counter $previousRatchetCounter -> ${state.ratchetCounter})")
        ctx.frameCrypto.encryptor.setPcmk(state.pcmk, state.epoch.toShort(), state.ratchetCounter.toShort())
    }

    @WorkerThread
    private fun cancelPendingPcmkReplacement() {
        GroupCallThreadUtil.assertDispatcherThread()

        pendingPcmkJob = pendingPcmkJob?.let {
            val message = "Cancelling PCMK replacement due to teardown"
            logger.debug(message)
            it.cancel(message)
            null
        }
    }

    @WorkerThread
    private fun refreshCallStateUpdateInterval() {
        GroupCallThreadUtil.assertDispatcherThread()

        if (call.context.isDesignatedToUpdateCallState()) {
            restartCallStateUpdateInterval()
        } else {
            stopCallStateUpdateInterval()
            logger.debug("Not designated to send call state updates")
        }
    }

    @WorkerThread
    private fun stopCallStateUpdateInterval() {
        GroupCallThreadUtil.assertDispatcherThread()

        callStateUpdateJob = callStateUpdateJob?.let {
            val message = "Cancelling call state update interval"
            logger.debug(message)
            it.cancel(message)
            null
        }
    }

    @WorkerThread
    private fun restartCallStateUpdateInterval() {
        GroupCallThreadUtil.assertDispatcherThread()

        stopCallStateUpdateInterval()
        logger.info("Start call state update interval")
        callStateUpdateJob = CoroutineScope(GroupCallThreadUtil.DISPATCHER).launch {
            while (true) {
                sendCallStateUpdate()
                delay(ProtocolDefines.GC_GROUP_CALL_UPDATE_PERIOD_SECONDS * 1_000)
            }
        }
    }

    @WorkerThread
    private fun sendCallStateUpdate() {
        GroupCallThreadUtil.assertDispatcherThread()

        if (call.callLeftSignal.isCompleted) {
            logger.info("Call has ended; stop sending call state updates")
            stopCallStateUpdateInterval()
        } else {
            call.context.sendCallStateUpdateToSfu(call.description)
        }
    }
}
