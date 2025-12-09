/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app.processors.incomingcspmessage.conversation

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.tasks.runCommonReactionMessageReceiveEmojiSequenceConversion
import ch.threema.app.tasks.runCommonReactionMessageReceiveSteps
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.messages.ReactionMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

private val logger = getThreemaLogger("IncomingContactReactionMessageTask")

class IncomingContactReactionMessageTask(
    message: ReactionMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<ReactionMessage>(message, triggerSource, serviceManager) {
    private val messageService by lazy { serviceManager.messageService }
    private val contactService by lazy { serviceManager.contactService }

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec) =
        processContactReactionMessage()

    override suspend fun executeMessageStepsFromSync() = processContactReactionMessage()

    private fun processContactReactionMessage(): ReceiveStepsResult {
        logger.debug("IncomingContactReactionMessageTask id: {}", message.data.messageId)

        val contactModel = contactService.getByIdentity(message.fromIdentity)
        if (contactModel == null) {
            logger.warn("Incoming Reaction Message: No contact found for ${message.fromIdentity}")
            return ReceiveStepsResult.DISCARD
        }

        val receiver = contactService.createReceiver(contactModel)
        val targetMessage = runCommonReactionMessageReceiveSteps(message, receiver, messageService)
            ?: return ReceiveStepsResult.DISCARD
        val emojiSequence =
            runCommonReactionMessageReceiveEmojiSequenceConversion(message.data.emojiSequenceBytes)
                ?: return ReceiveStepsResult.DISCARD

        messageService.saveEmojiReactionMessage(
            targetMessage,
            message.fromIdentity,
            message.data.actionCase,
            emojiSequence,
        )

        return ReceiveStepsResult.SUCCESS
    }
}
