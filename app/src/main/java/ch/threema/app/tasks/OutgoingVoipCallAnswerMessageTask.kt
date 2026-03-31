package ch.threema.app.tasks

import ch.threema.common.now
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerData
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.types.IdentityString

class OutgoingVoipCallAnswerMessageTask(
    private val voipCallAnswerData: VoipCallAnswerData,
    private val toIdentity: IdentityString,
) : OutgoingCspMessageTask() {
    override val type: String = "OutgoingVoipCallAnswerMessageTask"

    override val shortLogInfo: String = "cid=${voipCallAnswerData.callId}"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        val message = VoipCallAnswerMessage()
        message.data = voipCallAnswerData

        voipStateService.addRequiredMessageId(voipCallAnswerData.callId ?: 0, message.messageId)

        sendContactMessage(
            message = message,
            messageModel = null,
            toIdentity = toIdentity,
            messageId = message.messageId,
            createdAt = now(),
            handle = handle,
        )
    }

    // We do not need to persist this message
    override fun serialize(): SerializableTaskData? = null
}
