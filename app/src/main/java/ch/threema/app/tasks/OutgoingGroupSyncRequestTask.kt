package ch.threema.app.tasks

import android.text.format.DateUtils
import ch.threema.app.utils.OutgoingCspGroupMessageCreator
import ch.threema.app.utils.OutgoingCspMessageHandle
import ch.threema.app.utils.OutgoingCspMessageServices
import ch.threema.app.utils.runBundledMessagesSendSteps
import ch.threema.app.utils.toBasicContact
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.now
import ch.threema.data.models.GroupIdentity
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.GroupSyncRequestMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.IdentityString
import java.util.Date
import kotlinx.serialization.Serializable

private val logger = getThreemaLogger("OutgoingGroupSyncRequestTask")

/**
 * Send a group sync request to the specified group creator. Note that maximum one sync request is
 * sent per group and hour. If a sync request has been sent within the last hour, this task does not
 * send a sync request.
 *
 * The sync request is also sent to unknown or blocked contacts.
 * TODO(ANDR-3262): Replace this task by the Group Sync Request Steps
 */
class OutgoingGroupSyncRequestTask(
    private val groupId: GroupId,
    private val creatorIdentity: IdentityString,
    messageId: MessageId?,
) : OutgoingCspMessageTask() {
    private val messageId = messageId ?: MessageId.random()

    override val type: String = "OutgoingGroupSyncRequestTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        // Don't send group sync request to myself
        if (creatorIdentity.equals(identityStore.getIdentityString(), ignoreCase = true)) {
            return
        }

        val groupIdentity = GroupIdentity(
            creatorIdentity = creatorIdentity,
            groupId = groupId.toLong(),
        )

        // Only send a group sync request once in an hour for a specific group
        val groupSyncRequestLogModel = databaseService.outgoingGroupSyncRequestLogModelFactory[groupIdentity]
        val oneHourAgo = Date(System.currentTimeMillis() - DateUtils.HOUR_IN_MILLIS)
        val lastSyncRequest = groupSyncRequestLogModel?.lastRequest ?: Date(0)
        if (lastSyncRequest.after(oneHourAgo)) {
            logger.info(
                "Do not send request sync to group creator {}: last sync request was at {}",
                creatorIdentity,
                groupSyncRequestLogModel?.lastRequest,
            )
            return
        }

        val recipient = creatorIdentity.toBasicContact(
            contactModelRepository = contactModelRepository,
            contactStore = contactStore,
            apiConnector = apiConnector,
        )

        val createdAt = now()

        val messageCreator = OutgoingCspGroupMessageCreator(
            messageId,
            createdAt,
            groupId,
            creatorIdentity,
        ) { GroupSyncRequestMessage() }

        val outgoingCspMessageHandle = OutgoingCspMessageHandle(
            setOf(recipient),
            messageCreator,
        )

        // Send message
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

        // Update sync request sent date
        databaseService.outgoingGroupSyncRequestLogModelFactory.createOrUpdate(groupIdentity, createdAt)
    }

    override fun serialize(): SerializableTaskData = OutgoingGroupSyncRequestData(
        groupId = groupId.groupId,
        creatorIdentity = creatorIdentity,
        messageId = messageId.messageId,
    )

    @Serializable
    class OutgoingGroupSyncRequestData(
        private val groupId: ByteArray,
        private val creatorIdentity: IdentityString,
        private val messageId: ByteArray,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            OutgoingGroupSyncRequestTask(
                groupId = GroupId(groupId),
                creatorIdentity = creatorIdentity,
                messageId = MessageId(messageId),
            )
    }
}
