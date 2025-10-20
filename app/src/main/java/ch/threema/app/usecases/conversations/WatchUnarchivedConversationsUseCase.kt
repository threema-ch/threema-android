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

class WatchUnarchivedConversationsUseCase(
    private val conversationService: ConversationService,
    private val dispatcherProvider: DispatcherProvider = DispatcherProvider.Companion.default,
) {

    /**
     *  Creates a *cold* [kotlinx.coroutines.flow.Flow] of the most recent [ch.threema.storage.models.ConversationModel]s.
     *
     *  This flows values are produced inside of the IO dispatcher because of the database IO operations by [ConversationService.getAll].
     */
    fun call(): Flow<List<ConversationModel>> = callbackFlow {
        /**
         * Tries to publish an **immutable copy** of the most recent list of conversations
         */
        fun trySendCurrent() {
            trySend(
                conversationService.getAll(false).toList(),
            )
        }

        trySendCurrent()

        val conversationListener: ConversationListener = object : ConversationListener {

            override fun onNew(conversationModel: ConversationModel) {
                trySendCurrent()
            }

            override fun onModified(conversationModel: ConversationModel, oldPosition: Int?) {
                trySendCurrent()
            }

            override fun onRemoved(conversationModel: ConversationModel) {
                trySendCurrent()
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
