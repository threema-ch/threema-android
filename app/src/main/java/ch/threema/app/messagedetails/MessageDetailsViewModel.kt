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

package ch.threema.app.messagedetails

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import ch.threema.app.services.MessageService
import ch.threema.app.utils.QuoteUtil
import ch.threema.app.utils.StateBitmapUtil
import ch.threema.data.repositories.EmojiReactionsRepository
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityMode
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.DistributionListMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageState
import ch.threema.storage.models.MessageType
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MessageDetailsViewModel(
    messageService: MessageService,
    private val emojiReactionsRepository: EmojiReactionsRepository,
    private val messageId: Int,
    private val messageType: String,
) : ViewModel() {
    private val _uiState: MutableStateFlow<ChatMessageDetailsUiState> = let {
        val message = messageService.getMessageModelFromId(messageId, messageType)
        MutableStateFlow(
            ChatMessageDetailsUiState(
                message = message.toUiModel(),
                hasReactions = message.hasReactions(),
                shouldMarkupText = true,
            ),
        )
    }
    val uiState: StateFlow<ChatMessageDetailsUiState> = _uiState.asStateFlow()

    private fun AbstractMessageModel.hasReactions(): Boolean =
        emojiReactionsRepository.safeGetReactionsByMessage(this).isNotEmpty()

    fun refreshMessage(updatedMessage: AbstractMessageModel) {
        _uiState.update { it.copy(message = updatedMessage.toUiModel()) }
    }

    fun markupText(value: Boolean) {
        _uiState.update { it.copy(shouldMarkupText = value) }
    }
}

data class ChatMessageDetailsUiState(
    val message: MessageUiModel,
    val hasReactions: Boolean,
    val shouldMarkupText: Boolean,
)

// TODO(ANDR-3195): Move MessageModel mappings from ChatMessageDetailsViewModel to data models
data class MessageUiModel(
    val id: Int,
    val uid: String,
    val text: String,
    val createdAt: Date,
    val editedAt: Date?,
    val isDeleted: Boolean,
    val isOutbox: Boolean,
    @DrawableRes val deliveryIconRes: Int?,
    @StringRes val deliveryIconContentDescriptionRes: Int?,
    val messageTimestampsUiModel: MessageTimestampsUiModel,
    val messageDetailsUiModel: MessageDetailsUiModel,
    val type: MessageType?,
)

data class MessageTimestampsUiModel(
    val createdAt: Date? = null,
    val sentAt: Date? = null,
    val receivedAt: Date? = null,
    val deliveredAt: Date? = null,
    val readAt: Date? = null,
    val modifiedAt: Date? = null,
    val editedAt: Date? = null,
    val deletedAt: Date? = null,
) {
    fun hasProperties(): Boolean {
        return createdAt != null ||
            sentAt != null ||
            receivedAt != null ||
            deliveredAt != null ||
            readAt != null ||
            modifiedAt != null ||
            editedAt != null ||
            deletedAt != null
    }
}

data class MessageDetailsUiModel(
    val messageId: String? = null,
    val mimeType: String? = null,
    val fileSizeInBytes: Long? = null,
    val pfsState: ForwardSecurityMode? = null,
) {
    fun hasProperties(): Boolean {
        return messageId != null ||
            mimeType != null ||
            fileSizeInBytes != null ||
            pfsState != null
    }
}

fun AbstractMessageModel.toUiModel() = MessageUiModel(
    id = this.id,
    uid = this.uid!!,
    text = QuoteUtil.getMessageBody(this, false) ?: "",
    createdAt = this.createdAt!!,
    editedAt = this.editedAt,
    isDeleted = this.isDeleted,
    isOutbox = this.isOutbox,
    deliveryIconRes = StateBitmapUtil.getInstance()?.getStateDrawable(this.state),
    deliveryIconContentDescriptionRes = StateBitmapUtil.getInstance()
        ?.getStateDescription(this.state),
    messageTimestampsUiModel = this.toMessageTimestampsUiModel(),
    messageDetailsUiModel = this.toMessageDetailsUiModel(),
    type = this.type,
)

fun AbstractMessageModel?.toMessageTimestampsUiModel(): MessageTimestampsUiModel {
    if (this == null) {
        return MessageTimestampsUiModel()
    }
    if (this.isStatusMessage) {
        return MessageTimestampsUiModel(createdAt = this.createdAt)
    } else if (this.type == MessageType.GROUP_CALL_STATUS) {
        return MessageTimestampsUiModel(
            sentAt = this.createdAt,
            deliveredAt = if (!this.isOutbox) this.deliveredAt else null,
        )
    }

    return if (this.isOutbox) {
        val shouldShowAdditionalTimestamps =
            this.state != MessageState.SENT && !(this.type == MessageType.BALLOT && this is GroupMessageModel)

        val shouldShowPostedAt =
            (
                state != MessageState.SENDING && state != MessageState.SENDFAILED &&
                    state != MessageState.FS_KEY_MISMATCH && state != MessageState.PENDING
                ) ||
                type == MessageType.BALLOT

        val shouldShowModifiedAt =
            !(this.state == MessageState.READ && this.modifiedAt == this.readAt) &&
                !(this.state == MessageState.DELIVERED && this.modifiedAt == this.deliveredAt) &&
                !this.isDeleted

        MessageTimestampsUiModel(
            createdAt = this.createdAt,
            sentAt = if (shouldShowPostedAt) this.postedAt else null,
            deliveredAt = if (shouldShowAdditionalTimestamps) this.deliveredAt else null,
            readAt = if (shouldShowAdditionalTimestamps) this.readAt else null,
            modifiedAt = if (shouldShowAdditionalTimestamps && shouldShowModifiedAt) this.modifiedAt else null,
            editedAt = this.editedAt,
            deletedAt = this.deletedAt,
        )
    } else {
        MessageTimestampsUiModel(
            createdAt = this.postedAt,
            receivedAt = this.createdAt,
            readAt = if (this.state != MessageState.READ) this.modifiedAt else null,
            editedAt = this.editedAt,
            deletedAt = this.deletedAt,
        )
    }
}

fun AbstractMessageModel?.toMessageDetailsUiModel(): MessageDetailsUiModel {
    if (this == null) {
        return MessageDetailsUiModel()
    }
    if (this.isStatusMessage || this.type == MessageType.GROUP_CALL_STATUS) {
        return MessageDetailsUiModel()
    }
    val fileSize: Long? = if (this.type == MessageType.FILE) {
        this.fileData.fileSize.takeIf { fileSize -> fileSize > 0L }
    } else {
        null
    }
    val mimeType: String? =
        if (this.type == MessageType.FILE) this.fileData.mimeType.takeIf(String::isNotBlank) else null
    val messageId: String? = this.apiMessageId?.takeIf(String::isNotBlank)
    val pfsState: ForwardSecurityMode? =
        if (this !is DistributionListMessageModel) this.forwardSecurityMode else null

    return MessageDetailsUiModel(
        mimeType = mimeType,
        fileSizeInBytes = fileSize,
        messageId = messageId,
        pfsState = pfsState,
    )
}
