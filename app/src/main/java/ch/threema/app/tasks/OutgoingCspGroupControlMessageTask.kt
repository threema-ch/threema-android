package ch.threema.app.tasks

import ch.threema.app.utils.OutgoingCspGroupMessageCreator
import ch.threema.app.utils.OutgoingCspMessageHandle
import ch.threema.app.utils.OutgoingCspMessageServices
import ch.threema.app.utils.runBundledMessagesSendSteps
import ch.threema.app.utils.toBasicContacts
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.types.IdentityString
import java.util.Date

abstract class OutgoingCspGroupControlMessageTask : OutgoingCspMessageTask() {
    protected abstract val messageId: MessageId
    protected abstract val creatorIdentity: IdentityString
    protected abstract val groupId: GroupId
    protected abstract val recipientIdentities: Set<IdentityString>
    protected open val date: Date = Date()

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        val recipients = recipientIdentities
            .toBasicContacts(contactModelRepository, contactStore, apiConnector)
            .toSet()

        val messageCreator = OutgoingCspGroupMessageCreator(
            messageId,
            date,
            groupId,
            creatorIdentity,
        ) { createGroupMessage() }

        val outgoingCspMessageHandle = OutgoingCspMessageHandle(
            recipients,
            messageCreator,
        )

        handle.runBundledMessagesSendSteps(
            outgoingCspMessageHandle = outgoingCspMessageHandle,
            services = OutgoingCspMessageServices(
                forwardSecurityMessageProcessor,
                identityStore,
                userService,
                contactStore,
                contactService,
                contactModelRepository,
                groupService,
                nonceFactory,
                preferenceService,
                synchronizedSettingsService,
                multiDeviceManager,
            ),
            identityBlockedSteps = identityBlockedSteps,
        )
    }

    /**
     * Get the group message that will be sent. Note that this message must contain all the message
     * specific information. The message id, group id, creator identity, to identity, and the date
     * will be added before sending the message.
     *
     * Every invocation of this method must create a new instance of the message.
     */
    abstract fun createGroupMessage(): AbstractGroupMessage
}
