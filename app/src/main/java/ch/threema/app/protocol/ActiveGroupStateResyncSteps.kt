/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app.protocol

import ch.threema.app.profilepicture.GroupProfilePictureUploader
import ch.threema.app.profilepicture.GroupProfilePictureUploader.GroupProfilePictureUploadResult
import ch.threema.app.profilepicture.RawProfilePicture
import ch.threema.app.services.FileService
import ch.threema.app.services.UserService
import ch.threema.app.utils.OutgoingCspGroupMessageCreator
import ch.threema.app.utils.OutgoingCspMessageHandle
import ch.threema.app.utils.OutgoingCspMessageServices
import ch.threema.app.utils.runBundledMessagesSendSteps
import ch.threema.app.voip.groupcall.GroupCallManager
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.domain.models.BasicContact
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.GroupDeleteProfilePictureMessage
import ch.threema.domain.protocol.csp.messages.GroupNameMessage
import ch.threema.domain.protocol.csp.messages.GroupSetProfilePictureMessage
import ch.threema.domain.protocol.csp.messages.GroupSetupMessage
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallStartMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.storage.DatabaseService
import ch.threema.storage.models.IncomingGroupSyncRequestLogModel
import java.util.Date

private val logger = getThreemaLogger("ActiveGroupStateResyncSteps")

suspend fun runActiveGroupStateResyncSteps(
    groupModel: GroupModel,
    targetMembers: Set<BasicContact>,
    preGeneratedMessageIds: PreGeneratedMessageIds,
    userService: UserService,
    groupProfilePictureUploader: GroupProfilePictureUploader,
    fileService: FileService,
    groupCallManager: GroupCallManager,
    databaseService: DatabaseService,
    outgoingCspMessageServices: OutgoingCspMessageServices,
    handle: ActiveTaskCodec,
) {
    if (groupModel.groupIdentity.creatorIdentity != userService.identity) {
        logger.error("Cannot run active group state resync steps for group with different creator")
        return
    }

    val groupModelData = groupModel.data ?: run {
        logger.error("Cannot run active group state resync steps for deleted group")
        return
    }

    if (!groupModelData.isMember) {
        logger.error("Cannot run active group state resync steps for left group")
        return
    }

    val updatedTargetMembers = targetMembers
        .filter { groupModelData.otherMembers.contains(it.identity) }
        .toSet()

    if (updatedTargetMembers.isEmpty()) {
        logger.info("No target members are group members")
        return
    }

    val currentTimestamp = Date()

    val messages = listOfNotNull(
        createSetupMessageHandle(
            preGeneratedMessageIds.firstMessageId,
            currentTimestamp,
            updatedTargetMembers,
            groupModelData,
        ),
        createNameMessageHandle(
            preGeneratedMessageIds.secondMessageId,
            currentTimestamp,
            updatedTargetMembers,
            groupModelData,
        ),
        createProfilePictureMessageHandle(
            preGeneratedMessageIds.thirdMessageId,
            currentTimestamp,
            updatedTargetMembers,
            groupModel,
            groupProfilePictureUploader,
            fileService,
        ),
        createGroupCallStartMessageHandle(
            preGeneratedMessageIds.fourthMessageId,
            currentTimestamp,
            updatedTargetMembers,
            groupModel,
            groupCallManager,
        ),
    )

    handle.runBundledMessagesSendSteps(messages, outgoingCspMessageServices)

    val incomingGroupSyncRequestLogModelFactory =
        databaseService.incomingGroupSyncRequestLogModelFactory
    updatedTargetMembers.map {
        IncomingGroupSyncRequestLogModel(
            groupModel.getDatabaseId(),
            it.identity,
            currentTimestamp.time,
        )
    }.forEach {
        incomingGroupSyncRequestLogModelFactory.createOrUpdate(it)
    }
}

data class PreGeneratedMessageIds(
    val firstMessageId: MessageId,
    val secondMessageId: MessageId,
    val thirdMessageId: MessageId,
    val fourthMessageId: MessageId,
)

private fun createSetupMessageHandle(
    messageId: MessageId,
    currentTimestamp: Date,
    receivers: Set<BasicContact>,
    groupModelData: GroupModelData,
) = OutgoingCspMessageHandle(
    receivers,
    OutgoingCspGroupMessageCreator(
        messageId,
        currentTimestamp,
        groupModelData.groupIdentity,
    ) {
        GroupSetupMessage().apply {
            members = groupModelData.otherMembers.toTypedArray()
        }
    },
)

private fun createNameMessageHandle(
    messageId: MessageId,
    currentTimestamp: Date,
    receivers: Set<BasicContact>,
    groupModelData: GroupModelData,
) = OutgoingCspMessageHandle(
    receivers,
    OutgoingCspGroupMessageCreator(
        messageId,
        currentTimestamp,
        groupModelData.groupIdentity,
    ) {
        GroupNameMessage().apply {
            groupName = groupModelData.name ?: ""
        }
    },
)

private fun createProfilePictureMessageHandle(
    messageId: MessageId,
    currentTimestamp: Date,
    receivers: Set<BasicContact>,
    groupModel: GroupModel,
    groupProfilePictureUploader: GroupProfilePictureUploader,
    fileService: FileService,
): OutgoingCspMessageHandle? {
    val uploadResult = fileService.getGroupProfilePictureBytes(groupModel)?.let { bytes ->
        groupProfilePictureUploader.tryUploadingGroupProfilePicture(RawProfilePicture(bytes))
    }

    val groupProfilePictureMessageCreator = when (uploadResult) {
        is GroupProfilePictureUploadResult.Success -> {
            {
                GroupSetProfilePictureMessage().apply {
                    blobId = uploadResult.blobId
                    size = uploadResult.size
                    encryptionKey = uploadResult.encryptionKey
                }
            }
        }

        null -> {
            {
                GroupDeleteProfilePictureMessage()
            }
        }

        else -> {
            logger.warn("Could not upload group profile picture. Skipping profile picture message.")
            return null
        }
    }

    return OutgoingCspMessageHandle(
        receivers,
        OutgoingCspGroupMessageCreator(
            messageId,
            currentTimestamp,
            groupModel.groupIdentity,
        ) {
            groupProfilePictureMessageCreator()
        },
    )
}

private suspend fun createGroupCallStartMessageHandle(
    messageId: MessageId,
    currentTimestamp: Date,
    receivers: Set<BasicContact>,
    groupModel: GroupModel,
    groupCallManager: GroupCallManager,
): OutgoingCspMessageHandle? {
    return groupCallManager.getGroupCallStartData(groupModel)?.let {
        OutgoingCspMessageHandle(
            receivers,
            OutgoingCspGroupMessageCreator(
                messageId,
                currentTimestamp,
                groupModel,
            ) {
                GroupCallStartMessage(it)
            },
        )
    }
}
