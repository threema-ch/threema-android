package ch.threema.app.archive

import android.content.Context
import android.view.View
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.threema.app.R
import ch.threema.app.asynctasks.EmptyOrDeleteConversationsAsyncTask
import ch.threema.app.compose.conversation.models.ConversationListItemUiModel
import ch.threema.app.compose.conversation.models.ConversationUiModel
import ch.threema.app.drafts.DraftManager
import ch.threema.app.listeners.ConversationListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ContactService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.ConversationService
import ch.threema.app.services.DistributionListService
import ch.threema.app.services.GroupFlowDispatcher
import ch.threema.app.services.GroupService
import ch.threema.app.services.RingtoneService
import ch.threema.app.services.UserService
import ch.threema.app.usecases.WatchTypingIdentitiesUseCase
import ch.threema.app.usecases.availabilitystatus.WatchAllContactAvailabilityStatusesUseCase
import ch.threema.app.usecases.avatar.GetAndPrepareAvatarUseCase
import ch.threema.app.usecases.contacts.WatchAllMentionNamesUseCase
import ch.threema.app.usecases.contacts.WatchContactNameFormatSettingUseCase
import ch.threema.app.usecases.conversations.WatchArchivedConversationListItemsUseCase
import ch.threema.app.usecases.conversations.WatchArchivedConversationsUseCase
import ch.threema.app.usecases.conversations.WatchAvatarIterationsUseCase
import ch.threema.app.usecases.groups.WatchGroupCallsUseCase
import ch.threema.app.utils.ConfigUtils
import ch.threema.common.takeUnlessEmpty
import ch.threema.common.toggle
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.models.ContactReceiverIdentifier
import ch.threema.domain.models.DistributionListReceiverIdentifier
import ch.threema.domain.models.GroupReceiverIdentifier
import ch.threema.domain.models.ReceiverIdentifier
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.types.ConversationUID
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.DistributionListModel
import ch.threema.storage.models.ReceiverModel
import ch.threema.storage.models.group.GroupModelOld
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArchiveViewModel(
    private val appContext: Context,
    private val userService: UserService,
    private val conversationService: ConversationService,
    private val conversationCategoryService: ConversationCategoryService,
    private val contactService: ContactService,
    private val groupService: GroupService,
    private val distributionListService: DistributionListService,
    private val ringtoneService: RingtoneService,
    private val groupModelRepository: GroupModelRepository,
    private val draftManager: DraftManager,
    private val watchArchivedConversationsUseCase: WatchArchivedConversationsUseCase,
    private val watchGroupCallsUseCase: WatchGroupCallsUseCase,
    private val watchTypingIdentitiesUseCase: WatchTypingIdentitiesUseCase,
    private val groupFlowDispatcher: GroupFlowDispatcher,
    private val getAndPrepareAvatarUseCase: GetAndPrepareAvatarUseCase,
    preferenceService: PreferenceService,
    private val watchAvatarIterationsUseCase: WatchAvatarIterationsUseCase,
    private val watchContactNameFormatSettingUseCase: WatchContactNameFormatSettingUseCase,
    private val watchAllMentionNamesUseCase: WatchAllMentionNamesUseCase,
    private val watchAllContactAvailabilityStatusesUseCase: WatchAllContactAvailabilityStatusesUseCase,
) : ViewModel() {

    private val watchArchivedConversationListItemsUseCase = WatchArchivedConversationListItemsUseCase(
        watchArchivedConversationsUseCase = watchArchivedConversationsUseCase,
        watchGroupCallsUseCase = watchGroupCallsUseCase,
        watchTypingIdentitiesUseCase = watchTypingIdentitiesUseCase,
        conversationCategoryService = conversationCategoryService,
        contactService = contactService,
        groupService = groupService,
        distributionListService = distributionListService,
        ringtoneService = ringtoneService,
        watchAvatarIterationsUseCase = watchAvatarIterationsUseCase,
        watchContactNameFormatSettingUseCase = watchContactNameFormatSettingUseCase,
        watchAllMentionNamesUseCase = watchAllMentionNamesUseCase,
        watchAllContactAvailabilityStatusesUseCase = watchAllContactAvailabilityStatusesUseCase,
        draftManager = draftManager,
    )

    private val conversationUiModels: Flow<List<ConversationUiModel>> = watchArchivedConversationListItemsUseCase.call()
    private val arePrivateChatsHidden: Flow<Boolean> = preferenceService.watchArePrivateChatsHidden()
    private val selectedConversationUIDs = MutableStateFlow<Set<ConversationUID>>(emptySet())
    private val filterQuery = MutableStateFlow<String?>(null)

    val conversationListItemUiModels: StateFlow<List<ConversationListItemUiModel>> = combine(
        flow = conversationUiModels,
        flow2 = selectedConversationUIDs,
        flow3 = filterQuery,
        flow4 = arePrivateChatsHidden,
    ) { conversationUiModels, selectedConversationUIDs, filterQuery, arePrivateChatsHidden ->
        conversationUiModels
            .filter { conversationUiModel ->
                !conversationUiModel.isPrivate || !arePrivateChatsHidden
            }
            .filter { conversationModel ->
                conversationModel.matchesFilterQuery(filterQuery)
            }
            .map { conversationUiModel ->
                ConversationListItemUiModel(
                    model = conversationUiModel,
                    isChecked = selectedConversationUIDs.contains(conversationUiModel.conversationUID),
                    isHighlighted = false,
                )
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = emptyList(),
    )

    val contactNameFormat: StateFlow<ContactNameFormat> = watchContactNameFormatSettingUseCase
        .call()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = ContactNameFormat.DEFAULT,
        )

    val selectedCount: Int
        get() = selectedConversationUIDs.value.size

    private val currentlySelectedConversations: List<ConversationUiModel>
        get() = conversationListItemUiModels
            .value
            .filter(ConversationListItemUiModel::isChecked)
            .map(ConversationListItemUiModel::model)

    private val _events: MutableSharedFlow<ArchiveScreenEvent> = MutableSharedFlow()
    val events = _events.asSharedFlow()

    /**
     *  @return True if at least one conversation is selected **after** this toggle action.
     */
    fun toggleSelected(conversationUID: ConversationUID): Boolean {
        selectedConversationUIDs.update { it.toggle(conversationUID) }
        return selectedConversationUIDs.value.isNotEmpty()
    }

    /**
     *  @return True if at least one conversation is selected
     */
    fun selectAll(): Boolean {
        selectedConversationUIDs.value = conversationListItemUiModels
            .value
            .map { conversationListItemUiModel -> conversationListItemUiModel.model.conversationUID }
            .toSet()
        return selectedConversationUIDs.value.isNotEmpty()
    }

    fun deselectAll() {
        selectedConversationUIDs.value = emptySet()
    }

    fun setFilterQuery(query: String?) {
        filterQuery.update {
            query?.trim()?.takeUnlessEmpty()
        }
    }

    fun onClickedConversation(conversationUUID: ConversationUID) {
        viewModelScope.launch {
            val conversationListItemUiModel: ConversationListItemUiModel = conversationListItemUiModels.value
                .firstOrNull { conversationListItemUiModel ->
                    conversationListItemUiModel.model.conversationUID == conversationUUID
                } ?: return@launch
            when (val receiverIdentifier = conversationListItemUiModel.model.receiverIdentifier) {
                is ContactReceiverIdentifier -> {
                    _events.emit(ArchiveScreenEvent.OpenOneToOneConversation(receiverIdentifier.identity))
                }

                is GroupReceiverIdentifier -> {
                    _events.emit(ArchiveScreenEvent.OpenGroupConversation(receiverIdentifier.groupDatabaseId))
                }

                is DistributionListReceiverIdentifier -> {
                    _events.emit(ArchiveScreenEvent.OpenDistributionListConversation(receiverIdentifier.id))
                }
            }
        }
    }

    fun unarchiveAllSelected() {
        val selectedConversationUiModels: List<ConversationUiModel> = currentlySelectedConversations
        viewModelScope.launch {
            if (selectedConversationUiModels.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    val receiverIdentifiers: List<ReceiverIdentifier> = selectedConversationUiModels.map(ConversationUiModel::receiverIdentifier)
                    conversationService.unarchiveByReceiverIdentifiers(receiverIdentifiers, TriggerSource.LOCAL)
                }
            }
            _events.emit(ArchiveScreenEvent.ConversationsUnarchived)
        }
    }

    fun onClickDeleteAllSelected() {
        viewModelScope.launch {
            val selectedConversationUiModels: List<ConversationUiModel> = currentlySelectedConversations
            if (selectedConversationUiModels.isEmpty()) {
                _events.emit(ArchiveScreenEvent.ConversationsDeleted)
                return@launch
            }
            var title: String = appContext.resources.getString(
                when {
                    selectedConversationUiModels.size > 1 -> R.string.really_delete_multiple_threads
                    else -> R.string.really_delete_thread
                },
            )
            var message: String = ConfigUtils.getSafeQuantityString(
                appContext,
                R.plurals.really_delete_thread_message,
                selectedConversationUiModels.size,
                selectedConversationUiModels.size,
            ) + " " + appContext.getString(R.string.messages_cannot_be_recovered)

            val singleGroupReceiverIdentifier: GroupReceiverIdentifier? =
                (selectedConversationUiModels.singleOrNull() as? ConversationUiModel.GroupConversation)?.receiverIdentifier
            if (singleGroupReceiverIdentifier != null) {
                // If only one conversation is deleted, and it's a group, show a more specific message.
                val groupModel: GroupModelOld? = groupService.getById(singleGroupReceiverIdentifier.groupDatabaseId)
                if (groupModel != null && groupService.isGroupMember(groupModel)) {
                    title = appContext.getString((R.string.action_delete_group))
                    message = if (groupService.isGroupCreator(groupModel)) {
                        appContext.getString(R.string.delete_my_group_message)
                    } else {
                        appContext.getString(R.string.delete_group_message)
                    }
                }
            } else if (selectedConversationUiModels.any { it is ConversationUiModel.GroupConversation }) {
                // If multiple conversations are deleted and at least one of them is a group,
                // show a hint about the leave/dissolve behavior.
                message += " " + appContext.getString(R.string.groups_left_or_dissolved)
            }

            _events.emit(
                ArchiveScreenEvent.ShowReallyDeleteConversationsDialog(
                    ReallyDeleteConversationsDialogContent(
                        title = title,
                        message = message,
                    ),
                ),
            )
        }
    }

    // TODO(ANDR-4087): Remove the referenced view from these layers
    fun confirmDeleteCurrentlySelected(
        supportFragmentManager: FragmentManager,
        snackbarFeedbackView: View?,
    ) {
        val selectedReceiverIdentifiers = currentlySelectedConversations.map(ConversationUiModel::receiverIdentifier)

        val contactModels: List<ContactModel> = selectedReceiverIdentifiers
            .filterIsInstance<ContactReceiverIdentifier>()
            .map(ContactReceiverIdentifier::identity)
            .let { identities ->
                contactService.getByIdentities(identities)
            }

        val groupModels: List<GroupModelOld> = selectedReceiverIdentifiers
            .filterIsInstance<GroupReceiverIdentifier>()
            .map { groupReceiverIdentifier ->
                // TODO(ANDR-4354): Remove this cast to Int
                groupReceiverIdentifier.groupDatabaseId.toInt()
            }
            .let { groupDatabaseIds ->
                groupService.getByIds(groupDatabaseIds)
            }

        val distributionListModels: List<DistributionListModel> = selectedReceiverIdentifiers
            .filterIsInstance<DistributionListReceiverIdentifier>()
            .map(DistributionListReceiverIdentifier::id)
            .let { distributionListIds: List<Long> ->
                distributionListService.getByIds(distributionListIds)
            }

        val receiverModels: List<ReceiverModel> = contactModels + groupModels + distributionListModels
        val messageReceivers: List<MessageReceiver<*>> = receiverModels.mapNotNull { receiverModel ->
            when (receiverModel) {
                is ContactModel -> contactService.createReceiver(receiverModel)
                is GroupModelOld -> groupService.createReceiver(receiverModel)
                is DistributionListModel -> distributionListService.createReceiver(receiverModel)
                else -> null
            }
        }
        EmptyOrDeleteConversationsAsyncTask(
            EmptyOrDeleteConversationsAsyncTask.Mode.DELETE,
            messageReceivers.toTypedArray(),
            conversationService,
            distributionListService,
            groupModelRepository,
            groupFlowDispatcher,
            userService.identity!!,
            supportFragmentManager,
            snackbarFeedbackView,
        ) {
            viewModelScope.launch {
                // TODO(ANDR-4175): Remove this listener call when the conversation cache also holds archived conversations and calls ConversationListener.onRemoved correctly
                ListenerManager.conversationListeners.handle { listener: ConversationListener ->
                    listener.onModifiedAll()
                }
                _events.emit(ArchiveScreenEvent.ConversationsDeleted)
            }
        }.execute()
    }

    suspend fun provideAvatarBitmap(receiverIdentifier: ReceiverIdentifier) =
        getAndPrepareAvatarUseCase.call(receiverIdentifier)
}
