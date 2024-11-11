/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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

package ch.threema.app.tasks

import android.text.format.DateUtils
import ch.threema.app.managers.ServiceManager
import ch.threema.app.utils.OutgoingCspGroupMessageCreator
import ch.threema.app.utils.fetchContactModel
import ch.threema.app.utils.sendMessageToReceivers
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.GroupSyncRequestMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.storage.models.OutgoingGroupSyncRequestLogModel
import kotlinx.serialization.Serializable
import java.util.Date

private val logger = LoggingUtil.getThreemaLogger("OutgoingGroupSyncRequestTask")

/**
 * Send a group sync request to the specified group creator. Note that maximum one sync request is
 * sent per group and hour. If a sync request has been sent within the last hour, this task does not
 * send a sync request.
 *
 * The sync request is also sent to unknown or blocked contacts.
 */
class OutgoingGroupSyncRequestTask(
    private val groupId: GroupId,
    private val creatorIdentity: String,
    messageId: MessageId?,
    serviceManager: ServiceManager,
) : OutgoingCspMessageTask(serviceManager) {
    private val messageId = messageId ?: MessageId()
    private val apiConnector by lazy { serviceManager.apiConnector }
    private val taskCreator by lazy { serviceManager.taskCreator }
    private val outgoingGroupSyncRequestLogModelFactory by lazy { serviceManager.databaseServiceNew.outgoingGroupSyncRequestLogModelFactory }
    private val blackListService by lazy { serviceManager.blackListService }

    override val type: String = "OutgoingGroupSyncRequestTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        // Don't send group sync request to myself
        if (creatorIdentity.equals(identityStore.identity, true)) {
            return
        }

        // Only send a group sync request once in an hour for a specific group
        val model = outgoingGroupSyncRequestLogModelFactory.get(groupId.toString(), creatorIdentity)
        val oneHourAgo = Date(System.currentTimeMillis() - DateUtils.HOUR_IN_MILLIS)
        val lastSyncRequest = model?.lastRequest ?: Date(0)
        if (lastSyncRequest.after(oneHourAgo)) {
            logger.info(
                "Do not send request sync to group creator {}: last sync request was at {}",
                creatorIdentity,
                model.lastRequest
            )
            return
        }

        val recipient = contactService.getByIdentity(creatorIdentity)
            ?: creatorIdentity.fetchContactModel(apiConnector)

        val messageCreator = OutgoingCspGroupMessageCreator(
            messageId,
            groupId,
            creatorIdentity
        ) { GroupSyncRequestMessage() }

        // Send message
        handle.sendMessageToReceivers(
            messageCreator,
            setOf(recipient),
            forwardSecurityMessageProcessor,
            identityStore,
            contactStore,
            nonceFactory,
            blackListService,
            taskCreator
        )

        // Update sync request sent date
        if (model == null) {
            val newModel = OutgoingGroupSyncRequestLogModel()
            newModel.setAPIGroupId(groupId.toString(), creatorIdentity)
            newModel.lastRequest = Date()
            outgoingGroupSyncRequestLogModelFactory.create(newModel)
        } else {
            model.lastRequest = Date()
            outgoingGroupSyncRequestLogModelFactory.update(model)
        }
    }

    override fun serialize(): SerializableTaskData = OutgoingGroupSyncRequestData(
        groupId.groupId, creatorIdentity, messageId.messageId
    )

    @Serializable
    class OutgoingGroupSyncRequestData(
        private val groupId: ByteArray,
        private val creatorIdentity: String,
        private val messageId: ByteArray,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            OutgoingGroupSyncRequestTask(
                GroupId(groupId),
                creatorIdentity,
                MessageId(messageId),
                serviceManager,
            )
    }
}
