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

package ch.threema.app.tasks

import android.text.format.DateUtils
import ch.threema.app.managers.ServiceManager
import ch.threema.app.utils.OutgoingCspGroupMessageCreator
import ch.threema.app.utils.OutgoingCspMessageHandle
import ch.threema.app.utils.OutgoingCspMessageServices
import ch.threema.app.utils.runBundledMessagesSendSteps
import ch.threema.app.utils.toBasicContact
import ch.threema.base.utils.LoggingUtil
import ch.threema.common.now
import ch.threema.data.models.GroupIdentity
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.GroupSyncRequestMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.Identity
import java.util.Date
import kotlinx.serialization.Serializable

private val logger = LoggingUtil.getThreemaLogger("OutgoingGroupSyncRequestTask")

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
    private val creatorIdentity: Identity,
    messageId: MessageId?,
    serviceManager: ServiceManager,
) : OutgoingCspMessageTask(serviceManager) {
    private val messageId = messageId ?: MessageId.random()
    private val apiConnector by lazy { serviceManager.apiConnector }
    private val multiDeviceManager by lazy { serviceManager.multiDeviceManager }
    private val outgoingGroupSyncRequestLogModelFactory by lazy { serviceManager.databaseService.outgoingGroupSyncRequestLogModelFactory }
    private val blockedIdentitiesService by lazy { serviceManager.blockedIdentitiesService }

    override val type: String = "OutgoingGroupSyncRequestTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        // Don't send group sync request to myself
        if (creatorIdentity.equals(identityStore.getIdentity(), ignoreCase = true)) {
            return
        }

        val groupIdentity = GroupIdentity(
            creatorIdentity = creatorIdentity,
            groupId = groupId.toLong(),
        )

        // Only send a group sync request once in an hour for a specific group
        val groupSyncRequestLogModel = outgoingGroupSyncRequestLogModelFactory[groupIdentity]
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
            outgoingCspMessageHandle,
            OutgoingCspMessageServices(
                forwardSecurityMessageProcessor,
                identityStore,
                userService,
                contactStore,
                contactService,
                contactModelRepository,
                groupService,
                nonceFactory,
                blockedIdentitiesService,
                preferenceService,
                multiDeviceManager,
            ),
        )

        // Update sync request sent date
        outgoingGroupSyncRequestLogModelFactory.createOrUpdate(groupIdentity, createdAt)
    }

    override fun serialize(): SerializableTaskData = OutgoingGroupSyncRequestData(
        groupId = groupId.groupId,
        creatorIdentity = creatorIdentity,
        messageId = messageId.messageId,
    )

    @Serializable
    class OutgoingGroupSyncRequestData(
        private val groupId: ByteArray,
        private val creatorIdentity: Identity,
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
