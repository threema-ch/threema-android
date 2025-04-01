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

package ch.threema.domain.helpers

import ch.threema.base.crypto.NonceFactory
import ch.threema.domain.protocol.connection.data.InboundMessage
import ch.threema.domain.protocol.connection.data.OutboundMessage
import ch.threema.domain.protocol.multidevice.MultiDeviceKeys
import ch.threema.domain.taskmanager.MessageFilterInstruction
import ch.threema.domain.taskmanager.TaskCodec

/**
 * This task codec can be used in tests as placeholder. Note that this task codec throws
 * [IllegalStateException] when one of its methods is called.
 */
class UnusedTaskCodec : TaskCodec {
    override suspend fun read(preProcess: (InboundMessage) -> MessageFilterInstruction): InboundMessage {
        throw IllegalStateException("This task codec should not be used.")
    }

    override suspend fun write(message: OutboundMessage) {
        throw IllegalStateException("This task codec should not be used.")
    }

    override suspend fun reflectAndAwaitAck(
        encryptedEnvelopeResult: MultiDeviceKeys.EncryptedEnvelopeResult,
        storeD2dNonce: Boolean,
        nonceFactory: NonceFactory
    ): ULong {
        throw IllegalStateException("This task codec should not be used.")
    }

    override suspend fun reflect(encryptedEnvelopeResult: MultiDeviceKeys.EncryptedEnvelopeResult): UInt {
        throw IllegalStateException("This task codec should not be used.")
    }
}
