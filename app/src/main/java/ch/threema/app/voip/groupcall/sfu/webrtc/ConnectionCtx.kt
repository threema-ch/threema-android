/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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

package ch.threema.app.voip.groupcall.sfu.webrtc

import android.content.Context
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.voip.groupcall.GroupCallThreadUtil
import ch.threema.app.voip.groupcall.sfu.*
import ch.threema.app.voip.groupcall.sfu.messages.P2SMessage
import ch.threema.app.voip.groupcall.sfu.messages.S2PMessage
import ch.threema.app.webrtc.*
import ch.threema.base.utils.getThreemaLogger
import com.google.protobuf.InvalidProtocolBufferException
import java.lang.IllegalStateException
import java.nio.ByteBuffer
import kotlinx.coroutines.*
import org.webrtc.*

private val logger = getThreemaLogger("ConnectionCtx")

@WorkerThread
internal class ConnectionCtx(
    connectedSignal: CompletableDeferred<Set<ParticipantId>>,
    private val context: Context,
    private val sessionParameters: SessionParameters,
    private var _pc: PeerConnectionCtx?,
    private val factory: FactoryCtx,
    private val ipv6enabled: Boolean,
    gckh: ByteArray,
) {
    /**
     * A lazy provider for messages that are sent to the sfu.
     *
     * To avoid encryption related race conditions, messages must only be encrypted when they are
     * fetched using the [get] method.
     * Otherwise it cannot be guaranteed, that they are sent in the same order as they are encrypted
     * which is paramount for decryption at the receivers end as a serial number is used in the nonce.
     */
    fun interface P2SMessageProvider {
        fun get(): P2SMessage
    }

    //region companion object
    companion object {
        @WorkerThread
        fun create(
            context: Context,
            call: GroupCall,
            sessionParameters: SessionParameters,
            certificate: RtcCertificatePem,
            connectedSignal: CompletableDeferred<Set<ParticipantId>>,
        ): ConnectionCtx {
            GroupCallThreadUtil.assertDispatcherThread()

            logger.trace("Starting")
            // Determine factory parameters
            val factoryParameters = determineFactoryParameters(
                call.parameters.aecMode,
                call.parameters.videoCodec,
            )
            val factory = FactoryCtx(context, factoryParameters)

            val peerConnectionCtx = createPeerConnection(
                certificate,
                factory,
            )

            return ConnectionCtx(
                connectedSignal,
                context,
                sessionParameters,
                peerConnectionCtx,
                factory,
                call.parameters.ipv6Enabled,
                call.description.gckh,
            )
        }

        @WorkerThread
        private fun determineFactoryParameters(
            aecMode: String,
            videoCodec: String,
        ): FactoryCtx.Parameters = FactoryCtx.Parameters(
            acousticEchoCancelerMode = when (aecMode) {
                "sw" -> FactoryCtx.Parameters.AecMode.SOFTWARE
                else -> FactoryCtx.Parameters.AecMode.HARDWARE
            },
            hardwareVideoCodecs = when (videoCodec) {
                PreferenceService.VIDEO_CODEC_SW,
                PreferenceService.VIDEO_CODEC_NO_VP8,
                -> emptySet()

                else -> setOf(FactoryCtx.Parameters.HardwareVideoCodec.VP8)
            },
        )

        @WorkerThread
        private fun createPeerConnection(
            certificate: RtcCertificatePem,
            factory: FactoryCtx,
        ): PeerConnectionCtx {
            // Configuration for the peer connection
            val configuration = getPeerConnectionConfiguration(certificate)

            val observer = WrappedPeerConnectionObserver()
            val dependencies = PeerConnectionDependencies.builder(observer)
                .createPeerConnectionDependencies()

            // Create peer connection
            val pc = factory.factory.createPeerConnection(configuration, dependencies)
            return PeerConnectionCtx(
                pc = pc!!,
                observer = observer,
            )
        }

        @WorkerThread
        private fun getPeerConnectionConfiguration(certificate: RtcCertificatePem): PeerConnection.RTCConfiguration {
            // Order taken from RTCConfiguration constructor. Docs for each parameter can be
            // found here:
            // https://source.chromium.org/chromium/chromium/src/+/main:third_party/webrtc/api/peer_connection_interface.h;bpv=1;bpt=1;l=311?q=turnPortPrunePolicy&ss=chromium%2Fchromium%2Fsrc&gsn=RTCConfigurationType&gs=kythe%3A%2F%2Fchromium.googlesource.com%2Fchromium%2Fsrc%3Flang%3Dc%252B%252B%3Fpath%3Dsrc%2Fthird_party%2Fwebrtc%2Fapi%2Fpeer_connection_interface.h%23EnglTqBUUO_2RhVLtcib-vrLxQfj1_fRrBgF1-1K3tE
            //
            // Note: We don't need any STUN servers because the server itself is publicly
            //       reachable.
            return PeerConnection.RTCConfiguration(emptyList()).also {
                it.certificate = certificate
                it.iceTransportsType = PeerConnection.IceTransportsType.ALL
                it.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
                it.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
                it.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
                it.candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.ALL
                it.keyType = PeerConnection.KeyType.ECDSA
                it.continualGatheringPolicy =
                    PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                it.turnPortPrunePolicy = PeerConnection.PortPrunePolicy.PRUNE_BASED_ON_PRIORITY
                it.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                it.cryptoOptions = CryptoOptions.builder()
                    .setEnableGcmCryptoSuites(true)
                    .setEnableAes128Sha1_32CryptoCipher(false)
                    .setEnableAes128Sha1_80CryptoCipher(false)
                    .setEnableEncryptedRtpHeaderExtensions(false)
                    .createCryptoOptions()
                it.offerExtmapAllowMixed = true // NEVER disable this or you will see crashes!
            }
        }
    }
    //endregion companion object

    private val session = GroupCallSessionDescription(sessionParameters.participantId)

    private var _transceivers: TransceiversCtx? = TransceiversCtx(
        local = null,
        remote = mutableMapOf(),
    )
    private val transceivers
        get() = checkNotNull(_transceivers) { "Transceivers already disposed" }

    val pc: PeerConnectionCtx
        get() = checkNotNull(_pc) { "Peer connection not created or already disposed" }

    private var _p2s: DataChannelCtx? = null
    val p2s: DataChannelCtx
        get() = checkNotNull(_p2s) { "P2S data channel not created or already disposed" }

    val eglBase: EglBase
        get() = factory.eglBase

    val frameCrypto = ThreemaGroupCallFrameCryptoContext(gckh)
    val pcmk = LocalParticipantCallMediaKey()

    private val localAudioVideoContextDelegate = lazy { LocalCtx.create(context, factory) }
    val localAudioVideoContext: LocalCtx by localAudioVideoContextDelegate

    // Note: Accessing this property will initiate teardown of the connection
    private val teardownJob: Job by lazy { initTeardown() }
    private val teardownInitiatedSignal = CompletableDeferred<Unit>()

    init {
        GroupCallThreadUtil.assertDispatcherThread()

        _p2s = createParticipantToSfuChannel(connectedSignal)

        // Initialise encryptor
        pcmk.current.also {
            frameCrypto.encryptor.setPcmk(it.pcmk, it.epoch.toShort(), it.ratchetCounter.toShort())
        }
    }

    @WorkerThread
    internal suspend fun createAndApplyOffer(remoteParticipants: Set<ParticipantId>) {
        GroupCallThreadUtil.assertDispatcherThread()

        // Create offer
        val description = session.generateRemoteDescription(
            RemoteSessionDescriptionInit(
                parameters = sessionParameters,
                remoteParticipants = remoteParticipants,
            ),
        )

        // Apply offer
        logger.trace("Generated remote session description:\n{}", description)
        logger.debug("Applying (generated) offer")
        pc.pc.setRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, description))
    }

    @WorkerThread
    internal suspend fun createAndApplyAnswer() {
        GroupCallThreadUtil.assertDispatcherThread()

        // Create answer
        logger.debug("Creating answer")
        val answer = pc.pc.createAnswer().let {
            SessionDescription(it.type, session.patchLocalDescription(it.description))
        }

        // Apply answer
        logger.trace("Created local session description:\n{}", answer.description)
        logger.debug("Applying answer")
        pc.pc.setLocalDescription(answer)
    }

    @WorkerThread
    internal suspend fun addIceCandidates(addresses: List<JoinResponseBody.Address>) {
        GroupCallThreadUtil.assertDispatcherThread()

        withContext(GroupCallThreadUtil.dispatcher) {
            // Connect to the SFU
            addresses
                .filter { address -> ipv6enabled || !address.isIpv6 }
                .map { address ->
                    IceCandidate(
                        "",
                        0,
                        "candidate:${
                            arrayOf(
                                // Foundation is irrelevant because we bundle
                                0,
                                // Component ID is always RTP (1) because we bundle
                                1,
                                // Always UDP
                                "udp",
                                // IPv6 takes priority but we only expect one address for each address family
                                if (address.isIpv6) 2 else 1,
                                address.ip,
                                address.port,
                                "typ",
                                "host",
                            ).joinToString(" ")
                        }",
                    )
                }
                .map {
                    async {
                        logger.trace("Adding generated ICE candidate: {}", it.sdp)
                        logger.debug("Connecting")
                        pc.pc.addIceCandidateAsync(it)
                        logger.trace("Connected {}", it.sdp)
                    }
                }.awaitAll()
        }
    }

    @WorkerThread
    internal fun mapLocalTransceivers(participantId: ParticipantId) {
        GroupCallThreadUtil.assertDispatcherThread()

        // Get all transceivers available on the peer connection.
        //
        // IMPORTANT: `pc.pc.transceivers` should **never** be called again because
        //            it disposes all existing transceivers instances when doing so.
        //            Instead, we cache the transceivers in `pc.transceivers` by
        //            listening to 'track' events.
        val unmapped = pc.gatherInitialTransceivers()

        // Map all local transceivers for the first time
        logger.trace("Mapping all local transceivers")
        transceivers.local = run {
            val mids = Mids.fromParticipantId(participantId).toMap()

            mids.map { (kind, mid) ->
                val transceiver = unmapped.remove(mid.mid)
                    ?: throw Error("Local '${kind.sdpKind}' transceiver not found")

                // Initial mapping: Set direction to activate correctly
                logger.trace(
                    "Activating local transceiver (kind='{}', mid='{}')",
                    transceiver.mediaType.name, transceiver.mid,
                )
                transceiver.direction = RtpTransceiver.RtpTransceiverDirection.SEND_ONLY

                setCameraVideoSimulcastEncodingParameters(kind, transceiver)

                // Add it to the encryptor
                val tag =
                    "${participantId.id}.${mid.mid}.${if (kind === MediaKind.VIDEO) "vp8" else "opus"}.sender"
                frameCrypto.encryptor.attach(transceiver.sender, tag)

                // Attach to the correct local context
                attachToLocalContext(kind, transceiver)

                // Done
                kind to transceiver
            }.toMap().toMutableMap()
        }

        // Ensure there are no unmapped remaining transceivers
        if (unmapped.isNotEmpty()) {
            throw Error("Unmapped transceiver MIDs: ${unmapped.keys.joinToString(", ")}")
        }
    }

    @WorkerThread
    private fun attachToLocalContext(kind: MediaKind, transceiver: RtpTransceiver) {
        when (kind) {
            MediaKind.AUDIO -> {
                logger.trace("Attaching local microphone audio track to transceiver")
                localAudioVideoContext.microphoneAudioContext.sendTo(transceiver)
            }

            MediaKind.VIDEO -> {
                logger.trace("Attaching local camera video track to transceiver")
                localAudioVideoContext.cameraVideoContext.sendTo(transceiver)
            }
        }
    }

    @WorkerThread
    private fun setCameraVideoSimulcastEncodingParameters(
        kind: MediaKind,
        transceiver: RtpTransceiver,
    ) {
        // For camera video, we need to set simulcast encoding parameters
        if (kind == MediaKind.VIDEO) {
            logger.debug("Applying local video encoding parameters")
            transceiver.sender.parameters = with(transceiver.sender.parameters) {
                // Apply encoding parameters
                degradationPreference = RtpParameters.DegradationPreference.BALANCED
                encodings.clear()
                encodings.addAll(CAMERA_SEND_ENCODINGS.map { it.toRtcEncoding() })
                this
            }
        }
    }

    @WorkerThread
    internal suspend fun updateCall(
        call: GroupCall,
        remove: MutableSet<ParticipantId>,
        add: MutableSet<ParticipantId>,
    ) {
        GroupCallThreadUtil.assertDispatcherThread()

        if (teardownInitiatedSignal.isCompleted) {
            logger.info("Ignore update, teardown already initiated")
            return
        }
        if (remove.isEmpty() && add.isEmpty()) {
            logger.trace("Ignoring update, no participants to be removed or added")
            return
        }

        logger.debug("Update started (remove={}, add={})", remove, add)

        removeParticipantsFromCall(remove)

        addParticipantsToCall(add)

        // Create offer
        run {
            // The participants to be added are not yet in `transceivers.remote`, so we need to add
            // them explicitly.
            logger.trace(
                "Removed (now inactive) and existing (staying active): {}",
                transceivers.remote.keys,
            )
            logger.trace("Added (becoming active): {}", add)
            val remoteParticipants = transceivers.remote.keys + add
            createAndApplyOffer(remoteParticipants)
        }

        // Get all cached transceivers available on the peer connection.
        //
        // IMPORTANT: Do **not** call `pc.pc.transceivers` but use the cached one
        //            for reasons explained in the connecting state.
        val unmapped = pc.transceivers.toMutableMap()

        remapLocalTransceivers(unmapped, remove, add)
        remapExistingRemoteTransceivers(unmapped, add)
        remapAddedRemoteTransceivers(unmapped, add, call)

        // Ensure there are no unmapped remaining transceivers
        if (unmapped.isNotEmpty()) {
            throw Error("Unmapped transceiver MIDs: ${unmapped.keys.joinToString(", ")}")
        }

        // Create and apply answer
        createAndApplyAnswer()

        logger.debug("Update complete")
    }

    @WorkerThread
    private fun removeParticipantsFromCall(remove: MutableSet<ParticipantId>) {
        GroupCallThreadUtil.assertDispatcherThread()

        for (participantId in remove) {
            if (participantId !in transceivers.remote) {
                // Discard if the remote participant was not added
                logger.warn(
                    "Cannot remove participant '{}', transceivers do not exist",
                    participantId.id,
                )
                continue
            }

            // Remove the decryptor
            frameCrypto.removeDecryptor(participantId.id.toShort())

            // Remove the participant
            //
            // Note: The associated SDP media line will be marked as inactive and linger in the SDP.
            transceivers.remote.remove(participantId)!!.forEach { (_, transceiver) ->
                // So, apparently libwebrtc for Android does not recycle transceivers. Instead,
                // they are moved into STOPPED and a new instance is announced when recycling
                // them (by setting a=inactive to a=sendonly/recvonly in the SDP). Hence, we can
                // safely remove the transceiver from the peer connection transceiver map.
                //
                // Also, don't access the `direction` property of a stopped transceiver
                // because libwebrtc authors forgot to add a STOPPED value to the
                // Android-land enum, which means it crashes.
                logger.trace("Removing now inactive transceiver: {}", transceiver.mid)
                pc.removeInactiveTransceiver(transceiver.mid)
            }
        }

        // This was caught in the conditional above but since we are not allowed to remove while
        // iterating, do it here
        remove.removeAll(transceivers.remote.keys)
    }

    @WorkerThread
    private fun addParticipantsToCall(add: MutableSet<ParticipantId>) {
        GroupCallThreadUtil.assertDispatcherThread()

        for (participantId in add) {
            // Discard if the remote participant is already added
            if (participantId in transceivers.remote) {
                logger.warn(
                    "Cannot add participant '{}', transceivers already exist",
                    participantId.id,
                )
                continue
            }

            // Insert into SDP m-line order
            session.addParticipantToMLineOrder(participantId)
        }

        // This was caught in the conditional above but since we are not allowed to remove while
        // iterating, do it here
        add.removeAll(transceivers.remote.keys)
    }

    @WorkerThread
    private fun remapLocalTransceivers(
        unmapped: MutableMap<String, RtpTransceiver>,
        remove: Collection<ParticipantId>,
        add: Collection<ParticipantId>,
    ) {
        // Sanity checks
        if (sessionParameters.participantId !in session.mLineOrder ||
            sessionParameters.participantId in transceivers.remote ||
            sessionParameters.participantId in add ||
            sessionParameters.participantId in remove
        ) {
            throw Error("remapLocalTransceivers sanity check failed")
        }

        // Remap
        logger.trace("Remapping all local transceivers")
        transceivers.local = run {
            val mids = Mids.fromParticipantId(sessionParameters.participantId).toMap()
            mids.map { (kind, mid) ->
                // Mark it as mapped
                val transceiver = unmapped.remove(mid.mid)
                    ?: throw Error("Local '${kind.sdpKind}' transceiver not found")
                if (transceivers.local!![kind] != null && transceivers.local!![kind] !== transceiver) {
                    throw Error("Local '${kind.sdpKind}' transceiver mismatch")
                }
                // TODO(ANDR-2036): Activate sanity-check
                // if (transceiver.direction != RtpTransceiver.RtpTransceiverDirection.SEND_ONLY) {
                //    throw Error("Local '${kind.sdpKind}' transceiver direction mismatch: Got ${transceiver.direction}, expected ${RtpTransceiver.RtpTransceiverDirection.SEND_ONLY}")
                // }
                kind to transceiver
            }.toMap().toMutableMap()
        }
    }

    @WorkerThread
    private fun remapExistingRemoteTransceivers(
        unmapped: MutableMap<String, RtpTransceiver>,
        add: Collection<ParticipantId>,
    ) {
        // Remap all existing remote participant transceivers
        logger.trace("Remapping all existing remote transceivers")
        for ((participantId, remoteTransceivers) in transceivers.remote) {
            // Sanity checks
            if (participantId !in session.mLineOrder || participantId in add) {
                throw Error("remapExistingRemoteTransceivers sanity check failed")
            }

            for ((kind, mid) in Mids.fromParticipantId(participantId).toMap()) {
                // Mark it as mapped
                val transceiver = unmapped.remove(mid.mid)
                    ?: throw Error("Remote '${kind.sdpKind}' transceiver for MID '${mid.mid}' not found")

                // Ensure transceiver matches the expected instance
                if (remoteTransceivers[kind] !== transceiver) {
                    throw Error("Remote '${kind.sdpKind}' transceiver mismatch")
                }
                // TODO(ANDR-2036): Activate sanity-check
                // if (transceiver.direction != RtpTransceiver.RtpTransceiverDirection.RECV_ONLY) {
                //    throw Error("Remote '${kind.sdpKind}' transceiver direction mismatch: Got ${transceiver.direction}, expected ${RtpTransceiver.RtpTransceiverDirection.RECV_ONLY}")
                // }
            }
        }
    }

    @WorkerThread
    private fun remapAddedRemoteTransceivers(
        unmapped: MutableMap<String, RtpTransceiver>,
        add: Collection<ParticipantId>,
        call: GroupCall,
    ) {
        // Create all newly added (pending) remote participant states and map their
        // transceivers.
        logger.trace("Remapping all newly added remote transceivers")
        for (participantId in add) {
            // Sanity checks
            if (participantId !in session.mLineOrder || participantId in transceivers.remote) {
                throw Error("remapAddedRemoteTransceivers sanity check failed")
            }

            // Add decryptor
            val decryptor = frameCrypto.addDecryptor(participantId.id.toShort())

            // Create transceivers map
            val mids = Mids.fromParticipantId(participantId).toMap()
            val remoteTransceivers: Transceivers = mids.map { (kind, mid) ->
                // Mark it as mapped
                val transceiver = unmapped.remove(mid.mid)
                    ?: throw Error("Remote '${kind.sdpKind}' transceiver for MID '${mid.mid}' not found")

                // First encounter: Set direction to activate correctly
                logger.trace(
                    "Activating remote transceiver (kind='{}', mid='{}')",
                    transceiver.mediaType.name,
                    transceiver.mid,
                )
                transceiver.direction = RtpTransceiver.RtpTransceiverDirection.RECV_ONLY

                // Add stream to decryptor
                val tag =
                    "${participantId.id}.${mid.mid}.${if (kind === MediaKind.VIDEO) "vp8" else "opus"}.receiver"
                decryptor.attach(transceiver.receiver, tag)

                // Set transceiver
                kind to transceiver
            }.toMap().toMutableMap()

            // Apply newly mapped transceivers to the control state
            call.setRemoteCtx(participantId, RemoteCtx.fromTransceiverMap(remoteTransceivers))

            // Create new remote participant and store the gathered transceivers
            transceivers.remote[participantId] = remoteTransceivers
        }
    }

    @AnyThread
    fun sendMessageToSfu(provider: P2SMessageProvider) {
        CoroutineScope(GroupCallThreadUtil.dispatcher).launch {
            val message = provider.get()
            val envelope = message.toProtobufEnvelope()
            val bytes = envelope.toByteArray()
            val buffer = DataChannel.Buffer(ByteBuffer.wrap(bytes), true)
            try {
                if (!teardownInitiatedSignal.isCompleted) {
                    logger.trace("Send message {} to sfu", message.type)
                    p2s.dc.send(buffer)
                } else {
                    logger.warn(
                        "Connection is being teared down. Not sending message {}",
                        message.type,
                    )
                }
            } catch (e: IllegalStateException) {
                logger.error("Could not send message to sfu message=${message.type}", e)
            }
        }
    }

    /**
     * Cleanup a group call. This method can be called multiple times.
     */
    @AnyThread
    suspend fun teardown() {
        logger.trace("Teardown group call")
        teardownJob.join()
    }

    @AnyThread
    private fun initTeardown(): Job {
        logger.trace("Teardown: Initiate ConnectionCtx teardown")
        teardownInitiatedSignal.complete(Unit)
        return CoroutineScope(GroupCallThreadUtil.dispatcher).launch {
            logger.trace("Teardown: ConnectionCtx")
            if (localAudioVideoContextDelegate.isInitialized()) {
                localAudioVideoContext.teardown()
            }

            p2s.teardown()

            pc.teardown()

            frameCrypto.dispose()

            factory.teardown()
            logger.trace("Teardown: /ConnectionCtx")
        }
    }

    @WorkerThread
    private fun createParticipantToSfuChannel(connectedSignal: CompletableDeferred<Set<ParticipantId>>): DataChannelCtx {
        GroupCallThreadUtil.assertDispatcherThread()

        // Note: We will consider the connection to be successful once we have received
        //       the initial list of participants.
        logger.debug("Creating P2S data channel")
        // Create data channel
        val type = DataChannelType.P2S
        val parameters = DataChannel.Init()
        parameters.ordered = true
        parameters.negotiated = true
        parameters.id = type.id.toInt()
        val dc = pc.pc.createDataChannel(type.label, parameters)
        // Attach observer to buffer events
        val observer = WrappedDataChannelObserver(dc::state)
        dc.registerObserver(observer)

        val dataChannelCtx = DataChannelCtx(dc, observer)
        setSfuHelloMessageObserver(dataChannelCtx, connectedSignal)
        return dataChannelCtx
    }

    @WorkerThread
    private fun setSfuHelloMessageObserver(
        dataChannelCtx: DataChannelCtx,
        connectedSignal: CompletableDeferred<Set<ParticipantId>>,
    ) {
        GroupCallThreadUtil.assertDispatcherThread()

        dataChannelCtx.observer.replace(object : SaneDataChannelObserver {
            override fun onStateChange(state: DataChannel.State) {
                // Note: Not dispatching here because no thread unsafe variables are accessed and
                //       we need to know ASAP in case the data channel has been closed.

                logger.debug("P2S data channel state: {}", state.name)
                when (state) {
                    DataChannel.State.CLOSING, DataChannel.State.CLOSED ->
                        connectedSignal.completeExceptionally(
                            Error("P2S data channel closed during connection setup"),
                        )

                    else -> {
                        // noop
                    }
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                // Note: Not dispatching here because no thread unsafe variables are accessed.

                logger.trace(
                    "P2S data channel incoming message (length={}, binary={})",
                    buffer.data.remaining(),
                    buffer.binary,
                )

                try {
                    // Expect S2P 'Hello' message
                    val message = try {
                        S2PMessage.decode(buffer)
                    } catch (e: InvalidProtocolBufferException) {
                        logger.warn("Invalid S2P message, could not decode protobuf", e)
                        return
                    }
                    if (message !is S2PMessage.SfuHello) {
                        connectedSignal.completeExceptionally(Error("Unexpected S2P message"))
                        return
                    }
                    logger.info("Received hello from sfu: {}", message.participantIds)

                    // Detach the current state observers.
                    //
                    // Note: This will force all subsequent events to be buffered, so we won't
                    //       miss any events.
                    pc.observer.replace(null)
                    dataChannelCtx.observer.replace(null)

                    // We're connected!
                    connectedSignal.complete(message.participantIds)
                } catch (e: Exception) {
                    connectedSignal.completeExceptionally(e)
                }
            }
        })
    }
}
