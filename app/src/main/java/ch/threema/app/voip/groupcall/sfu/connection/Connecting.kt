package ch.threema.app.voip.groupcall.sfu.connection

import android.content.Context
import androidx.annotation.WorkerThread
import ch.threema.app.voip.groupcall.GroupCallThreadUtil
import ch.threema.app.voip.groupcall.sfu.*
import ch.threema.app.voip.groupcall.sfu.GroupCallContext
import ch.threema.app.voip.groupcall.sfu.webrtc.ConnectionCtx
import ch.threema.app.webrtc.PeerConnectionObserver
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.domain.types.Identity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.webrtc.RtcCertificatePem

private val logger = getThreemaLogger("GroupCallConnectionState.Connecting")

private const val TIMEOUT_CONNECTED_SIGNAL_MILLIS = 20000L

class Connecting internal constructor(
    call: GroupCall,
    private val myIdentity: Identity,
    private val myDisplayName: String,
    private val context: Context,
    private val certificate: RtcCertificatePem,
    private val joinResponse: JoinResponseBody,
) : GroupCallConnectionState(StateName.CONNECTING, call) {
    private companion object {
        // If there are fewer than this amount of remote participants in a call when joining
        // the microphone will not be muted upon join.
        private const val MUTE_MICROPHONE_PARTICIPANT_THRESHOLD = 4
    }

    @WorkerThread
    override fun getStateProviders() = listOf(
        this::observeCallEnd,
        this::startWebRtcConnection,
    )

    @WorkerThread
    private suspend fun startWebRtcConnection(): GroupCallConnectionState {
        GroupCallThreadUtil.assertDispatcherThread()

        val connectedSignal = CompletableDeferred<Set<ParticipantId>>()
        val ctx = ConnectionCtx.create(
            context,
            call,
            joinResponse.sessionParameters,
            certificate,
            connectedSignal,
        )

        call.addTeardownRoutine { ctx.teardown() }

        ctx.pc.observer.replace(
            PeerConnectionObserver(
                addTransceiver = ctx.pc::addTransceiverFromEvent,
                failedSignal = connectedSignal,
            ),
        )

        ctx.createAndApplyOffer(emptySet())

        // Init participant with local audio / video context
        logger.info("Create local participant with {}", joinResponse.participantId)

        val user = object : DisplayableParticipant {
            override val identity: Identity
                get() = myIdentity

            // The local user's nickname isn't displayed and can therefore be empty
            override val nickname: String
                get() = ""

            override fun getDisplayName(contactNameFormat: ContactNameFormat): String = myDisplayName
        }

        val participant = LocalParticipant(
            id = joinResponse.participantId,
            displayableParticipant = user,
            localCtx = ctx.localAudioVideoContext,
        )

        call.context = GroupCallContext(ctx, participant)

        call.setParticipant(participant)

        ctx.mapLocalTransceivers(joinResponse.participantId)

        ctx.createAndApplyAnswer()

        ctx.addIceCandidates(joinResponse.addresses)

        call.setEglBase(ctx.eglBase)

        logger.trace("Waiting for connected signal")
        return withTimeout(TIMEOUT_CONNECTED_SIGNAL_MILLIS) {
            val participantIds = connectedSignal.await()

            // set initial video/audio states
            participant.cameraActive = false
            participant.microphoneActive =
                participantIds.size < MUTE_MICROPHONE_PARTICIPANT_THRESHOLD
            participant.screenShareActive = false

            call.completableConnectedSignal.complete(joinResponse.startedAt to participantIds)
            logger.trace("Waiting for call confirmation")
            call.callConfirmedSignal.await()
            Connected(call, participant)
        }
    }
}
