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

package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.utils.MimeUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.messages.file.FileData
import ch.threema.domain.protocol.csp.messages.file.FileMessage
import ch.threema.protobuf.Common
import ch.threema.protobuf.d2d.MdD2D
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageState
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.media.FileDataModel
import java.util.Date

private val logger = LoggingUtil.getThreemaLogger("ReflectedOutgoingFileTask")

internal class ReflectedOutgoingFileTask(
    message: MdD2D.OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingContactMessageTask(message, Common.CspE2eMessageType.FILE, serviceManager) {
    private val messageService by lazy { serviceManager.messageService }

    private val fileMessage: FileMessage by lazy { FileMessage.fromByteArray(message.body.toByteArray()) }

    override val storeNonces: Boolean
        get() = fileMessage.protectAgainstReplay()

    override val shouldBumpLastUpdate: Boolean = true

    override fun processOutgoingMessage() {
        // 1: Check if the message already exists locally (from previous run(s) of this task).
        //    If so, cancel and accept that the download for the content(s) might not be complete.
        messageService.getContactMessageModel(
            fileMessage.messageId,
            messageReceiver.contact.identity,
        )?.run { return }

        val fileData: FileData = fileMessage.fileData ?: run {
            logger.warn("Message {} error: missing file data", message.messageId)
            return
        }

        // 2. Map the FileData object to a FileDataModel instance (field "downloaded" is false)
        val fileDataModel: FileDataModel = FileDataModel.fromIncomingFileData(fileData)

        // 3. Create the actual AbstractMessageModel containing the file and receiver information
        val messageModel: MessageModel = createMessageModelFromFileMessage(
            fileMessage = fileMessage,
            fileDataModel = fileDataModel,
            fileData = fileData,
        )

        // 4. Save group message model and inform listeners about new message
        messageService.save(messageModel)
        ListenerManager.messageListeners.handle { messageListener ->
            messageListener.onNew(
                messageModel,
            )
        }

        // 5. Download thumbnail and content blob (if auto download enabled)
        processMediaContent(fileData, messageModel)
    }

    /**
     *  @return A new instance of `AbstractMessageModel` with type [MessageType.FILE] containing
     *  the `body` and `dataObject` from the passed file information.
     *  The messages receiver information is set according to the [messageReceiver].
     *  State will be [MessageState.SENT].
     */
    private fun createMessageModelFromFileMessage(
        fileMessage: FileMessage,
        fileDataModel: FileDataModel,
        fileData: FileData,
    ): MessageModel {
        val messageModel: MessageModel = messageReceiver.createLocalModel(
            /* type = */
            MessageType.FILE,
            /* contentsType = */
            MimeUtil.getContentTypeFromFileData(fileDataModel),
            /* postedAt = */
            Date(message.createdAt),
        )
        initializeMessageModelsCommonFields(messageModel)
        return messageModel.apply {
            this.fileData = fileDataModel
            messageFlags = fileMessage.messageFlags
            correlationId = fileData.correlationId
            forwardSecurityMode = fileMessage.forwardSecurityMode
            state = MessageState.SENT
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
