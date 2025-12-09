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
import ch.threema.app.profilepicture.ProfilePicture
import ch.threema.app.profilepicture.RawProfilePicture
import ch.threema.app.services.FileService
import ch.threema.app.utils.OutgoingCspGroupMessageCreator
import ch.threema.app.utils.OutgoingCspMessageHandle
import ch.threema.app.utils.OutgoingCspMessageServices
import ch.threema.app.utils.runBundledMessagesSendSteps
import ch.threema.app.voip.groupcall.GroupCallManager
import ch.threema.base.utils.getThreemaLogger
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
import ch.threema.domain.taskmanager.ProtocolException
import java.util.Date
import kotlin.jvm.Throws

private val logger = getThreemaLogger("ActiveGroupUpdateSteps")

sealed interface ExpectedProfilePictureChange {

    /**
     * The profile picture is expected to be set.
     */
    sealed interface Set : ExpectedProfilePictureChange {
        val profilePicture: ProfilePicture

        /**
         * The profile picture is expected to be set and was uploaded to the blob server. This is the case for groups with at least one member or if
         * multi device has been active when the group was created or updated.
         */
        data class WithUpload(
            val profilePictureUploadResultSuccess: GroupProfilePictureUploadResult.Success,
        ) : Set {
            override val profilePicture: ProfilePicture
                get() = profilePictureUploadResultSuccess.profilePicture
        }

        /**
         * The profile picture is expected to be set but wasn't uploaded to the blob server as when the change was made, the group was a notes group and
         * multi device wasn't active.
         */
        data class WithoutUpload(
            override val profilePicture: ProfilePicture,
        ) : Set
    }

    /**
     * The profile picture is expected to be removed.
     */
    data object Remove : ExpectedProfilePictureChange
}

data class PredefinedMessageIds(
    val messageId1: MessageId,
    val messageId2: MessageId,
    val messageId3: MessageId,
    val messageId4: MessageId,
) {
    companion object {
        fun random(): PredefinedMessageIds =
            PredefinedMessageIds(
                messageId1 = MessageId.random(),
                messageId2 = MessageId.random(),
                messageId3 = MessageId.random(),
                messageId4 = MessageId.random(),
            )
    }
}

/**
 * The active group update steps are executed with the *expected* changes. It is possible that the
 * group has changed in the meantime. Therefore, it is checked whether the changes are up to date.
 *
 * Note that if the group profile picture needs to be uploaded and it fails, a [ProtocolException]
 * will be thrown.
 */
@Throws(ProtocolException::class)
suspend fun runActiveGroupUpdateSteps(
    expectedProfilePictureChange: ExpectedProfilePictureChange?,
    addMembers: Set<String>,
    removeMembers: Set<String>,
    predefinedMessageIds: PredefinedMessageIds,
    groupModel: GroupModel,
    services: OutgoingCspMessageServices,
    groupCallManager: GroupCallManager,
    fileService: FileService,
    groupProfilePictureUploader: GroupProfilePictureUploader,
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
                    expectedProfilePictureChange,
                    fileService,
                    groupProfilePictureUploader,
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
    expectedProfilePictureChange: ExpectedProfilePictureChange?,
    fileService: FileService,
    groupProfilePictureUploader: GroupProfilePictureUploader,
    messageId: MessageId,
): OutgoingCspMessageHandle {
    val currentGroupProfilePicture = fileService.getGroupProfilePictureBytes(groupModel)?.let { bytes -> RawProfilePicture(bytes) }

    when (expectedProfilePictureChange) {
        is ExpectedProfilePictureChange.Set -> {
            if (currentGroupProfilePicture == null) {
                logger.info("Unexpected change: No profile picture set")
            }
        }

        is ExpectedProfilePictureChange.Remove -> {
            if (currentGroupProfilePicture != null) {
                logger.info("Unexpected change: Profile picture available")
            }
        }

        null -> Unit
    }

    if (currentGroupProfilePicture == null) {
        return getDeleteProfilePictureMessageHandle(receivers, messageId, groupModel.groupIdentity)
    }

    val groupPhotoUploadResult = getFinalGroupPhotoUploadResult(expectedProfilePictureChange, currentGroupProfilePicture, groupProfilePictureUploader)

    when (groupPhotoUploadResult) {
        is GroupProfilePictureUploadResult.Success -> Unit
        is GroupProfilePictureUploadResult.Failure.OnPremAuthTokenInvalid ->
            throw ProtocolException("Could not upload profile picture (onprem auth token invalid)")

        is GroupProfilePictureUploadResult.Failure.UploadFailed ->
            throw ProtocolException("Could not upload profile picture (upload failed)")
    }

    return getSetProfilePictureMessageHandle(
        receivers = receivers,
        messageId = messageId,
        groupIdentity = groupModel.groupIdentity,
        groupProfilePictureUploadResultSuccess = groupPhotoUploadResult,
    )
}

private fun getFinalGroupPhotoUploadResult(
    expectedProfilePictureChange: ExpectedProfilePictureChange?,
    currentGroupProfilePicture: ProfilePicture,
    groupProfilePictureUploader: GroupProfilePictureUploader,
): GroupProfilePictureUploadResult {
    // If the group profile picture has been uploaded and is still equal to the current group profile picture, then we can reuse the blob information
    // from the previous upload.
    if (expectedProfilePictureChange is ExpectedProfilePictureChange.Set.WithUpload &&
        expectedProfilePictureChange.profilePictureUploadResultSuccess.profilePicture.contentEquals(currentGroupProfilePicture)
    ) {
        return expectedProfilePictureChange.profilePictureUploadResultSuccess
    }

    // Otherwise, we just upload it again.
    return groupProfilePictureUploader.tryUploadingGroupProfilePicture(currentGroupProfilePicture)
}

private fun getSetProfilePictureMessageHandle(
    receivers: Set<BasicContact>,
    messageId: MessageId,
    groupIdentity: GroupIdentity,
    groupProfilePictureUploadResultSuccess: GroupProfilePictureUploadResult.Success,
): OutgoingCspMessageHandle {
    return OutgoingCspMessageHandle(
        receivers,
        OutgoingCspGroupMessageCreator(
            messageId,
            Date(),
            groupIdentity,
        ) {
            GroupSetProfilePictureMessage().apply {
                this.blobId = groupProfilePictureUploadResultSuccess.blobId
                this.encryptionKey = groupProfilePictureUploadResultSuccess.encryptionKey
                this.size = groupProfilePictureUploadResultSuccess.size
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
