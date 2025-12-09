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

package ch.threema.app.usecases.conversations

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import ch.threema.android.ResolvableString
import ch.threema.android.ResolvedString
import ch.threema.android.ResourceIdString
import ch.threema.app.R
import ch.threema.app.compose.conversation.models.ConversationNameStyle
import ch.threema.app.compose.conversation.models.ConversationUiModel
import ch.threema.app.compose.conversation.models.GroupCallUiModel
import ch.threema.app.compose.conversation.models.IconInfo
import ch.threema.app.compose.conversation.models.UnreadState
import ch.threema.app.drafts.DraftManager
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.services.ContactService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.DistributionListService
import ch.threema.app.services.GroupService
import ch.threema.app.services.RingtoneService
import ch.threema.app.usecases.WatchGroupCallsUseCase
import ch.threema.app.usecases.WatchTypingIdentitiesUseCase
import ch.threema.app.utils.MessageUtil
import ch.threema.app.utils.NameUtil
import ch.threema.app.utils.StateBitmapUtil
import ch.threema.app.voip.groupcall.localGroupId
import ch.threema.domain.types.Identity
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.ConversationModel
import ch.threema.storage.models.DistributionListModel
import ch.threema.storage.models.GroupModel
import ch.threema.storage.models.MessageState
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.ReceiverModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

abstract class WatchConversationListItemsUseCase(
    private val conversationModels: Flow<List<ConversationModel>>,
    watchGroupCallsUseCase: WatchGroupCallsUseCase,
    watchTypingIdentitiesUseCase: WatchTypingIdentitiesUseCase,
    private val conversationCategoryService: ConversationCategoryService,
    private val contactService: ContactService,
    private val groupService: GroupService,
    private val distributionListService: DistributionListService,
    private val ringtoneService: RingtoneService,
    private val draftManager: DraftManager,
) {

    private val groupCalls: Flow<Set<GroupCallUiModel>> = watchGroupCallsUseCase.call().distinctUntilChanged()

    // TODO(ANDR-4175): Watching this separately may not be necessary anymore if the isTyping value of archived conversation models is updated correctly
    private val typingIdentities: Flow<Set<Identity>> = watchTypingIdentitiesUseCase.call()

    fun call(): Flow<List<ConversationUiModel>> = combine(
        flow = conversationModels,
        flow2 = groupCalls.distinctUntilChanged(),
        flow3 = typingIdentities.distinctUntilChanged(),
    ) { conversationModels, groupCalls, typingIdentities ->
        conversationModels.mapNotNull { conversationModel ->
            mapToConversationUiModel(
                conversationModel = conversationModel,
                groupCalls = groupCalls,
                typingIdentities = typingIdentities,
            )
        }
    }

    private fun mapToConversationUiModel(
        conversationModel: ConversationModel,
        groupCalls: Set<GroupCallUiModel>,
        typingIdentities: Set<Identity>,
    ): ConversationUiModel? =
        if (conversationModel.contact != null) {
            mapToContactConversationUiModel(conversationModel, typingIdentities)
        } else if (conversationModel.group != null) {
            mapToGroupConversationUiModel(conversationModel, groupCalls)
        } else if (conversationModel.distributionList != null) {
            mapToDistributionListConversationUiModel(conversationModel)
        } else {
            null
        }

    private fun mapToContactConversationUiModel(
        conversationModel: ConversationModel,
        typingIdentities: Set<Identity>,
    ): ConversationUiModel.ContactConversation? {
        val contactModel: ContactModel = conversationModel.contact ?: return null
        return ConversationUiModel.ContactConversation(
            conversationUID = conversationModel.uid,
            latestMessage = conversationModel.latestMessage,
            receiverModel = contactModel,
            receiverDisplayName = getReceiverDisplayNameOrNull(conversationModel.receiverModel),
            conversationName = conversationModel.messageReceiver.displayName,
            conversationNameStyle = ConversationNameStyle.Companion.forConversationModel(conversationModel),
            draft = getDraftOrNull(conversationModel),
            latestMessageStateIcon = getLatestMessageStateIconOrNull(conversationModel),
            unreadState = getUnreadStateOrNull(conversationModel),
            isPinned = conversationModel.isPinTagged,
            isPrivate = getIsPrivate(conversationModel),
            muteStatusIcon = getMuteStatusIconOrNull(conversationModel),
            showWorkBadge = contactService.showBadge(contactModel),
            isTyping = typingIdentities.contains(contactModel.identity),
        )
    }

    private fun mapToGroupConversationUiModel(
        conversationModel: ConversationModel,
        groupCalls: Set<GroupCallUiModel>,
    ): ConversationUiModel.GroupConversation? {
        val groupModel: GroupModel = conversationModel.group ?: return null
        return ConversationUiModel.GroupConversation(
            conversationUID = conversationModel.uid,
            latestMessage = conversationModel.latestMessage,
            receiverModel = groupModel,
            receiverDisplayName = getReceiverDisplayNameOrNull(conversationModel.receiverModel),
            conversationName = conversationModel.messageReceiver.displayName,
            conversationNameStyle = ConversationNameStyle.Companion.forConversationModel(conversationModel),
            draft = getDraftOrNull(conversationModel),
            latestMessageStateIcon = getLatestMessageStateIconOrNull(conversationModel),
            unreadState = getUnreadStateOrNull(conversationModel),
            isPinned = conversationModel.isPinTagged,
            isPrivate = getIsPrivate(conversationModel),
            groupCall = getGroupCallState(groupModel, groupCalls),
            muteStatusIcon = getMuteStatusIconOrNull(conversationModel),
            latestMessageSenderName = getGroupMessageSenderNameOrNull(conversationModel),
        )
    }

    private fun mapToDistributionListConversationUiModel(
        conversationModel: ConversationModel,
    ): ConversationUiModel.DistributionListConversation? {
        val distributionListModel: DistributionListModel = conversationModel.distributionList ?: return null
        return ConversationUiModel.DistributionListConversation(
            conversationUID = conversationModel.uid,
            latestMessage = conversationModel.latestMessage,
            receiverModel = distributionListModel,
            receiverDisplayName = getReceiverDisplayNameOrNull(conversationModel.receiverModel),
            conversationName = conversationModel.messageReceiver.displayName,
            conversationNameStyle = ConversationNameStyle.Companion.forConversationModel(conversationModel),
            draft = getDraftOrNull(conversationModel),
            latestMessageStateIcon = getLatestMessageStateIconOrNull(conversationModel),
            unreadState = getUnreadStateOrNull(conversationModel),
            isPinned = conversationModel.isPinTagged,
            isPrivate = getIsPrivate(conversationModel),
            muteStatusIcon = getMuteStatusIconOrNull(conversationModel),
        )
    }

    private fun getDraftOrNull(conversationModel: ConversationModel): String? =
        draftManager.get(conversationUniqueId = conversationModel.messageReceiver.uniqueIdString)
            ?.text

    private fun getLatestMessageStateIconOrNull(conversationModel: ConversationModel): IconInfo? {
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

    private fun getUnreadStateOrNull(conversationModel: ConversationModel): UnreadState? {
        return when {
            conversationModel.hasUnreadMessage() -> UnreadState.Messages(conversationModel.unreadCount)
            conversationModel.isUnreadTagged -> UnreadState.JustMarked
            else -> null
        }
    }

    private fun getReceiverDisplayNameOrNull(receiverModel: ReceiverModel): String? {
        return when (receiverModel) {
            is ContactModel -> NameUtil.getDisplayNameOrNickname(receiverModel, true)
            is GroupModel -> NameUtil.getDisplayName(receiverModel, groupService)
            is DistributionListModel -> NameUtil.getDisplayName(receiverModel, distributionListService)
            else -> null
        }
    }

    private fun getIsPrivate(conversationModel: ConversationModel): Boolean {
        return conversationCategoryService.isPrivateChat(
            uniqueIdString = conversationModel.messageReceiver.uniqueIdString,
        )
    }

    private fun getGroupCallState(groupModel: GroupModel, groupCalls: Set<GroupCallUiModel>): GroupCallUiModel? {
        return groupCalls.firstOrNull { groupCallUiModel ->
            groupCallUiModel.groupId == groupModel.localGroupId
        }
    }

    @DrawableRes
    private fun getMuteStatusIconOrNull(conversationModel: ConversationModel): Int? {
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

    private fun getGroupMessageSenderNameOrNull(conversationModel: ConversationModel): ResolvableString? {
        val hasOwnDraft: Boolean = getDraftOrNull(conversationModel) != null
        val latestMessage: AbstractMessageModel? = conversationModel.latestMessage

        if (
            !conversationModel.isGroupConversation ||
            latestMessage == null ||
            latestMessage.type == MessageType.GROUP_CALL_STATUS ||
            hasOwnDraft
        ) {
            return null
        }

        return if (latestMessage.isOutbox) {
            ResourceIdString(R.string.me_myself_and_i)
        } else {
            NameUtil.getShortName(
                contactService.getByIdentity(latestMessage.identity),
            )?.let { shortName ->
                ResolvedString(
                    string = shortName,
                )
            }
        }
    }
}
