/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.TypingIndicatorMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import java.util.Date

class OutgoingTypingIndicatorMessageTask(
    private val isTyping: Boolean,
    private val toIdentity: String,
    serviceManager: ServiceManager,
) : OutgoingCspMessageTask(serviceManager) {
    override val type: String = "OutgoingTypingIndicatorMessageTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        val message = TypingIndicatorMessage().also {
            it.isTyping = isTyping
        }

        sendContactMessage(message, null, toIdentity, MessageId(), Date(), handle)
    }

    override fun serialize(): SerializableTaskData? = null
}
