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

package ch.threema.app.ui

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.coroutineScope
import ch.threema.app.utils.DispatcherProvider
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TypingIndicatorTextWatcher @JvmOverloads constructor(
    sendTypingIndicator: (Boolean) -> Unit,
    lifecycleOwner: LifecycleOwner,
    dispatcherProvider: DispatcherProvider = DispatcherProvider.default,
) : SimpleTextWatcher() {
    private val textChangeDetector = TextChangeDetector()
    private val typingIndicatorSendManager = TypingIndicatorSendManager(
        dispatcherProvider = dispatcherProvider,
        sendTypingIndicator = sendTypingIndicator,
        lifecycleOwner = lifecycleOwner,
    )

    override fun onTextChanged(text: CharSequence, start: Int, before: Int, count: Int) {
        if (textChangeDetector.checkNextText(text)) {
            if (text.isEmpty()) {
                // We immediately send a typing indicator if we clear the text
                typingIndicatorSendManager.onStoppedTyping()
            } else {
                typingIndicatorSendManager.registerTypingEvent()
            }
        }
    }

    fun stopSending() {
        typingIndicatorSendManager.onStoppedTyping()
    }
}

private class TypingIndicatorSendManager(
    dispatcherProvider: DispatcherProvider,
    private val sendTypingIndicator: (Boolean) -> Unit,
    lifecycleOwner: LifecycleOwner,
) {
    private val typingEvents = Channel<Unit>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val isTypingStateFlow = MutableStateFlow(false)

    init {
        lifecycleOwner.lifecycle.coroutineScope.launch(dispatcherProvider.io) {
            // This coroutine just collects the typing state flow and sends a typing indicator whenever the state changes
            launch {
                observeIsTypingStateFlow()
            }
            // This coroutine checks whether the user has been typing in the last interval
            launch {
                periodicallyCheckTypingActivity()
            }
        }
    }

    fun registerTypingEvent() {
        typingEvents.trySend(Unit)
        isTypingStateFlow.tryEmit(true)
    }

    fun onStoppedTyping() {
        isTypingStateFlow.tryEmit(false)
    }

    /**
     * Observes the typing state flow and sends a corresponding typing indicator when it changes. Note that the initial state (not-typing) is skipped
     * as we do not need to send a message in that case.
     */
    private suspend fun observeIsTypingStateFlow() {
        isTypingStateFlow.drop(1).collect { isTyping -> sendTypingIndicator(isTyping) }
    }

    /**
     * Periodically checks whether the user is still typing and in this case sends a typing indicator. If the user is not typing anymore, it changes
     * the state to not-typing and waits until the user starts typing again to start periodic checks again.
     */
    private suspend fun periodicallyCheckTypingActivity() = coroutineScope {
        // Consume the typing event initially to start the loop without any typing events in the channel
        typingEvents.receive()
        while (isActive) {
            delay(TYPING_RESEND_INTERVAL)
            val hasRecentEvent = typingEvents.tryReceive().getOrNull() != null
            if (hasRecentEvent) {
                // In case there was a recent typing event and we are still in typing mode, then send a new typing indicator
                if (isTypingStateFlow.value) {
                    sendTypingIndicator(true)
                }
            } else {
                // In case there was no recent typing event, we finish typing mode and start the loop once we get more typing activity
                isTypingStateFlow.emit(false)
                typingEvents.receive()
            }
        }
    }

    companion object {
        private val TYPING_RESEND_INTERVAL = 10.seconds
    }
}

private class TextChangeDetector {
    private var lastText: String? = null

    fun checkNextText(currentText: CharSequence): Boolean {
        return if (currentText != lastText) {
            // Conversion to string is necessary as the char sequence is mutable
            lastText = currentText.toString()
            true
        } else {
            false
        }
    }
}
