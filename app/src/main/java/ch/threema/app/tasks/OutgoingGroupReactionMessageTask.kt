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
import ch.threema.domain.protocol.csp.messages.GroupReactionMessage
import ch.threema.domain.protocol.csp.messages.ReactionMessageData
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.protobuf.csp.e2e.Reaction.ActionCase
import com.google.protobuf.ByteString
import kotlinx.serialization.Serializable
import java.util.Date

class OutgoingGroupReactionMessageTask(
    private val targetMessageModelId: Int,
    private val reactionMessageId: MessageId,
    private val actionCase: ActionCase,
    private val emojiSequence: String,
    private val reactedAt: Date,
    private val recipientIdentities: Set<String>,
    serviceManager: ServiceManager,
) : OutgoingCspMessageTask(serviceManager) {

    override val type: String = "OutgoingGroupReactionMessageTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        val messageModel = getGroupMessageModel(targetMessageModelId)
            ?: throw ThreemaException("No group message model found for messageModelId=$targetMessageModelId")

        val group = groupService.getById(messageModel.groupId)
            ?: throw ThreemaException("No group model found for groupId=${messageModel.groupId}")

        val targetMessageIdLong = MessageId.fromString(messageModel.apiMessageId).messageIdLong

        sendGroupMessage(
            group,
            recipientIdentities,
            null,
            reactedAt,
            reactionMessageId,
            createAbstractMessage = { createReactionMessage(targetMessageIdLong) },
            handle
        )
    }

    private fun createReactionMessage(targetMessageId: Long): GroupReactionMessage {
        val groupReactionMessage = GroupReactionMessage(
            ReactionMessageData(
                messageId = targetMessageId,
                actionCase = actionCase,
                emojiSequenceBytes = ByteString.copyFromUtf8(emojiSequence)
            )
        )
        return groupReactionMessage
    }

    override fun serialize(): SerializableTaskData = OutgoingGroupReactionMessageData(
        targetMessageModelId,
        reactionMessageId.messageId,
        actionCase,
        emojiSequence,
        reactedAt.time,
        recipientIdentities
    )

    @Serializable
    class OutgoingGroupReactionMessageData(
        private val messageModelId: Int,
        private val messageId: ByteArray,
        private val actionCase: ActionCase,
        private val emojiSequence: String,
        private val reactedAt: Long,
        private val recipientIdentities: Set<String>
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            OutgoingGroupReactionMessageTask(
                messageModelId,
                MessageId(messageId),
                actionCase,
                emojiSequence,
                Date(reactedAt),
                recipientIdentities,
                serviceManager
            )
    }
}
