/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.getEncryptedIncomingGroupMessageUpdateReadEnvelope
import kotlinx.serialization.Serializable

class OutboundIncomingGroupMessageUpdateReadTask(
    private val messageIds: Set<MessageId>,
    private val timestamp: Long,
    private val groupId: GroupId,
    private val creatorIdentity: String,
    serviceManager: ServiceManager,
) : OutboundD2mMessageTask<Unit>, PersistableTask {
    private val multiDeviceProperties by lazy { serviceManager.multiDeviceManager.propertiesProvider.get() }
    private val deviceId by lazy { multiDeviceProperties.mediatorDeviceId }
    private val multiDeviceKeys by lazy { multiDeviceProperties.keys }

    private val nonceFactory by lazy { serviceManager.nonceFactory }

    override val type: String = "OutboundIncomingGroupMessageUpdateReadTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        val encryptedEnvelopeResult = getEncryptedIncomingGroupMessageUpdateReadEnvelope(
            messageIds,
            timestamp,
            creatorIdentity,
            groupId,
            deviceId,
            multiDeviceKeys,
        )
        handle.reflectAndAwaitAck(
            encryptedEnvelopeResult = encryptedEnvelopeResult,
            storeD2dNonce = true,
            nonceFactory = nonceFactory
        )
    }

    override fun serialize() = OutboundIncomingGroupMessageUpdateReadData(
        messageIds.map { it.messageId }.toSet(),
        timestamp,
        groupId.groupId,
        creatorIdentity,
    )

    @Serializable
    class OutboundIncomingGroupMessageUpdateReadData(
        private val messageIds: Set<ByteArray>,
        private val timestamp: Long,
        private val groupId: ByteArray,
        private val creatorIdentity: String,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            OutboundIncomingGroupMessageUpdateReadTask(
                messageIds.map { MessageId(it) }.toSet(),
                timestamp,
                GroupId(groupId),
                creatorIdentity,
                serviceManager,
            )
    }
}
