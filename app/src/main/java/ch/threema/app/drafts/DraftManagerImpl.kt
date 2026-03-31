package ch.threema.app.drafts

import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.utils.DispatcherProvider
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.models.MessageId
import ch.threema.domain.types.ConversationUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = getThreemaLogger("DraftManagerImpl")

@OptIn(FlowPreview::class)
class DraftManagerImpl(
    private val preferenceService: PreferenceService,
    dispatcherProvider: DispatcherProvider,
) : DraftManager {
    private val messageDraftsFlow = MutableStateFlow(mapOf<ConversationUID, MessageDraft>())
    private val coroutineScope = CoroutineScope(dispatcherProvider.worker)

    override val drafts: StateFlow<Map<ConversationUID, MessageDraft>> = messageDraftsFlow

    fun init() {
        try {
            val messages = preferenceService.getMessageDrafts() ?: emptyMap()
            val quotes = preferenceService.getQuoteDrafts() ?: emptyMap()

            messageDraftsFlow.value = messages
                .mapValues { (conversationUniqueId, text) ->
                    MessageDraft(
                        text = text ?: "",
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
            preferenceService.setMessageDrafts(texts)
            preferenceService.setQuoteDrafts(quotes)
        } catch (e: Exception) {
            logger.error("Failed to persist drafts", e)
        }
    }

    override fun get(conversationUID: ConversationUID): MessageDraft? =
        messageDraftsFlow.value[conversationUID]

    override fun remove(conversationUID: ConversationUID) {
        set(conversationUID, text = null)
    }

    override fun set(conversationUID: ConversationUID, text: String?, quotedMessageId: MessageId?) {
        messageDraftsFlow.update { messageDrafts ->
            if (text.isNullOrBlank()) {
                messageDrafts.minus(conversationUID)
            } else {
                messageDrafts.plus(
                    conversationUID to MessageDraft(
                        text = text,
                        quotedMessageId = quotedMessageId,
                    ),
                )
            }
        }
    }
}
