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

import ch.threema.app.profilepicture.ProfilePicture
import ch.threema.app.services.ApiService
import ch.threema.app.services.FileService
import ch.threema.app.tasks.GroupPhotoUploadResult
import ch.threema.app.tasks.tryUploadingGroupPhoto
import ch.threema.app.utils.OutgoingCspGroupMessageCreator
import ch.threema.app.utils.OutgoingCspMessageHandle
import ch.threema.app.utils.OutgoingCspMessageServices
import ch.threema.app.utils.runBundledMessagesSendSteps
import ch.threema.app.voip.groupcall.GroupCallManager
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.models.BasicContact
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.GroupDeleteProfilePictureMessage
import ch.threema.domain.protocol.csp.messages.GroupNameMessage
import ch.threema.domain.protocol.csp.messages.GroupSetProfilePictureMessage
import ch.threema.domain.protocol.csp.messages.GroupSetupMessage
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallStartMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import java.io.FileNotFoundException
import java.util.Date
import kotlinx.serialization.Serializable

private val logger = LoggingUtil.getThreemaLogger("ActiveGroupUpdateSteps")

@Serializable
sealed interface ProfilePictureChange

@Serializable
class SetProfilePicture(
    val profilePicture: ProfilePicture,
    val profilePictureUploadResult: GroupPhotoUploadResult?,
) : ProfilePictureChange

@Serializable
data object RemoveProfilePicture : ProfilePictureChange

@Serializable
class PredefinedMessageIds private constructor(
    private val messageIdBytes1: ByteArray,
    private val messageIdBytes2: ByteArray,
    private val messageIdBytes3: ByteArray,
    private val messageIdBytes4: ByteArray,
) {
    constructor() : this(
        MessageId.random().messageId,
        MessageId.random().messageId,
        MessageId.random().messageId,
        MessageId.random().messageId,
    )

    val messageId1 by lazy { MessageId(messageIdBytes1) }

    val messageId2 by lazy { MessageId(messageIdBytes2) }

    val messageId3 by lazy { MessageId(messageIdBytes3) }

    val messageId4 by lazy { MessageId(messageIdBytes4) }
}

/**
 * The active group update steps are executed with the *expected* changes. It is possible that the
 * group has changed in the meantime. Therefore, it is checked whether the changes are up to date.
 */
suspend fun runActiveGroupUpdateSteps(
    profilePictureChange: ProfilePictureChange?,
    addMembers: Set<String>,
    removeMembers: Set<String>,
    predefinedMessageIds: PredefinedMessageIds,
    groupModel: GroupModel,
    services: OutgoingCspMessageServices,
    groupCallManager: GroupCallManager,
    fileService: FileService,
    apiService: ApiService,
    handle: ActiveTaskCodec,
) {
    // This is the current snapshot that represents the truth. All changes are validated based on
    // this data before they are communicated (sent out as csp messages).
    val groupModelData = groupModel.data ?: run {
        logger.warn("Group model data not available")
        return
    }

    if (groupModelData.groupIdentity.creatorIdentity != services.userService.identity) {
        logger.error("User is not the creator of the group")
        return
    }

    if (!groupModelData.isMember) {
        logger.error("The group has been disbanded")
        return
    }

    val sanitizedMembersToRemove =
        (removeMembers - groupModelData.otherMembers).toBasicContacts(services.contactModelRepository)

    val messages = mutableListOf<OutgoingCspMessageHandle>()

    if (sanitizedMembersToRemove.isNotEmpty()) {
        messages.add(
            createGroupSetup(
                sanitizedMembersToRemove,
                emptySet(),
                groupModel.groupIdentity,
                predefinedMessageIds.messageId1,
            ),
        )
    }

    if (groupModelData.otherMembers.isNotEmpty()) {
        val members = groupModelData.otherMembers.toBasicContacts(services.contactModelRepository)

        messages.addAll(
            listOfNotNull(
                createGroupSetup(members, groupModelData, predefinedMessageIds.messageId1),
                createGroupName(members, groupModelData, predefinedMessageIds.messageId2),
                createGroupProfilePictureMessage(
                    members,
                    groupModel,
                    profilePictureChange,
                    fileService,
                    apiService,
                    predefinedMessageIds.messageId3,
                ),
                createGroupCallStartMessage(
                    addMembers.intersect(groupModelData.otherMembers)
                        .toBasicContacts(services.contactModelRepository),
                    groupModel,
                    predefinedMessageIds.messageId4,
                    groupCallManager,
                ),
            ),
        )
    }

    handle.runBundledMessagesSendSteps(messages, services)
}

private fun createGroupSetup(
    receivers: Set<BasicContact>,
    groupModelData: GroupModelData,
    messageId: MessageId,
) = createGroupSetup(
    receivers,
    groupModelData.otherMembers,
    groupModelData.groupIdentity,
    messageId,
)

private fun createGroupSetup(
    receivers: Set<BasicContact>,
    members: Set<String>,
    groupIdentity: GroupIdentity,
    messageId: MessageId,
) = OutgoingCspMessageHandle(
    receivers,
    OutgoingCspGroupMessageCreator(
        messageId,
        Date(),
        groupIdentity,
    ) {
        GroupSetupMessage().apply {
            this.members = members.toTypedArray()
        }
    },
)

private fun createGroupName(
    receivers: Set<BasicContact>,
    groupModelData: GroupModelData,
    messageId: MessageId,
) = OutgoingCspMessageHandle(
    receivers,
    OutgoingCspGroupMessageCreator(
        messageId,
        Date(),
        groupModelData.groupIdentity,
    ) {
        GroupNameMessage().apply {
            this.groupName = groupModelData.name
        }
    },
)

private fun createGroupProfilePictureMessage(
    receivers: Set<BasicContact>,
    groupModel: GroupModel,
    profilePictureChange: ProfilePictureChange?,
    fileService: FileService,
    apiService: ApiService,
    messageId: MessageId,
): OutgoingCspMessageHandle? {
    val currentGroupProfilePicture = fileService.getGroupAvatarBytes(groupModel)
    if (currentGroupProfilePicture == null) {
        if (profilePictureChange is SetProfilePicture) {
            logger.info("Unexpected change: No profile picture set")
        }
        return getDeleteProfilePictureMessageHandle(receivers, messageId, groupModel.groupIdentity)
    }

    if (profilePictureChange is RemoveProfilePicture) {
        logger.info("Unexpected change: Profile picture available")
    }

    val groupPhotoUploadResult = getFinalGroupPhotoUploadResult(
        profilePictureChange, currentGroupProfilePicture, apiService,
    ) ?: return null

    return getSetProfilePictureMessageHandle(
        receivers = receivers,
        messageId = messageId,
        groupIdentity = groupModel.groupIdentity,
        groupPhotoUploadResult = groupPhotoUploadResult,
    )
}

private fun getFinalGroupPhotoUploadResult(
    profilePictureChange: ProfilePictureChange?,
    currentGroupProfilePicture: ByteArray,
    apiService: ApiService,
): GroupPhotoUploadResult? {
    if (profilePictureChange is SetProfilePicture &&
        profilePictureChange.profilePicture.profilePictureBytes.contentEquals(currentGroupProfilePicture)
    ) {
        return profilePictureChange.profilePictureUploadResult
    }

    try {
        return tryUploadingGroupPhoto(currentGroupProfilePicture, apiService)
    } catch (e: FileNotFoundException) {
        logger.error("Could not upload group photo", e)
        return null
    }
}

private fun getSetProfilePictureMessageHandle(
    receivers: Set<BasicContact>,
    messageId: MessageId,
    groupIdentity: GroupIdentity,
    groupPhotoUploadResult: GroupPhotoUploadResult,
): OutgoingCspMessageHandle {
    return OutgoingCspMessageHandle(
        receivers,
        OutgoingCspGroupMessageCreator(
            messageId,
            Date(),
            groupIdentity,
        ) {
            GroupSetProfilePictureMessage().apply {
                this.blobId = groupPhotoUploadResult.blobId
                this.encryptionKey = groupPhotoUploadResult.encryptionKey
                this.size = groupPhotoUploadResult.size
            }
        },
    )
}

private fun getDeleteProfilePictureMessageHandle(
    receivers: Set<BasicContact>,
    messageId: MessageId,
    groupIdentity: GroupIdentity,
): OutgoingCspMessageHandle {
    return OutgoingCspMessageHandle(
        receivers,
        OutgoingCspGroupMessageCreator(
            messageId,
            Date(),
            groupIdentity,
        ) {
            GroupDeleteProfilePictureMessage()
        },
    )
}

private suspend fun createGroupCallStartMessage(
    receivers: Set<BasicContact>,
    groupModel: GroupModel,
    messageId: MessageId,
    groupCallManager: GroupCallManager,
): OutgoingCspMessageHandle? {
    return groupCallManager.getGroupCallStartData(groupModel)?.let {
        return OutgoingCspMessageHandle(
            receivers,
            OutgoingCspGroupMessageCreator(
                messageId,
                Date(),
                groupModel.groupIdentity,
            ) {
                GroupCallStartMessage(it)
            },
        )
    }
}

private fun Iterable<String>.toBasicContacts(contactModelRepository: ContactModelRepository) =
    this.map { contactModelRepository.getByIdentity(it) }
        .mapNotNull { it?.data }
        .map { it.toBasicContact() }
        .toSet()
