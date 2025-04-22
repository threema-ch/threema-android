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

package ch.threema.domain.taskmanager

import ch.threema.base.crypto.NonceFactory
import ch.threema.domain.protocol.connection.data.InboundMessage
import ch.threema.domain.protocol.connection.data.OutboundD2mMessage
import ch.threema.domain.protocol.connection.data.OutboundMessage
import ch.threema.domain.protocol.multidevice.MultiDeviceKeys
import java.util.function.Supplier

class BypassTaskCodec(taskCodecSupplier: Supplier<TaskCodec>) : TaskCodec {
    private val taskCodec by lazy { taskCodecSupplier.get() }

    override suspend fun write(message: OutboundMessage) {
        require(message is OutboundD2mMessage.ReflectedAck) {
            "Unexpected outgoing message with payload ${message.payloadType} in bypassed task"
        }
        taskCodec.write(message)
    }

    override suspend fun reflectAndAwaitAck(
        encryptedEnvelopeResult: MultiDeviceKeys.EncryptedEnvelopeResult,
        storeD2dNonce: Boolean,
        nonceFactory: NonceFactory,
    ): ULong {
        throw IllegalStateException("Unexpected reflection with awaiting ack inside bypassed task")
    }

    override suspend fun reflect(encryptedEnvelopeResult: MultiDeviceKeys.EncryptedEnvelopeResult): UInt {
        throw IllegalStateException("Unexpected reflection inside bypassed task")
    }

    override suspend fun read(preProcess: (InboundMessage) -> MessageFilterInstruction): InboundMessage {
        throw IllegalStateException("Unexpected read operation inside bypassed task")
    }
}
