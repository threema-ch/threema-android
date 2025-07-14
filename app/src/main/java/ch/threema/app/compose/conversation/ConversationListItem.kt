/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.app.compose.conversation

import android.content.Context
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.compose.common.Avatar
import ch.threema.app.compose.common.SpacerHorizontal
import ch.threema.app.compose.common.buttons.ButtonPrimarySmall
import ch.threema.app.compose.common.text.conversation.ConversationText
import ch.threema.app.compose.common.text.conversation.ConversationTextDefaults
import ch.threema.app.compose.common.text.conversation.MentionFeature
import ch.threema.app.compose.common.text.conversation.ThreemaTextPreviewProvider
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.drafts.DraftManager
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.preference.service.PreferenceService.EmojiStyle
import ch.threema.app.preference.service.PreferenceService.EmojiStyle_ANDROID
import ch.threema.app.services.AvatarCacheService
import ch.threema.app.services.ContactService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.DistributionListService
import ch.threema.app.services.GroupService
import ch.threema.app.services.RingtoneService
import ch.threema.app.utils.MessageUtil
import ch.threema.app.utils.NameUtil
import ch.threema.app.utils.StateBitmapUtil
import ch.threema.domain.models.IdentityState
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.ConversationModel
import ch.threema.storage.models.DistributionListModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.GroupModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageState
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.ReceiverModel
import java.util.Date
import java.util.Locale

/**
 *  Not yet implemented:
 *  - Ongoing group call state
 *  - Typing indicator
 */
@Composable
fun ConversationListItem(
    modifier: Modifier = Modifier,
    conversationModel: ConversationModel,
    contactService: ContactService,
    groupService: GroupService,
    distributionListService: DistributionListService,
    conversationCategoryService: ConversationCategoryService,
    avatarCacheService: AvatarCacheService,
    ringtoneService: RingtoneService,
    preferenceService: PreferenceService,
    isChecked: Boolean,
    onClick: (ConversationModel) -> Unit,
    onLongClick: (ConversationModel) -> Unit,
    onClickJoinCall: () -> Unit,
) {
    val context = LocalContext.current

    val lastUpdateAt: String? = remember(
        conversationModel.latestMessage,
    ) {
        MessageUtil.getDisplayDate(context, conversationModel.latestMessage, false)
    }

    val draft: String? = DraftManager.getMessageDraft(conversationModel.messageReceiver.uniqueIdString)
        ?.takeIf(String::isNotBlank)

    ConversationListItemContent(
        modifier = modifier,
        ownIdentity = contactService.me.identity,
        identityNameProvider = { identity ->
            if (identity == ContactService.ALL_USERS_PLACEHOLDER_ID) {
                context.getString(R.string.all)
            } else {
                NameUtil.getDisplayNameOrNickname(identity, contactService)
            }
        },
        avatarContent = {
            AvatarAsyncCheckable(
                avatarCacheService = avatarCacheService,
                receiverModel = conversationModel.receiverModel,
                contentDescription = getAvatarContentDescription(
                    context = context,
                    receiverModel = conversationModel.receiverModel,
                    groupService = groupService,
                    distributionListService = distributionListService,
                ),
                fallbackIcon = when {
                    conversationModel.isGroupConversation -> R.drawable.ic_group
                    conversationModel.isDistributionListConversation -> R.drawable.ic_distribution_list
                    else -> R.drawable.ic_contact
                },
                showWorkBadge = conversationModel.contact?.let(contactService::showBadge) ?: false,
                isChecked = isChecked,
            )
        },
        conversationName = conversationModel.messageReceiver.displayName,
        conversationNameStyle = ConversationNameStyle.forConversationModel(conversationModel),
        latestMessage = conversationModel.latestMessage,
        groupMessageSender = getGroupMessageSenderOrNull(
            contactService = contactService,
            conversation = conversationModel,
            conversationHasOwnDraft = draft != null,
            context = context,
        ),
        lastUpdateAt = lastUpdateAt,
        deliveryIcon = getLatestMessageStateIcon(conversationModel),
        unreadState = when {
            conversationModel.hasUnreadMessage() -> UnreadState.Messages(conversationModel.unreadCount)
            conversationModel.isUnreadTagged -> UnreadState.JustMarked
            else -> null
        },
        isPinned = conversationModel.isPinTagged,
        isPrivate = conversationCategoryService.isPrivateChat(
            conversationModel.messageReceiver.uniqueIdString,
        ),
        hasOngoingCall = false,
        draft = draft,
        muteStatusIcon = getMuteStatusIconResOrNull(conversationModel, ringtoneService),
        emojiStyle = preferenceService.emojiStyle,
        onClick = {
            onClick(conversationModel)
        },
        onLongClick = {
            onLongClick(conversationModel)
        },
        onClickJoinCall = onClickJoinCall,
    )
}

private fun getLatestMessageStateIcon(
    conversationModel: ConversationModel,
): IconInfo? {
    val conversationIconRes: Int? = conversationModel.getConversationIconRes()

    if (conversationIconRes != null) {
        @StringRes
        val contentDescription: Int? = when {
            conversationModel.isContactConversation -> R.string.state_sent
            conversationModel.isGroupConversation -> {
                if (conversationModel.groupModel?.isNotesGroup() == true) {
                    R.string.notes
                } else {
                    R.string.prefs_group_notifications
                }
            }

            conversationModel.isDistributionListConversation -> R.string.distribution_list
            else -> null
        }

        return IconInfo(
            icon = conversationIconRes,
            contentDescription = contentDescription,
        )
    }

    return conversationModel.latestMessage?.let { latestMessageModel ->

        if (!MessageUtil.showStatusIcon(latestMessageModel)) {
            return@let null
        }
        val messageState: MessageState = latestMessageModel.state
            ?: return@let null

        val stateBitmapUtil = StateBitmapUtil.getInstance()
            ?: return@let null

        val stateIconRes: Int = stateBitmapUtil.getStateDrawable(messageState)
            ?: return@let null

        val stateIconContentDescriptionRes: Int? = stateBitmapUtil.getStateDescription(messageState)

        @ColorInt
        val tintOverride: Int? = when (messageState) {
            MessageState.SENDFAILED, MessageState.FS_KEY_MISMATCH -> stateBitmapUtil.warningColor
            else -> null
        }

        return@let IconInfo(
            icon = stateIconRes,
            contentDescription = stateIconContentDescriptionRes,
            tintOverride = tintOverride,
        )
    }
}

@Stable
data class IconInfo(
    @DrawableRes val icon: Int,
    @StringRes val contentDescription: Int?,
    @ColorInt val tintOverride: Int? = null,
)

private fun getAvatarContentDescription(
    context: Context,
    receiverModel: ReceiverModel,
    groupService: GroupService,
    distributionListService: DistributionListService,
): String? =
    when (receiverModel) {
        is ContactModel -> context.getString(
            R.string.edit_type_content_description,
            context.getString(R.string.mime_contact),
            NameUtil.getDisplayNameOrNickname(receiverModel, true),
        )

        is GroupModel -> context.getString(
            R.string.edit_type_content_description,
            ThreemaApplication.getAppContext().getString(R.string.group),
            NameUtil.getDisplayName(receiverModel, groupService),
        )

        is DistributionListModel -> context.getString(
            R.string.edit_type_content_description,
            context.getString(R.string.distribution_list),
            NameUtil.getDisplayName(receiverModel, distributionListService),
        )

        else -> null
    }

@DrawableRes
private fun getMuteStatusIconResOrNull(
    conversationModel: ConversationModel,
    ringtoneService: RingtoneService,
): Int? {
    var iconRes: Int? = null
    val messageReceiver = conversationModel.messageReceiver
    if (messageReceiver is ContactMessageReceiver) {
        iconRes = messageReceiver.contact.currentNotificationTriggerPolicyOverride().iconResRightNow
    } else if (messageReceiver is GroupMessageReceiver) {
        iconRes = messageReceiver.group.currentNotificationTriggerPolicyOverride().iconResRightNow
    }
    if (
        iconRes == null &&
        ringtoneService.hasCustomRingtone(conversationModel.messageReceiver.uniqueIdString) &&
        ringtoneService.isSilent(conversationModel.messageReceiver.uniqueIdString, conversationModel.isGroupConversation)
    ) {
        iconRes = R.drawable.ic_notifications_off_filled
    }
    return iconRes
}

/**
 *  @return A string in the form of `Bob:` **if** the [conversation] is a group conversation, otherwise `null`.
 */
private fun getGroupMessageSenderOrNull(
    contactService: ContactService,
    conversation: ConversationModel,
    conversationHasOwnDraft: Boolean,
    context: Context,
): String? {
    if (
        conversation.isGroupConversation &&
        conversation.latestMessage != null &&
        conversation.latestMessage?.type != MessageType.GROUP_CALL_STATUS &&
        !conversationHasOwnDraft
    ) {
        val senderName = NameUtil.getShortName(context, conversation.latestMessage, contactService)
        return "$senderName:"
    } else {
        return null
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationListItemContent(
    modifier: Modifier = Modifier,
    ownIdentity: String,
    identityNameProvider: (String) -> String,
    avatarContent: @Composable () -> Unit,
    conversationName: String,
    conversationNameStyle: ConversationNameStyle,
    latestMessage: AbstractMessageModel?,
    groupMessageSender: String?,
    lastUpdateAt: String?,
    deliveryIcon: IconInfo?,
    unreadState: UnreadState?,
    isPinned: Boolean,
    isPrivate: Boolean,
    hasOngoingCall: Boolean,
    draft: String?,
    muteStatusIcon: Int?,
    @EmojiStyle emojiStyle: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClickJoinCall: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .clip(
                RoundedCornerShape(GridUnit.x0_5),
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = GridUnit.x1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpacerHorizontal(GridUnit.x2)
            avatarContent()
            SpacerHorizontal(GridUnit.x1_5)
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    FirstLine(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(
                                min = dimensionResource(R.dimen.listitem_min_height_first_line),
                            ),
                        conversationName = conversationName,
                        conversationNameStyle = conversationNameStyle,
                        isPinned = isPinned,
                        unreadState = unreadState,
                        hasOngoingCall = hasOngoingCall,
                        muteStatusIcon = muteStatusIcon,
                        emojiStyle = emojiStyle,
                    )
                    SecondLine(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(
                                min = dimensionResource(R.dimen.listitem_min_height_second_line),
                            ),
                        ownIdentity = ownIdentity,
                        identityNameProvider = identityNameProvider,
                        latestMessage = latestMessage,
                        groupMessageSender = groupMessageSender,
                        isPrivate = isPrivate,
                        hasOngoingCall = hasOngoingCall,
                        lastUpdateAt = lastUpdateAt,
                        deliveryIcon = deliveryIcon,
                        unreadState = unreadState,
                        draft = draft,
                        emojiStyle = emojiStyle,
                    )
                }

                if (hasOngoingCall) {
                    ButtonPrimarySmall(
                        modifier = Modifier.padding(horizontal = GridUnit.x1_5),
                        onClick = onClickJoinCall,
                        text = stringResource(R.string.voip_gc_join_call),
                    )
                }
            }
            SpacerHorizontal(GridUnit.x2)
        }

        if (unreadState != null) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(GridUnit.x0_5)
                    .padding(vertical = GridUnit.x0_5)
                    .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                    .background(color = Color.Red),
            )
        }

        if (isPrivate) {
            Image(
                modifier = Modifier
                    .size(GridUnit.x3)
                    .align(Alignment.TopEnd),
                painter = painterResource(R.drawable.ic_incognito),
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun FirstLine(
    modifier: Modifier,
    conversationName: String,
    conversationNameStyle: ConversationNameStyle,
    isPinned: Boolean,
    unreadState: UnreadState?,
    hasOngoingCall: Boolean,
    @DrawableRes muteStatusIcon: Int?,
    @EmojiStyle emojiStyle: Int,
) {
    val fontWeightConversationName: FontWeight = remember(unreadState) {
        when {
            unreadState != null -> FontWeight.SemiBold
            else -> FontWeight.Normal
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (muteStatusIcon != null && !hasOngoingCall) {
            Icon(
                modifier = Modifier.size(
                    with(LocalDensity.current) {
                        20.sp.toDp()
                    },
                ),
                tint = LocalContentColor.current,
                painter = painterResource(muteStatusIcon),
                contentDescription = null,
            )
            SpacerHorizontal(GridUnit.x0_5)
        }
        ConversationText(
            modifier = Modifier
                .heightIn(
                    min = dimensionResource(R.dimen.listitem_min_height_first_line),
                )
                .weight(1f)
                .alpha(
                    if (conversationNameStyle.dimAlpha) .4f else 1f,
                ),
            rawInput = conversationName,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = fontWeightConversationName,
                textDecoration = if (conversationNameStyle.strikethrough) TextDecoration.LineThrough else TextDecoration.None,
            ),
            maxLines = 1,
            emojiSettings = ConversationTextDefaults.EmojiSettings.copy(
                style = emojiStyle,
            ),
            mentionFeature = MentionFeature.Off,
        )
        if (isPinned && !hasOngoingCall) {
            SpacerHorizontal(GridUnit.x0_5)
            Image(
                modifier = Modifier.size(GridUnit.x3),
                painter = painterResource(R.drawable.ic_pin_circle),
                contentDescription = null,
            )
        }
        if (unreadState != null && !hasOngoingCall) {
            SpacerHorizontal(GridUnit.x0_5)
            UnreadCounter(
                unreadState = unreadState,
            )
        }
    }
}

@Composable
private fun SecondLine(
    modifier: Modifier,
    ownIdentity: String,
    identityNameProvider: (String) -> String,
    latestMessage: AbstractMessageModel?,
    groupMessageSender: String?,
    isPrivate: Boolean,
    hasOngoingCall: Boolean,
    lastUpdateAt: String?,
    deliveryIcon: IconInfo?,
    unreadState: UnreadState?,
    draft: String?,
    @EmojiStyle emojiStyle: Int,
) {
    if (isPrivate) {
        SecondLinePrivate(
            modifier = modifier,
            unreadState = unreadState,
        )
    } else if (hasOngoingCall) {
        SecondLineOngoingGroupCall(
            modifier = modifier,
        )
    } else if (draft != null) {
        SecondLineDraft(
            modifier = modifier,
            draft = draft,
            emojiStyle = emojiStyle,
            ownIdentity = ownIdentity,
            identityNameProvider = identityNameProvider,
        )
    } else {
        SecondLineDefault(
            modifier = modifier,
            latestMessage = latestMessage,
            groupMessageSender = groupMessageSender,
            lastUpdateAt = lastUpdateAt,
            deliveryIcon = deliveryIcon,
            unreadState = unreadState,
            ownIdentity = ownIdentity,
            identityNameProvider = identityNameProvider,
            emojiStyle = emojiStyle,
        )
    }
}

@Composable
private fun SecondLinePrivate(
    modifier: Modifier,
    unreadState: UnreadState?,
) {
    Text(
        modifier = modifier,
        text = stringResource(R.string.private_chat_subject),
        style = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = if (unreadState != null) FontWeight.SemiBold else FontWeight.Normal,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SecondLineOngoingGroupCall(
    modifier: Modifier,
) {
    Text(
        modifier = modifier,
        text = stringResource(R.string.voip_gc_ongoing_call),
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SecondLineDraft(
    modifier: Modifier,
    draft: String,
    @EmojiStyle emojiStyle: Int,
    ownIdentity: String,
    identityNameProvider: (String) -> String,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConversationText(
            modifier = Modifier.weight(2f),
            rawInput = draft,
            textStyle = MaterialTheme.typography.bodyMedium,
            color = LocalContentColor.current,
            maxLines = 1,
            emojiSettings = ConversationTextDefaults.EmojiSettings.copy(
                style = emojiStyle,
            ),
            mentionFeature = MentionFeature.On(
                ownIdentity = ownIdentity,
                identityNameProvider = identityNameProvider,
            ),
        )
        SpacerHorizontal(GridUnit.x1)
        Text(
            modifier = Modifier.widthIn(max = 150.dp),
            text = stringResource(R.string.draft).uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color.Red,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SecondLineDefault(
    modifier: Modifier,
    latestMessage: AbstractMessageModel?,
    groupMessageSender: String?,
    lastUpdateAt: String?,
    deliveryIcon: IconInfo?,
    unreadState: UnreadState?,
    ownIdentity: String,
    identityNameProvider: (String) -> String,
    @EmojiStyle emojiStyle: Int,
) {
    val context = LocalContext.current
    val latestMessageViewElement: MessageUtil.MessageViewElement? = remember(latestMessage) {
        latestMessage?.let { latestMessageNotNull ->
            MessageUtil.getViewElement(context, latestMessageNotNull)
        }
    }
    val latestMessagePreview: String = remember(latestMessageViewElement) {
        if (latestMessageViewElement == null || latestMessage == null) {
            return@remember ""
        } else if (latestMessage.isDeleted) {
            context.getString(R.string.message_was_deleted)
        } else {
            latestMessageViewElement.text ?: ""
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (groupMessageSender != null) {
            val fontWeightSender: FontWeight = when {
                unreadState != null -> FontWeight.SemiBold
                else -> FontWeight.Normal
            }
            ConversationText(
                rawInput = groupMessageSender,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = fontWeightSender,
                ),
                maxLines = 1,
                emojiSettings = ConversationTextDefaults.EmojiSettings.copy(
                    style = emojiStyle,
                ),
                mentionFeature = MentionFeature.Off,
            )
            SpacerHorizontal(GridUnit.x0_5)
        }

        if (latestMessageViewElement?.icon != null && latestMessage?.isDeleted != true) {
            Icon(
                modifier = Modifier
                    .padding(end = GridUnit.x0_5)
                    .size(
                        with(LocalDensity.current) {
                            16.sp.toDp()
                        },
                    ),
                painter = painterResource(latestMessageViewElement.icon),
                contentDescription = latestMessageViewElement.placeholder,
                tint = latestMessageViewElement.color?.let { colorRes ->
                    colorResource(colorRes)
                } ?: LocalContentColor.current,
            )
        }

        val fontWeightMessagePreview: FontWeight = when {
            latestMessage?.isDeleted == true -> FontWeight.Normal
            unreadState != null -> FontWeight.SemiBold
            else -> FontWeight.Normal
        }
        val fontStyleMessagePreview = when {
            latestMessage?.isDeleted == true -> FontStyle.Italic
            else -> FontStyle.Normal
        }
        val textAlphaMessagePreview = when (latestMessage?.isDeleted) {
            true -> 0.6f
            else -> 1.0f
        }
        ConversationText(
            modifier = Modifier.weight(1f),
            rawInput = latestMessagePreview,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = fontWeightMessagePreview,
                fontStyle = fontStyleMessagePreview,
            ),
            color = LocalContentColor.current.copy(
                alpha = textAlphaMessagePreview,
            ),
            maxLines = 1,
            emojiSettings = ConversationTextDefaults.EmojiSettings.copy(
                style = emojiStyle,
            ),
            mentionFeature = MentionFeature.On(
                ownIdentity = ownIdentity,
                identityNameProvider = identityNameProvider,
            ),
        )

        if (lastUpdateAt != null) {
            SpacerHorizontal(GridUnit.x1)
            Text(
                text = lastUpdateAt,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (deliveryIcon != null && latestMessage?.isDeleted != true) {
            SpacerHorizontal(GridUnit.x0_5)
            Icon(
                modifier = Modifier.size(GridUnit.x2_5),
                tint = deliveryIcon.tintOverride
                    ?.let(::Color)
                    ?: LocalContentColor.current,
                painter = painterResource(deliveryIcon.icon),
                contentDescription = deliveryIcon.contentDescription?.let { contentDescriptionRes ->
                    stringResource(contentDescriptionRes)
                },
            )
        }
    }
}

sealed interface UnreadState {
    data class Messages(val count: Long) : UnreadState
    data object JustMarked : UnreadState
}

private data class ConversationNameStyle(
    val strikethrough: Boolean = false,
    val dimAlpha: Boolean = false,
) {
    companion object {
        fun inactiveContact() = ConversationNameStyle(
            strikethrough = false,
            dimAlpha = true,
        )

        fun invalidContact() = ConversationNameStyle(
            strikethrough = true,
            dimAlpha = false,
        )

        fun groupNotAMemberOf() = ConversationNameStyle(
            strikethrough = true,
            dimAlpha = false,
        )

        fun forConversationModel(conversationModel: ConversationModel): ConversationNameStyle =
            if (conversationModel.isContactConversation) {
                conversationModel.contact?.let { contact ->
                    when (contact.state) {
                        IdentityState.INACTIVE -> inactiveContact()
                        IdentityState.INVALID -> invalidContact()
                        else -> null
                    }
                } ?: ConversationNameStyle()
            } else {
                if (conversationModel.groupModel?.isMember() == false) {
                    groupNotAMemberOf()
                } else {
                    ConversationNameStyle()
                }
            }
    }
}

@Composable
private fun AvatarForPreview() {
    Avatar(
        avatar = null,
        contentDescription = null,
        fallbackIcon = R.drawable.ic_group,
        showWorkBadge = false,
    )
}

@Preview
@Composable
private fun Preview() {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            ConversationListItemContent(
                ownIdentity = "01234567",
                identityNameProvider = ThreemaTextPreviewProvider.mentionedIdentityNameProviderPreviewImpl,
                avatarContent = { AvatarForPreview() },
                conversationName = "Group Name \uD83C\uDF36",
                conversationNameStyle = ConversationNameStyle(),
                latestMessage = GroupMessageModel().apply {
                    this.type = MessageType.TEXT
                    this.body = "\uD83D\uDE35\u200D\uD83D\uDCAB"
                },
                groupMessageSender = "Alice:",
                lastUpdateAt = "Vor 4 Tagen",
                deliveryIcon = IconInfo(
                    icon = R.drawable.ic_reply_filled,
                    contentDescription = null,
                ),
                unreadState = null,
                isPinned = false,
                isPrivate = false,
                hasOngoingCall = false,
                draft = null,
                muteStatusIcon = null,
                emojiStyle = EmojiStyle_ANDROID,
                onClick = {},
                onLongClick = {},
                onClickJoinCall = {},
            )
        }
    }
}

@Preview
@Composable
private fun Preview_Unread() {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            ConversationListItemContent(
                ownIdentity = "01234567",
                identityNameProvider = ThreemaTextPreviewProvider.mentionedIdentityNameProviderPreviewImpl,
                avatarContent = { AvatarForPreview() },
                conversationName = "Vorname Nachname",
                conversationNameStyle = ConversationNameStyle(),
                latestMessage = null,
                groupMessageSender = null,
                lastUpdateAt = "3:15 PM",
                deliveryIcon = IconInfo(
                    icon = R.drawable.ic_reply_filled,
                    contentDescription = null,
                ),
                unreadState = UnreadState.Messages(5L),
                isPinned = false,
                isPrivate = false,
                hasOngoingCall = false,
                draft = null,
                muteStatusIcon = null,
                emojiStyle = EmojiStyle_ANDROID,
                onClick = {},
                onLongClick = {},
                onClickJoinCall = {},
            )
        }
    }
}

@Preview
@Composable
private fun Preview_Pinned() {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            ConversationListItemContent(
                ownIdentity = "01234567",
                identityNameProvider = ThreemaTextPreviewProvider.mentionedIdentityNameProviderPreviewImpl,
                avatarContent = { AvatarForPreview() },
                conversationName = "Vorname Nachname",
                conversationNameStyle = ConversationNameStyle(),
                latestMessage = null,
                groupMessageSender = null,
                lastUpdateAt = "3:15 PM",
                deliveryIcon = IconInfo(
                    icon = R.drawable.ic_reply_filled,
                    contentDescription = null,
                ),
                unreadState = null,
                isPinned = true,
                isPrivate = false,
                hasOngoingCall = false,
                draft = null,
                muteStatusIcon = null,
                emojiStyle = EmojiStyle_ANDROID,
                onClick = {},
                onLongClick = {},
                onClickJoinCall = {},
            )
        }
    }
}

@Preview
@Composable
private fun Preview_Pinned_Unread() {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            ConversationListItemContent(
                ownIdentity = "01234567",
                identityNameProvider = ThreemaTextPreviewProvider.mentionedIdentityNameProviderPreviewImpl,
                avatarContent = { AvatarForPreview() },
                conversationName = "Vorname Nachname",
                conversationNameStyle = ConversationNameStyle(),
                latestMessage = null,
                groupMessageSender = null,
                lastUpdateAt = "3:15 PM",
                deliveryIcon = IconInfo(
                    icon = R.drawable.ic_reply_filled,
                    contentDescription = null,
                ),
                unreadState = UnreadState.Messages(1000L),
                isPinned = true,
                isPrivate = false,
                hasOngoingCall = false,
                draft = null,
                muteStatusIcon = null,
                emojiStyle = EmojiStyle_ANDROID,
                onClick = {},
                onLongClick = {},
                onClickJoinCall = {},
            )
        }
    }
}

@Preview
@Composable
private fun Preview_KickedGroup() {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            ConversationListItemContent(
                ownIdentity = "01234567",
                identityNameProvider = ThreemaTextPreviewProvider.mentionedIdentityNameProviderPreviewImpl,
                avatarContent = { AvatarForPreview() },
                conversationName = "Group kicked",
                conversationNameStyle = ConversationNameStyle.groupNotAMemberOf(),
                latestMessage = null,
                groupMessageSender = null,
                lastUpdateAt = "4:36 PM",
                deliveryIcon = IconInfo(
                    icon = R.drawable.ic_group_filled,
                    contentDescription = null,
                ),
                unreadState = null,
                isPinned = false,
                isPrivate = false,
                hasOngoingCall = false,
                draft = null,
                muteStatusIcon = null,
                emojiStyle = EmojiStyle_ANDROID,
                onClick = {},
                onLongClick = {},
                onClickJoinCall = {},
            )
        }
    }
}

@Preview
@Composable
private fun Preview_InactiveContact() {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            ConversationListItemContent(
                ownIdentity = "01234567",
                identityNameProvider = ThreemaTextPreviewProvider.mentionedIdentityNameProviderPreviewImpl,
                avatarContent = { AvatarForPreview() },
                conversationName = "Inactive Contact",
                conversationNameStyle = ConversationNameStyle.inactiveContact(),
                latestMessage = null,
                groupMessageSender = null,
                lastUpdateAt = "4:36 PM",
                deliveryIcon = IconInfo(
                    icon = R.drawable.ic_reply_filled,
                    contentDescription = null,
                ),
                unreadState = null,
                isPinned = false,
                isPrivate = false,
                hasOngoingCall = false,
                draft = null,
                muteStatusIcon = null,
                emojiStyle = EmojiStyle_ANDROID,
                onClick = {},
                onLongClick = {},
                onClickJoinCall = {},
            )
        }
    }
}

@Preview
@Composable
private fun Preview_InvalidContact() {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            ConversationListItemContent(
                ownIdentity = "01234567",
                identityNameProvider = ThreemaTextPreviewProvider.mentionedIdentityNameProviderPreviewImpl,
                avatarContent = { AvatarForPreview() },
                conversationName = "Invalid Contact",
                conversationNameStyle = ConversationNameStyle.invalidContact(),
                groupMessageSender = null,
                latestMessage = null,
                lastUpdateAt = "4:36 PM",
                deliveryIcon = IconInfo(
                    icon = R.drawable.ic_reply_filled,
                    contentDescription = null,
                ),
                unreadState = null,
                isPinned = false,
                isPrivate = false,
                hasOngoingCall = false,
                draft = null,
                muteStatusIcon = null,
                emojiStyle = EmojiStyle_ANDROID,
                onClick = {},
                onLongClick = {},
                onClickJoinCall = {},
            )
        }
    }
}

@Preview
@Composable
private fun Preview_Private() {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            ConversationListItemContent(
                ownIdentity = "01234567",
                identityNameProvider = ThreemaTextPreviewProvider.mentionedIdentityNameProviderPreviewImpl,
                avatarContent = { AvatarForPreview() },
                conversationName = "Vorname Nachname",
                conversationNameStyle = ConversationNameStyle(),
                latestMessage = null,
                groupMessageSender = null,
                lastUpdateAt = "4:36 PM",
                deliveryIcon = IconInfo(
                    icon = R.drawable.ic_reply_filled,
                    contentDescription = null,
                ),
                unreadState = null,
                isPinned = false,
                isPrivate = true,
                hasOngoingCall = false,
                draft = null,
                muteStatusIcon = null,
                emojiStyle = EmojiStyle_ANDROID,
                onClick = {},
                onLongClick = {},
                onClickJoinCall = {},
            )
        }
    }
}

@Preview
@Composable
private fun Preview_GroupCall() {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            ConversationListItemContent(
                ownIdentity = "01234567",
                identityNameProvider = ThreemaTextPreviewProvider.mentionedIdentityNameProviderPreviewImpl,
                avatarContent = { AvatarForPreview() },
                conversationName = "Gruppe mit Calls",
                conversationNameStyle = ConversationNameStyle(),
                latestMessage = null,
                groupMessageSender = null,
                lastUpdateAt = "4:36 PM",
                deliveryIcon = IconInfo(
                    icon = R.drawable.ic_group_filled,
                    contentDescription = null,
                ),
                unreadState = null,
                isPinned = false,
                isPrivate = false,
                hasOngoingCall = true,
                draft = null,
                muteStatusIcon = null,
                emojiStyle = EmojiStyle_ANDROID,
                onClick = {},
                onLongClick = {},
                onClickJoinCall = {},
            )
        }
    }
}

@Preview
@Composable
private fun Preview_GroupCall_Max() {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            ConversationListItemContent(
                ownIdentity = "01234567",
                identityNameProvider = ThreemaTextPreviewProvider.mentionedIdentityNameProviderPreviewImpl,
                avatarContent = { AvatarForPreview() },
                conversationName = "Gruppe mit Callssssssssssssssssssssss",
                conversationNameStyle = ConversationNameStyle(),
                latestMessage = null,
                groupMessageSender = null,
                lastUpdateAt = "4:36 PM",
                deliveryIcon = IconInfo(
                    icon = R.drawable.ic_group_filled,
                    contentDescription = null,
                ),
                unreadState = UnreadState.Messages(150L),
                isPinned = true,
                isPrivate = true,
                hasOngoingCall = true,
                draft = null,
                muteStatusIcon = null,
                emojiStyle = EmojiStyle_ANDROID,
                onClick = {},
                onLongClick = {},
                onClickJoinCall = {},
            )
        }
    }
}

@Preview
@Composable
private fun Preview_Draft() {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            ConversationListItemContent(
                ownIdentity = "01234567",
                identityNameProvider = ThreemaTextPreviewProvider.mentionedIdentityNameProviderPreviewImpl,
                avatarContent = { AvatarForPreview() },
                conversationName = "Chat mit Draft",
                conversationNameStyle = ConversationNameStyle(),
                latestMessage = null,
                groupMessageSender = null,
                lastUpdateAt = "4:36 PM",
                deliveryIcon = IconInfo(
                    icon = R.drawable.ic_group_filled,
                    contentDescription = null,
                ),
                unreadState = null,
                isPinned = false,
                isPrivate = false,
                hasOngoingCall = false,
                draft = "Noch nicht geschickt kjahsdkjhaskjhdkjashdkjhasjkhd",
                muteStatusIcon = null,
                emojiStyle = EmojiStyle_ANDROID,
                onClick = {},
                onLongClick = {},
                onClickJoinCall = {},
            )
        }
    }
}

@Preview
@Composable
private fun Preview_Draft_Max() {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            ConversationListItemContent(
                ownIdentity = "01234567",
                identityNameProvider = ThreemaTextPreviewProvider.mentionedIdentityNameProviderPreviewImpl,
                avatarContent = { AvatarForPreview() },
                conversationName = "Chat mit Draft",
                conversationNameStyle = ConversationNameStyle.inactiveContact(),
                latestMessage = null,
                groupMessageSender = null,
                lastUpdateAt = "4:36 PM",
                deliveryIcon = IconInfo(
                    icon = R.drawable.ic_reply_filled,
                    contentDescription = null,
                ),
                unreadState = UnreadState.Messages(5L),
                isPinned = true,
                isPrivate = false,
                hasOngoingCall = false,
                draft = "Noch nicht geschickt",
                muteStatusIcon = null,
                emojiStyle = EmojiStyle_ANDROID,
                onClick = {},
                onLongClick = {},
                onClickJoinCall = {},
            )
        }
    }
}

@Preview
@Composable
private fun Preview_Draft_Call() {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            ConversationListItemContent(
                ownIdentity = "01234567",
                identityNameProvider = ThreemaTextPreviewProvider.mentionedIdentityNameProviderPreviewImpl,
                avatarContent = { AvatarForPreview() },
                conversationName = "Group mit Draft",
                conversationNameStyle = ConversationNameStyle(),
                latestMessage = null,
                groupMessageSender = null,
                lastUpdateAt = "4:36 PM",
                deliveryIcon = IconInfo(
                    icon = R.drawable.ic_reply_filled,
                    contentDescription = null,
                ),
                unreadState = UnreadState.Messages(5L),
                isPinned = true,
                isPrivate = false,
                hasOngoingCall = true,
                draft = "Noch nicht geschickt",
                muteStatusIcon = null,
                emojiStyle = EmojiStyle_ANDROID,
                onClick = {},
                onLongClick = {},
                onClickJoinCall = {},
            )
        }
    }
}

@Preview
@Composable
private fun Preview_Muted() {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            ConversationListItemContent(
                ownIdentity = "01234567",
                identityNameProvider = ThreemaTextPreviewProvider.mentionedIdentityNameProviderPreviewImpl,
                avatarContent = { AvatarForPreview() },
                conversationName = "Muted Chat",
                conversationNameStyle = ConversationNameStyle(),
                latestMessage = null,
                groupMessageSender = null,
                lastUpdateAt = "4:36 PM",
                deliveryIcon = IconInfo(
                    icon = R.drawable.ic_reply_filled,
                    contentDescription = null,
                ),
                unreadState = null,
                isPinned = false,
                isPrivate = false,
                hasOngoingCall = false,
                draft = null,
                muteStatusIcon = R.drawable.ic_dnd_mention_grey600_24dp,
                emojiStyle = EmojiStyle_ANDROID,
                onClick = {},
                onLongClick = {},
                onClickJoinCall = {},
            )
        }
    }
}

@Preview
@Composable
private fun Preview_Muted_Call() {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            ConversationListItemContent(
                ownIdentity = "01234567",
                identityNameProvider = ThreemaTextPreviewProvider.mentionedIdentityNameProviderPreviewImpl,
                avatarContent = { AvatarForPreview() },
                conversationName = "Muted Chat",
                conversationNameStyle = ConversationNameStyle(),
                latestMessage = null,
                groupMessageSender = null,
                lastUpdateAt = "4:36 PM",
                deliveryIcon = IconInfo(
                    icon = R.drawable.ic_reply_filled,
                    contentDescription = null,
                ),
                unreadState = null,
                isPinned = false,
                isPrivate = false,
                hasOngoingCall = true,
                draft = null,
                muteStatusIcon = R.drawable.ic_dnd_mention_grey600_24dp,
                emojiStyle = EmojiStyle_ANDROID,
                onClick = {},
                onLongClick = {},
                onClickJoinCall = {},
            )
        }
    }
}

@Preview
@Composable
private fun Preview_Muted_Max() {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            ConversationListItemContent(
                ownIdentity = "01234567",
                identityNameProvider = ThreemaTextPreviewProvider.mentionedIdentityNameProviderPreviewImpl,
                avatarContent = { AvatarForPreview() },
                conversationName = "Muted Chat Max",
                conversationNameStyle = ConversationNameStyle.groupNotAMemberOf(),
                latestMessage = null,
                groupMessageSender = null,
                lastUpdateAt = "4:36 PM",
                deliveryIcon = IconInfo(
                    icon = R.drawable.ic_group_filled,
                    contentDescription = null,
                ),
                unreadState = UnreadState.Messages(8L),
                isPinned = true,
                isPrivate = true,
                hasOngoingCall = false,
                draft = null,
                muteStatusIcon = R.drawable.ic_dnd_mention_grey600_24dp,
                emojiStyle = EmojiStyle_ANDROID,
                onClick = {},
                onLongClick = {},
                onClickJoinCall = {},
            )
        }
    }
}

@Preview
@Composable
private fun Preview_Group() {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            ConversationListItemContent(
                ownIdentity = "01234567",
                identityNameProvider = ThreemaTextPreviewProvider.mentionedIdentityNameProviderPreviewImpl,
                avatarContent = { AvatarForPreview() },
                conversationName = "Ganz normale Gruppe",
                conversationNameStyle = ConversationNameStyle(),
                latestMessage = GroupMessageModel().apply {
                    type = MessageType.TEXT
                    body = "Schau mal mein Name"
                },
                groupMessageSender = "\uD83C\uDF36:",
                lastUpdateAt = "4:36 PM",
                deliveryIcon = IconInfo(
                    icon = R.drawable.ic_mark_read,
                    contentDescription = null,
                ),
                unreadState = null,
                isPinned = false,
                isPrivate = false,
                hasOngoingCall = false,
                draft = null,
                muteStatusIcon = null,
                emojiStyle = EmojiStyle_ANDROID,
                onClick = {},
                onLongClick = {},
                onClickJoinCall = {},
            )
        }
    }
}

@Preview
@Composable
private fun Preview_Deleted() {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            ConversationListItemContent(
                ownIdentity = "01234567",
                identityNameProvider = ThreemaTextPreviewProvider.mentionedIdentityNameProviderPreviewImpl,
                avatarContent = { AvatarForPreview() },
                conversationName = "Deleted Message",
                conversationNameStyle = ConversationNameStyle(),
                latestMessage = MessageModel().apply {
                    type = MessageType.TEXT
                    body = null
                    deletedAt = Date()
                },
                groupMessageSender = "\uD83C\uDF36:",
                lastUpdateAt = "4:36 PM",
                deliveryIcon = IconInfo(
                    icon = R.drawable.ic_reply_filled,
                    contentDescription = null,
                ),
                unreadState = null,
                isPinned = false,
                isPrivate = false,
                hasOngoingCall = false,
                draft = null,
                muteStatusIcon = null,
                emojiStyle = EmojiStyle_ANDROID,
                onClick = {},
                onLongClick = {},
                onClickJoinCall = {},
            )
        }
    }
}

@Preview
@Composable
private fun Preview_SendFailed() {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            ConversationListItemContent(
                ownIdentity = "01234567",
                identityNameProvider = ThreemaTextPreviewProvider.mentionedIdentityNameProviderPreviewImpl,
                avatarContent = { AvatarForPreview() },
                conversationName = "FS Key Mismatch",
                conversationNameStyle = ConversationNameStyle(),
                latestMessage = MessageModel().apply {
                    type = MessageType.TEXT
                    body = "Hi"
                    state = MessageState.FS_KEY_MISMATCH
                },
                groupMessageSender = "Ich:",
                lastUpdateAt = "4:36 PM",
                deliveryIcon = IconInfo(
                    icon = R.drawable.ic_baseline_key_off_24,
                    contentDescription = null,
                    tintOverride = colorResource(R.color.material_red).toArgb(),
                ),
                unreadState = null,
                isPinned = false,
                isPrivate = false,
                hasOngoingCall = false,
                draft = null,
                muteStatusIcon = null,
                emojiStyle = EmojiStyle_ANDROID,
                onClick = {},
                onLongClick = {},
                onClickJoinCall = {},
            )
        }
    }
}

@Preview
@Composable
private fun Preview_Mentions() {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            ConversationListItemContent(
                ownIdentity = "01234567",
                identityNameProvider = ThreemaTextPreviewProvider.mentionedIdentityNameProviderPreviewImpl,
                avatarContent = { AvatarForPreview() },
                conversationName = "Deleted Message",
                conversationNameStyle = ConversationNameStyle(),
                latestMessage = GroupMessageModel().apply {
                    type = MessageType.TEXT
                    body = "Hey @[01234567] @[0123ABCD]"
                },
                groupMessageSender = "Bob:",
                lastUpdateAt = "4:36 PM",
                deliveryIcon = IconInfo(
                    icon = R.drawable.ic_reply_filled,
                    contentDescription = null,
                ),
                unreadState = null,
                isPinned = false,
                isPrivate = false,
                hasOngoingCall = false,
                draft = null,
                muteStatusIcon = null,
                emojiStyle = EmojiStyle_ANDROID,
                onClick = {},
                onLongClick = {},
                onClickJoinCall = {},
            )
        }
    }
}

@Preview(fontScale = 2.0f)
@Composable
private fun Preview_Scale() {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            ConversationListItemContent(
                ownIdentity = "01234567",
                identityNameProvider = ThreemaTextPreviewProvider.mentionedIdentityNameProviderPreviewImpl,
                avatarContent = { AvatarForPreview() },
                conversationName = "Scaled Up Conversation",
                conversationNameStyle = ConversationNameStyle(),
                latestMessage = GroupMessageModel().apply {
                    type = MessageType.TEXT
                    body = "Hey @[01234567] @[0123ABCD]"
                },
                groupMessageSender = "A:",
                lastUpdateAt = "4:25 PM",
                deliveryIcon = IconInfo(
                    icon = R.drawable.ic_reply_filled,
                    contentDescription = null,
                ),
                unreadState = UnreadState.Messages(
                    count = 1000L,
                ),
                isPinned = true,
                isPrivate = false,
                hasOngoingCall = false,
                draft = null,
                muteStatusIcon = R.drawable.ic_dnd_mention_black_18dp,
                emojiStyle = EmojiStyle_ANDROID,
                onClick = {},
                onLongClick = {},
                onClickJoinCall = {},
            )
        }
    }
}
