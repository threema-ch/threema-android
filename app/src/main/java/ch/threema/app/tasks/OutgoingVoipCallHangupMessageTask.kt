package ch.threema.app.tasks

import ch.threema.common.now
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.voip.VoipCallHangupData
import ch.threema.domain.protocol.csp.messages.voip.VoipCallHangupMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.types.IdentityString

class OutgoingVoipCallHangupMessageTask(
    private val voipCallHangupData: VoipCallHangupData,
    private val toIdentity: IdentityString,
) : OutgoingCspMessageTask() {
    override val type: String = "OutgoingVoipCallHangupMessageTask"

    override val shortLogInfo: String = "cid=${voipCallHangupData.callId}"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        val message = VoipCallHangupMessage().apply {
            this.data = voipCallHangupData
        }
        sendContactMessage(
            message = message,
            messageModel = null,
            toIdentity = toIdentity,
            messageId = MessageId.random(),
            createdAt = now(),
            handle = handle,
        )
    }

    // We do not need to persist this message
    override fun serialize(): SerializableTaskData? = null
}
