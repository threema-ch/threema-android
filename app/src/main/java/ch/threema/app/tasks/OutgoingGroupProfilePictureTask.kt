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

import android.graphics.Bitmap
import ch.threema.app.managers.ServiceManager
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import kotlinx.serialization.Serializable

private val logger = LoggingUtil.getThreemaLogger("OutgoingGroupProfilePictureTask")

/**
 * This task sends a set-profile-picture message to the group if there is a group picture. If no
 * group picture is set for the given group, an [OutgoingGroupDeleteProfilePictureTask] is started
 * directly. Note that the messages are only sent to the given [receiverIdentities].
 */
class OutgoingGroupProfilePictureTask(
    private val groupId: GroupId,
    private val creatorIdentity: String,
    receiverIdentities: Set<String>,
    messageId: MessageId?,
    private val serviceManager: ServiceManager,
) : OutgoingCspMessageTask(serviceManager) {
    private val messageId by lazy { messageId ?: MessageId() }
    private val userService by lazy { serviceManager.userService }
    private val receiverIdentities by lazy { receiverIdentities - userService.identity }
    private val fileService by lazy { serviceManager.fileService }

    override val type: String = "OutgoingGroupProfilePictureTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        if (creatorIdentity != userService.identity) {
            logger.warn("Only the group creator should send the group picture to the members")
            return
        }

        val group = groupService.getByApiGroupIdAndCreator(groupId, creatorIdentity)
        if (group == null) {
            logger.error(
                "Could not find group {} with creator {} to send the profile picture",
                groupId,
                creatorIdentity
            )
            return
        }

        if (fileService.hasGroupAvatarFile(group)) {
            sendGroupPhoto(fileService.getGroupAvatar(group), handle)
        } else {
            sendGroupDeletePhoto(handle)
        }
    }

    private suspend fun sendGroupPhoto(groupPhoto: Bitmap, handle: ActiveTaskCodec) {
        OutgoingGroupSetProfilePictureTask(
            groupId,
            creatorIdentity,
            receiverIdentities,
            groupPhoto,
            null,
            serviceManager
        ).invoke(handle)
    }

    private suspend fun sendGroupDeletePhoto(handle: ActiveTaskCodec) {
        OutgoingGroupDeleteProfilePictureTask(
            groupId,
            creatorIdentity,
            receiverIdentities,
            null,
            serviceManager
        ).invoke(handle)
    }

    override fun serialize(): SerializableTaskData = OutgoingGroupProfilePictureData(
        groupId.groupId,
        creatorIdentity,
        receiverIdentities,
        messageId.messageId
    )

    @Serializable
    class OutgoingGroupProfilePictureData(
        private val groupId: ByteArray,
        private val creatorIdentity: String,
        private val receiverIdentities: Set<String>,
        private val messageId: ByteArray,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            OutgoingGroupProfilePictureTask(
                GroupId(groupId),
                creatorIdentity,
                receiverIdentities,
                MessageId(messageId),
                serviceManager
            )
    }
}
