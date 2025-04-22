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

package ch.threema.app.processors.incomingcspmessage.conversation

import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.processors.incomingcspmessage.groupcontrol.runCommonGroupReceiveSteps
import ch.threema.app.utils.MimeUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.base.utils.now
import ch.threema.data.models.GroupModel
import ch.threema.domain.protocol.csp.messages.file.FileData
import ch.threema.domain.protocol.csp.messages.file.GroupFileMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.media.FileDataModel
import java.util.UUID
import org.slf4j.Logger

private val logger: Logger = LoggingUtil.getThreemaLogger("IncomingGroupFileMessageTask")

class IncomingGroupFileMessageTask(
    groupFileMessage: GroupFileMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<GroupFileMessage>(
    groupFileMessage,
    triggerSource,
    serviceManager,
) {
    private val messageService = serviceManager.messageService
    private val groupService = serviceManager.groupService
    private val groupModelRepository by lazy { serviceManager.modelRepositories.groups }

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        val groupModel: GroupModel = runCommonGroupReceiveSteps(message, handle, serviceManager)
            ?: run {
                logger.warn("Discarding message ${message.messageId}: Could not find group for incoming group file message")
                return ReceiveStepsResult.DISCARD
            }
        return processIncomingMessage(
            preCheckedGroupModel = groupModel,
            triggerSource = TriggerSource.REMOTE,
        )
    }

    override suspend fun executeMessageStepsFromSync() = processIncomingMessage(
        preCheckedGroupModel = null,
        triggerSource = TriggerSource.SYNC,
    )

    private fun processIncomingMessage(
        preCheckedGroupModel: GroupModel?,
        triggerSource: TriggerSource,
    ): ReceiveStepsResult {
        // 0: Group model must exist locally at this point
        val groupModel: GroupModel = preCheckedGroupModel
            ?: groupModelRepository.getByCreatorIdentityAndId(
                message.groupCreator,
                message.apiGroupId,
            ) ?: run {
            logger.error("Discarding message ${message.messageId}: Could not find group model with api id ${message.apiGroupId}")
            return ReceiveStepsResult.DISCARD
        }

        // 1: Check if the group message already exists locally (from previous run(s) of this task).
        //    If so, cancel and accept that the download for the content(s) might not be complete.
        messageService.getGroupMessageModel(
            message.messageId,
            message.groupCreator,
            message.apiGroupId,
        )?.run { return ReceiveStepsResult.DISCARD }

        val fileData: FileData = message.fileData ?: run {
            logger.error("Discarding message ${message.messageId}: Missing file data")
            return ReceiveStepsResult.DISCARD
        }

        // 2. Map the FileData object to a FileDataModel instance (field "downloaded" is false)
        val fileDataModel: FileDataModel = FileDataModel.fromIncomingFileData(fileData)

        // 3. Create the actual AbstractMessageModel containing the file and sender information
        val messageModel: GroupMessageModel = createGroupMessageModelFromFileMessage(
            groupFileMessage = message,
            fileDataModel = fileDataModel,
            fileData = fileData,
            groupModel = groupModel,
        )

        val oldGroupModel = groupService.getByGroupMessage(message) ?: run {
            logger.error("Old group model is null")
            return ReceiveStepsResult.DISCARD
        }

        // 4. Un-archive group if currently archived
        if (triggerSource == TriggerSource.REMOTE) {
            groupModel.setIsArchivedFromLocalOrRemote(false)
        }

        // 5. Bump last updated timestamp if necessary to move conversation up in list
        if (message.bumpLastUpdate()) {
            groupService.bumpLastUpdate(oldGroupModel)
        }

        // 6. Save message model and inform listeners about new message
        messageService.save(messageModel)
        ListenerManager.messageListeners.handle { messageListener ->
            messageListener.onNew(
                messageModel,
            )
        }

        // 7. Download thumbnail and content blob (if auto download enabled)
        //    We still return SUCCESS even if the blobs could net be downloaded
        processMediaContent(fileData, messageModel)

        return ReceiveStepsResult.SUCCESS
    }

    /**
     *  @return A new instance of `AbstractMessageModel` with type [MessageType.FILE] containing
     *  the `body` and `dataObject` from the passed file information.
     */
    private fun createGroupMessageModelFromFileMessage(
        groupFileMessage: GroupFileMessage,
        fileDataModel: FileDataModel,
        fileData: FileData,
        groupModel: GroupModel,
    ): GroupMessageModel {
        return GroupMessageModel().apply {
            uid = UUID.randomUUID().toString()
            apiMessageId = message.messageId.toString()

            identity = groupFileMessage.fromIdentity
            groupId = groupModel.getDatabaseId().toInt()

            this.fileData = fileDataModel
            messageContentsType = MimeUtil.getContentTypeFromFileData(fileDataModel)

            postedAt = message.date
            createdAt = now()

            messageFlags = message.messageFlags

            isOutbox = false
            isSaved = true

            correlationId = fileData.correlationId
            forwardSecurityMode = message.forwardSecurityMode
        }
    }

    /**
     *  **Synchronously**
     *
     *  Attempt to download the thumbnail and the actual media content. Even if
     *  the thumbnail download failed, we try to download the actual blob contents.
     */
    private fun processMediaContent(fileData: FileData, messageModel: GroupMessageModel) {
        runCatching {
            messageService.downloadThumbnailIfPresent(fileData, messageModel)
        }.onSuccess { thumbnailWasDownloaded: Boolean ->
            if (thumbnailWasDownloaded) {
                ListenerManager.messageListeners.handle { messageListener ->
                    messageListener.onModified(
                        listOf(messageModel),
                    )
                }
            }
        }.onFailure { throwable ->
            logger.error("Unable to download thumbnail blob", throwable)
        }
        if (messageService.shouldAutoDownload(messageModel)) {
            runCatching {
                messageService.downloadMediaMessage(messageModel, null)
            }.onFailure { throwable ->
                // a failed blob auto-download should not be considered a failure as the user can try again manually
                logger.error("Unable to auto-download blob", throwable)
            }
        }
    }
}
