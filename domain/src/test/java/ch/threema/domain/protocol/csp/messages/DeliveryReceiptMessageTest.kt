/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages

import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.ProtocolDefines.DELIVERYRECEIPT_MSGRECEIVED
import ch.threema.domain.protocol.csp.ProtocolDefines.MESSAGE_ID_LEN
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DeliveryReceiptMessageTest {
    @Test
    fun `DeliveryReceiptMessage_fromByteArray can read a valid byte array`() {
        val messageIds = Array(3) { MessageId.random() }
        val messageBytes = ByteArray(1 + messageIds.size * MESSAGE_ID_LEN)
        messageBytes[0] = DELIVERYRECEIPT_MSGRECEIVED.toByte()
        messageIds.forEachIndexed { index, messageId ->
            messageId.messageId.copyInto(messageBytes, 1 + index * MESSAGE_ID_LEN)
        }

        val deliveryReceiptMessage = DeliveryReceiptMessage.fromByteArray(messageBytes, 0, messageBytes.size)

        assertEquals(DELIVERYRECEIPT_MSGRECEIVED, deliveryReceiptMessage.receiptType)
        assertContentEquals(messageIds, deliveryReceiptMessage.receiptMessageIds)
    }

    @Test
    fun `DeliveryReceiptMessage_fromByteArray fails when byte array length is invalid`() {
        val messageIds = Array(3) { MessageId.random() }
        val messageBytes = ByteArray(1 + messageIds.size * MESSAGE_ID_LEN + 1) // extra byte added, making it invalid
        messageBytes[0] = DELIVERYRECEIPT_MSGRECEIVED.toByte()
        messageIds.forEachIndexed { index, messageId ->
            messageId.messageId.copyInto(messageBytes, 1 + index * MESSAGE_ID_LEN)
        }

        assertFailsWith<BadMessageException> {
            DeliveryReceiptMessage.fromByteArray(messageBytes, 0, messageBytes.size)
        }
    }

    @Test
    fun `DeliveryReceiptMessage_fromByteArray fails when offset is invalid`() {
        val messageIds = Array(3) { MessageId.random() }
        val messageBytes = ByteArray(1 + messageIds.size * MESSAGE_ID_LEN)
        messageBytes[0] = DELIVERYRECEIPT_MSGRECEIVED.toByte()
        messageIds.forEachIndexed { index, messageId ->
            messageId.messageId.copyInto(messageBytes, 1 + index * MESSAGE_ID_LEN)
        }

        assertFailsWith<BadMessageException> {
            DeliveryReceiptMessage.fromByteArray(messageBytes, 1, messageBytes.size)
        }
    }

    @Test
    fun `DeliveryReceiptMessage_fromByteArray fails when minimum number of message ids is not met`() {
        val messageBytes = ByteArray(1)
        messageBytes[0] = DELIVERYRECEIPT_MSGRECEIVED.toByte()

        assertFailsWith<BadMessageException> {
            DeliveryReceiptMessage.fromByteArray(messageBytes, 0, messageBytes.size)
        }
    }
}
