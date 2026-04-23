package ch.threema.app.tasks

import ch.threema.app.groupflows.DisbandGroupFlow
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
import ch.threema.domain.protocol.csp.messages.GroupSetupMessage
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TransactionScope
import ch.threema.domain.taskmanager.createTransaction
import java.util.Date
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("OutgoingGroupDisbandTask")

/**
 * This task is used to send out an empty [GroupSetupMessage] to each member of the given group.
 * Note that this task should only be scheduled by the [DisbandGroupFlow] as it only handles csp
 * messages.
 */
class OutgoingGroupDisbandTask(
    private val groupIdentity: GroupIdentity,
    private val members: Set<String>,
    private val messageId: MessageId,
) : ActiveTask<Unit>, PersistableTask, KoinComponent {
    private val outgoingCspMessageServices: OutgoingCspMessageServices by inject()
    private val multiDeviceManager by lazy { outgoingCspMessageServices.multiDeviceManager }
    private val groupModelRepository: GroupModelRepository by inject()
    private val apiConnector: APIConnector by inject()
    private val identityBlockedSteps: IdentityBlockedSteps by inject()

    override val type = "OutgoingGroupDisbandTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        if (multiDeviceManager.isMultiDeviceActive) {
            try {
                sendEmptySetupMessagesInTransaction(handle)
            } catch (e: TransactionScope.TransactionException) {
                logger.warn("A group sync race occurred", e)
            }
        } else {
            sendEmptySetupMessages(handle)
        }
    }

    private suspend fun sendEmptySetupMessagesInTransaction(handle: ActiveTaskCodec) {
        val multiDeviceProperties = multiDeviceManager.propertiesProvider.get()

        handle.createTransaction(
            keys = multiDeviceProperties.keys,
            scope = ch.threema.protobuf.d2d.TransactionScope.Scope.GROUP_SYNC,
            ttl = TRANSACTION_TTL_MAX,
            precondition = {
                val groupModelData = groupModelRepository.getByGroupIdentity(groupIdentity)?.data
                if (groupModelData != null) {
                    // If the group exists, then the user state must be left
                    val isLeft = groupModelData.userState == UserState.LEFT

                    if (!isLeft) {
                        logger.error("A major group state inconsistency detected: group must be left")
                    }

                    isLeft
                } else {
                    // It is fine if the group does not exist
                    true
                }
            },
        ).execute {
            sendEmptySetupMessages(handle)
        }
    }

    private suspend fun sendEmptySetupMessages(handle: ActiveTaskCodec) {
        val receivers = members.toBasicContacts(
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
                    GroupSetupMessage().also {
                        it.members = emptyArray()
                    }
                },
            ),
            services = outgoingCspMessageServices,
            identityBlockedSteps = identityBlockedSteps,
        )
    }

    override fun serialize() =
        OutgoingGroupDisbandTaskData(groupIdentity, members, messageId.messageId)

    @Serializable
    class OutgoingGroupDisbandTaskData(
        private val groupIdentity: GroupIdentity,
        private val members: Set<String>,
        private val messageId: ByteArray,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            OutgoingGroupDisbandTask(
                groupIdentity,
                members,
                MessageId(messageId),
            )
    }
}
