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

package ch.threema.app.preference.usecases

import ch.threema.app.listeners.ConversationListener
import ch.threema.app.managers.ListenerProvider
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.DistributionListMessageReceiver
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.ConversationService
import ch.threema.app.widget.WidgetUpdater
import ch.threema.data.models.GroupIdentity

class RemoveAllPrivateMarksUseCase(
    private val conversationService: ConversationService,
    private val conversationCategoryService: ConversationCategoryService,
    private val preferenceService: PreferenceService,
    private val widgetUpdater: WidgetUpdater,
    private val listenerProvider: ListenerProvider,
) {
    fun call() {
        val messageReceivers = conversationService.getAll(false)
            .plus(conversationService.archived)
            .map { conversation -> conversation.messageReceiver }
        var hadPrivateConversations = false
        messageReceivers.forEach { messageReceiver ->
            if (conversationCategoryService.removePrivateMark(messageReceiver)) {
                fireReceiverUpdate(messageReceiver)
                hadPrivateConversations = true
            }
        }
        if (hadPrivateConversations) {
            preferenceService.isPrivateChatsHidden = false
            widgetUpdater.updateWidgets()
            listenerProvider.conversationListeners.handle(ConversationListener::onModifiedAll)
        }
    }

    private fun fireReceiverUpdate(receiver: MessageReceiver<*>) {
        when (receiver) {
            is ContactMessageReceiver -> {
                listenerProvider.contactListeners.handle { listener ->
                    listener.onModified(receiver.contact.identity)
                }
            }
            is GroupMessageReceiver -> {
                val groupIdentity = GroupIdentity(
                    creatorIdentity = receiver.group.creatorIdentity,
                    groupId = receiver.group.apiGroupId.toLong(),
                )
                listenerProvider.groupListeners.handle { listener -> listener.onUpdate(groupIdentity) }
            }
            is DistributionListMessageReceiver -> {
                listenerProvider.distributionListListeners.handle { listener ->
                    listener.onModify(receiver.distributionList)
                }
            }
        }
    }
}
