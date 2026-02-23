package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.common.now
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.voip.VoipCallRingingData
import ch.threema.domain.protocol.csp.messages.voip.VoipCallRingingMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.types.Identity

class OutgoingVoipCallRingingMessageTask(
    private val voipCallRingingData: VoipCallRingingData,
    private val toIdentity: Identity,
    serviceManager: ServiceManager,
) : OutgoingCspMessageTask(serviceManager) {
    private val voipStateService by lazy { serviceManager.voipStateService }

    override val type: String = "OutgoingVoipCallRingingMessageTask"

    override val shortLogInfo: String = "cid=${voipCallRingingData.callId}"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        val message = VoipCallRingingMessage()
        message.data = voipCallRingingData
        message.toIdentity = toIdentity
        message.messageId = MessageId.random()

        voipStateService.addRequiredMessageId(voipCallRingingData.callId ?: 0, message.messageId)

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
