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

package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.managers.ServiceManager
import ch.threema.app.tasks.runCommonReactionMessageReceiveEmojiSequenceConversion
import ch.threema.app.tasks.runCommonReactionMessageReceiveSteps
import ch.threema.domain.protocol.csp.messages.GroupReactionMessage
import ch.threema.protobuf.Common
import ch.threema.protobuf.d2d.MdD2D.OutgoingMessage
import ch.threema.storage.models.AbstractMessageModel

internal class ReflectedOutgoingGroupReactionTask(
    message: OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingGroupMessageTask(
    message,
    Common.CspE2eMessageType.GROUP_REACTION,
    serviceManager,
) {
    private val messageService by lazy { serviceManager.messageService }
    private val contactService by lazy { serviceManager.contactService }

    private val groupReactionMessage by lazy { GroupReactionMessage.fromReflected(message) }

    override val storeNonces: Boolean
        get() = groupReactionMessage.protectAgainstReplay()

    override val shouldBumpLastUpdate: Boolean
        get() = groupReactionMessage.bumpLastUpdate()

    override fun processOutgoingMessage() {
        check(message.conversation.hasGroup()) {
            "The message does not have a group identity set"
        }

        val targetMessage: AbstractMessageModel = runCommonReactionMessageReceiveSteps(
            reactionMessage = groupReactionMessage,
            receiver = messageReceiver,
            messageService = messageService,
        ) ?: return

        val emojiSequence: String = runCommonReactionMessageReceiveEmojiSequenceConversion(
            emojiSequenceBytes = groupReactionMessage.data.emojiSequenceBytes,
        ) ?: return

        messageService.saveEmojiReactionMessage(
            /* targetMessage = */
            targetMessage,
            /* senderIdentity = */
            contactService.me.identity,
            /* actionCase = */
            groupReactionMessage.data.actionCase,
            /* emojiSequence = */
            emojiSequence,
        )
    }
}
