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
import ch.threema.app.utils.OutgoingCspGroupMessageCreator
import ch.threema.app.utils.OutgoingCspMessageHandle
import ch.threema.app.utils.OutgoingCspMessageServices.Companion.getOutgoingCspMessageServices
import ch.threema.app.utils.runBundledMessagesSendSteps
import ch.threema.app.utils.toBasicContact
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.domain.models.BasicContact
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage
import ch.threema.domain.protocol.csp.messages.GroupLeaveMessage
import ch.threema.domain.protocol.csp.messages.GroupSetupMessage
import ch.threema.domain.protocol.csp.messages.GroupSyncRequestMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.types.Identity
import java.util.Date

private val logger = LoggingUtil.getThreemaLogger("IncomingGroupMessageUtils")

/**
 * Run the common group receive steps. If the returned group is not null, then the common group receive steps have been run successfully. If null is
 * returned, this indicates that the message should be discarded.
 */
suspend fun runCommonGroupReceiveSteps(
    groupIdentity: GroupIdentity,
    fromIdentity: Identity,
    handle: ActiveTaskCodec,
    serviceManager: ServiceManager,
): GroupModel? = runCommonGroupReceiveSteps(
    groupIdentity.creatorIdentity,
    GroupId(groupIdentity.groupId),
    fromIdentity,
    handle,
    serviceManager,
)

/**
 * Run the common group receive steps. If the returned group is not null, then the common group receive steps have been run successfully. If null is
 * returned, this indicates that the message should be discarded.
 */
suspend fun runCommonGroupReceiveSteps(
    message: AbstractGroupMessage,
    handle: ActiveTaskCodec,
    serviceManager: ServiceManager,
): GroupModel? = runCommonGroupReceiveSteps(
    message.groupCreator,
    message.apiGroupId,
    message.fromIdentity,
    handle,
    serviceManager,
)

/**
 * Run the common group receive steps. If the returned group is not null, then the common group receive steps have been run successfully. If null is
 * returned, this indicates that the message should be discarded.
 */
suspend fun runCommonGroupReceiveSteps(
    creatorIdentity: Identity,
    groupId: GroupId,
    fromIdentity: Identity,
    handle: ActiveTaskCodec,
    serviceManager: ServiceManager,
): GroupModel? {
    val myIdentity = serviceManager.userService.identity
    val isCreator = myIdentity == creatorIdentity

    // Look up the group
    val group = serviceManager.modelRepositories.groups.getByCreatorIdentityAndId(creatorIdentity, groupId)
    val groupModelData = group?.data

    // If the group could not be found
    if (groupModelData == null) {
        if (!isCreator) {
            runGroupSyncRequestSteps(
                GroupIdentity(creatorIdentity, groupId.toLong()),
                serviceManager,
                handle,
            )
        }
        return null
    }

    // If the group is marked as left
    if (!groupModelData.isMember) {
        val sender = getContactForIdentity(fromIdentity, serviceManager)
        if (isCreator) {
            handle.runBundledMessagesSendSteps(
                OutgoingCspMessageHandle(
                    sender,
                    OutgoingCspGroupMessageCreator(
                        MessageId.random(),
                        Date(),
                        groupModelData.groupIdentity,
                    ) {
                        GroupSetupMessage().apply {
                            members = emptyArray()
                        }
                    },
                ),
                serviceManager.getOutgoingCspMessageServices(),
            )
        } else {
            handle.runBundledMessagesSendSteps(
                OutgoingCspMessageHandle(
                    sender,
                    OutgoingCspGroupMessageCreator(
                        MessageId.random(),
                        Date(),
                        groupModelData.groupIdentity,
                    ) {
                        GroupLeaveMessage()
                    },
                ),
                serviceManager.getOutgoingCspMessageServices(),
            )
        }

        return null
    }

    if (!groupModelData.otherMembers.contains(fromIdentity)) {
        val sender = getContactForIdentity(fromIdentity, serviceManager)
        if (isCreator) {
            handle.runBundledMessagesSendSteps(
                OutgoingCspMessageHandle(
                    sender,
                    OutgoingCspGroupMessageCreator(
                        MessageId.random(),
                        Date(),
                        groupModelData.groupIdentity,
                    ) {
                        GroupSetupMessage().apply {
                            members = emptyArray()
                        }
                    },
                ),
                serviceManager.getOutgoingCspMessageServices(),
            )
        } else {
            runGroupSyncRequestSteps(
                groupModelData.groupIdentity,
                serviceManager,
                handle,
            )
        }

        return null
    }

    return group
}

private fun getContactForIdentity(identity: Identity, serviceManager: ServiceManager): BasicContact =
    serviceManager.contactStore.getCachedContact(identity)
        ?: serviceManager.modelRepositories.contacts.getByIdentity(identity)?.data?.toBasicContact()
        ?: throw IllegalStateException("Could not get cached contact for identity $identity")

suspend fun runGroupSyncRequestSteps(
    groupCreator: BasicContact,
    groupIdentity: GroupIdentity,
    serviceManager: ServiceManager,
    handle: ActiveTaskCodec,
) {
    if (groupIdentity.creatorIdentity == serviceManager.userService.identity) {
        logger.error("Cannot run the group sync request steps for a group where the user is the creator")
        return
    }

    if (groupCreator.identity != groupIdentity.creatorIdentity) {
        logger.error("Cannot run the group sync request with a contact that is not the group creator")
        return
    }

    // If the group has been recently resynced (less than one hour ago), abort these steps
    val syncFactory = serviceManager.databaseService.outgoingGroupSyncRequestLogModelFactory
    val syncModel = syncFactory[groupIdentity]

    val lastSyncedTimestamp = syncModel?.lastRequest?.time ?: 0
    val now = Date()
    val oneHourAgoTimestamp = now.time - DateUtils.HOUR_IN_MILLIS

    if (lastSyncedTimestamp > oneHourAgoTimestamp) {
        logger.info("Group has already been synced at {}", lastSyncedTimestamp)
        return
    }

    // Send a group sync request
    handle.runBundledMessagesSendSteps(
        OutgoingCspMessageHandle(
            groupCreator,
            OutgoingCspGroupMessageCreator(
                MessageId.random(),
                Date(),
                groupIdentity,
            ) {
                GroupSyncRequestMessage()
            },
        ),
        serviceManager.getOutgoingCspMessageServices(),
    )

    // Mark the group as recently resynced
    syncFactory.createOrUpdate(groupIdentity, now)
}

/**
 * Fetches the group creator if not locally available and runs the group sync request steps.
 */
private suspend fun runGroupSyncRequestSteps(
    groupIdentity: GroupIdentity,
    serviceManager: ServiceManager,
    handle: ActiveTaskCodec,
) {
    runGroupSyncRequestSteps(
        groupIdentity.creatorIdentity.toBasicContact(
            serviceManager.modelRepositories.contacts,
            serviceManager.contactStore,
            serviceManager.apiConnector,
        ),
        groupIdentity,
        serviceManager,
        handle,
    )
}
