package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.common.now
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.voip.VoipICECandidatesData
import ch.threema.domain.protocol.csp.messages.voip.VoipICECandidatesMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.types.Identity

class OutgoingVoipICECandidateMessageTask(
    private val voipICECandidatesData: VoipICECandidatesData,
    private val toIdentity: Identity,
    serviceManager: ServiceManager,
) : OutgoingCspMessageTask(serviceManager) {
    override val type: String = "OutgoingVoipICECandidateMessageTask"

    override val shortLogInfo: String = "cid=${voipICECandidatesData.callId}"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        val message = VoipICECandidatesMessage()
        message.data = voipICECandidatesData

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
