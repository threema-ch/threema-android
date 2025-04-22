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
import ch.threema.app.utils.MimeUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.base.utils.now
import ch.threema.domain.protocol.csp.messages.file.FileData
import ch.threema.domain.protocol.csp.messages.file.FileMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.media.FileDataModel
import java.util.UUID
import org.slf4j.Logger

private val logger: Logger = LoggingUtil.getThreemaLogger("IncomingContactFileMessageTask")

class IncomingContactFileMessageTask(
    fileMessage: FileMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<FileMessage>(
    fileMessage,
    triggerSource,
    serviceManager,
) {
    private val messageService = serviceManager.messageService
    private val contactService = serviceManager.contactService
    private val contactRepository = serviceManager.modelRepositories.contacts

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec) =
        processIncomingMessage(
            triggerSource = TriggerSource.REMOTE,
        )

    override suspend fun executeMessageStepsFromSync() = processIncomingMessage(
        triggerSource = TriggerSource.SYNC,
    )

    private fun processIncomingMessage(triggerSource: TriggerSource): ReceiveStepsResult {
        // 0: Contact must exist locally at this point
        if (!contactRepository.existsByIdentity(message.fromIdentity)) {
            logger.error("Discarding message ${message.messageId}: Sender contact with identity ${message.fromIdentity} does not exist locally.")
            return ReceiveStepsResult.DISCARD
        }

        // 1: Check if the message already exists locally (from previous run(s) of this task).
        //    If so, cancel and accept that the download for the content(s) might not be complete.
        messageService.getContactMessageModel(
            message.messageId,
            message.fromIdentity,
        )?.run { return ReceiveStepsResult.DISCARD }

        val fileData: FileData = message.fileData ?: run {
            logger.error("Discarding message ${message.messageId}: Missing file data")
            return ReceiveStepsResult.DISCARD
        }

        // 2. Map the FileData object to a FileDataModel instance (field "downloaded" is false)
        val fileDataModel: FileDataModel = FileDataModel.fromIncomingFileData(fileData)

        // 3. Create the actual AbstractMessageModel containing the file and sender information
        val messageModel: MessageModel = createMessageModelFromFileMessage(
            fileMessage = message,
            fileDataModel = fileDataModel,
            fileData = fileData,
        )

        // 4. Un-archive the contact and set the the acquaintance level to "direct" because it is a 1:1 chat now
        if (triggerSource == TriggerSource.REMOTE) {
            contactService.setIsArchived(message.fromIdentity, false, triggerSource)
            contactService.setAcquaintanceLevel(
                message.fromIdentity,
                ContactModel.AcquaintanceLevel.DIRECT,
            )
        }

        // 5. Bump last updated timestamp if necessary to move conversation up in list
        if (message.bumpLastUpdate()) {
            contactService.bumpLastUpdate(message.fromIdentity)
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
    private fun createMessageModelFromFileMessage(
        fileMessage: FileMessage,
        fileDataModel: FileDataModel,
        fileData: FileData,
    ): MessageModel {
        return MessageModel().apply {
            uid = UUID.randomUUID().toString()
            apiMessageId = message.messageId.toString()

            identity = fileMessage.fromIdentity

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
    private fun processMediaContent(fileData: FileData, messageModel: MessageModel) {
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
