package ch.threema.app.usecases.conversations

import ch.threema.app.drafts.DraftManager
import ch.threema.app.services.ContactService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.DistributionListService
import ch.threema.app.services.GroupService
import ch.threema.app.services.RingtoneService
import ch.threema.app.usecases.WatchTypingIdentitiesUseCase
import ch.threema.app.usecases.contacts.WatchAllMentionNamesUseCase
import ch.threema.app.usecases.contacts.WatchContactNameFormatSettingUseCase
import ch.threema.app.usecases.groups.WatchGroupCallsUseCase

// TODO(SE-508): When group calls un-archive a conversation, we do not need to watch them here
class WatchArchivedConversationListItemsUseCase(
    watchArchivedConversationsUseCase: WatchArchivedConversationsUseCase,
    watchGroupCallsUseCase: WatchGroupCallsUseCase,
    conversationCategoryService: ConversationCategoryService,
    watchTypingIdentitiesUseCase: WatchTypingIdentitiesUseCase,
    contactService: ContactService,
    groupService: GroupService,
    distributionListService: DistributionListService,
    ringtoneService: RingtoneService,
    watchAvatarIterationsUseCase: WatchAvatarIterationsUseCase,
    watchContactNameFormatSettingUseCase: WatchContactNameFormatSettingUseCase,
    watchAllMentionNamesUseCase: WatchAllMentionNamesUseCase,
    draftManager: DraftManager,
) : WatchConversationListItemsUseCase(
    watchConversationsUseCase = watchArchivedConversationsUseCase,
    watchGroupCallsUseCase = watchGroupCallsUseCase,
    watchTypingIdentitiesUseCase = watchTypingIdentitiesUseCase,
    watchAvatarIterationsUseCase = watchAvatarIterationsUseCase,
    watchContactNameFormatSettingUseCase = watchContactNameFormatSettingUseCase,
    watchAllMentionNamesUseCase = watchAllMentionNamesUseCase,
    draftManager = draftManager,
    conversationCategoryService = conversationCategoryService,
    contactService = contactService,
    groupService = groupService,
    distributionListService = distributionListService,
    ringtoneService = ringtoneService,
)
