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
import ch.threema.domain.protocol.csp.messages.DeliveryReceiptMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import kotlinx.serialization.Serializable
import java.util.Date

class OutgoingContactDeliveryReceiptMessageTask(
    private val receiptType: Int,
    private val messageIds: Array<MessageId>,
    private val date: Long,
    private val toIdentity: String,
    serviceManager: ServiceManager,
) : OutgoingCspMessageTask(serviceManager) {
    override val type: String = "OutgoingContactDeliveryReceiptMessageTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        val message = DeliveryReceiptMessage().also {
            it.receiptType = receiptType
            it.receiptMessageIds = messageIds
        }

        sendContactMessage(message, null, toIdentity, MessageId(), Date(date), handle)
    }

    override fun serialize(): SerializableTaskData = OutgoingDeliveryReceiptMessageData(
        receiptType,
        messageIds.map { it.toString() },
        date,
        toIdentity
    )

    @Serializable
    data class OutgoingDeliveryReceiptMessageData(
        private val receiptType: Int,
        private val messageIds: List<String>,
        private val date: Long,
        private val toIdentity: String,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            OutgoingContactDeliveryReceiptMessageTask(
                receiptType,
                messageIds.map { MessageId.fromString(it) }.toTypedArray(),
                date,
                toIdentity,
                serviceManager,
            )
    }
}
