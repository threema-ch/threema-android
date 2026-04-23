package ch.threema.app.processors.incomingcspmessage.groupcontrol

import android.text.format.DateUtils
import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.protocolsteps.PreGeneratedMessageIds
import ch.threema.app.protocolsteps.runActiveGroupStateResyncSteps
import ch.threema.app.utils.OutgoingCspGroupMessageCreator
import ch.threema.app.utils.OutgoingCspMessageHandle
import ch.threema.app.utils.OutgoingCspMessageServices.Companion.getOutgoingCspMessageServices
import ch.threema.app.utils.runBundledMessagesSendSteps
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.models.GroupModel
import ch.threema.domain.models.BasicContact
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.GroupSetupMessage
import ch.threema.domain.protocol.csp.messages.GroupSyncRequestMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.TransactionScope
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.taskmanager.createTransaction
import java.util.Date

private val logger = getThreemaLogger("IncomingGroupSyncRequestTask")

class IncomingGroupSyncRequestTask(
    message: GroupSyncRequestMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<GroupSyncRequestMessage>(message, triggerSource, serviceManager) {
    private val groupModelRepository by lazy { serviceManager.modelRepositories.groups }

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        logger.info("Processing incoming group-sync-request message for group with id {}", message.apiGroupId)

        // Look up the group. If the group could not be found, abort these steps.
        val group = groupModelRepository.getByCreatorIdentityAndId(
            message.groupCreator,
            message.apiGroupId,
        )
        if (group == null) {
            logger.warn("Discarding group sync request message because group could not be found")
            return ReceiveStepsResult.DISCARD
        }

        val senderContact = message.fromIdentity.let {
            serviceManager.contactStore.getCachedContact(it)
                ?: serviceManager.modelRepositories.contacts.getByIdentity(it)?.data?.toBasicContact()
        } ?: run {
            logger.error("Cannot handle incoming group sync request because sender is unknown")
            return ReceiveStepsResult.DISCARD
        }

        return handleIncomingGroupSyncRequest(
            group,
            senderContact,
            handle,
            serviceManager,
        )
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult {
        logger.info("Discarding incoming group sync request from sync")
        return ReceiveStepsResult.DISCARD
    }
}

suspend fun handleIncomingGroupSyncRequest(
    group: GroupModel,
    sender: BasicContact,
    handle: ActiveTaskCodec,
    serviceManager: ServiceManager,
): ReceiveStepsResult {
    val incomingGroupSyncRequestLogModelFactory =
        serviceManager.databaseService.incomingGroupSyncRequestLogModelFactory

    // Check whether the user is the creator. If not, abort these steps.
    if (serviceManager.userService.identity != group.groupIdentity.creatorIdentity) {
        logger.info("Group sync request received for group where the user is not the creator")
        return ReceiveStepsResult.DISCARD
    }

    // If a group-sync-request from this sender and group has already been handled within the
    // last hour, log a notice and abort these steps
    val groupSyncLog = incomingGroupSyncRequestLogModelFactory.getByGroupIdAndSenderIdentity(
        localDbGroupId = group.getDatabaseId(),
        senderIdentity = sender.identity,
    )
    val now = System.currentTimeMillis()
    val oneHourAgo = now - DateUtils.HOUR_IN_MILLIS
    if (groupSyncLog.lastHandledRequest > oneHourAgo) {
        logger.info("Group sync request already handled at {}", groupSyncLog.lastHandledRequest)
        return ReceiveStepsResult.DISCARD
    }

    val multiDeviceManager = serviceManager.multiDeviceManager
    if (multiDeviceManager.isMultiDeviceActive) {
        val multiDeviceProperties = multiDeviceManager.propertiesProvider.get()

        try {
            handle.createTransaction(
                keys = multiDeviceProperties.keys,
                scope = ch.threema.protobuf.d2d.TransactionScope.Scope.GROUP_SYNC,
                ttl = TRANSACTION_TTL_MAX,
                precondition = {
                    group.data != null
                },
            ).execute {
                answerGroupSyncRequest(group, sender, serviceManager, handle)
            }
        } catch (e: TransactionScope.TransactionException) {
            logger.warn("Group sync race: Could not start transaction", e)
            return ReceiveStepsResult.DISCARD
        }
    } else {
        answerGroupSyncRequest(group, sender, serviceManager, handle)
    }

    return ReceiveStepsResult.SUCCESS
}

private suspend fun answerGroupSyncRequest(
    group: GroupModel,
    sender: BasicContact,
    serviceManager: ServiceManager,
    handle: ActiveTaskCodec,
) {
    val data = group.data ?: run {
        logger.error("Group model data cannot be null at this point")
        return
    }
    if (!data.isMember || !data.otherMembers.contains(sender.identity)) {
        handle.runBundledMessagesSendSteps(
            outgoingCspMessageHandle = OutgoingCspMessageHandle(
                sender,
                OutgoingCspGroupMessageCreator(
                    MessageId.random(),
                    Date(),
                    group,
                ) {
                    GroupSetupMessage().apply {
                        members = emptyArray()
                    }
                },
            ),
            services = serviceManager.getOutgoingCspMessageServices(),
            identityBlockedSteps = serviceManager.identityBlockedSteps,
        )
    } else {
        runActiveGroupStateResyncSteps(
            group,
            setOf(sender),
            PreGeneratedMessageIds(
                firstMessageId = MessageId.random(),
                secondMessageId = MessageId.random(),
                thirdMessageId = MessageId.random(),
                fourthMessageId = MessageId.random(),
            ),
            serviceManager.userService,
            serviceManager.groupProfilePictureUploader,
            serviceManager.fileService,
            serviceManager.groupCallManager,
            serviceManager.databaseService,
            serviceManager.getOutgoingCspMessageServices(),
            serviceManager.identityBlockedSteps,
            handle,
        )
    }
}
