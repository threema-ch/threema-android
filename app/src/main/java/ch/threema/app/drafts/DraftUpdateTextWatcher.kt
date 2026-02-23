package ch.threema.app.drafts

import android.os.Handler
import android.os.Looper
import ch.threema.android.postDelayed
import ch.threema.app.ui.SimpleTextWatcher
import ch.threema.domain.models.MessageId
import ch.threema.domain.types.ConversationUniqueId
import kotlin.time.Duration.Companion.milliseconds

class DraftUpdateTextWatcher
@JvmOverloads
constructor(
    private val draftManager: DraftManager,
    private val conversationUniqueId: ConversationUniqueId,
    private val getText: () -> String?,
    private val getQuotedMessageId: () -> MessageId? = { null },
) : SimpleTextWatcher() {
    private val handler = Handler(Looper.getMainLooper())

    private val persistRunnable = Runnable {
        draftManager.set(
            conversationUniqueId = conversationUniqueId,
            text = getText(),
            quotedMessageId = getQuotedMessageId(),
        )
    }

    override fun onTextChanged(text: CharSequence, start: Int, before: Int, count: Int) {
        handler.removeCallbacks(persistRunnable)
        if (text.isEmpty()) {
            persistRunnable.run()
        } else {
            handler.postDelayed(PERSIST_DELAY, persistRunnable)
        }
    }

    fun stop() {
        handler.removeCallbacks(persistRunnable)
    }

    companion object {
        private val PERSIST_DELAY = 700.milliseconds
    }
}
