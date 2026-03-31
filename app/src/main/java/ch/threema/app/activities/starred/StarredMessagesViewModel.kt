package ch.threema.app.activities.starred

import ch.threema.android.ResolvableString
import ch.threema.android.ResourceIdString
import ch.threema.android.toResolvedString
import ch.threema.app.R
import ch.threema.app.activities.starred.models.ConversationParticipant
import ch.threema.app.activities.starred.models.StarredMessageListItemUiModel
import ch.threema.app.activities.starred.models.StarredMessageUiModel
import ch.threema.app.activities.starred.models.StarredMessagesViewState
import ch.threema.app.compose.common.immutables.ImmutableBitmap
import ch.threema.app.framework.BaseViewModel
import ch.threema.app.managers.ListenerManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ContactService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.MessageService
import ch.threema.app.services.ballot.BallotService
import ch.threema.app.stores.IdentityProvider
import ch.threema.app.usecases.GetStarredMessagesUseCase
import ch.threema.app.usecases.StarredMessageWithMentionInfo
import ch.threema.app.usecases.avatar.GetAndPrepareAvatarUseCase
import ch.threema.app.usecases.groups.GetGroupDisplayNameUseCase
import ch.threema.app.utils.ContactUtil
import ch.threema.app.utils.DispatcherProvider
import ch.threema.app.utils.GroupUtil
import ch.threema.common.takeUnlessBlank
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.data.datatypes.localGroupId
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.models.GroupReceiverIdentifier
import ch.threema.domain.models.ReceiverIdentifier
import ch.threema.domain.types.Identity
import ch.threema.domain.types.MessageUid
import ch.threema.domain.types.toIdentityOrNull
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.DisplayTag.DISPLAY_TAG_NONE
import ch.threema.storage.models.group.GroupMessageModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

class StarredMessagesViewModel(
    private val getStarredMessagesUseCase: GetStarredMessagesUseCase,
    private val preferenceService: PreferenceService,
    private val contactService: ContactService,
    private val contactModelRepository: ContactModelRepository,
    private val groupModelRepository: GroupModelRepository,
    private val messageService: MessageService,
    private val ballotService: BallotService,
    private val conversationCategoryService: ConversationCategoryService,
    private val dispatcherProvider: DispatcherProvider,
    private val getAndPrepareAvatarUseCase: GetAndPrepareAvatarUseCase,
    private val identityProvider: IdentityProvider,
    private val getGroupDisplayNameUseCase: GetGroupDisplayNameUseCase,
) : BaseViewModel<StarredMessagesViewState, StarredMessagesScreenEvent>() {

    @Volatile
    private var loadStarredMessagesJob: Job? = null

    override fun initialize() = runInitialization {
        StarredMessagesViewState(
            isLoading = false,
            query = null,
            sortOrder = preferenceService.getStarredMessagesSortOrder(),
            contactNameFormat = preferenceService.getContactNameFormat(),
            emojiStyle = preferenceService.getEmojiStyle(),
            listItems = emptyList(),
        )
    }

    override suspend fun onActive() {
        loadStarredMessages()
    }

    fun loadStarredMessages() {
        loadStarredMessagesJob?.cancel()
        loadStarredMessagesJob = runAction {
            updateViewState {
                copy(isLoading = true)
            }
            val previouslySelectedMessageId: Set<Int> = currentViewState
                .listItems
                .filter(StarredMessageListItemUiModel::isSelected)
                .map { starredMessageListItemUiModel -> starredMessageListItemUiModel.starredMessageUiModel.messageModel.id }
                .toSet()
            val contactNameFormat = preferenceService.getContactNameFormat()

            val ownIdentity = identityProvider.getIdentity()
            if (ownIdentity == null) {
                updateViewState {
                    copy(isLoading = false)
                }
                return@runAction
            }

            val starredMessages = getStarredMessagesUseCase.call(
                query = currentViewState.query,
                order = currentViewState.sortOrder,
            ).mapNotNull { starredMessageWithMentionInfo ->
                when (starredMessageWithMentionInfo.abstractMessageModel) {
                    is MessageModel -> mapToStarredContactMessageUiModel(
                        ownIdentity = ownIdentity,
                        starredMessageWithMentionInfo = starredMessageWithMentionInfo,
                    )
                    is GroupMessageModel -> mapToStarredGroupMessageUiModel(
                        ownIdentity = ownIdentity,
                        starredMessageWithMentionInfo = starredMessageWithMentionInfo,
                        contactNameFormat = contactNameFormat,
                    )
                    else -> null
                }
            }.map { starredMessageUiModel ->
                StarredMessageListItemUiModel(
                    starredMessageUiModel = starredMessageUiModel,
                    isSelected = starredMessageUiModel.messageModel.id in previouslySelectedMessageId,
                )
            }
            updateViewState {
                copy(
                    isLoading = false,
                    listItems = starredMessages,
                )
            }
        }
    }

    private fun mapToStarredContactMessageUiModel(
        ownIdentity: Identity,
        starredMessageWithMentionInfo: StarredMessageWithMentionInfo,
    ): StarredMessageUiModel.StarredContactMessage? {
        val abstractMessageModel = starredMessageWithMentionInfo.abstractMessageModel
        val messageUid: MessageUid = abstractMessageModel.uid
            ?: return null
        val messageIdentity = abstractMessageModel.identity
            ?.toIdentityOrNull()
            ?: return null
        val conversationParticipantSender = if (abstractMessageModel.isOutbox) {
            ConversationParticipant.Me(ownIdentity)
        } else {
            getConversationParticipantContact(identity = messageIdentity)
        }
        conversationParticipantSender ?: return null
        val conversationParticipantReceiver = if (abstractMessageModel.isOutbox) {
            getConversationParticipantContact(identity = messageIdentity)
        } else {
            ConversationParticipant.Me(ownIdentity)
        }
        conversationParticipantReceiver ?: return null
        val showWorkBadge = contactService.showBadge(messageIdentity.value)
        val isPrivate = conversationCategoryService.isPrivateChat(
            uniqueIdString = ContactUtil.getUniqueIdString(messageIdentity.value),
        )
        return StarredMessageUiModel.StarredContactMessage(
            uid = messageUid,
            messageModel = abstractMessageModel,
            messageContent = getMessageContent(abstractMessageModel),
            mentionNames = starredMessageWithMentionInfo.mentionedNames,
            sender = conversationParticipantSender,
            receiver = conversationParticipantReceiver,
            showWorkBadge = showWorkBadge,
            isPrivate = isPrivate,
        )
    }

    private fun mapToStarredGroupMessageUiModel(
        ownIdentity: Identity,
        starredMessageWithMentionInfo: StarredMessageWithMentionInfo,
        contactNameFormat: ContactNameFormat,
    ): StarredMessageUiModel.StarredGroupMessage? {
        val groupMessageModel = starredMessageWithMentionInfo.abstractMessageModel as? GroupMessageModel
            ?: return null
        val groupModel = groupModelRepository.getByLocalGroupDbId(
            localGroupDbId = groupMessageModel.groupId.toLong(),
        ) ?: return null
        val messageUid: MessageUid = groupMessageModel.uid
            ?: return null
        val conversationParticipantSender = if (groupMessageModel.isOutbox) {
            ConversationParticipant.Me(ownIdentity)
        } else {
            val messageSenderIdentity = groupMessageModel.identity
                ?.toIdentityOrNull()
                ?: return null
            getConversationParticipantContact(messageSenderIdentity)
        }
        conversationParticipantSender ?: return null
        val isPrivate = conversationCategoryService.isPrivateChat(
            uniqueIdString = GroupUtil.getUniqueIdString(groupModel.localGroupId.id.toLong()),
        )
        return StarredMessageUiModel.StarredGroupMessage(
            uid = messageUid,
            messageModel = groupMessageModel,
            messageContent = getMessageContent(groupMessageModel),
            mentionNames = starredMessageWithMentionInfo.mentionedNames,
            sender = conversationParticipantSender,
            groupIdentifier = GroupReceiverIdentifier(
                groupDatabaseId = groupModel.localGroupId.id.toLong(),
                groupCreatorIdentity = groupModel.groupIdentity.creatorIdentity,
                groupApiId = groupModel.groupIdentity.groupId,
            ),
            groupDisplayName = getGroupDisplayNameUseCase.call(
                groupModel = groupModel,
                contactNameFormat = contactNameFormat,
            ),
            isPrivate = isPrivate,
        )
    }

    private fun getConversationParticipantContact(identity: Identity): ConversationParticipant? {
        val contactModelData = contactModelRepository.getByIdentity(identity)?.data
            ?: return null
        return ConversationParticipant.Contact(
            identity = identity,
            firstname = contactModelData.firstName,
            lastname = contactModelData.lastName,
        )
    }

    /**
     *  Same implementation as [ch.threema.app.globalsearch.GlobalSearchAdapter.setSnippetToTextView]
     */
    private fun getMessageContent(abstractMessageModel: AbstractMessageModel): ResolvableString? =
        when (abstractMessageModel.type) {
            MessageType.TEXT -> abstractMessageModel.body?.takeUnlessBlank()?.toResolvedString()
            MessageType.FILE, MessageType.IMAGE -> abstractMessageModel.caption?.takeUnlessBlank()?.toResolvedString()
            MessageType.BALLOT -> getBallotMessageContent(abstractMessageModel)
            MessageType.LOCATION -> getLocationMessageContent(abstractMessageModel)
            else -> {
                // Deprecated audio and video Messages don't have text or captions
                null
            }
        }

    private fun getBallotMessageContent(abstractMessageModel: AbstractMessageModel): ResolvableString =
        abstractMessageModel
            .body
            ?.takeUnlessBlank()
            ?.let { ballotService.get(abstractMessageModel.ballotData.ballotId) }
            ?.name
            ?.toResolvedString()
            ?: ResourceIdString(R.string.attach_ballot)

    private fun getLocationMessageContent(abstractMessageModel: AbstractMessageModel): ResolvableString? =
        abstractMessageModel
            .locationData
            .poi
            ?.getSnippetForSearchOrNull()
            ?.toResolvedString()

    fun removeStarsFromSelectedMessages() {
        runAction {
            val selectedMessageModels: List<AbstractMessageModel> = currentViewState
                .listItems
                .filter(StarredMessageListItemUiModel::isSelected)
                .map { starredMessageListItemUiModel ->
                    starredMessageListItemUiModel.starredMessageUiModel.messageModel
                }

            withContext(dispatcherProvider.io) {
                selectedMessageModels.forEach { abstractMessageModel ->
                    abstractMessageModel.displayTags = DISPLAY_TAG_NONE
                    messageService.save(abstractMessageModel)
                }
            }

            ListenerManager.messageListeners.handle { listener ->
                listener.onModified(selectedMessageModels)
            }

            loadStarredMessages()
            emitEvent(StarredMessagesScreenEvent.SelectedStarsRemoved)
        }
    }

    fun removeAllStars() {
        runAction {
            withContext(dispatcherProvider.io) {
                messageService.unstarAllMessages()
            }
            loadStarredMessages()
        }
    }

    fun onQueryChanged(query: String?) {
        runAction {
            updateViewState {
                copy(query = query)
            }
            loadStarredMessages()
        }
    }

    fun onSortOrderChanged(@PreferenceService.StarredMessagesSortOrder sortOrder: Int) {
        runAction {
            preferenceService.setStarredMessagesSortOrder(sortOrder)
            updateViewState {
                copy(sortOrder = sortOrder)
            }
            loadStarredMessages()
        }
    }

    fun toggleListItemSelected(messageId: Int, initiatedByLongClick: Boolean) {
        runAction {
            val updatedListItems = currentViewState.listItems.map { starredMessageListItemUiModel ->
                if (starredMessageListItemUiModel.starredMessageUiModel.messageModel.id == messageId) {
                    starredMessageListItemUiModel.copy(
                        isSelected = !starredMessageListItemUiModel.isSelected,
                    )
                } else {
                    starredMessageListItemUiModel
                }
            }
            updateViewState {
                copy(listItems = updatedListItems)
            }
            emitEvent(
                StarredMessagesScreenEvent.ListItemSelectedToggled(
                    updatedSelectedListItemsCount = updatedListItems.count(StarredMessageListItemUiModel::isSelected),
                    initiatedByLongClick = initiatedByLongClick,
                ),
            )
        }
    }

    fun unselectAllMessageItems() {
        runAction {
            val updatedListItems = currentViewState.listItems.map { starredMessageListItemUiModel ->
                if (starredMessageListItemUiModel.isSelected) {
                    starredMessageListItemUiModel.copy(
                        isSelected = false,
                    )
                } else {
                    starredMessageListItemUiModel
                }
            }
            updateViewState {
                copy(listItems = updatedListItems)
            }
        }
    }

    val selectedMessageItemsCount: Int
        get() = viewState.value?.listItems?.count(StarredMessageListItemUiModel::isSelected) ?: 0

    suspend fun provideAvatarBitmap(receiverIdentifier: ReceiverIdentifier): ImmutableBitmap? =
        getAndPrepareAvatarUseCase.call(receiverIdentifier)
}
