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

package ch.threema.domain.helpers

import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.connection.data.OutboundD2mMessage
import ch.threema.domain.protocol.connection.data.OutboundMessage

/**
 * This task codec is used only for tests. It acts as the server and creates server acknowledgements
 * for sent transaction begin and commit messages. Note that this also acts as [ServerAckTaskCodec].
 */
class TransactionAckTaskCodec : ServerAckTaskCodec() {
    var transactionBeginCount = 0
    var transactionCommitCount = 0

    override suspend fun write(message: OutboundMessage) {
        if (message is OutboundD2mMessage) {
            when (message) {
                is OutboundD2mMessage.BeginTransaction -> {
                    transactionBeginCount++
                    inboundMessages.add(InboundD2mMessage.BeginTransactionAck())
                }

                is OutboundD2mMessage.CommitTransaction -> {
                    transactionCommitCount++
                    inboundMessages.add(InboundD2mMessage.CommitTransactionAck())
                }

                else -> Unit
            }
        } else {
            super.write(message)
        }
    }
}
