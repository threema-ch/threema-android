/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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

package ch.threema.app.processors.statusupdates

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.IncomingCspMessageSubTask
import ch.threema.app.processors.ReceiveStepsResult
import ch.threema.domain.protocol.csp.messages.TypingIndicatorMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec

class IncomingTypingIndicatorTask(
    private val message: TypingIndicatorMessage,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask(serviceManager) {
    private val contactService = serviceManager.contactService

    override suspend fun run(handle: ActiveTaskCodec): ReceiveStepsResult {
        if (contactService.getByIdentity(message.fromIdentity) != null) {
            contactService.setIsTyping(message.fromIdentity, message.isTyping)
            return ReceiveStepsResult.SUCCESS
        }
        return ReceiveStepsResult.DISCARD
    }

}
