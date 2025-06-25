/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.processors.incomingcspmessage.groupcontrol

import android.text.format.DateUtils
import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.protocol.PreGeneratedMessageIds
import ch.threema.app.protocol.runActiveGroupStateResyncSteps
import ch.threema.app.utils.OutgoingCspGroupMessageCreator
import ch.threema.app.utils.OutgoingCspMessageHandle
import ch.threema.app.utils.OutgoingCspMessageServices.Companion.getOutgoingCspMessageServices
import ch.threema.app.utils.runBundledMessagesSendSteps
import ch.threema.base.utils.LoggingUtil
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
import ch.threema.protobuf.d2d.MdD2D
import java.util.Date

private val logger = LoggingUtil.getThreemaLogger("IncomingGroupSyncRequestTask")

class IncomingGroupSyncRequestTask(
    message: GroupSyncRequestMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<GroupSyncRequestMessage>(message, triggerSource, serviceManager) {
    private val groupModelRepository by lazy { serviceManager.modelRepositories.groups }

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
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
                ?: serviceManager.modelRepositories.contacts.getByIdentity(it)?.data?.value?.toBasicContact()
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
                multiDeviceProperties.keys,
                MdD2D.TransactionScope.Scope.GROUP_SYNC,
                TRANSACTION_TTL_MAX,
                precondition = {
                    group.data.value != null
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
    val data = group.data.value ?: run {
        logger.error("Group model data cannot be null at this point")
        return
    }
    if (!data.isMember || !data.otherMembers.contains(sender.identity)) {
        handle.runBundledMessagesSendSteps(
            OutgoingCspMessageHandle(
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
            serviceManager.getOutgoingCspMessageServices(),
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
            serviceManager.apiService,
            serviceManager.fileService,
            serviceManager.groupCallManager,
            serviceManager.databaseService,
            serviceManager.getOutgoingCspMessageServices(),
            handle,
        )
    }
}
