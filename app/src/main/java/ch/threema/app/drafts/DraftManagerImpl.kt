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

package ch.threema.app.drafts

import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.utils.DispatcherProvider
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.models.MessageId
import ch.threema.domain.types.ConversationUniqueId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = getThreemaLogger("DraftManagerImpl")

@OptIn(FlowPreview::class)
class DraftManagerImpl(
    private val preferenceService: PreferenceService,
    dispatcherProvider: DispatcherProvider,
) : DraftManager {
    private val messageDraftsFlow = MutableStateFlow(mapOf<ConversationUniqueId, MessageDraft>())
    private val coroutineScope = CoroutineScope(dispatcherProvider.worker)

    fun init() {
        try {
            val messages = preferenceService.messageDrafts
            val quotes = preferenceService.quoteDrafts

            messageDraftsFlow.value = messages
                .mapValues { (conversationUniqueId, text) ->
                    MessageDraft(
                        text = text,
                        quotedMessageId = quotes[conversationUniqueId]?.let(MessageId::fromString),
                    )
                }
        } catch (e: Exception) {
            logger.error("Failed to retrieve message drafts from storage", e)
        }

        coroutineScope.launch {
            messageDraftsFlow
                .drop(1)
                .collect {
                    persistDrafts()
                }
        }
    }

    private fun persistDrafts() {
        try {
            val messageDrafts = messageDraftsFlow.value
            logger.debug("Persisting {} drafts", messageDrafts.size)
            val texts = messageDrafts.mapValues { (_, draft) ->
                draft.text
            }
            val quotes = messageDrafts
                .mapValues { (_, draft) ->
                    draft.quotedMessageId?.toString()
                }
                .filterValues { quoteApiMessageId ->
                    quoteApiMessageId != null
                }
            preferenceService.messageDrafts = texts
            preferenceService.quoteDrafts = quotes
        } catch (e: Exception) {
            logger.error("Failed to persist drafts", e)
        }
    }

    override fun get(conversationUniqueId: ConversationUniqueId): MessageDraft? =
        messageDraftsFlow.value[conversationUniqueId]

    override fun remove(conversationUniqueId: ConversationUniqueId) {
        set(conversationUniqueId, text = null)
    }

    override fun set(conversationUniqueId: ConversationUniqueId, text: String?, quotedMessageId: MessageId?) {
        messageDraftsFlow.update { messageDrafts ->
            if (text.isNullOrBlank()) {
                messageDrafts.minus(conversationUniqueId)
            } else {
                messageDrafts.plus(
                    conversationUniqueId to MessageDraft(
                        text = text,
                        quotedMessageId = quotedMessageId,
                    ),
                )
            }
        }
    }
}
