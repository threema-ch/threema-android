package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.common.now
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.TypingIndicatorMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.types.Identity

class OutgoingTypingIndicatorMessageTask(
    private val isTyping: Boolean,
    private val toIdentity: Identity,
    serviceManager: ServiceManager,
) : OutgoingCspMessageTask(serviceManager) {
    override val type: String = "OutgoingTypingIndicatorMessageTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        val message = TypingIndicatorMessage().also {
            it.isTyping = isTyping
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

    override fun serialize(): SerializableTaskData? = null
}
