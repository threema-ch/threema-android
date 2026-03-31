package ch.threema.app.fragments.conversations

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.viewModelScope
import ch.threema.app.BuildFlavor
import ch.threema.app.asynctasks.AddOrUpdateSupportContactBackgroundTask
import ch.threema.app.asynctasks.ContactAvailable
import ch.threema.app.asynctasks.ContactResult
import ch.threema.app.asynctasks.EmptyOrDeleteConversationsAsyncTask
import ch.threema.app.asynctasks.Failed
import ch.threema.app.asynctasks.GenericFailure
import ch.threema.app.compose.conversation.models.ConversationListItemUiModel
import ch.threema.app.compose.conversation.models.ConversationUiModel
import ch.threema.app.framework.BaseViewModel
import ch.threema.app.listeners.ContactListener
import ch.threema.app.listeners.DistributionListListener
import ch.threema.app.listeners.GroupListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.DistributionListMessageReceiver
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.ConversationService
import ch.threema.app.services.DistributionListService
import ch.threema.app.services.GroupFlowDispatcher
import ch.threema.app.services.MessageService
import ch.threema.app.services.notification.NotificationService
import ch.threema.app.stores.IdentityProvider
import ch.threema.app.usecases.avatar.GetAndPrepareAvatarUseCase
import ch.threema.app.usecases.contacts.WatchContactNameFormatSettingUseCase
import ch.threema.app.usecases.conversations.WatchOpenedConversationUseCase
import ch.threema.app.usecases.conversations.WatchUnarchivedConversationListItemsUseCase
import ch.threema.app.utils.DispatcherProvider
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.stateFlowOf
import ch.threema.common.takeUnlessEmpty
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.data.models.GroupIdentity
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.models.ContactReceiverIdentifier
import ch.threema.domain.models.ReceiverIdentifier
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.types.ConversationUID
import ch.threema.storage.models.ConversationModel
import ch.threema.storage.models.ConversationTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.Logger

private val logger: Logger = getThreemaLogger("ConversationsViewModel")

/**
 *  @param initiallyOpenedConversationUid If the screen that uses this viewmodel is used in a multi-pane-mode, this value can be passed. The
 *  currently opened conversation is shown as a highlighted list item. It will only be used if [isMultiPaneEnabled] is set to `true`.
 */
class ConversationsViewModel(
    private val dispatcherProvider: DispatcherProvider,
    private val conversationService: ConversationService,
    private val conversationCategoryService: ConversationCategoryService,
    private val distributionListService: DistributionListService,
    private val preferenceService: PreferenceService,
    private val messageService: MessageService,
    private val notificationService: NotificationService,
    private val apiConnector: APIConnector,
    private val appRestrictions: AppRestrictions,
    private val contactModelRepository: ContactModelRepository,
    private val groupModelRepository: GroupModelRepository,
    private val groupFlowDispatcher: GroupFlowDispatcher,
    private val identityProvider: IdentityProvider,
    private val getAndPrepareAvatarUseCase: GetAndPrepareAvatarUseCase,
    private val watchConversationListItemsUseCase: WatchUnarchivedConversationListItemsUseCase,
    private val watchContactNameFormatSettingUseCase: WatchContactNameFormatSettingUseCase,
    watchOpenedConversationUseCase: WatchOpenedConversationUseCase,
    isMultiPaneEnabled: Boolean,
    initiallyOpenedConversationUid: ConversationUID?,
) : BaseViewModel<ConversationsViewState, ConversationsViewEvent>() {

    private lateinit var watchConversationListItemsFlow: Flow<Result<List<ConversationUiModel>>>

    private val highlightedConversationUidFlow: StateFlow<ConversationUID?> =
        if (isMultiPaneEnabled) {
            watchOpenedConversationUseCase.call()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly,
                    initialValue = initiallyOpenedConversationUid,
                )
        } else {
            stateFlowOf(null)
        }

    // We have to remember the unfiltered models
    @Volatile
    private var latestConversationUiModelsResult: Result<List<ConversationUiModel>>? = null

    private var addOrUpdateSupportContactJob: Job? = null

    override fun initialize() = runInitialization {
        watchConversationListItemsFlow = watchConversationListItemsUseCase
            .call()
            .map { conversationUiModels ->
                Result.success(conversationUiModels)
            }
            .catch { throwable ->
                logger.error("Failed to load conversation ui models", throwable)
                emit(Result.failure(throwable))
            }
        produceOngoingState()
        produceInitialState()
    }

    private suspend fun produceInitialState(): ConversationsViewState {
        val hidePrivateConversations: Boolean = preferenceService.arePrivateChatsHidden()
        val initialItemsState: ItemsState = produceInitialItemsState(hidePrivateConversations)
        val archivedConversationsCount: Long = conversationService.countArchived(
            /* searchQuery = */
            null,
            /* excludePrivateConversations = */
            hidePrivateConversations,
        )
        val contactNameFormatOrDefault =
            runCatching {
                preferenceService.getContactNameFormat()
            }.getOrElse { throwable ->
                logger.error("Failed to read contact name format setting from preferences", throwable)
                ContactNameFormat.DEFAULT
            }
        return ConversationsViewState(
            itemsState = initialItemsState,
            filterQuery = null,
            hidePrivateConversations = hidePrivateConversations,
            hasPrivateConversations = conversationCategoryService.hasPrivateChats(),
            archivedConversationsCount = archivedConversationsCount,
            contactNameFormat = contactNameFormatOrDefault,
        )
    }

    private suspend fun produceInitialItemsState(hidePrivateConversations: Boolean): ItemsState {
        val conversationUiModelsResult: Result<List<ConversationUiModel>> = watchConversationListItemsFlow.first()
        latestConversationUiModelsResult = conversationUiModelsResult
        return conversationUiModelsResult
            .fold(
                onSuccess = { conversationUiModels ->
                    ItemsState.Loaded(
                        items = filterConversationUiModels(
                            conversationUiModels = conversationUiModels,
                            hidePrivateConversations = hidePrivateConversations,
                            filterQuery = null,
                        ).map(::ConversationListItemUiModel),
                    )
                },
                onFailure = {
                    ItemsState.Failed
                },
            )
    }

    private fun produceOngoingState() = runAction {
        combine(
            flow = watchConversationListItemsFlow,
            flow2 = preferenceService.watchArePrivateChatsHidden(),
            flow3 = highlightedConversationUidFlow,
            flow4 = watchContactNameFormatSettingUseCase.call(),
        ) { conversationUiModelsResult, hidePrivateConversations, openedConversationUID, contactNameFormat ->
            latestConversationUiModelsResult = conversationUiModelsResult
            val updatedItemsState = conversationUiModelsResult
                .fold(
                    onSuccess = { conversationUiModels ->
                        ItemsState.Loaded(
                            items = filterConversationUiModels(
                                conversationUiModels = conversationUiModels,
                                hidePrivateConversations = hidePrivateConversations,
                                filterQuery = currentViewState.filterQuery,
                            ).map { conversationUiModel ->
                                ConversationListItemUiModel(
                                    model = conversationUiModel,
                                    isHighlighted = conversationUiModel.conversationUID == openedConversationUID,
                                )
                            },
                        )
                    },
                    onFailure = {
                        ItemsState.Failed
                    },
                )
            val archivedConversationsCount: Long = conversationService.countArchived(
                /* searchQuery = */
                currentViewState.filterQuery,
                /* excludePrivateConversations = */
                hidePrivateConversations,
            )
            updateViewState {
                copy(
                    itemsState = updatedItemsState,
                    hidePrivateConversations = hidePrivateConversations,
                    hasPrivateConversations = conversationCategoryService.hasPrivateChats(),
                    archivedConversationsCount = archivedConversationsCount,
                    contactNameFormat = contactNameFormat,
                )
            }
        }.launchIn(viewModelScope)
    }

    fun onLongClickConversationItem(conversationUiModel: ConversationUiModel) = runAction {
        val conversationModel: ConversationModel? = conversationService.get(conversationUiModel.receiverIdentifier)
        if (conversationModel != null) {
            emitEvent(ConversationsViewEvent.OpenConversationActionDialog(conversationModel))
        } else {
            logger.warn(
                "Could not show conversation action dialog because conversation model is missing for receiver {}",
                conversationUiModel.receiverIdentifier,
            )
            emitEvent(ConversationsViewEvent.InternalError)
        }
    }

    fun onFilterQueryChange(filterQuery: String?) = runAction {
        val latestConversationUiModels: List<ConversationUiModel> = latestConversationUiModelsResult?.getOrNull()
            ?: endAction()
        val didFilterQueryChangeEffectively = didFilterQueryChangeEffectively(
            currentQuery = currentViewState.filterQuery,
            updatedQuery = filterQuery,
        )
        if (!didFilterQueryChangeEffectively) {
            // Skip unnecessary filtering and view state update
            endAction()
        }
        val updatedItems: List<ConversationListItemUiModel> =
            filterConversationUiModels(
                conversationUiModels = latestConversationUiModels,
                hidePrivateConversations = currentViewState.hidePrivateConversations,
                filterQuery = filterQuery,
            ).map { conversationUiModel ->
                ConversationListItemUiModel(
                    model = conversationUiModel,
                    isHighlighted = conversationUiModel.conversationUID == highlightedConversationUidFlow.value,
                )
            }
        val archivedConversationsCount: Long = conversationService.countArchived(
            /* searchQuery = */
            filterQuery,
            /* excludePrivateConversations = */
            currentViewState.hidePrivateConversations,
        )
        updateViewState {
            copy(
                itemsState = ItemsState.Loaded(
                    items = updatedItems,
                ),
                filterQuery = filterQuery,
                archivedConversationsCount = archivedConversationsCount,
            )
        }
    }

    /**
     *  Determine if the change to the query value **could** have an effective impact on the filtered result list. A query change from `null` to an
     *  `empty string`, or vice versa, will have no effect on the filtering logic.
     *
     *  @see ConversationUiModel.matchesFilterQuery
     */
    private fun didFilterQueryChangeEffectively(currentQuery: String?, updatedQuery: String?): Boolean {
        val current = currentQuery?.takeUnlessEmpty()
        val updated = updatedQuery?.takeUnlessEmpty()
        return current != updated
    }

    /**
     *  Takes a list of all conversation models and returns a list of conversation models that should be effectively presented to the user.
     */
    private fun filterConversationUiModels(
        conversationUiModels: List<ConversationUiModel>,
        hidePrivateConversations: Boolean,
        filterQuery: String?,
    ): List<ConversationUiModel> =
        conversationUiModels
            .filter { conversationUiModel ->
                !conversationUiModel.isPrivate || !hidePrivateConversations
            }
            .filter { conversationUiModel ->
                conversationUiModel.matchesFilterQuery(filterQuery)
            }

    /**
     *  @param isAndroidSystemLockConfigured Whether the system has a screen lock defined (not Threema)
     */
    fun onViewResumed(isAndroidSystemLockConfigured: Boolean) = runAction {
        // Show private hidden chats if the lock machinism was SYSTEM and it was removed by the user
        if (preferenceService.getLockMechanism() == PreferenceService.LOCKING_MECH_SYSTEM && !isAndroidSystemLockConfigured) {
            emitEvent(ConversationsViewEvent.OnSystemLockWasRemoved)
            with(preferenceService) {
                setLockMechanism(PreferenceService.LOCKING_MECH_NONE)
                setAppLockEnabled(false)
                setArePrivateChatsHidden(false)
            }
            emitEvent(ConversationsViewEvent.UpdateWidgets)
            firePrivateReceiverUpdate()
        }
    }

    fun onClickMarkConversationAsPrivate(conversationModel: ConversationModel) = runAction {
        if (preferenceService.getLockMechanism() == PreferenceService.LOCKING_MECH_NONE) {
            emitEvent(
                event = ConversationsViewEvent.LockingMechanismRequiredToUpdatePrivateConversationMark(
                    conversationModel = conversationModel,
                    targetValueIsMarkedAsPrivate = true,
                ),
            )
            endAction()
        }

        if (conversationCategoryService.isPrivateChat(conversationModel.messageReceiver.getUniqueIdString())) {
            logger.warn("Could not mark the conversation as private because it is already private")
            emitEvent(ConversationsViewEvent.InternalError)
            endAction()
        }

        emitEvent(ConversationsViewEvent.ConfirmationRequiredToMarkConversationAsPrivate(conversationModel))
    }

    fun onLockingMechanismConfiguredToMarkConversationAsPrivate(conversationModel: ConversationModel) {
        markConversationAsPrivateEffectively(conversationModel)
    }

    fun onClickConfirmMarkConversationAsPrivate(conversationModel: ConversationModel) {
        markConversationAsPrivateEffectively(conversationModel)
    }

    private fun markConversationAsPrivateEffectively(conversationModel: ConversationModel) = runAction {
        if (preferenceService.getLockMechanism() == PreferenceService.LOCKING_MECH_NONE) {
            logger.warn("Can not mark a conversation as private without a configured locking mechanism")
            emitEvent(ConversationsViewEvent.InternalError)
            endAction()
        }
        val messageReceiver: MessageReceiver<*> = conversationModel.messageReceiver
        val success = conversationCategoryService.markAsPrivate(messageReceiver)
        if (success) {
            emitEvent(ConversationsViewEvent.ConversationMarkAsPrivateSuccess)
            fireReceiverUpdate(messageReceiver)
            if (preferenceService.arePrivateChatsHidden()) {
                firePrivateReceiverUpdate()
            }
        } else {
            logger.warn("Could not effectively mark the conversation as private because it is already private")
            emitEvent(ConversationsViewEvent.InternalError)
        }
    }

    fun onClickUnmarkConversationAsPrivate(conversationModel: ConversationModel) = runAction {
        if (preferenceService.getLockMechanism() == PreferenceService.LOCKING_MECH_NONE) {
            emitEvent(
                event = ConversationsViewEvent.LockingMechanismRequiredToUpdatePrivateConversationMark(
                    conversationModel = conversationModel,
                    targetValueIsMarkedAsPrivate = false,
                ),
            )
            endAction()
        }

        if (!conversationCategoryService.isPrivateChat(conversationModel.messageReceiver.getUniqueIdString())) {
            logger.warn("Could not unmark the conversation as private because it is not private")
            emitEvent(ConversationsViewEvent.InternalError)
            endAction()
        }

        emitEvent(ConversationsViewEvent.UnlockRequiredToUnmarkConversationAsPrivate(conversationModel))
    }

    fun onLockingMechanismConfiguredToUnmarkConversationAsPrivate(conversationModel: ConversationModel) {
        unmarkConversationAsPrivateEffectively(conversationModel)
    }

    fun onUnlockSuccessToUnmarkConversationAsPrivate(conversationModel: ConversationModel) {
        unmarkConversationAsPrivateEffectively(conversationModel)
    }

    private fun unmarkConversationAsPrivateEffectively(conversationModel: ConversationModel) = runAction {
        if (preferenceService.getLockMechanism() == PreferenceService.LOCKING_MECH_NONE) {
            endAction()
        }
        val success = conversationCategoryService.removePrivateMark(conversationModel.messageReceiver)
        if (success) {
            emitEvent(ConversationsViewEvent.ConversationUnmarkAsPrivateSuccess)
            fireReceiverUpdate(conversationModel.messageReceiver)
        } else {
            logger.warn("Could not effectively unmark the conversation as private because it is not private")
            emitEvent(ConversationsViewEvent.InternalError)
        }
    }

    fun onClickHideOrShowPrivateConversations() = runAction {
        val arePrivateChatsCurrentlyHidden = preferenceService.arePrivateChatsHidden()

        val lockingMechanismExists = preferenceService.getLockMechanism() != PreferenceService.LOCKING_MECH_NONE

        // This state can only be reached by setting the SYSTEM locking mechanism in Threema and then removing it in system settings
        if (!arePrivateChatsCurrentlyHidden && !lockingMechanismExists) {
            emitEvent(ConversationsViewEvent.LockingMechanismRequiredToHidePrivateConversations)
            endAction()
        }

        if (arePrivateChatsCurrentlyHidden) {
            emitEvent(ConversationsViewEvent.UnlockRequiredToShowPrivateConversations)
            endAction()
        }

        preferenceService.setArePrivateChatsHidden(true)
        emitEvent(ConversationsViewEvent.UpdateWidgets)
        firePrivateReceiverUpdate()
    }

    fun onLockingMechanismConfiguredToHidePrivateConversations() = runAction {
        if (preferenceService.getLockMechanism() != PreferenceService.LOCKING_MECH_NONE) {
            preferenceService.setArePrivateChatsHidden(true)
            emitEvent(ConversationsViewEvent.UpdateWidgets)
            firePrivateReceiverUpdate()
        }
    }

    fun onUnlockSuccessToShowPrivateConversations() = runAction {
        if (!preferenceService.arePrivateChatsHidden()) {
            // The setting changed in the meantime, nothing to do
            endAction()
        }
        preferenceService.setArePrivateChatsHidden(false)
        emitEvent(ConversationsViewEvent.UpdateWidgets)
        firePrivateReceiverUpdate()
    }

    fun onSwipedListItemPin(conversationUiModel: ConversationUiModel) = runAction {
        val conversationModel: ConversationModel? = conversationService.get(conversationUiModel.receiverIdentifier)
        if (conversationModel != null) {
            conversationService.togglePinned(conversationModel, TriggerSource.LOCAL)
        } else {
            logger.warn("Could not toggle pinned state because conversation model is missing for receiver {}", conversationUiModel.receiverIdentifier)
            emitEvent(ConversationsViewEvent.InternalError)
        }
    }

    fun onSwipedListItemArchive(conversationUiModel: ConversationUiModel) {
        archiveConversation(
            receiverIdentifier = conversationUiModel.receiverIdentifier,
        )
    }

    fun onClickedArchiveConversation(conversationModel: ConversationModel) {
        archiveConversation(
            receiverIdentifier = conversationModel.receiverModel.identifier,
        )
    }

    private fun archiveConversation(receiverIdentifier: ReceiverIdentifier) = runAction {
        val conversationModel: ConversationModel? = conversationService.get(receiverIdentifier)
        if (conversationModel != null) {
            conversationService.archive(conversationModel, TriggerSource.LOCAL)
            emitEvent(ConversationsViewEvent.ConversationArchived(conversationModel))
        } else {
            logger.warn("Could not archive conversation because conversation model is missing for receiver {}", receiverIdentifier)
            emitEvent(ConversationsViewEvent.InternalError)
        }
    }

    fun onClickedMarkConversationAsRead(conversationModel: ConversationModel) = runAction {
        conversationService.untag(conversationModel, ConversationTag.MARKED_AS_UNREAD, TriggerSource.LOCAL)
        withContext(dispatcherProvider.io) {
            messageService.markConversationAsRead(conversationModel.messageReceiver, notificationService)
        }
    }

    fun onClickedMarkConversationAsUnread(conversationModel: ConversationModel) = runAction {
        conversationService.tag(conversationModel, ConversationTag.MARKED_AS_UNREAD, TriggerSource.LOCAL)
    }

    fun onClickedEmptyConversation(conversationModel: ConversationModel) = runAction {
        emitEvent(
            event = ConversationsViewEvent.ConfirmationRequiredToEmptyConversation(
                conversationModel = conversationModel,
            ),
        )
    }

    fun onClickedConfirmEmptyConversation(conversationModel: ConversationModel, fragmentManager: FragmentManager) = runAction {
        emptyOrDeleteConversation(
            conversationModel = conversationModel,
            mode = EmptyOrDeleteConversationsAsyncTask.Mode.EMPTY,
            fragmentManager = fragmentManager,
        )
    }

    fun onClickedDeleteContactConversation(conversationModel: ConversationModel) = runAction {
        emitEvent(
            event = ConversationsViewEvent.ConfirmationRequiredToDeleteContactConversation(
                conversationModel = conversationModel,
            ),
        )
    }

    fun onClickedConfirmDeleteContactConversation(conversationModel: ConversationModel, fragmentManager: FragmentManager) = runAction {
        emptyOrDeleteConversation(
            conversationModel = conversationModel,
            mode = EmptyOrDeleteConversationsAsyncTask.Mode.DELETE,
            fragmentManager = fragmentManager,
        )
    }

    fun onClickedDeleteDistributionListConversation(conversationModel: ConversationModel) = runAction {
        emitEvent(
            event = ConversationsViewEvent.ConfirmationRequiredToDeleteDistributionListConversation(
                conversationModel = conversationModel,
            ),
        )
    }

    fun onClickedConfirmDeleteDistributionListConversation(conversationModel: ConversationModel, fragmentManager: FragmentManager) = runAction {
        emptyOrDeleteConversation(
            conversationModel = conversationModel,
            mode = EmptyOrDeleteConversationsAsyncTask.Mode.DELETE,
            fragmentManager = fragmentManager,
        )
    }

    suspend fun provideAvatarBitmap(receiverIdentifier: ReceiverIdentifier) =
        getAndPrepareAvatarUseCase.call(receiverIdentifier)

    private fun emptyOrDeleteConversation(
        conversationModel: ConversationModel,
        mode: EmptyOrDeleteConversationsAsyncTask.Mode,
        fragmentManager: FragmentManager,
    ) {
        val myIdentity = identityProvider.getIdentity() ?: run {
            logger.error("Cannot empty or delete conversation if the identity is null")
            return
        }

        val messageReceiver: MessageReceiver<*> = conversationModel.messageReceiver
        logger.info("{} chat with receiver {} (type={}).", mode, messageReceiver.getUniqueIdString(), messageReceiver.getType())
        EmptyOrDeleteConversationsAsyncTask(
            mode,
            arrayOf(conversationModel.messageReceiver),
            conversationService,
            distributionListService,
            groupModelRepository,
            groupFlowDispatcher,
            myIdentity.value,
            fragmentManager,
            null,
            null,
        ).execute()
    }

    private fun fireReceiverUpdate(receiver: MessageReceiver<*>) {
        when (receiver) {
            is GroupMessageReceiver -> {
                val groupIdentity = GroupIdentity(
                    creatorIdentity = receiver.group.creatorIdentity,
                    groupId = receiver.group.apiGroupId.toLong(),
                )
                ListenerManager.groupListeners.handle { listener: GroupListener -> listener.onUpdate(groupIdentity) }
            }

            is ContactMessageReceiver -> {
                ListenerManager.contactListeners.handle { listener: ContactListener ->
                    listener.onModified(receiver.contact.identity)
                }
            }

            is DistributionListMessageReceiver -> {
                ListenerManager.distributionListListeners.handle { listener: DistributionListListener ->
                    listener.onModify(receiver.distributionList)
                }
            }
        }
    }

    private fun firePrivateReceiverUpdate() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // fire an update for every secret receiver (to update webclient data)
                conversationService.getAll(false)
                    .filter { conversationModel ->
                        conversationCategoryService.isPrivateChat(conversationModel.messageReceiver.getUniqueIdString())
                    }
                    .forEach { conversationModel ->
                        fireReceiverUpdate(conversationModel.messageReceiver)
                    }
            }
        }
    }

    fun onClickContactSupport() {
        if (addOrUpdateSupportContactJob?.isActive == true || BuildFlavor.current.isOnPrem) {
            return
        }
        addOrUpdateSupportContactJob = runAction {
            // Failed case needs to be checked first, as the result can be both Failed and ContactAvailable at the same time
            // See the definition of `LocalPublicKeyMismatch`
            val event = when (val contactResult: ContactResult = addOrUpdateSupportContact()) {
                is Failed -> ConversationsViewEvent.OnSupportContactUnavailable(contactResult.message)
                is ContactAvailable -> ConversationsViewEvent.OnSupportContactAvailable(
                    receiverIdentifier = ContactReceiverIdentifier(contactResult.contactModel.identity),
                )
            }
            emitEvent(event)
        }
    }

    private suspend fun addOrUpdateSupportContact(): ContactResult {
        val myIdentity = identityProvider.getIdentity() ?: run {
            logger.error("Cannot contact support without a user identity")
            return GenericFailure
        }
        return withContext(dispatcherProvider.worker) {
            AddOrUpdateSupportContactBackgroundTask(
                myIdentity = myIdentity.value,
                apiConnector = apiConnector,
                contactModelRepository = contactModelRepository,
                appRestrictions = appRestrictions,
            ).runSynchronously()
        }
    }
}
