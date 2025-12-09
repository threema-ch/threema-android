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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.threema.app.compose.conversation.models.ConversationListItemUiModel
import ch.threema.app.drafts.DraftManager
import ch.threema.app.services.ContactService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.ConversationService
import ch.threema.app.services.DistributionListService
import ch.threema.app.services.GroupService
import ch.threema.app.services.RingtoneService
import ch.threema.app.usecases.WatchGroupCallsUseCase
import ch.threema.app.usecases.WatchTypingIdentitiesUseCase
import ch.threema.app.usecases.conversations.WatchUnarchivedConversationListItemsUseCase
import ch.threema.app.usecases.conversations.WatchUnarchivedConversationsUseCase
import ch.threema.app.utils.DispatcherProvider
import ch.threema.app.voip.groupcall.GroupCallManager
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ConversationListViewModel(
    private val conversationService: ConversationService,
    private val groupCallManager: GroupCallManager,
    private val conversationCategoryService: ConversationCategoryService,
    private val contactService: ContactService,
    private val groupService: GroupService,
    private val distributionListService: DistributionListService,
    private val ringtoneService: RingtoneService,
    private val draftManager: DraftManager,
) : ViewModel() {
    private val watchConversationListItemsUseCase = WatchUnarchivedConversationListItemsUseCase(
        watchUnarchivedConversationsUseCase = WatchUnarchivedConversationsUseCase(
            conversationService = conversationService,
        ),
        watchGroupCallsUseCase = WatchGroupCallsUseCase(
            groupCallManager = groupCallManager,
            dispatcherProvider = DispatcherProvider.default,
        ),
        watchTypingIdentitiesUseCase = WatchTypingIdentitiesUseCase(
            contactService = contactService,
        ),
        conversationCategoryService = conversationCategoryService,
        contactService = contactService,
        groupService = groupService,
        distributionListService = distributionListService,
        ringtoneService = ringtoneService,
        draftManager = draftManager,
    )

    val conversationListItemUiModels: StateFlow<List<ConversationListItemUiModel>> =
        watchConversationListItemsUseCase
            .call()
            .map { conversationUiModels ->
                conversationUiModels.map { conversationUiModel ->
                    ConversationListItemUiModel(
                        model = conversationUiModel,
                        isChecked = false,
                    )
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeout = 5.seconds),
                initialValue = emptyList(),
            )
}
