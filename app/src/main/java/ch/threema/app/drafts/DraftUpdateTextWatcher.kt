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
