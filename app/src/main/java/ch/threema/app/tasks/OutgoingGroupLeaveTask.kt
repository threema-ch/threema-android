package ch.threema.app.tasks

import ch.threema.app.groupflows.LeaveGroupFlow
import ch.threema.app.protocolsteps.IdentityBlockedSteps
import ch.threema.app.utils.OutgoingCspGroupMessageCreator
import ch.threema.app.utils.OutgoingCspMessageHandle
import ch.threema.app.utils.OutgoingCspMessageServices
import ch.threema.app.utils.runBundledMessagesSendSteps
import ch.threema.app.utils.toBasicContacts
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.models.GroupIdentity
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.models.MessageId
import ch.threema.domain.models.UserState
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.protocol.csp.messages.GroupLeaveMessage
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TransactionScope
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.domain.types.IdentityString
import java.util.Date
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("OutgoingGroupLeaveTask")

/**
 * This task is used to send out a [GroupLeaveMessage] to each member of the given group. Note that
 * this task should only be scheduled by the [LeaveGroupFlow] as it only handles csp
 * messages.
 */
class OutgoingGroupLeaveTask(
    private val groupIdentity: GroupIdentity,
    private val messageId: MessageId,
    private val memberIdentities: Set<IdentityString>,
) : ActiveTask<Unit>, PersistableTask, KoinComponent {
    private val apiConnector: APIConnector by inject()
    private val groupModelRepository: GroupModelRepository by inject()
    private val multiDeviceManager by lazy { outgoingCspMessageServices.multiDeviceManager }
    private val outgoingCspMessageServices: OutgoingCspMessageServices by inject()
    private val identityBlockedSteps: IdentityBlockedSteps by inject()

    override val type = "GroupLeaveTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        if (multiDeviceManager.isMultiDeviceActive) {
            try {
                sendLeaveMessagesInTransaction(handle)
            } catch (e: TransactionScope.TransactionException) {
                logger.warn("A group sync race occurred", e)
            }
        } else {
            sendLeaveMessages(handle)
        }
    }

    private suspend fun sendLeaveMessagesInTransaction(handle: ActiveTaskCodec) {
        val multiDeviceProperties = multiDeviceManager.propertiesProvider.get()

        handle.createTransaction(
            keys = multiDeviceProperties.keys,
            scope = ch.threema.protobuf.d2d.TransactionScope.Scope.GROUP_SYNC,
            ttl = TRANSACTION_TTL_MAX,
            precondition = {
                val groupModelData = groupModelRepository.getByGroupIdentity(groupIdentity)?.data
                if (groupModelData != null) {
                    // If the group exists, then the user state must be left
                    groupModelData.userState == UserState.LEFT
                } else {
                    // It is fine if the group does not exist
                    true
                }
            },
        ).execute {
            sendLeaveMessages(handle)
        }
    }

    private suspend fun sendLeaveMessages(handle: ActiveTaskCodec) {
        val receivers = memberIdentities.toBasicContacts(
            outgoingCspMessageServices.contactModelRepository,
            outgoingCspMessageServices.contactStore,
            apiConnector,
        ).toSet()

        handle.runBundledMessagesSendSteps(
            outgoingCspMessageHandle = OutgoingCspMessageHandle(
                receivers,
                OutgoingCspGroupMessageCreator(
                    messageId,
                    Date(),
                    groupIdentity,
                ) {
                    GroupLeaveMessage()
                },
            ),
            services = outgoingCspMessageServices,
            identityBlockedSteps = identityBlockedSteps,
        )
    }

    override fun serialize() = OutgoingGroupLeaveTaskData(
        groupIdentity = groupIdentity,
        messageId = messageId.messageId,
        memberIdentities = memberIdentities,
    )

    @Serializable
    class OutgoingGroupLeaveTaskData(
        private val groupIdentity: GroupIdentity,
        private val messageId: ByteArray,
        private val memberIdentities: Set<IdentityString>,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            OutgoingGroupLeaveTask(
                groupIdentity = groupIdentity,
                messageId = MessageId(messageId),
                memberIdentities = memberIdentities,
            )
    }
}
