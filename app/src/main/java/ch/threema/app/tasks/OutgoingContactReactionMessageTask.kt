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

package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.base.ThreemaException
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.domain.protocol.csp.messages.ReactionMessage
import ch.threema.domain.protocol.csp.messages.ReactionMessageData
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.protobuf.csp.e2e.Reaction.ActionCase
import com.google.protobuf.ByteString
import java.util.Date
import kotlinx.serialization.Serializable

class OutgoingContactReactionMessageTask(
    private val toIdentity: String,
    private val messageModelId: Int,
    private val messageId: MessageId,
    private val actionCase: ActionCase,
    private val emojiSequence: String,
    private val createdAt: Date,
    serviceManager: ServiceManager,
) : OutgoingCspMessageTask(serviceManager) {
    override val type: String = "OutgoingContactReactionMessageTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        val messageModel = getContactMessageModel(messageModelId)
            ?: throw ThreemaException("No contact message model found for messageModelId=$messageModelId")

        val reactionMessageData = try {
            ReactionMessageData.forActionCase(
                actionCase = actionCase,
                messageId = MessageId.fromString(messageModel.apiMessageId).messageIdLong,
                emojiSequenceBytes = ByteString.copyFromUtf8(emojiSequence),
            )
        } catch (e: BadMessageException) {
            throw ThreemaException("Failed to create reaction message data", e)
        }

        val reactionMessage = ReactionMessage(reactionMessageData)

        sendContactMessage(
            message = reactionMessage,
            messageModel = null,
            toIdentity = toIdentity,
            messageId = messageId,
            createdAt = createdAt,
            handle = handle,
        )
    }

    override fun serialize(): SerializableTaskData = OutgoingContactReactionMessageData(
        toIdentity,
        messageModelId,
        messageId.messageId,
        actionCase,
        emojiSequence,
        createdAt.time,
    )

    @Serializable
    class OutgoingContactReactionMessageData(
        private val toIdentity: String,
        private val messageModelId: Int,
        private val messageId: ByteArray,
        private val actionCase: ActionCase,
        private val emojiSequence: String,
        private val createdAt: Long,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            OutgoingContactReactionMessageTask(
                toIdentity,
                messageModelId,
                MessageId(messageId),
                actionCase,
                emojiSequence,
                Date(createdAt),
                serviceManager,
            )
    }
}
