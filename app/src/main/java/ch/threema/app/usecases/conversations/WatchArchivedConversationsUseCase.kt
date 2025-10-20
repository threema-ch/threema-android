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

import ch.threema.app.listeners.ConversationListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.services.ConversationService
import ch.threema.common.DispatcherProvider
import ch.threema.storage.models.ConversationModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

class WatchArchivedConversationsUseCase(
    private val conversationService: ConversationService,
    private val dispatcherProvider: DispatcherProvider,
) {

    /**
     *  Creates a *cold* [Flow] of the most recent archived [ConversationModel]s.
     *
     *  This flows values are produced inside of the IO dispatcher because of the database IO operations by [ConversationService.getArchived].
     *
     *
     *  **Listener logic** :
     *
     *  - This flow gets its updates from a *globally* registered [ConversationListener]
     *  - This listener will also get any events from un-archived conversations
     *  - We skip unnecessary re-reads of the archived conversations from database and only emit a new list of archived conversations
     *  if the change(s) affected an archived conversation
     *  - In the event of [ConversationListener.onModifiedAll] all archived conversations must be read from database in every case
     */
    // TODO(ANDR-4175): Rework the listener callbacks, when they are called in a correct way
    fun call(): Flow<List<ConversationModel>> = callbackFlow {
        /**
         * Tries to publish an **immutable copy** of the most recent list of conversations
         */
        fun trySendCurrent() {
            trySend(conversationService.getArchived().toList())
        }

        trySendCurrent()

        val conversationListener: ConversationListener = object : ConversationListener {

            /**
             *  TODO(ANDR-4175): Skip refreshes here if conversationModel is not archived
             *
             *  Right now we are not able to skip these events for an un-archived conversationModel, as this will be called if a conversation gets
             *  un-archived. The idea behind ANDR-4175 is to switch this event to onModified.
             */
            override fun onNew(conversationModel: ConversationModel) {
                trySendCurrent()
            }

            override fun onModified(conversationModel: ConversationModel, oldPosition: Int?) {
                if (conversationModel.isArchived) {
                    trySendCurrent()
                }
            }

            override fun onRemoved(conversationModel: ConversationModel) {
                if (conversationModel.isArchived) {
                    trySendCurrent()
                }
            }

            override fun onModifiedAll() {
                trySendCurrent()
            }
        }

        ListenerManager.conversationListeners.add(conversationListener)

        awaitClose {
            ListenerManager.conversationListeners.remove(conversationListener)
        }
    }.flowOn(dispatcherProvider.io)
}
