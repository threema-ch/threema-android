package ch.threema.app.fragments.conversations

import ch.threema.app.usecases.contacts.WatchAllMentionNamesUseCase
import ch.threema.app.usecases.contacts.WatchContactNameFormatSettingUseCase
import ch.threema.app.usecases.conversations.WatchOpenedConversationUseCase
import ch.threema.app.usecases.conversations.WatchUnarchivedConversationListItemsUseCase
import ch.threema.app.usecases.conversations.WatchUnarchivedConversationsUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val conversationsFeatureModule = module {
    viewModel { parameters ->
        ConversationsViewModel(
            dispatcherProvider = get(),
            conversationService = get(),
            conversationCategoryService = get(),
            distributionListService = get(),
            preferenceService = get(),
            messageService = get(),
            notificationService = get(),
            apiConnector = get(),
            appRestrictions = get(),
            contactModelRepository = get(),
            groupModelRepository = get(),
            groupFlowDispatcher = get(),
            identityProvider = get(),
            getAndPrepareAvatarUseCase = get(),
            watchConversationListItemsUseCase = get(),
            watchOpenedConversationUseCase = get(),
            watchContactNameFormatSettingUseCase = get(),
            isMultiPaneEnabled = parameters[0],
            initiallyOpenedConversationUid = parameters[1],
        )
    }
    factoryOf(::WatchUnarchivedConversationsUseCase)
    factoryOf(::WatchUnarchivedConversationListItemsUseCase)
    factoryOf(::WatchOpenedConversationUseCase)
    factoryOf(::WatchContactNameFormatSettingUseCase)
    factoryOf(::WatchAllMentionNamesUseCase)
}
