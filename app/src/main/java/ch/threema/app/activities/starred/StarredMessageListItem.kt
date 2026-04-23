package ch.threema.app.activities.starred

import android.content.Context
import android.content.res.ColorStateList
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ch.threema.android.ResolvedString
import ch.threema.android.ResourceIdString
import ch.threema.android.toResolvedString
import ch.threema.app.R
import ch.threema.app.activities.starred.models.ConversationParticipant
import ch.threema.app.activities.starred.models.StarredMessageUiModel
import ch.threema.app.compose.common.LocalDayOfYear
import ch.threema.app.compose.common.SpacerHorizontal
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.common.avatar.AvatarAsync
import ch.threema.app.compose.common.colorReferenceResource
import ch.threema.app.compose.common.extensions.get
import ch.threema.app.compose.common.immutables.ImmutableBitmap
import ch.threema.app.compose.common.text.conversation.ConversationText
import ch.threema.app.compose.common.text.conversation.EmojiSettings
import ch.threema.app.compose.common.text.conversation.HighlightFeature
import ch.threema.app.compose.common.text.conversation.MentionFeature
import ch.threema.app.compose.preview.PreviewData
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.preference.service.PreferenceService.Companion.EMOJI_STYLE_ANDROID
import ch.threema.app.preference.service.PreferenceService.EmojiStyle
import ch.threema.app.usecases.groups.GroupDisplayName
import ch.threema.app.utils.IconUtil
import ch.threema.app.utils.LocaleUtil
import ch.threema.app.utils.MimeUtil
import ch.threema.app.utils.NameUtil
import ch.threema.common.minus
import ch.threema.common.now
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.domain.models.ContactReceiverIdentifier
import ch.threema.domain.models.GroupReceiverIdentifier
import ch.threema.domain.models.ReceiverIdentifier
import ch.threema.domain.types.Identity
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.MessageContentsType
import java.util.Date
import kotlin.time.Duration.Companion.days

private const val FLOW_CHARACTER = "\u25BA\uFE0E" // "►"

@Composable
fun StarredMessageListItem(
    modifier: Modifier = Modifier,
    localDayOfYear: LocalDayOfYear,
    avatarBitmapProvider: suspend (ReceiverIdentifier) -> ImmutableBitmap?,
    messageMediaPreviewProvider: suspend (AbstractMessageModel) -> ImmutableBitmap?,
    ownIdentity: Identity,
    @EmojiStyle emojiStyle: Int,
    contactNameFormat: ContactNameFormat,
    starredMessageUiModel: StarredMessageUiModel,
    isSelected: Boolean,
    highlightedMessageContent: String?,
    onClickMessageBubble: (AbstractMessageModel) -> Unit,
    onLongClickMessageBubble: (AbstractMessageModel) -> Unit,
    onClickNavigateToMessage: (AbstractMessageModel) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpacerHorizontal(GridUnit.x1_5)

        AvatarContent(
            starredMessageUiModel = starredMessageUiModel,
            avatarBitmapProvider = avatarBitmapProvider,
        )

        SpacerHorizontal(GridUnit.x1_5)

        MessageBubble(
            starredMessageUiModel = starredMessageUiModel,
            messageMediaPreviewProvider = messageMediaPreviewProvider,
            ownIdentity = ownIdentity,
            contactNameFormat = contactNameFormat,
            emojiStyle = emojiStyle,
            localDayOfYear = localDayOfYear,
            isSelected = isSelected,
            highlightedMessageContent = highlightedMessageContent,
            onClick = onClickMessageBubble,
            onLongClick = onLongClickMessageBubble,
        )

        IconButton(
            modifier = Modifier
                .size(32.dp)
                .padding(GridUnit.x0_5),
            onClick = {
                onClickNavigateToMessage(starredMessageUiModel.messageModel)
            },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_keyboard_arrow_right),
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun AvatarContent(
    modifier: Modifier = Modifier,
    starredMessageUiModel: StarredMessageUiModel,
    avatarBitmapProvider: suspend (ReceiverIdentifier) -> ImmutableBitmap?,
) {
    val avatarSize = 40.dp
    if (!starredMessageUiModel.isPrivate && !starredMessageUiModel.messageModel.isDeleted) {
        val receiverIdentifier = when (starredMessageUiModel) {
            is StarredMessageUiModel.StarredContactMessage -> {
                ContactReceiverIdentifier(
                    if (starredMessageUiModel.messageModel.isOutbox) {
                        starredMessageUiModel.receiver.identity.value
                    } else {
                        starredMessageUiModel.sender.identity.value
                    },
                )
            }
            is StarredMessageUiModel.StarredGroupMessage -> starredMessageUiModel.groupIdentifier
        }

        AvatarAsync(
            modifier = modifier.size(avatarSize),
            receiverIdentifier = receiverIdentifier,
            bitmapProvider = avatarBitmapProvider,
            contentDescription = null,
            fallbackIcon = when (starredMessageUiModel) {
                is StarredMessageUiModel.StarredContactMessage -> R.drawable.ic_contact
                is StarredMessageUiModel.StarredGroupMessage -> R.drawable.ic_group
            },
            showWorkBadge = when (starredMessageUiModel) {
                is StarredMessageUiModel.StarredContactMessage -> starredMessageUiModel.showWorkBadge
                else -> false
            },
            availabilityStatus = AvailabilityStatus.None, // TODO(ANDR-4714): Evaluate to set existing value
        )
    } else {
        Box(
            modifier = Modifier.size(avatarSize),
        )
    }
}

@Composable
private fun RowScope.MessageBubble(
    modifier: Modifier = Modifier,
    starredMessageUiModel: StarredMessageUiModel,
    messageMediaPreviewProvider: suspend (AbstractMessageModel) -> ImmutableBitmap?,
    ownIdentity: Identity,
    contactNameFormat: ContactNameFormat,
    @EmojiStyle emojiStyle: Int,
    localDayOfYear: LocalDayOfYear,
    isSelected: Boolean,
    highlightedMessageContent: String?,
    onClick: (AbstractMessageModel) -> Unit,
    onLongClick: (AbstractMessageModel) -> Unit,
) {
    Row(
        modifier = modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(
                color = getMessageBubbleBackgroundColor(
                    isOutbox = starredMessageUiModel.messageModel.isOutbox,
                    isSelected = isSelected,
                ),
            )
            .combinedClickable(
                onClick = {
                    onClick(starredMessageUiModel.messageModel)
                },
                onLongClick = {
                    onLongClick(starredMessageUiModel.messageModel)
                },
            )
            .padding(GridUnit.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val messageBubbleContentColor = getMessageBubbleContentColor(
            isOutbox = starredMessageUiModel.messageModel.isOutbox,
            isSelected = isSelected,
        )
        CompositionLocalProvider(LocalContentColor provides messageBubbleContentColor) {
            SpacerHorizontal(GridUnit.x1)

            if (!starredMessageUiModel.isPrivate) {
                MessageBubbleContent(
                    starredMessageUiModel = starredMessageUiModel,
                    messageMediaPreviewProvider = messageMediaPreviewProvider,
                    isSelected = isSelected,
                    ownIdentity = ownIdentity,
                    contactNameFormat = contactNameFormat,
                    emojiStyle = emojiStyle,
                    localDayOfYear = localDayOfYear,
                    highlightedMessageContent = highlightedMessageContent,
                )
            } else {
                MessageBubbleContentPrivate()
            }
        }
    }
}

@Composable
private fun RowScope.MessageBubbleContent(
    modifier: Modifier = Modifier,
    starredMessageUiModel: StarredMessageUiModel,
    messageMediaPreviewProvider: suspend (AbstractMessageModel) -> ImmutableBitmap?,
    isSelected: Boolean,
    ownIdentity: Identity,
    contactNameFormat: ContactNameFormat,
    @EmojiStyle emojiStyle: Int,
    localDayOfYear: LocalDayOfYear,
    highlightedMessageContent: String?,
) {
    Column(
        modifier = modifier.weight(1f),
    ) {
        ConversationText(
            rawInput = getTitle(
                context = LocalContext.current,
                starredMessageUiModel = starredMessageUiModel,
                contactNameFormat = contactNameFormat,
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (!starredMessageUiModel.messageModel.isDeleted) {
            ConversationText(
                rawInput = starredMessageUiModel.messageContent?.get() ?: "",
                textStyle = MaterialTheme.typography.bodyMedium,
                mentionFeature = MentionFeature.On(
                    ownIdentity = ownIdentity,
                    identityDisplayNames = starredMessageUiModel.mentionNames,
                ),
                highlightFeature = HighlightFeature.On(
                    highlightedContent = highlightedMessageContent,
                    ignoreCase = true,
                    backgroundColor = colorReferenceResource(android.R.attr.textColorHighlight),
                    foregroundColor = MaterialTheme.colorScheme.onPrimary,
                    spotlight = HighlightFeature.Spotlight.ShowAsSnippet(
                        maxCharactersBeforeHighlight = 50,
                    ),
                ),
                emojiSettings = EmojiSettings(
                    style = emojiStyle,
                ),
                markupEnabled = true,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            ThemedText(
                text = stringResource(R.string.message_was_deleted),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = LocalContentColor.current.copy(
                    alpha = 0.6f,
                ),
            )
        }

        val displayDate: String? = remember(
            key1 = starredMessageUiModel.messageModel.createdAt,
            key2 = localDayOfYear,
        ) {
            starredMessageUiModel.messageModel.createdAt?.let { messageCreatedAt ->
                LocaleUtil.formatDateRelative(messageCreatedAt.time)
            }
        }
        if (displayDate != null) {
            ThemedText(
                text = displayDate,
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(
                    alpha = 0.5f,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (starredMessageUiModel.showMediaPreview()) {
        SpacerHorizontal(GridUnit.x2)
        MessageMediaPreview(
            messageModel = starredMessageUiModel.messageModel,
            messageMediaPreviewProvider = messageMediaPreviewProvider,
            contentDescription = null,
            isSelected = isSelected,
        )
    }
}

private fun StarredMessageUiModel.showMediaPreview(): Boolean {
    if (messageModel.isDeleted) {
        return false
    }
    return messageModel.type == MessageType.FILE || messageModel.type == MessageType.BALLOT
}

@Composable
private fun MessageBubbleContentPrivate() {
    ThemedText(
        modifier = Modifier.padding(
            vertical = GridUnit.x1,
        ),
        text = stringResource(R.string.private_chat_subject),
        style = MaterialTheme.typography.bodyLarge,
        color = LocalContentColor.current.copy(
            alpha = 0.9f,
        ),
    )
}

@Composable
private fun MessageMediaPreview(
    modifier: Modifier = Modifier,
    messageModel: AbstractMessageModel,
    messageMediaPreviewProvider: suspend (AbstractMessageModel) -> ImmutableBitmap?,
    contentDescription: String?,
    isSelected: Boolean,
) {
    var avatarBitmap: ImageBitmap? by remember(messageModel.id) {
        mutableStateOf(null)
    }
    LaunchedEffect(messageModel.id) {
        val messageMimeType = MimeUtil.getMimeTypeFromMessageModel(messageModel)
        val mimeTypeCouldHaveThumbnail = MimeUtil.isSupportedImageFile(messageMimeType) || MimeUtil.isVideoFile(messageMimeType)
        if (messageModel.type == MessageType.FILE && mimeTypeCouldHaveThumbnail) {
            avatarBitmap = messageMediaPreviewProvider(messageModel)?.imageBitmap
        }
    }

    Box(
        modifier = modifier
            .size(60.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                color = getMessageMediaBoxColor(
                    context = LocalContext.current,
                    isSelected = isSelected,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        avatarBitmap?.let { imageBitmap ->
            Image(
                modifier = modifier.size(60.dp),
                bitmap = imageBitmap,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
            )
        } ?: run {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(
                    id = getMessageTypeIndicatorIcon(messageModel),
                ),
                contentDescription = null,
                tint = getOnMessageMediaBoxColor(
                    context = LocalContext.current,
                    isSelected = isSelected,
                ),
            )
        }
    }
}

private fun getMessageMediaBoxColor(context: Context, isSelected: Boolean): Color {
    val colorStateList: ColorStateList = ContextCompat.getColorStateList(context, R.color.controller_view_background_colorstatelist)!!
    val colorDefault = colorStateList.defaultColor
    val colorActivated = colorStateList.getColorForState(intArrayOf(android.R.attr.state_activated), colorDefault)
    return if (isSelected) {
        Color(colorActivated)
    } else {
        Color(colorDefault)
    }
}

private fun getOnMessageMediaBoxColor(context: Context, isSelected: Boolean): Color {
    val colorStateList: ColorStateList = ContextCompat.getColorStateList(context, R.color.controller_view_on_background_colorstatelist)!!
    val colorDefault = colorStateList.defaultColor
    val colorActivated = colorStateList.getColorForState(intArrayOf(android.R.attr.state_activated), colorDefault)
    return if (isSelected) {
        Color(colorActivated)
    } else {
        Color(colorDefault)
    }
}

@DrawableRes
private fun getMessageTypeIndicatorIcon(messageModel: AbstractMessageModel): Int {
    return if (messageModel.messageContentsType == MessageContentsType.VOICE_MESSAGE) {
        R.drawable.ic_keyboard_voice_outline
    } else if (messageModel.type == MessageType.FILE) {
        IconUtil.getMimeIcon(messageModel.fileData.getMimeType())
    } else if (messageModel.type == MessageType.BALLOT) {
        R.drawable.ic_outline_rule
    } else {
        IconUtil.getMimeIcon("application/x-error")
    }
}

private fun getTitle(
    context: Context,
    starredMessageUiModel: StarredMessageUiModel,
    contactNameFormat: ContactNameFormat,
): String {
    val senderDisplayName = getSenderDisplayName(
        context = context,
        sender = starredMessageUiModel.sender,
        contactNameFormat = contactNameFormat,
    )
    return when (starredMessageUiModel) {
        is StarredMessageUiModel.StarredContactMessage -> {
            if (starredMessageUiModel.receiver is ConversationParticipant.Contact) {
                val receiverDisplayName = NameUtil.getContactDisplayName(
                    /* identity = */
                    starredMessageUiModel.receiver.identity.value,
                    /* firstName = */
                    starredMessageUiModel.receiver.firstname,
                    /* lastName = */
                    starredMessageUiModel.receiver.lastname,
                    /* contactNameFormat = */
                    contactNameFormat,
                )
                "$senderDisplayName $FLOW_CHARACTER $receiverDisplayName"
            } else {
                senderDisplayName
            }
        }
        is StarredMessageUiModel.StarredGroupMessage -> {
            val resolvedGroupName = starredMessageUiModel.groupDisplayName?.resolve(context) ?: ""
            "$senderDisplayName $FLOW_CHARACTER $resolvedGroupName"
        }
    }
}

private fun getSenderDisplayName(
    context: Context,
    sender: ConversationParticipant,
    contactNameFormat: ContactNameFormat,
): String =
    when (sender) {
        is ConversationParticipant.Me -> context.getString(R.string.me_myself_and_i)
        is ConversationParticipant.Contact -> NameUtil.getContactDisplayName(
            /* identity = */
            sender.identity.value,
            /* firstName = */
            sender.firstname,
            /* lastName = */
            sender.lastname,
            /* contactNameFormat = */
            contactNameFormat,
        )
    }

@Composable
@ReadOnlyComposable
private fun getMessageBubbleBackgroundColor(isOutbox: Boolean, isSelected: Boolean): Color {
    return if (isOutbox) {
        if (isSelected) {
            colorReferenceResource(R.attr.colorMessageBubbleSendContainerSelected)
        } else {
            colorReferenceResource(R.attr.colorMessageBubbleSendContainer)
        }
    } else {
        if (isSelected) {
            colorReferenceResource(R.attr.colorMessageBubbleReceiveContainerSelected)
        } else {
            colorReferenceResource(R.attr.colorMessageBubbleReceiveContainer)
        }
    }
}

@Composable
@ReadOnlyComposable
private fun getMessageBubbleContentColor(isOutbox: Boolean, isSelected: Boolean): Color {
    return if (isOutbox) {
        if (isSelected) {
            colorReferenceResource(R.attr.colorOnMessageBubbleSendContainerSelected)
        } else {
            colorReferenceResource(R.attr.colorOnMessageBubbleSendContainer)
        }
    } else {
        if (isSelected) {
            colorReferenceResource(R.attr.colorOnMessageBubbleReceiveContainerSelected)
        } else {
            colorReferenceResource(R.attr.colorOnMessageBubbleReceiveContainer)
        }
    }
}

private class PreviewProviderStarredContactMessage : PreviewParameterProvider<StarredMessageUiModel.StarredContactMessage> {

    private companion object {

        fun createStarredContactMessageOutgoing(
            messageContent: String = "Outgoing contact message body.",
            messageBody: String = "Outgoing contact message body.",
            messageType: MessageType = MessageType.TEXT,
            messageCreatedAt: Date? = now().minus(1.days),
            messageDeletedAt: Date? = null,
            receiver: ConversationParticipant = ConversationParticipant.Contact(
                identity = PreviewData.IDENTITY_OTHER_1,
                firstname = "Roberto",
                lastname = "Diaz",
            ),
            showWorkBadge: Boolean = false,
            isPrivate: Boolean = false,
        ) = StarredMessageUiModel.StarredContactMessage(
            uid = "0",
            messageModel = MessageModel().apply {
                this.isOutbox = true
                this.body = messageBody
                this.type = messageType
                this.createdAt = messageCreatedAt
                this.deletedAt = messageDeletedAt
            },
            messageContent = messageContent.toResolvedString(),
            mentionNames = PreviewData.mentionNames,
            sender = ConversationParticipant.Me(
                identity = PreviewData.IDENTITY_ME,
            ),
            receiver = receiver,
            showWorkBadge = showWorkBadge,
            isPrivate = isPrivate,
        )

        fun createStarredContactMessageIncoming(
            messageContent: String = "Incoming contact message body.",
            messageBody: String = "Incoming contact message body.",
            messageType: MessageType = MessageType.TEXT,
            messageCreatedAt: Date? = now().minus(1.days),
            messageDeletedAt: Date? = null,
            sender: ConversationParticipant = ConversationParticipant.Contact(
                identity = PreviewData.IDENTITY_OTHER_1,
                firstname = "Roberto",
                lastname = "Diaz",
            ),
            showWorkBadge: Boolean = false,
            isPrivate: Boolean = false,
        ) = StarredMessageUiModel.StarredContactMessage(
            uid = "0",
            messageModel = MessageModel().apply {
                this.isOutbox = false
                this.body = messageBody
                this.type = messageType
                this.createdAt = messageCreatedAt
                this.deletedAt = messageDeletedAt
            },
            messageContent = messageContent.toResolvedString(),
            mentionNames = PreviewData.mentionNames,
            sender = sender,
            receiver = ConversationParticipant.Me(
                identity = PreviewData.IDENTITY_ME,
            ),
            showWorkBadge = showWorkBadge,
            isPrivate = isPrivate,
        )
    }

    override val values: Sequence<StarredMessageUiModel.StarredContactMessage>
        get() = sequenceOf(
            createStarredContactMessageOutgoing(),
            createStarredContactMessageOutgoing(showWorkBadge = true),
            createStarredContactMessageOutgoing(isPrivate = true),
            createStarredContactMessageOutgoing(messageDeletedAt = now()),
            createStarredContactMessageOutgoing(
                messageType = MessageType.FILE,
                messageBody = "[\"1\",\"1\",\"image/jpeg\",1,\"filename.jpg\",1,true,\"Look at this picture \uD83C\uDFD4\",\"image/jpeg\",{\"w\":" +
                    "1512,\"h\":2688}]",
                messageContent = "Look at this picture \uD83C\uDFD4",
            ),
            createStarredContactMessageOutgoing(
                messageType = MessageType.FILE,
                messageBody = "[\"1\",\"1\",\"image/jpeg\",1,\"filename.jpg\",1,true,\"Look at this picture \uD83C\uDFD4\",\"image/jpeg\",{\"w\":" +
                    "1512,\"h\":2688}]",
                messageContent = "Look at this picture \uD83C\uDFD4",
                isPrivate = true,
            ),
            createStarredContactMessageOutgoing(
                messageBody = PreviewData.LOREM_IPSUM_WORDS_50,
                receiver = ConversationParticipant.Contact(
                    identity = PreviewData.IDENTITY_OTHER_1,
                    firstname = "Robertoooooooooooooooooooooooooooooooooooooooooooooooooo",
                    lastname = "Diaz",
                ),
            ),

            createStarredContactMessageIncoming(),
            createStarredContactMessageIncoming(showWorkBadge = true),
            createStarredContactMessageIncoming(isPrivate = true),
            createStarredContactMessageIncoming(messageDeletedAt = now()),
            createStarredContactMessageIncoming(
                messageType = MessageType.FILE,
                messageBody = "[\"1\",\"1\",\"image/jpeg\",1,\"filename.jpg\",1,true,\"Look at this picture \uD83C\uDFD4\",\"image/jpeg\",{\"w\":" +
                    "1512,\"h\":2688}]",
                messageContent = "Look at this picture \uD83C\uDFD4",
            ),
            createStarredContactMessageIncoming(
                messageType = MessageType.FILE,
                messageBody = "[\"1\",\"1\",\"image/jpeg\",1,\"filename.jpg\",1,true,\"Look at this picture \uD83C\uDFD4\",\"image/jpeg\",{\"w\":" +
                    "1512,\"h\":2688}]",
                messageContent = "Look at this picture \uD83C\uDFD4",
                isPrivate = true,
            ),
            createStarredContactMessageIncoming(
                messageBody = PreviewData.LOREM_IPSUM_WORDS_50,
                sender = ConversationParticipant.Contact(
                    identity = PreviewData.IDENTITY_OTHER_1,
                    firstname = "Robertoooooooooooooooooooooooooooooooooooooooooooooooooo",
                    lastname = "Diaz",
                ),
            ),
        )
}

private class PreviewProviderStarredGroupMessage : PreviewParameterProvider<StarredMessageUiModel.StarredGroupMessage> {

    private companion object {

        fun createStarredGroupMessageOutgoing(
            messageContent: String = "Outgoing group message body.",
            messageBody: String = "Outgoing group message body.",
            messageType: MessageType = MessageType.TEXT,
            messageCreatedAt: Date? = now().minus(1.days),
            messageDeletedAt: Date? = null,
            groupDisplayName: GroupDisplayName = GroupDisplayName.AllMembers(
                memberDisplayNames = listOf(
                    ResourceIdString(R.string.me_myself_and_i),
                    ResolvedString("Roberto Diaz"),
                ),
            ),
            isPrivate: Boolean = false,
        ) = StarredMessageUiModel.StarredGroupMessage(
            uid = "0",
            messageModel = MessageModel().apply {
                this.isOutbox = true
                this.body = messageBody
                this.type = messageType
                this.createdAt = messageCreatedAt
                this.deletedAt = messageDeletedAt
            },
            messageContent = messageContent.toResolvedString(),
            mentionNames = PreviewData.mentionNames,
            sender = ConversationParticipant.Me(
                identity = PreviewData.IDENTITY_ME,
            ),
            groupIdentifier = GroupReceiverIdentifier(
                groupDatabaseId = 0L,
                groupCreatorIdentity = PreviewData.IDENTITY_ME.value,
                groupApiId = 0L,
            ),
            groupDisplayName = groupDisplayName,
            isPrivate = isPrivate,
        )

        fun createStarredGroupMessageIncoming(
            messageContent: String = "Incoming group message body.",
            messageBody: String = "Incoming group message body.",
            messageType: MessageType = MessageType.TEXT,
            messageCreatedAt: Date? = now().minus(1.days),
            messageDeletedAt: Date? = null,
            sender: ConversationParticipant = ConversationParticipant.Contact(
                identity = PreviewData.IDENTITY_OTHER_1,
                firstname = "Roberto",
                lastname = "Diaz",
            ),
            groupDisplayName: GroupDisplayName = GroupDisplayName.AllMembers(
                memberDisplayNames = listOf(
                    ResourceIdString(R.string.me_myself_and_i),
                    ResolvedString("Roberto Diaz"),
                ),
            ),
            isPrivate: Boolean = false,
        ) = StarredMessageUiModel.StarredGroupMessage(
            uid = "0",
            messageModel = MessageModel().apply {
                this.isOutbox = false
                this.body = messageBody
                this.type = messageType
                this.createdAt = messageCreatedAt
                this.deletedAt = messageDeletedAt
            },
            messageContent = messageContent.toResolvedString(),
            mentionNames = PreviewData.mentionNames,
            sender = sender,
            groupIdentifier = GroupReceiverIdentifier(
                groupDatabaseId = 0L,
                groupCreatorIdentity = PreviewData.IDENTITY_ME.value,
                groupApiId = 0L,
            ),
            groupDisplayName = groupDisplayName,
            isPrivate = isPrivate,
        )
    }

    override val values: Sequence<StarredMessageUiModel.StarredGroupMessage>
        get() = sequenceOf(
            createStarredGroupMessageOutgoing(),
            createStarredGroupMessageOutgoing(isPrivate = true),
            createStarredGroupMessageOutgoing(messageDeletedAt = now()),
            createStarredGroupMessageOutgoing(
                messageType = MessageType.FILE,
                messageBody = "[\"1\",\"1\",\"image/jpeg\",1,\"filename.jpg\",1,true,\"Look at this picture \uD83C\uDFD4\",\"image/jpeg\",{\"w\":" +
                    "1512,\"h\":2688}]",
                messageContent = "Look at this picture \uD83C\uDFD4",
            ),
            createStarredGroupMessageOutgoing(
                messageType = MessageType.FILE,
                messageBody = "[\"1\",\"1\",\"image/jpeg\",1,\"filename.jpg\",1,true,\"Look at this picture \uD83C\uDFD4\",\"image/jpeg\",{\"w\":" +
                    "1512,\"h\":2688}]",
                messageContent = "Look at this picture \uD83C\uDFD4",
                isPrivate = true,
            ),
            createStarredGroupMessageOutgoing(
                messageBody = PreviewData.LOREM_IPSUM_WORDS_50,
                groupDisplayName = GroupDisplayName.AllMembers(
                    memberDisplayNames = listOf(
                        ResourceIdString(R.string.me_myself_and_i),
                        ResolvedString("Robertooooooooooooooooooooooooooooooooooooo Diaz"),
                    ),
                ),
            ),
            createStarredGroupMessageIncoming(),
            createStarredGroupMessageIncoming(isPrivate = true),
            createStarredGroupMessageIncoming(messageDeletedAt = now()),
            createStarredGroupMessageIncoming(
                messageType = MessageType.FILE,
                messageBody = "[\"1\",\"1\",\"image/jpeg\",1,\"filename.jpg\",1,true,\"Look at this picture \uD83C\uDFD4\",\"image/jpeg\",{\"w\":" +
                    "1512,\"h\":2688}]",
                messageContent = "Look at this picture \uD83C\uDFD4",
            ),
            createStarredGroupMessageIncoming(
                messageType = MessageType.FILE,
                messageBody = "[\"1\",\"1\",\"image/jpeg\",1,\"filename.jpg\",1,true,\"Look at this picture \uD83C\uDFD4\",\"image/jpeg\",{\"w\":" +
                    "1512,\"h\":2688}]",
                messageContent = "Look at this picture \uD83C\uDFD4",
                isPrivate = true,
            ),
            createStarredGroupMessageIncoming(
                messageBody = PreviewData.LOREM_IPSUM_WORDS_50,
                groupDisplayName = GroupDisplayName.AllMembers(
                    memberDisplayNames = listOf(
                        ResourceIdString(R.string.me_myself_and_i),
                        ResolvedString("Robertooooooooooooooooooooooooooooooooooooo Diaz"),
                    ),
                ),
            ),
        )
}

@Composable
@PreviewLightDark
private fun Preview_ContactMessage(
    @PreviewParameter(PreviewProviderStarredContactMessage::class)
    starredMessageUiModel: StarredMessageUiModel,
) = Preview(
    starredMessageUiModel = starredMessageUiModel,
)

@Composable
@PreviewLightDark
private fun Preview_GroupMessage(
    @PreviewParameter(PreviewProviderStarredGroupMessage::class)
    starredMessageUiModel: StarredMessageUiModel,
) = Preview(
    starredMessageUiModel = starredMessageUiModel,
)

@Composable
@PreviewLightDark
private fun Preview_ContactMessage_Selected(
    @PreviewParameter(PreviewProviderStarredContactMessage::class)
    starredMessageUiModel: StarredMessageUiModel,
) = PreviewSelected(
    starredMessageUiModel = starredMessageUiModel,
)

@Composable
@PreviewLightDark
private fun Preview_GroupMessage_Selected(
    @PreviewParameter(PreviewProviderStarredGroupMessage::class)
    starredMessageUiModel: StarredMessageUiModel,
) = PreviewSelected(
    starredMessageUiModel = starredMessageUiModel,
)

@Composable
private fun Preview(starredMessageUiModel: StarredMessageUiModel) {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            StarredMessageListItem(
                modifier = Modifier.padding(vertical = GridUnit.x0_5),
                localDayOfYear = 1,
                avatarBitmapProvider = { null },
                messageMediaPreviewProvider = { null },
                ownIdentity = PreviewData.IDENTITY_ME,
                emojiStyle = EMOJI_STYLE_ANDROID,
                contactNameFormat = ContactNameFormat.DEFAULT,
                starredMessageUiModel = starredMessageUiModel,
                isSelected = false,
                highlightedMessageContent = null,
                onClickMessageBubble = {},
                onLongClickMessageBubble = {},
                onClickNavigateToMessage = {},
            )
        }
    }
}

@Composable
private fun PreviewSelected(starredMessageUiModel: StarredMessageUiModel) {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            StarredMessageListItem(
                modifier = Modifier.padding(vertical = GridUnit.x0_5),
                localDayOfYear = 1,
                avatarBitmapProvider = { null },
                messageMediaPreviewProvider = { null },
                ownIdentity = PreviewData.IDENTITY_ME,
                emojiStyle = EMOJI_STYLE_ANDROID,
                contactNameFormat = ContactNameFormat.DEFAULT,
                starredMessageUiModel = starredMessageUiModel,
                isSelected = true,
                highlightedMessageContent = null,
                onClickMessageBubble = {},
                onLongClickMessageBubble = {},
                onClickNavigateToMessage = {},
            )
        }
    }
}
