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

package ch.threema.app.voip.groupcall.sfu.connection

import android.content.Context
import androidx.annotation.WorkerThread
import ch.threema.app.voip.groupcall.GroupCallThreadUtil
import ch.threema.app.voip.groupcall.sfu.*
import ch.threema.app.voip.groupcall.sfu.GroupCallContext
import ch.threema.app.voip.groupcall.sfu.webrtc.ConnectionCtx
import ch.threema.app.webrtc.PeerConnectionObserver
import ch.threema.base.utils.LoggingUtil
import ch.threema.storage.models.ContactModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.webrtc.RtcCertificatePem

private val logger = LoggingUtil.getThreemaLogger("GroupCallConnectionState.Connecting")

private const val TIMEOUT_CONNECTED_SIGNAL_MILLIS = 20000L

class Connecting internal constructor(
    call: GroupCall,
    private val me: ContactModel,
    private val context: Context,
    private val certificate: RtcCertificatePem,
    private val joinResponse: JoinResponseBody
) : GroupCallConnectionState(StateName.CONNECTING, call) {
    private companion object {
        // If there are fewer than this amount of remote participants in a call when joining
        // the microphone will not be muted upon join.
        private const val MUTE_MICROPHONE_PARTICIPANT_THRESHOLD = 4
    }

    @WorkerThread
    override fun getStateProviders() = listOf(
        this::observeCallEnd,
        this::startWebRtcConnection
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
            connectedSignal
        )

        call.addTeardownRoutine { ctx.teardown() }

        ctx.pc.observer.replace(
            PeerConnectionObserver(
                addTransceiver = ctx.pc::addTransceiverFromEvent,
                failedSignal = connectedSignal
            )
        )

        ctx.createAndApplyOffer(setOf())

        // Init participant with local audio / video context
        logger.info("Create local participant with {}", joinResponse.participantId)
        val participant = LocalParticipant(
            joinResponse.participantId,
            me,
            ctx.localAudioVideoContext
        )

        call.context = GroupCallContext(
            ctx, participant
        )

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

            call.completableConnectedSignal.complete(joinResponse.startedAt to participantIds)
            logger.trace("Waiting for call confirmation")
            call.callConfirmedSignal.await()
            Connected(call, participant)
        }
    }
}
