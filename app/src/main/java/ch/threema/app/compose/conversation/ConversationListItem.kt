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
import androidx.annotation.DrawableRes
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.threema.app.R
import ch.threema.app.compose.common.ResolvableString
import ch.threema.app.compose.common.ResolvedString
import ch.threema.app.compose.common.SpacerHorizontal
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.common.buttons.ButtonPrimaryDense
import ch.threema.app.compose.common.list.swipe.ListItemSwipeContainer
import ch.threema.app.compose.common.list.swipe.ListItemSwipeFeature
import ch.threema.app.compose.common.list.swipe.ListItemSwipeFeatureState
import ch.threema.app.compose.common.text.conversation.ConversationText
import ch.threema.app.compose.common.text.conversation.ConversationTextDefaults
import ch.threema.app.compose.common.text.conversation.MentionFeature
import ch.threema.app.compose.common.text.conversation.PreviewParameterProviderConversationText
import ch.threema.app.compose.conversation.models.ConversationListItemUiModel
import ch.threema.app.compose.conversation.models.ConversationNameStyle
import ch.threema.app.compose.conversation.models.ConversationUiModel
import ch.threema.app.compose.conversation.models.GroupCallUiModel
import ch.threema.app.compose.conversation.models.INACTIVE_CONTACT_ALPHA
import ch.threema.app.compose.conversation.models.IconInfo
import ch.threema.app.compose.conversation.models.UnreadState
import ch.threema.app.compose.preview.PreviewLightAndDarkMode
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.preference.service.PreferenceService.EmojiStyle
import ch.threema.app.preference.service.PreferenceService.EmojiStyle_ANDROID
import ch.threema.app.utils.MessageUtil
import ch.threema.app.voip.groupcall.LocalGroupId
import ch.threema.app.voip.groupcall.localGroupId
import ch.threema.app.voip.groupcall.sfu.CallId
import ch.threema.common.emptyByteArray
import ch.threema.common.now
import ch.threema.common.toHMMSS
import ch.threema.domain.types.ConversationUID
import ch.threema.domain.types.Identity
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.DistributionListModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.GroupModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageState
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.ReceiverModel
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val listItemContainerColor: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.background

private val listItemShape: Shape = RoundedCornerShape(
    size = GridUnit.x1,
)

@Composable
fun ConversationListItem(
    modifier: Modifier = Modifier,
    conversationListItemUiModel: ConversationListItemUiModel,
    identityNameProvider: (Identity) -> ResolvableString?,
    ownIdentity: Identity,
    @EmojiStyle emojiStyle: Int,
    onClick: (ConversationUiModel) -> Unit,
    onLongClick: (ConversationUiModel) -> Unit,
    onClickJoinOrOpenGroupCall: (LocalGroupId) -> Unit,
    swipeFeatureStartToEnd: ListItemSwipeFeature.StartToEnd<ConversationUID>? = null,
    swipeFeatureEndToStart: ListItemSwipeFeature.EndToStart<ConversationUID>? = null,
) {
    val conversationUiModel: ConversationUiModel = conversationListItemUiModel.model
    val context: Context = LocalContext.current
    val lastUpdateAt: String? = remember(
        key1 = conversationUiModel.latestMessage,
    ) {
        MessageUtil.getDisplayDate(context, conversationUiModel.latestMessage, false)
    }

    val swipeToDismissBoxState: SwipeToDismissBoxState = rememberMySwipeToDismissBoxState(
        swipeFeatureStartToEnd = swipeFeatureStartToEnd,
        swipeFeatureEndToStart = swipeFeatureEndToStart,
        conversationUiModel = conversationUiModel,
    )

    SwipeToDismissBox(
        modifier = modifier,
        state = swipeToDismissBoxState,
        gesturesEnabled = swipeFeatureStartToEnd != null || swipeFeatureEndToStart != null,
        enableDismissFromStartToEnd = swipeFeatureStartToEnd != null,
        enableDismissFromEndToStart = swipeFeatureEndToStart != null,
        backgroundContent = {
            // This swipeToDismissBoxState has a fixed progress of 1f when transitioning back to the settled state after thumb release.
            // Setting 0.1f in this case prevents our color animation in ListItemSwipeContainer to show its flashy end color in this state
            val swipeProgress: Float =
                if (swipeToDismissBoxState.progress != 1f || swipeToDismissBoxState.targetValue != SwipeToDismissBoxValue.Settled) {
                    swipeToDismissBoxState.progress
                } else {
                    0.1f
                }

            when (swipeToDismissBoxState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> swipeFeatureStartToEnd?.let {
                    ListItemSwipeContainer(
                        swipeFeature = swipeFeatureStartToEnd,
                        containerColorSettled = listItemContainerColor,
                        shape = listItemShape,
                        swipeProgress = swipeProgress,
                    )
                }

                SwipeToDismissBoxValue.EndToStart -> swipeFeatureEndToStart?.let {
                    ListItemSwipeContainer(
                        swipeFeature = swipeFeatureEndToStart,
                        containerColorSettled = listItemContainerColor,
                        shape = listItemShape,
                        swipeProgress = swipeProgress,
                    )
                }

                SwipeToDismissBoxValue.Settled -> {}
            }
        },
    ) {
        ConversationListItemContent(
            ownIdentity = ownIdentity,
            identityNameProvider = identityNameProvider,
            avatarContent = {
                AvatarContentBuilder(
                    conversationUiModel = conversationUiModel,
                    isChecked = conversationListItemUiModel.isChecked,
                )
            },
            conversationName = conversationUiModel.conversationName,
            conversationNameStyle = conversationUiModel.conversationNameStyle,
            latestMessage = conversationUiModel.latestMessage,
            groupMessageSenderName = when (conversationUiModel) {
                is ConversationUiModel.GroupConversation -> conversationUiModel.latestMessageSenderName
                else -> null
            },
            lastUpdateAt = lastUpdateAt,
            deliveryIcon = conversationUiModel.latestMessageStateIcon,
            unreadState = conversationUiModel.unreadState,
            isPinned = conversationUiModel.isPinned,
            isPrivate = conversationUiModel.isPrivate,
            groupCall = when (conversationUiModel) {
                is ConversationUiModel.GroupConversation -> conversationUiModel.groupCall
                else -> null
            },
            draft = conversationUiModel.draft,
            muteStatusIcon = conversationUiModel.muteStatusIcon,
            emojiStyle = emojiStyle,
            isTyping = when (conversationUiModel) {
                is ConversationUiModel.ContactConversation -> conversationUiModel.isTyping
                else -> false
            },
            onClick = {
                onClick(conversationUiModel)
            },
            onLongClick = {
                onLongClick(conversationUiModel)
            },
            onClickJoinOrOpenGroupCall = {
                if (conversationUiModel is ConversationUiModel.GroupConversation) {
                    onClickJoinOrOpenGroupCall(
                        conversationUiModel.receiverModel.localGroupId,
                    )
                }
            },
        )
    }
}

/**
 *  Depending on [swipeFeatureStartToEnd] and [swipeFeatureEndToStart], a [SwipeToDismissBoxState] will be created.
 *
 *  If no gesture is enabled, the default implementation of [rememberSwipeToDismissBoxState] is returned.
 */
@Composable
private fun rememberMySwipeToDismissBoxState(
    swipeFeatureStartToEnd: ListItemSwipeFeature.StartToEnd<ConversationUID>?,
    swipeFeatureEndToStart: ListItemSwipeFeature.EndToStart<ConversationUID>?,
    conversationUiModel: ConversationUiModel,
): SwipeToDismissBoxState {
    val isSwipeFeatureEnabled: Boolean = swipeFeatureStartToEnd != null || swipeFeatureEndToStart != null
    val hapticFeedback: HapticFeedback = LocalHapticFeedback.current
    return rememberSwipeToDismissBoxState(
        confirmValueChange = if (isSwipeFeatureEnabled) {
            { swipeToDismissBoxValue ->
                when (swipeToDismissBoxValue) {
                    SwipeToDismissBoxValue.StartToEnd -> swipeFeatureStartToEnd?.let { gesture ->
                        gesture.hapticFeedback?.let(hapticFeedback::performHapticFeedback)
                        gesture.onSwipe(conversationUiModel.conversationUID)
                    }

                    SwipeToDismissBoxValue.EndToStart -> swipeFeatureEndToStart?.let { gesture ->
                        gesture.hapticFeedback?.let(hapticFeedback::performHapticFeedback)
                        gesture.onSwipe(conversationUiModel.conversationUID)
                    }

                    SwipeToDismissBoxValue.Settled -> {}
                }
                // Return false here to prevent this item from disappearing directly by the SwipeToDismissBox
                false
            }
        } else {
            { swipeToDismissBoxValue -> false }
        },
        positionalThreshold = { totalDistance ->
            totalDistance * ListItemSwipeFeature.SWIPE_TRIGGER_FROM_PERCENT
        },
    )
}

@Composable
private fun AvatarContentBuilder(
    conversationUiModel: ConversationUiModel,
    isChecked: Boolean,
) {
    AvatarAsyncCheckable(
        receiverModel = conversationUiModel.receiverModel,
        contentDescription = conversationUiModel.receiverDisplayName?.let {
            getAvatarContentDescription(
                context = LocalContext.current,
                receiverModel = conversationUiModel.receiverModel,
                receiverDisplayName = conversationUiModel.receiverDisplayName ?: "",
            )
        },
        fallbackIcon = when (conversationUiModel) {
            is ConversationUiModel.ContactConversation -> R.drawable.ic_contact
            is ConversationUiModel.GroupConversation -> R.drawable.ic_group
            is ConversationUiModel.DistributionListConversation -> R.drawable.ic_distribution_list
        },
        showWorkBadge = when (conversationUiModel) {
            is ConversationUiModel.ContactConversation -> conversationUiModel.showWorkBadge
            else -> false
        },
        isChecked = isChecked,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationListItemContent(
    modifier: Modifier = Modifier,
    ownIdentity: Identity,
    identityNameProvider: (Identity) -> ResolvableString?,
    avatarContent: @Composable () -> Unit,
    conversationName: String,
    conversationNameStyle: ConversationNameStyle,
    latestMessage: AbstractMessageModel?,
    groupMessageSenderName: ResolvableString?,
    lastUpdateAt: String?,
    deliveryIcon: IconInfo?,
    unreadState: UnreadState?,
    isPinned: Boolean,
    isPrivate: Boolean,
    groupCall: GroupCallUiModel?,
    draft: String?,
    muteStatusIcon: Int?,
    @EmojiStyle emojiStyle: Int,
    isTyping: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClickJoinOrOpenGroupCall: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .clip(
                shape = listItemShape,
            )
            .background(
                color = listItemContainerColor,
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
                        groupCall = groupCall,
                        muteStatusIcon = muteStatusIcon,
                        emojiStyle = emojiStyle,
                    )
                    SecondLine(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(
                                min = 24.dp,
                            ),
                        ownIdentity = ownIdentity,
                        identityNameProvider = identityNameProvider,
                        latestMessage = latestMessage,
                        groupMessageSenderName = groupMessageSenderName,
                        isPrivate = isPrivate,
                        groupCall = groupCall,
                        lastUpdateAt = lastUpdateAt,
                        deliveryIcon = deliveryIcon,
                        unreadState = unreadState,
                        draft = draft,
                        emojiStyle = emojiStyle,
                        isTyping = isTyping,
                    )
                }

                if (groupCall != null) {
                    ButtonPrimaryDense(
                        modifier = Modifier.padding(horizontal = GridUnit.x1_5),
                        onClick = onClickJoinOrOpenGroupCall,
                        text = stringResource(
                            if (groupCall.isJoined) {
                                R.string.voip_gc_open_call
                            } else {
                                R.string.voip_gc_join_call
                            },
                        ),
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
    groupCall: GroupCallUiModel?,
    @DrawableRes muteStatusIcon: Int?,
    @EmojiStyle emojiStyle: Int,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (muteStatusIcon != null && groupCall == null) {
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
                    if (conversationNameStyle.dimAlpha) INACTIVE_CONTACT_ALPHA else 1f,
                ),
            rawInput = conversationName,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (unreadState != null) FontWeight.SemiBold else FontWeight.Normal,
                textDecoration = if (conversationNameStyle.strikethrough) TextDecoration.LineThrough else TextDecoration.None,
            ),
            color = LocalContentColor.current,
            maxLines = 1,
            emojiSettings = ConversationTextDefaults.EmojiSettings.copy(
                style = emojiStyle,
            ),
            mentionFeature = MentionFeature.Off,
            markupEnabled = false,
        )
        if (isPinned && groupCall == null) {
            SpacerHorizontal(GridUnit.x0_5)
            Image(
                modifier = Modifier.size(GridUnit.x3),
                painter = painterResource(R.drawable.ic_pin_circle),
                contentDescription = null,
            )
        }
        if (unreadState != null && groupCall == null) {
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
    ownIdentity: Identity,
    identityNameProvider: (Identity) -> ResolvableString?,
    latestMessage: AbstractMessageModel?,
    groupMessageSenderName: ResolvableString?,
    isPrivate: Boolean,
    groupCall: GroupCallUiModel?,
    lastUpdateAt: String?,
    deliveryIcon: IconInfo?,
    unreadState: UnreadState?,
    draft: String?,
    @EmojiStyle emojiStyle: Int,
    isTyping: Boolean,
) {
    if (isPrivate) {
        SecondLinePrivate(
            modifier = modifier,
            unreadState = unreadState,
        )
    } else if (groupCall != null) {
        SecondLineOngoingGroupCall(
            modifier = modifier,
            groupCall = groupCall,
        )
    } else if (isTyping) {
        SecondLineTyping(
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
            groupMessageSenderName = groupMessageSenderName,
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
    ThemedText(
        modifier = modifier,
        text = stringResource(R.string.private_chat_subject),
        style = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = if (unreadState != null) FontWeight.SemiBold else FontWeight.Normal,
        ),
        color = LocalContentColor.current,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SecondLineOngoingGroupCall(
    modifier: Modifier,
    groupCall: GroupCallUiModel,
) {
    var callDuration: Duration by remember(
        key1 = groupCall.id,
        key2 = groupCall.startedAt,
        key3 = groupCall.processedAt,
    ) {
        mutableStateOf(groupCall.getCallDurationNow())
    }
    LaunchedEffect(
        key1 = groupCall.id,
        key2 = groupCall.startedAt,
        key3 = groupCall.processedAt,
    ) {
        launch {
            while (isActive) {
                delay(1.seconds)
                callDuration += 1.seconds
            }
        }
    }

    ThemedText(
        modifier = modifier,
        text = buildString {
            append(callDuration.toHMMSS())
            append(" | ")
            append(
                stringResource(
                    id = if (groupCall.isJoined) R.string.voip_gc_in_call else R.string.voip_gc_ongoing_call,
                ),
            )
        },
        color = LocalContentColor.current,
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
    ownIdentity: Identity,
    identityNameProvider: (Identity) -> ResolvableString?,
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
            markupEnabled = true,
        )
        SpacerHorizontal(GridUnit.x1)
        ThemedText(
            modifier = Modifier.widthIn(max = 150.dp),
            text = stringResource(R.string.draft).uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Red,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SecondLineTyping(
    modifier: Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TypingIndicator()
    }
}

@Composable
private fun SecondLineDefault(
    modifier: Modifier,
    latestMessage: AbstractMessageModel?,
    groupMessageSenderName: ResolvableString?,
    lastUpdateAt: String?,
    deliveryIcon: IconInfo?,
    unreadState: UnreadState?,
    ownIdentity: Identity,
    identityNameProvider: (Identity) -> ResolvableString?,
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
        if (groupMessageSenderName != null) {
            val fontWeightSender: FontWeight = when {
                unreadState != null -> FontWeight.SemiBold
                else -> FontWeight.Normal
            }
            ConversationText(
                rawInput = groupMessageSenderName.get(context),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = fontWeightSender,
                ),
                color = LocalContentColor.current,
                maxLines = 1,
                emojiSettings = ConversationTextDefaults.EmojiSettings.copy(
                    style = emojiStyle,
                ),
                mentionFeature = MentionFeature.Off,
                markupEnabled = false,
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
            ThemedText(
                text = lastUpdateAt,
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current,
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

private fun getAvatarContentDescription(
    context: Context,
    receiverModel: ReceiverModel,
    receiverDisplayName: String,
): String? =
    when (receiverModel) {
        is ContactModel -> context.getString(
            R.string.edit_type_content_description,
            context.getString(R.string.mime_contact),
            receiverDisplayName,
        )

        is GroupModel -> context.getString(
            R.string.edit_type_content_description,
            context.getString(R.string.group),
            receiverDisplayName,
        )

        is DistributionListModel -> context.getString(
            R.string.edit_type_content_description,
            context.getString(R.string.distribution_list),
            receiverDisplayName,
        )

        else -> null
    }

private class PreviewProviderContactConversationListItemItemUiModel : PreviewParameterProvider<ConversationListItemUiModel> {

    private companion object {

        fun createContactConversationListItemUiModel(
            receiverIdentity: Identity = "00000000",
            receiverDisplayName: String = "Contact Name",
            conversationName: String = "Contact Name",
            conversationNameStyle: ConversationNameStyle = ConversationNameStyle(),
            draft: String? = null,
            latestMessage: AbstractMessageModel? = MessageModel().apply {
                this.type = MessageType.TEXT
                this.body = "Contact message body"
                this.createdAt = Date()
                this.postedAt = Date()
            },
            latestMessageStateIcon: IconInfo? = IconInfo(
                icon = R.drawable.ic_reply_filled,
                contentDescription = null,
            ),
            unreadState: UnreadState? = null,
            isPinned: Boolean = false,
            isPrivate: Boolean = false,
            muteStatusIcon: Int? = null,
            isChecked: Boolean = false,
            showWorkBadge: Boolean = false,
            isTyping: Boolean = false,
        ) = ConversationListItemUiModel(
            model = ConversationUiModel.ContactConversation(
                conversationUID = "0",
                receiverModel = ContactModel.createUnchecked(receiverIdentity, Random.nextBytes(32)),
                receiverDisplayName = receiverDisplayName,
                conversationName = conversationName,
                conversationNameStyle = conversationNameStyle,
                draft = draft,
                latestMessage = latestMessage,
                latestMessageStateIcon = latestMessageStateIcon,
                unreadState = unreadState,
                isPinned = isPinned,
                isPrivate = isPrivate,
                muteStatusIcon = muteStatusIcon,
                showWorkBadge = showWorkBadge,
                isTyping = isTyping,
            ),
            isChecked = isChecked,
        )
    }

    override val values: Sequence<ConversationListItemUiModel> = sequenceOf(
        createContactConversationListItemUiModel(
            latestMessage = null,
            latestMessageStateIcon = null,
        ),
        createContactConversationListItemUiModel(
            showWorkBadge = true,
        ),
        createContactConversationListItemUiModel(
            isTyping = true,
        ),
        createContactConversationListItemUiModel(
            unreadState = UnreadState.Messages(
                count = 5,
            ),
        ),
        createContactConversationListItemUiModel(
            isPinned = true,
        ),
        createContactConversationListItemUiModel(
            isPrivate = true,
            isTyping = true,
        ),
        createContactConversationListItemUiModel(
            unreadState = UnreadState.Messages(
                count = 5,
            ),
            isPinned = true,
            isPrivate = true,
        ),
        createContactConversationListItemUiModel(
            conversationNameStyle = ConversationNameStyle.inactiveContact(),
        ),
        createContactConversationListItemUiModel(
            conversationNameStyle = ConversationNameStyle.invalidContact(),
        ),
        createContactConversationListItemUiModel(
            draft = "This message is just a draft and is maybe sent in the future",
        ),
        createContactConversationListItemUiModel(
            muteStatusIcon = R.drawable.ic_do_not_disturb_filled,
        ),
        createContactConversationListItemUiModel(
            latestMessage = MessageModel().apply {
                type = MessageType.TEXT
                body = null
                deletedAt = Date()
            },
        ),
        createContactConversationListItemUiModel(
            latestMessage = MessageModel().apply {
                type = MessageType.TEXT
                body = "Contact message body"
                state = MessageState.FS_KEY_MISMATCH
            },
            latestMessageStateIcon = IconInfo(
                icon = R.drawable.ic_baseline_key_off_24,
                contentDescription = null,
                tintOverride = Color.Red.toArgb(),
            ),
        ),
        createContactConversationListItemUiModel(
            isChecked = true,
        ),
    )
}

private class PreviewProviderGroupConversationListItemUiModel : PreviewParameterProvider<ConversationListItemUiModel> {

    private companion object {
        fun createGroupConversationListItemUiModel(
            receiverDisplayName: String = "Group Name",
            conversationName: String = "Group Name",
            conversationNameStyle: ConversationNameStyle = ConversationNameStyle(),
            draft: String? = null,
            latestMessage: AbstractMessageModel? = GroupMessageModel().apply {
                this.type = MessageType.TEXT
                this.body = "Group message body"
            },
            latestMessageStateIcon: IconInfo? = IconInfo(
                icon = R.drawable.ic_reply_filled,
                contentDescription = null,
            ),
            latestMessageSenderName: ResolvableString? = ResolvedString("Alice:"),
            unreadState: UnreadState? = null,
            isPinned: Boolean = false,
            isPrivate: Boolean = false,
            muteStatusIcon: Int? = null,
            groupCall: GroupCallUiModel? = null,
            isChecked: Boolean = false,
        ) = ConversationListItemUiModel(
            model = ConversationUiModel.GroupConversation(
                conversationUID = "0",
                receiverModel = GroupModel(),
                receiverDisplayName = receiverDisplayName,
                conversationName = conversationName,
                conversationNameStyle = conversationNameStyle,
                draft = draft,
                latestMessage = latestMessage,
                latestMessageStateIcon = latestMessageStateIcon,
                latestMessageSenderName = latestMessageSenderName,
                unreadState = unreadState,
                isPinned = isPinned,
                isPrivate = isPrivate,
                muteStatusIcon = muteStatusIcon,
                groupCall = groupCall,
            ),
            isChecked = isChecked,
        )
    }

    override val values: Sequence<ConversationListItemUiModel> = sequenceOf(
        createGroupConversationListItemUiModel(
            latestMessage = null,
            latestMessageSenderName = null,
            latestMessageStateIcon = null,
        ),
        createGroupConversationListItemUiModel(),
        createGroupConversationListItemUiModel(
            latestMessage = GroupMessageModel().apply {
                type = MessageType.TEXT
                body = "Hey @[01234567] @[0123ABCD]"
            },
        ),
        createGroupConversationListItemUiModel(
            conversationNameStyle = ConversationNameStyle.groupNotAMemberOf(),
        ),
        createGroupConversationListItemUiModel(
            isPinned = true,
        ),
        createGroupConversationListItemUiModel(
            unreadState = UnreadState.Messages(
                count = 5,
            ),
        ),
        createGroupConversationListItemUiModel(
            isPinned = true,
            unreadState = UnreadState.Messages(
                count = 5,
            ),
            isPrivate = true,
        ),
        createGroupConversationListItemUiModel(
            groupCall = GroupCallUiModel(
                id = CallId(emptyByteArray()),
                groupId = LocalGroupId(0),
                startedAt = now().time - (1000L * 30L),
                processedAt = now().time - (1000L * 30L),
                isJoined = false,
            ),
        ),
        createGroupConversationListItemUiModel(
            groupCall = GroupCallUiModel(
                id = CallId(emptyByteArray()),
                groupId = LocalGroupId(0),
                startedAt = now().time - (1000L * 30L),
                processedAt = now().time - (1000L * 30L),
                isJoined = true,
            ),
        ),
        createGroupConversationListItemUiModel(
            groupCall = GroupCallUiModel(
                id = CallId(emptyByteArray()),
                groupId = LocalGroupId(0),
                startedAt = now().time - (1000L * 30L),
                processedAt = now().time - (1000L * 30L),
                isJoined = false,
            ),
            isPinned = true,
        ),
        createGroupConversationListItemUiModel(
            groupCall = GroupCallUiModel(
                id = CallId(emptyByteArray()),
                groupId = LocalGroupId(0),
                startedAt = now().time - (1000L * 30L),
                processedAt = now().time - (1000L * 30L),
                isJoined = false,
            ),
            unreadState = UnreadState.Messages(
                count = 5,
            ),
        ),
        createGroupConversationListItemUiModel(
            groupCall = GroupCallUiModel(
                id = CallId(emptyByteArray()),
                groupId = LocalGroupId(0),
                startedAt = now().time - (1000L * 30L),
                processedAt = now().time - (1000L * 30L),
                isJoined = false,
            ),
            isPrivate = true,
        ),
        createGroupConversationListItemUiModel(
            groupCall = GroupCallUiModel(
                id = CallId(emptyByteArray()),
                groupId = LocalGroupId(0),
                startedAt = now().time - (1000L * 30L),
                processedAt = now().time - (1000L * 30L),
                isJoined = false,
            ),
            isPinned = true,
            muteStatusIcon = R.drawable.ic_dnd_mention_grey600_24dp,
            unreadState = UnreadState.Messages(
                count = 5,
            ),
        ),
        createGroupConversationListItemUiModel(
            isChecked = true,
        ),
    )
}

private class PreviewProviderDistributionListConversationListItemUiModel :
    PreviewParameterProvider<ConversationListItemUiModel> {

    private companion object {
        fun createDistributionListConversationListItemUiModel(
            receiverDisplayName: String = "Distribution List",
            conversationName: String = "Distribution List",
            conversationNameStyle: ConversationNameStyle = ConversationNameStyle(),
            draft: String? = null,
            latestMessage: AbstractMessageModel? = GroupMessageModel().apply {
                this.type = MessageType.TEXT
                this.body = "Distribution list message"
            },
            latestMessageStateIcon: IconInfo? = IconInfo(
                icon = R.drawable.ic_distribution_list_filled,
                contentDescription = null,
            ),
            unreadState: UnreadState? = null,
            isPinned: Boolean = false,
            isPrivate: Boolean = false,
            muteStatusIcon: Int? = null,
            isChecked: Boolean = false,
        ) = ConversationListItemUiModel(
            model = ConversationUiModel.DistributionListConversation(
                conversationUID = "0",
                receiverModel = DistributionListModel(),
                receiverDisplayName = receiverDisplayName,
                conversationName = conversationName,
                conversationNameStyle = conversationNameStyle,
                draft = draft,
                latestMessage = latestMessage,
                latestMessageStateIcon = latestMessageStateIcon,
                unreadState = unreadState,
                isPinned = isPinned,
                isPrivate = isPrivate,
                muteStatusIcon = muteStatusIcon,
            ),
            isChecked = isChecked,
        )
    }

    override val values: Sequence<ConversationListItemUiModel> = sequenceOf(
        createDistributionListConversationListItemUiModel(
            latestMessage = null,
        ),
        createDistributionListConversationListItemUiModel(),
        createDistributionListConversationListItemUiModel(
            isPinned = true,
        ),
        createDistributionListConversationListItemUiModel(
            isPrivate = true,
        ),
        createDistributionListConversationListItemUiModel(
            unreadState = UnreadState.Messages(
                count = 16,
            ),
        ),
        createDistributionListConversationListItemUiModel(
            unreadState = UnreadState.Messages(
                count = 16,
            ),
            isPinned = true,
            isPrivate = true,
        ),
        createDistributionListConversationListItemUiModel(
            isChecked = true,
        ),
    )
}

@Composable
@PreviewLightAndDarkMode
private fun Preview_ContactConversation(
    @PreviewParameter(PreviewProviderContactConversationListItemItemUiModel::class)
    conversationListItemUiModel: ConversationListItemUiModel,
) = Preview(
    conversationListItemUiModel = conversationListItemUiModel,
)

@Composable
@PreviewLightAndDarkMode
private fun Preview_GroupConversation(
    @PreviewParameter(PreviewProviderGroupConversationListItemUiModel::class)
    conversationListItemUiModel: ConversationListItemUiModel,
) = Preview(
    conversationListItemUiModel = conversationListItemUiModel,
)

@Composable
@PreviewLightAndDarkMode
private fun Preview_DistributionListConversation(
    @PreviewParameter(PreviewProviderDistributionListConversationListItemUiModel::class)
    conversationListItemUiModel: ConversationListItemUiModel,
) = Preview(
    conversationListItemUiModel = conversationListItemUiModel,
)

@Composable
private fun Preview(conversationListItemUiModel: ConversationListItemUiModel) {
    ThreemaThemePreview {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            ConversationListItem(
                modifier = Modifier,
                conversationListItemUiModel = conversationListItemUiModel,
                identityNameProvider = PreviewParameterProviderConversationText.mentionedIdentityNameProviderPreviewImpl,
                ownIdentity = "01234567",
                emojiStyle = EmojiStyle_ANDROID,
                onClick = {},
                onLongClick = {},
                onClickJoinOrOpenGroupCall = {},
                swipeFeatureStartToEnd = ListItemSwipeFeature.StartToEnd(
                    onSwipe = {},
                    hapticFeedback = null,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    state = ListItemSwipeFeatureState(
                        icon = R.drawable.ic_new_feature,
                        text = "Swipe start to end",
                    ),
                ),
                swipeFeatureEndToStart = ListItemSwipeFeature.EndToStart(
                    onSwipe = {},
                    hapticFeedback = null,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    state = ListItemSwipeFeatureState(
                        icon = R.drawable.ic_new_feature,
                        text = "Swipe end to start",
                    ),
                ),
            )
        }
    }
}
