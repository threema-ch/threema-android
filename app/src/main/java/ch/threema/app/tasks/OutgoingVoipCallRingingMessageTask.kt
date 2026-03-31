package ch.threema.app.tasks

import ch.threema.common.now
import ch.threema.domain.protocol.csp.messages.voip.VoipCallRingingData
import ch.threema.domain.protocol.csp.messages.voip.VoipCallRingingMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.types.IdentityString

class OutgoingVoipCallRingingMessageTask(
    private val voipCallRingingData: VoipCallRingingData,
    private val toIdentity: IdentityString,
) : OutgoingCspMessageTask() {
    override val type: String = "OutgoingVoipCallRingingMessageTask"

    override val shortLogInfo: String = "cid=${voipCallRingingData.callId}"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        val message = VoipCallRingingMessage()
        message.data = voipCallRingingData
        message.toIdentity = toIdentity

        voipStateService.addRequiredMessageId(voipCallRingingData.callId ?: 0, message.messageId)

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
