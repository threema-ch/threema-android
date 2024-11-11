/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

package ch.threema.app.activities

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.threema.app.ThreemaApplication
import ch.threema.app.services.MessageService
import ch.threema.app.utils.QuoteUtil
import ch.threema.app.utils.StateBitmapUtil
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityMode
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.DistributionListMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageState
import ch.threema.storage.models.MessageType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.Date

class MessageDetailsViewModel(
    savedStateHandle: SavedStateHandle,
    messageService: MessageService,
    private val myIdentity: String,
) : StateFlowViewModel() {

    companion object {

        private const val MESSAGE_ID = "messageId"
        private const val MESSAGE_TYPE = "messageType"

        fun provideFactory(
            messageId: Int,
            messageType: String?,
            myIdentity: String,
        ) = viewModelFactory {
            initializer {
                MessageDetailsViewModel(
                    this.createSavedStateHandle().apply {
                        set(MESSAGE_ID, messageId)
                        set(MESSAGE_TYPE, messageType)
                    },
                    ThreemaApplication.requireServiceManager().messageService,
                    myIdentity,
                )
            }
        }
    }

    private val _uiState: MutableStateFlow<ChatMessageDetailsUiState> = let {
        val messageId = checkNotNull(savedStateHandle[MESSAGE_ID]) as Int
        val messageType = checkNotNull(savedStateHandle[MESSAGE_TYPE]) as String
        val message = messageService.getMessageModelFromId(messageId, messageType)
        MutableStateFlow(
            ChatMessageDetailsUiState(message.toUiModel(myIdentity), true)
        )
    }
    val uiState: StateFlow<ChatMessageDetailsUiState> = _uiState.stateInViewModel(initialValue = _uiState.value)

    fun refreshMessage(updatedMessage: AbstractMessageModel) {
        _uiState.update { it.copy(message = updatedMessage.toUiModel(myIdentity)) }
    }

    fun markupText(value: Boolean) {
        _uiState.update { it.copy(shouldMarkupText = value) }
    }
}

data class ChatMessageDetailsUiState(
    val message: MessageUiModel,
    val shouldMarkupText: Boolean
)

// TODO(ANDR-3195): Move MessageModel mappings from ChatMessageDetailsViewModel to data models
data class MessageUiModel(
    val uid: String,
    val text: String,
    val createdAt: Date,
    val editedAt: Date?,
    val isDeleted: Boolean,
    val isOutbox: Boolean,
    val ackUiModel: AckUiModel?,
    @DrawableRes val deliveryIconRes: Int?,
    @StringRes val deliveryIconContentDescriptionRes: Int?,
    val messageTimestampsUiModel: MessageTimestampsUiModel,
    val messageDetailsUiModel: MessageDetailsUiModel
)

enum class UserReaction {
    REACTED,
    NONE,
}

enum class ContactAckDecState {
    ACK,
    DEC,
    NONE,
}

class GroupAckDecState(val count: Int, val userReaction: UserReaction)

sealed interface AckUiModel

data class ContactAckUiModel(
    val ackDecState: ContactAckDecState,
) : AckUiModel

data class GroupAckUiModel(
    val ackState: GroupAckDecState,
    val decState: GroupAckDecState,
) : AckUiModel

data class MessageTimestampsUiModel(
    val createdAt: Date? = null,
    val sentAt: Date? = null,
    val receivedAt: Date? = null,
    val deliveredAt: Date? = null,
    val readAt: Date? = null,
    val modifiedAt: Date? = null,
    val editedAt: Date? = null,
    val deletedAt: Date? = null
)

data class MessageDetailsUiModel(
    val messageId: String? = null,
    val mimeType: String? = null,
    val fileSizeInBytes: Long? = null,
    val pfsState: ForwardSecurityMode? = null
)

fun AbstractMessageModel.toUiModel(myIdentity: String) = MessageUiModel(
    uid = this.uid,
    text = QuoteUtil.getMessageBody(this, false) ?: "",
    createdAt = this.createdAt,
    editedAt = this.editedAt,
    isDeleted = this.isDeleted,
    isOutbox = this.isOutbox,
    deliveryIconRes = StateBitmapUtil.getInstance()?.getStateDrawable(this.state),
    deliveryIconContentDescriptionRes = StateBitmapUtil.getInstance()?.getStateDescription(this.state),
    ackUiModel = this.getAckUiModel(myIdentity),
    messageTimestampsUiModel = this.toMessageTimestampsUiModel(),
    messageDetailsUiModel = this.toMessageDetailsUiModel()
)

private fun AbstractMessageModel.getAckUiModel(myIdentity: String) = let {
    if (this is GroupMessageModel) {
        val ackStates = groupMessageStates?.filter { groupMessageState -> groupMessageState.value == MessageState.USERACK.name }
        val decStates = groupMessageStates?.filter { groupMessageState -> groupMessageState.value == MessageState.USERDEC.name }

        val ackUserReaction = if (ackStates?.containsKey(myIdentity) == true) {
            UserReaction.REACTED
        } else {
            UserReaction.NONE
        }
        val decUserReaction = if (decStates?.containsKey(myIdentity) == true) {
            UserReaction.REACTED
        } else {
            UserReaction.NONE
        }

        GroupAckUiModel(
            ackState = GroupAckDecState(ackStates?.size ?: 0, ackUserReaction),
            decState = GroupAckDecState(decStates?.size ?: 0, decUserReaction),
        )
    } else {
        val contactAckDecState = when (this.state) {
            MessageState.USERACK -> ContactAckDecState.ACK
            MessageState.USERDEC -> ContactAckDecState.DEC
            else -> ContactAckDecState.NONE
        }

        ContactAckUiModel(contactAckDecState)
    }
}

fun AbstractMessageModel?.toMessageTimestampsUiModel(): MessageTimestampsUiModel {
    if (this == null) {
        return MessageTimestampsUiModel()
    }
    if (this.isStatusMessage) {
        return MessageTimestampsUiModel(createdAt = this.createdAt)
    } else if (this.type == MessageType.GROUP_CALL_STATUS) {
        return MessageTimestampsUiModel(sentAt = this.createdAt, deliveredAt = if (!this.isOutbox) this.deliveredAt else null)
    }

    return if (this.isOutbox) {
        val shouldShowAdditionalTimestamps = this.state != MessageState.SENT && !(this.type == MessageType.BALLOT && this is GroupMessageModel)

        val shouldShowPostedAt =
            (this.state != MessageState.SENDING && this.state != MessageState.SENDFAILED && this.state != MessageState.FS_KEY_MISMATCH && this.state != MessageState.PENDING)
                || this.type == MessageType.BALLOT

        val shouldShowModifiedAt = !(this.state == MessageState.READ && this.modifiedAt == this.readAt)
            && !(this.state == MessageState.DELIVERED && this.modifiedAt == this.deliveredAt)

        MessageTimestampsUiModel(
            createdAt = this.createdAt,
            sentAt = if (shouldShowPostedAt) this.postedAt else null,
            deliveredAt = if (shouldShowAdditionalTimestamps) this.deliveredAt else null,
            readAt = if (shouldShowAdditionalTimestamps) this.readAt else null,
            modifiedAt = if (shouldShowAdditionalTimestamps && shouldShowModifiedAt) this.modifiedAt else null,
            editedAt = this.editedAt,
            deletedAt = this.deletedAt
        )
    } else {
        MessageTimestampsUiModel(
            createdAt = this.postedAt,
            receivedAt = this.createdAt,
            readAt = if (this.state != MessageState.READ) this.modifiedAt else null,
            editedAt = this.editedAt,
            deletedAt = this.deletedAt
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
    val mimeType: String? = if (this.type == MessageType.FILE) this.fileData.mimeType.takeIf(String::isNotBlank) else null
    val messageId: String? = this.apiMessageId?.takeIf(String::isNotBlank)
    val pfsState: ForwardSecurityMode? = if (this !is DistributionListMessageModel) this.forwardSecurityMode else null

    return MessageDetailsUiModel(
        mimeType = mimeType,
        fileSizeInBytes = fileSize,
        messageId = messageId,
        pfsState = pfsState
    )
}
