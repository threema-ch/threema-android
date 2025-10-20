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
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.MessageId
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.getEncryptedIncomingContactMessageUpdateReadEnvelope
import ch.threema.domain.types.Identity
import kotlinx.serialization.Serializable

private val logger = LoggingUtil.getThreemaLogger("OutboundIncomingContactMessageUpdateReadTask")

class OutboundIncomingContactMessageUpdateReadTask(
    private val messageIds: Set<MessageId>,
    private val timestamp: Long,
    private val recipientIdentity: Identity,
    serviceManager: ServiceManager,
) : OutboundD2mMessageTask<Unit>, PersistableTask {
    private val multiDeviceManager by lazy { serviceManager.multiDeviceManager }
    private val multiDeviceProperties by lazy { multiDeviceManager.propertiesProvider.get() }
    private val deviceId by lazy { multiDeviceProperties.mediatorDeviceId }
    private val multiDeviceKeys by lazy { multiDeviceProperties.keys }

    private val nonceFactory by lazy { serviceManager.nonceFactory }

    override val type: String = "OutboundIncomingContactMessageUpdateReadTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        if (!multiDeviceManager.isMultiDeviceActive) {
            logger.warn("Multi device is not active")
            return
        }

        val encryptedEnvelopeResult = getEncryptedIncomingContactMessageUpdateReadEnvelope(
            messageIds,
            timestamp,
            recipientIdentity,
            deviceId,
            multiDeviceKeys,
        )
        handle.reflectAndAwaitAck(
            encryptedEnvelopeResult = encryptedEnvelopeResult,
            storeD2dNonce = true,
            nonceFactory = nonceFactory,
        )
    }

    override fun serialize(): SerializableTaskData = OutboundIncomingContactMessageUpdateReadData(
        messageIds.map { it.messageId }.toSet(),
        timestamp,
        recipientIdentity,
    )

    @Serializable
    data class OutboundIncomingContactMessageUpdateReadData(
        private val messageIds: Set<ByteArray>,
        private val timestamp: Long,
        private val recipientIdentity: Identity,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            OutboundIncomingContactMessageUpdateReadTask(
                messageIds.map { MessageId(it) }.toSet(),
                timestamp,
                recipientIdentity,
                serviceManager,
            )
    }
}
