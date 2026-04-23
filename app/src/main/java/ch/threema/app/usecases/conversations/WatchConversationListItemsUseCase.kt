package ch.threema.app.usecases.conversations

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import ch.threema.android.ResolvableString
import ch.threema.android.ResourceIdString
import ch.threema.android.toResolvedString
import ch.threema.app.R
import ch.threema.app.compose.common.text.conversation.ConversationTextAnalyzer
import ch.threema.app.compose.conversation.models.ConversationNameStyle
import ch.threema.app.compose.conversation.models.ConversationUiModel
import ch.threema.app.compose.conversation.models.GroupCallUiModel
import ch.threema.app.compose.conversation.models.IconInfo
import ch.threema.app.compose.conversation.models.UnreadState
import ch.threema.app.drafts.DraftManager
import ch.threema.app.drafts.MessageDraft
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.services.ContactService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.DistributionListService
import ch.threema.app.services.GroupService
import ch.threema.app.services.RingtoneService
import ch.threema.app.usecases.WatchTypingIdentitiesUseCase
import ch.threema.app.usecases.availabilitystatus.WatchAllContactAvailabilityStatusesUseCase
import ch.threema.app.usecases.contacts.WatchAllMentionNamesUseCase
import ch.threema.app.usecases.contacts.WatchContactNameFormatSettingUseCase
import ch.threema.app.usecases.groups.WatchGroupCallsUseCase
import ch.threema.app.utils.MessageUtil
import ch.threema.app.utils.NameUtil
import ch.threema.app.utils.QuoteUtil
import ch.threema.app.utils.StateBitmapUtil
import ch.threema.common.combine
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.data.datatypes.MentionNameData
import ch.threema.data.datatypes.localGroupId
import ch.threema.domain.models.ReceiverIdentifier
import ch.threema.domain.types.ConversationUID
import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.ConversationModel
import ch.threema.storage.models.DistributionListModel
import ch.threema.storage.models.MessageState
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.ReceiverModel
import ch.threema.storage.models.group.GroupModelOld
import kotlinx.coroutines.flow.Flow

// TODO(ANDR-4175): Watching the typing indicator separately may not be necessary anymore if the isTyping value of archived conversation models is updated correctly
// TODO(ANDR-4277): Group conversation names need to refresh when members added/removed
abstract class WatchConversationListItemsUseCase(
    private val watchConversationsUseCase: WatchConversationsUseCase,
    private val watchGroupCallsUseCase: WatchGroupCallsUseCase,
    private val watchTypingIdentitiesUseCase: WatchTypingIdentitiesUseCase,
    private val watchAvatarIterationsUseCase: WatchAvatarIterationsUseCase,
    private val watchContactNameFormatSettingUseCase: WatchContactNameFormatSettingUseCase,
    private val watchAllMentionNamesUseCase: WatchAllMentionNamesUseCase,
    private val watchAllContactAvailabilityStatusesUseCase: WatchAllContactAvailabilityStatusesUseCase,
    private val draftManager: DraftManager,
    private val conversationCategoryService: ConversationCategoryService,
    private val contactService: ContactService,
    private val groupService: GroupService,
    private val distributionListService: DistributionListService,
    private val ringtoneService: RingtoneService,
) {

    /**
     *  Creates a flow holding the most recent [ConversationUiModel]s.
     *
     *  Things that influence the list of conversation models:
     *  - Any change to the underlying [ConversationModel]s that is published via the global [ch.threema.app.listeners.ConversationListener], like:
     *      - the latest message
     *      - the category applied to a conversation (private-marked)
     *      - the tag(s) applied to a conversation (pinned, unread)
     *  - Some change to the [ch.threema.app.messagereceiver.MessageReceiver], like:
     *      - Name
     *      - Avatar
     *  - Naming changes to mentioned contacts in the latest message
     *  - Currently running group calls
     *  - Currently typing identities
     *  - User preferences
     *      - Show contact defined avatars
     *      - Show default avatar with colors
     *      - Display order of firstname and lastname
     *  - Message drafts
     *
     *  Note that this list will contain all private-marked conversations, ignoring the user setting to hide them.
     */
    fun call(): Flow<List<ConversationUiModel>> =
        combine(
            watchConversationsUseCase.call(),
            watchGroupCallsUseCase.call(),
            watchTypingIdentitiesUseCase.call(),
            watchAvatarIterationsUseCase.call(),
            watchContactNameFormatSettingUseCase.call(),
            draftManager.drafts,
            watchAllMentionNamesUseCase.call(),
            watchAllContactAvailabilityStatusesUseCase.call(),
        ) {
                conversationModels,
                groupCalls,
                typingIdentities,
                avatarIterations,
                contactNameFormat,
                drafts,
                mentionNameData,
                contactAvailabilityStatuses,
            ->
            val privateConversationUIDs: Set<ConversationUID> = conversationModels
                .mapNotNull { conversationModel ->
                    if (getIsPrivate(conversationModel)) conversationModel.uid else null
                }
                .toSet()
            conversationModels
                .mapNotNull { conversationModel ->
                    mapToConversationUiModel(
                        conversationModel = conversationModel,
                        groupCalls = groupCalls,
                        typingIdentities = typingIdentities,
                        privateConversationUIDs = privateConversationUIDs,
                        avatarIterations = avatarIterations,
                        contactNameFormat = contactNameFormat,
                        drafts = drafts,
                        mentionNameData = mentionNameData,
                        contactAvailabilityStatuses = contactAvailabilityStatuses,
                    )
                }
        }

    private fun mapToConversationUiModel(
        conversationModel: ConversationModel,
        groupCalls: Set<GroupCallUiModel>,
        typingIdentities: Set<IdentityString>,
        privateConversationUIDs: Set<ConversationUID>,
        avatarIterations: Map<ReceiverIdentifier, AvatarIteration>,
        contactNameFormat: ContactNameFormat,
        drafts: Map<ConversationUID, MessageDraft>,
        mentionNameData: List<MentionNameData>,
        contactAvailabilityStatuses: Map<IdentityString, AvailabilityStatus>,
    ): ConversationUiModel? =
        if (conversationModel.contact != null) {
            mapToContactConversationUiModel(
                conversationModel = conversationModel,
                typingIdentities = typingIdentities,
                privateConversationUIDs = privateConversationUIDs,
                avatarIterations = avatarIterations,
                contactNameFormat = contactNameFormat,
                drafts = drafts,
                mentionNameData = mentionNameData,
                contactAvailabilityStatuses = contactAvailabilityStatuses,
            )
        } else if (conversationModel.group != null) {
            mapToGroupConversationUiModel(
                conversationModel = conversationModel,
                groupCalls = groupCalls,
                privateConversationUIDs = privateConversationUIDs,
                avatarIterations = avatarIterations,
                contactNameFormat = contactNameFormat,
                drafts = drafts,
                mentionNameData = mentionNameData,
            )
        } else if (conversationModel.distributionList != null) {
            mapToDistributionListConversationUiModel(
                conversationModel = conversationModel,
                privateConversationUIDs = privateConversationUIDs,
                contactNameFormat = contactNameFormat,
                drafts = drafts,
                mentionNameData = mentionNameData,
            )
        } else {
            null
        }

    private fun mapToContactConversationUiModel(
        conversationModel: ConversationModel,
        typingIdentities: Set<IdentityString>,
        privateConversationUIDs: Set<ConversationUID>,
        avatarIterations: Map<ReceiverIdentifier, AvatarIteration>,
        contactNameFormat: ContactNameFormat,
        drafts: Map<ConversationUID, MessageDraft>,
        mentionNameData: List<MentionNameData>,
        contactAvailabilityStatuses: Map<IdentityString, AvailabilityStatus>,
    ): ConversationUiModel.ContactConversation? {
        val contactModel: ContactModel = conversationModel.contact ?: return null
        return ConversationUiModel.ContactConversation(
            conversationUID = conversationModel.uid,
            receiverIdentifier = contactModel.identifier,
            latestMessageData = getLatestMessageData(conversationModel, mentionNameData, contactNameFormat),
            receiverDisplayName = getReceiverDisplayNameOrNull(
                receiverModel = conversationModel.receiverModel,
                contactNameFormat = contactNameFormat,
            ),
            conversationName = conversationModel.messageReceiver.getDisplayName(contactNameFormat),
            conversationNameStyle = ConversationNameStyle.forConversationModel(conversationModel),
            draftData = getDraftData(conversationModel, drafts, mentionNameData, contactNameFormat),
            unreadState = getUnreadStateOrNull(conversationModel),
            isPinned = conversationModel.isPinTagged,
            isPrivate = privateConversationUIDs.contains(conversationModel.uid),
            icon = getConversationIconOrNull(conversationModel),
            muteStatusIcon = getMuteStatusIconOrNull(conversationModel),
            showWorkBadge = contactService.showBadge(contactModel),
            isTyping = typingIdentities.contains(contactModel.identity),
            avatarIteration = avatarIterations[contactModel.identifier] ?: AvatarIteration.initial,
            availabilityStatus = contactAvailabilityStatuses[contactModel.identity],
        )
    }

    private fun mapToGroupConversationUiModel(
        conversationModel: ConversationModel,
        groupCalls: Set<GroupCallUiModel>,
        privateConversationUIDs: Set<ConversationUID>,
        avatarIterations: Map<ReceiverIdentifier, AvatarIteration>,
        contactNameFormat: ContactNameFormat,
        drafts: Map<ConversationUID, MessageDraft>,
        mentionNameData: List<MentionNameData>,
    ): ConversationUiModel.GroupConversation? {
        val groupModel: GroupModelOld = conversationModel.group ?: return null
        return ConversationUiModel.GroupConversation(
            conversationUID = conversationModel.uid,
            receiverIdentifier = groupModel.identifier,
            latestMessageData = getLatestMessageData(conversationModel, mentionNameData, contactNameFormat),
            receiverDisplayName = getReceiverDisplayNameOrNull(
                receiverModel = conversationModel.receiverModel,
                contactNameFormat = contactNameFormat,
            ),
            conversationName = conversationModel.messageReceiver.getDisplayName(contactNameFormat),
            conversationNameStyle = ConversationNameStyle.forConversationModel(conversationModel),
            draftData = getDraftData(conversationModel, drafts, mentionNameData, contactNameFormat),
            unreadState = getUnreadStateOrNull(conversationModel),
            isPinned = conversationModel.isPinTagged,
            isPrivate = privateConversationUIDs.contains(conversationModel.uid),
            groupCall = getGroupCallState(groupModel, groupCalls),
            icon = getConversationIconOrNull(conversationModel),
            muteStatusIcon = getMuteStatusIconOrNull(conversationModel),
            latestMessageSenderName = getGroupMessageSenderNameOrNull(
                conversationModel = conversationModel,
                contactNameFormat = contactNameFormat,
                drafts = drafts,
            ),
            avatarIteration = avatarIterations[groupModel.identifier] ?: AvatarIteration.initial,
        )
    }

    private fun mapToDistributionListConversationUiModel(
        conversationModel: ConversationModel,
        privateConversationUIDs: Set<ConversationUID>,
        contactNameFormat: ContactNameFormat,
        drafts: Map<ConversationUID, MessageDraft>,
        mentionNameData: List<MentionNameData>,
    ): ConversationUiModel.DistributionListConversation? {
        val distributionListModel: DistributionListModel = conversationModel.distributionList ?: return null
        return ConversationUiModel.DistributionListConversation(
            conversationUID = conversationModel.uid,
            receiverIdentifier = distributionListModel.identifier,
            latestMessageData = getLatestMessageData(conversationModel, mentionNameData, contactNameFormat),
            receiverDisplayName = getReceiverDisplayNameOrNull(
                receiverModel = conversationModel.receiverModel,
                contactNameFormat = contactNameFormat,
            ),
            conversationName = conversationModel.messageReceiver.getDisplayName(contactNameFormat),
            conversationNameStyle = ConversationNameStyle.forConversationModel(conversationModel),
            draftData = getDraftData(conversationModel, drafts, mentionNameData, contactNameFormat),
            unreadState = getUnreadStateOrNull(conversationModel),
            isPinned = conversationModel.isPinTagged,
            isPrivate = privateConversationUIDs.contains(conversationModel.uid),
            icon = getConversationIconOrNull(conversationModel),
            muteStatusIcon = getMuteStatusIconOrNull(conversationModel),
            avatarIteration = AvatarIteration.initial,
        )
    }

    private fun getLatestMessageData(
        conversationModel: ConversationModel,
        mentionNameData: List<MentionNameData>,
        contactNameFormat: ContactNameFormat,
    ): ConversationUiModel.LatestMessageData? {
        val messageModel = conversationModel.latestMessage ?: return null
        val messageTypeRequired = messageModel.type ?: return null

        @Suppress("DEPRECATION")
        val messageContentThatCouldContainMentions: String =
            when (messageModel.type) {
                MessageType.TEXT -> QuoteUtil.getMessageBody(
                    messageModel.type,
                    messageModel.body,
                    messageModel.caption,
                    messageModel.isOutbox,
                    false,
                    contactNameFormat,
                )
                MessageType.IMAGE -> messageModel.caption
                MessageType.VIDEO -> messageModel.caption
                MessageType.FILE -> messageModel.caption
                else -> null
            } ?: ""

        val mentionNames = ConversationTextAnalyzer.findResolvableMentionNames(
            input = messageContentThatCouldContainMentions,
            mentionNameData = mentionNameData,
            contactNameFormat = contactNameFormat,
        )
        return ConversationUiModel.LatestMessageData(
            type = messageTypeRequired,
            body = messageModel.body,
            caption = messageModel.caption,
            isOutbox = messageModel.isOutbox,
            isDeleted = messageModel.isDeleted,
            postedAt = messageModel.postedAt,
            modifiedAt = messageModel.modifiedAt,
            mentionNames = mentionNames,
        )
    }

    private fun getDraftData(
        conversationModel: ConversationModel,
        drafts: Map<ConversationUID, MessageDraft>,
        mentionNameData: List<MentionNameData>,
        contactNameFormat: ContactNameFormat,
    ): ConversationUiModel.DraftData? {
        return drafts[conversationModel.messageReceiver.uniqueIdString]?.text?.let { draft ->
            val mentionNames: Map<Identity, ResolvableString> = ConversationTextAnalyzer.findResolvableMentionNames(
                input = draft,
                mentionNameData = mentionNameData,
                contactNameFormat = contactNameFormat,
            )
            ConversationUiModel.DraftData(
                draft = draft,
                mentionNames = mentionNames,
            )
        }
    }

    private fun getConversationIconOrNull(conversationModel: ConversationModel): IconInfo? =
        when {
            conversationModel.isContactConversation -> getContactConversationIconOrNull(conversationModel)
            conversationModel.isGroupConversation -> getGroupConversationIconOrNull(conversationModel)
            conversationModel.isDistributionListConversation -> IconInfo(
                res = R.drawable.ic_distribution_list_filled,
                contentDescription = R.string.distribution_list,
            )
            else -> null
        }

    /**
     * @throws IllegalArgumentException if the given [conversationModel] is not a contact conversation model
     */
    private fun getContactConversationIconOrNull(conversationModel: ConversationModel): IconInfo? {
        require(conversationModel.isContactConversation) {
            "Must be a contact conversation"
        }
        val latestMessageModel: AbstractMessageModel = conversationModel.latestMessage
            ?: return null
        if (latestMessageModel.type == MessageType.VOIP_STATUS) {
            // TODO(ANDR-4549): Correct the content description used for this icon
            return IconInfo(
                res = R.drawable.ic_phone_locked,
                contentDescription = R.string.state_sent,
            )
        }
        if (!latestMessageModel.isOutbox) {
            // TODO(ANDR-4549): Correct the content description used for this icon
            return IconInfo(
                res = R.drawable.ic_reply_filled,
                contentDescription = R.string.state_sent,
            )
        }

        if (!MessageUtil.showStatusIcon(latestMessageModel)) {
            return null
        }
        val messageState: MessageState = latestMessageModel.state
            ?: return null

        val stateBitmapUtil = StateBitmapUtil.getInstance()
            ?: return null

        @DrawableRes
        val stateIconRes: Int = stateBitmapUtil.getStateDrawable(messageState)
            ?: return null

        val stateIconContentDescriptionRes: Int? = stateBitmapUtil.getStateDescription(messageState)

        @ColorInt
        val tintOverride: Int? =
            if (messageState == MessageState.SENDFAILED || messageState == MessageState.FS_KEY_MISMATCH) {
                stateBitmapUtil.warningColor
            } else {
                null
            }

        return IconInfo(
            res = stateIconRes,
            contentDescription = stateIconContentDescriptionRes,
            tintOverride = tintOverride,
        )
    }

    /**
     * @throws IllegalArgumentException if the given [conversationModel] is not a group conversation model
     */
    private fun getGroupConversationIconOrNull(conversationModel: ConversationModel): IconInfo? {
        check(conversationModel.isGroupConversation) {
            "Must be a group conversation"
        }
        val groupModel = conversationModel.groupModel
            ?: return null
        return if (groupModel.isNotesGroup() == true) {
            IconInfo(
                res = R.drawable.ic_spiral_bound_booklet_outline,
                contentDescription = R.string.notes,
            )
        } else {
            IconInfo(
                res = R.drawable.ic_group_filled,
                contentDescription = R.string.prefs_group_notifications,
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

    private fun getReceiverDisplayNameOrNull(receiverModel: ReceiverModel, contactNameFormat: ContactNameFormat): String? {
        return when (receiverModel) {
            is ContactModel -> NameUtil.getContactDisplayNameOrNickname(
                /* contactModel = */
                receiverModel,
                /* nicknameWithPrefix = */
                true,
                /* contactNameFormat = */
                contactNameFormat,
            )

            is GroupModelOld -> NameUtil.getGroupDisplayName(
                /* groupModel = */
                receiverModel,
                /* groupService = */
                groupService,
                /* contactNameFormat = */
                contactNameFormat,
            )

            is DistributionListModel -> NameUtil.getDistributionListDisplayName(
                /* distributionListModel = */
                receiverModel,
                /* distributionListService = */
                distributionListService,
                /* contactNameFormat = */
                contactNameFormat,
            )

            else -> null
        }
    }

    private fun getIsPrivate(conversationModel: ConversationModel): Boolean {
        return conversationCategoryService.isPrivateChat(
            uniqueIdString = conversationModel.messageReceiver.uniqueIdString,
        )
    }

    private fun getGroupCallState(groupModel: GroupModelOld, groupCalls: Set<GroupCallUiModel>): GroupCallUiModel? {
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

    private fun getGroupMessageSenderNameOrNull(
        conversationModel: ConversationModel,
        contactNameFormat: ContactNameFormat,
        drafts: Map<ConversationUID, MessageDraft>,
    ): ResolvableString? {
        val hasOwnDraft: Boolean = drafts.contains(conversationModel.messageReceiver.uniqueIdString)
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
            val contactModel: ContactModel? = contactService.getByIdentity(latestMessage.identity)
            NameUtil
                .getShortName(contactModel, contactNameFormat)
                ?.toResolvedString()
        }
    }
}
