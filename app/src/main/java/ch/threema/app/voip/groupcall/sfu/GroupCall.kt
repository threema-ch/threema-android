package ch.threema.app.voip.groupcall.sfu

import androidx.annotation.WorkerThread
import ch.threema.app.voip.groupcall.sfu.connection.GroupCallConnectionState
import ch.threema.app.voip.groupcall.sfu.webrtc.RemoteCtx
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import org.webrtc.EglBase

internal interface GroupCall : GroupCallController {
    /**
     * A signal that indicates whether a call is valid and can move on to the CONNECTED state after
     * connecting.
     */
    val callConfirmedSignal: Deferred<Unit>

    val completableConnectedSignal: CompletableDeferred<Pair<ULong, Set<ParticipantId>>>

    /**
     * In some cases the [GroupCallController] can decide that a participant should be treated as if
     * it had left the call. This can for example be the case, when someone has been kicked from a group
     * during a call.
     *
     * In such cases the corresponding [Participant]'s [ParticipantId] is emitted by this flow.
     */
    val dislodgedParticipants: Flow<ParticipantId>

    var parameters: GroupCallParameters
    var dependencies: GroupCallDependencies

    var context: GroupCallContext

    @WorkerThread
    fun setRemoteCtx(participantId: ParticipantId, remote: RemoteCtx)

    @WorkerThread
    fun setParticipant(participant: LocalParticipant)

    /**
     * Update the participants in a call.
     *
     * Note: There is no guarantee for this call to be run on a specific thread, therefore, it must
     * be able to be run on any thread (and switch threads by itself if required)
     */
    @WorkerThread
    fun updateParticipants(update: ParticipantsUpdate)

    @WorkerThread
    fun updateCaptureStates(
        // TODO(ANDR-4127): Remove
        screenShareActivated: Boolean,
    )

    @WorkerThread
    fun updateState(state: GroupCallConnectionState)

    @WorkerThread
    fun setEglBase(eglBase: EglBase)

    @WorkerThread
    fun addTeardownRoutine(routine: suspend () -> Unit)

    @WorkerThread
    suspend fun teardown()

    // TODO(ANDR-1951): Should be Collections of type RemoteParticipant (resolve inheritance problems...)
    data class ParticipantsUpdate(
        val add: Set<NormalRemoteParticipant> = emptySet(),
        val remove: Set<NormalRemoteParticipant> = emptySet(),
    ) {
        companion object {
            fun empty() = ParticipantsUpdate()

            fun addParticipant(participant: NormalRemoteParticipant) = ParticipantsUpdate(add = setOf(participant))

            fun removeParticipant(participant: NormalRemoteParticipant) = ParticipantsUpdate(remove = setOf(participant))
        }
    }
}
