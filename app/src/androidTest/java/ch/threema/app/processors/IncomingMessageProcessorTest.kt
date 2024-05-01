/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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

package ch.threema.app.processors

import ch.threema.app.DangerousTest
import ch.threema.app.testutils.TestHelpers.TestContact
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.ProtocolDefines.DELIVERYRECEIPT_MSGCONSUMED
import ch.threema.domain.protocol.csp.ProtocolDefines.DELIVERYRECEIPT_MSGREAD
import ch.threema.domain.protocol.csp.ProtocolDefines.DELIVERYRECEIPT_MSGRECEIVED
import ch.threema.domain.protocol.csp.ProtocolDefines.DELIVERYRECEIPT_MSGUSERACK
import ch.threema.domain.protocol.csp.ProtocolDefines.DELIVERYRECEIPT_MSGUSERDEC
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.DeliveryReceiptMessage
import ch.threema.domain.protocol.csp.messages.LocationMessage
import ch.threema.domain.protocol.csp.messages.TextMessage
import ch.threema.domain.protocol.csp.messages.TypingIndicatorMessage
import ch.threema.domain.protocol.csp.messages.ballot.BallotData
import ch.threema.domain.protocol.csp.messages.ballot.BallotDataChoice
import ch.threema.domain.protocol.csp.messages.ballot.BallotDataChoiceBuilder
import ch.threema.domain.protocol.csp.messages.ballot.BallotId
import ch.threema.domain.protocol.csp.messages.ballot.BallotVote
import ch.threema.domain.protocol.csp.messages.ballot.PollSetupMessage
import ch.threema.domain.protocol.csp.messages.ballot.PollVoteMessage
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.fail
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
@DangerousTest
class IncomingMessageProcessorTest : MessageProcessorProvider() {

    @Test
    fun testIncomingTextMessage() = runTest {
        assertSuccessfulMessageProcessing(
            TextMessage().also { it.text = "Hello!" }.enrich(),
            contactA
        )
    }

    @Test
    fun testIncomingLocationMessage() = runTest {
        assertSuccessfulMessageProcessing(LocationMessage().also {
            it.longitude = 0.0
            it.longitude = 0.0
        }.enrich(), contactA)

        assertSuccessfulMessageProcessing(LocationMessage().enrich(), contactA)
    }

    @Test
    fun testIncomingPoll() = runTest {
        val ballotId = BallotId()
        val ballotCreator = contactA.identity

        val ballotData = BallotData().also { data ->
            data.description = "This describes the ballot!"
            data.assessmentType = BallotData.AssessmentType.SINGLE
            data.type = BallotData.Type.INTERMEDIATE
            List<BallotDataChoice>(10) { index ->
                BallotDataChoiceBuilder()
                    .setId(index)
                    .setDescription("This is choice $index!")
                    .setSortKey(index)
                    .build()
            }.forEach { data.addChoice(it) }
            data.displayType = BallotData.DisplayType.LIST_MODE
            data.state = BallotData.State.OPEN
        }

        val pollSetupMessage = PollSetupMessage().also {
            it.ballotCreator = ballotCreator
            it.ballotId = ballotId
            it.data = ballotData
        }.enrich()

        // Test a valid ballot setup message that opens a poll
        assertSuccessfulMessageProcessing(pollSetupMessage, contactA)

        val pollVoteMessage = PollVoteMessage().also { voteMessage ->
            voteMessage.ballotId = ballotId
            voteMessage.ballotCreator = ballotCreator
            voteMessage.ballotVotes.addAll(List(5) { index ->
                BallotVote().also {
                    it.id = index
                    it.value = 0
                }
            })
        }.enrich()

        assertSuccessfulMessageProcessing(pollVoteMessage, contactA)
    }

    @Test
    fun testIncomingDeliveryReceipt() = runTest {
        val messageId = MessageId()

        // Test 'received'
        assertSuccessfulMessageProcessing(
            DeliveryReceiptMessage().also {
                it.receiptType = DELIVERYRECEIPT_MSGRECEIVED
                it.receiptMessageIds = arrayOf(messageId)
                it.messageId = MessageId(0)
            }.enrich(), contactA
        )

        // Test 'consumed'
        assertSuccessfulMessageProcessing(
            DeliveryReceiptMessage().also {
                it.receiptType = DELIVERYRECEIPT_MSGCONSUMED
                it.receiptMessageIds = arrayOf(messageId)
            }.enrich(), contactA
        )

        // Test 'read'
        assertSuccessfulMessageProcessing(
            DeliveryReceiptMessage().also {
                it.receiptType = DELIVERYRECEIPT_MSGREAD
                it.receiptMessageIds = arrayOf(messageId)
            }.enrich(), contactA
        )

        // Test 'userack'
        assertSuccessfulMessageProcessing(
            DeliveryReceiptMessage().also {
                it.receiptType = DELIVERYRECEIPT_MSGUSERACK
                it.receiptMessageIds = arrayOf(messageId)
            }.enrich(), contactA
        )

        // Test 'userdec'
        assertSuccessfulMessageProcessing(
            DeliveryReceiptMessage().also {
                it.receiptType = DELIVERYRECEIPT_MSGUSERDEC
                it.receiptMessageIds = arrayOf(messageId)
            }.enrich(), contactA
        )

        // Test 'received' with two times the same message id
        assertSuccessfulMessageProcessing(
            DeliveryReceiptMessage().also {
                it.receiptType = DELIVERYRECEIPT_MSGRECEIVED
                it.receiptMessageIds = arrayOf(messageId, messageId)
                it.messageId = MessageId(0)
            }.enrich(), contactA
        )

        // Test 'received' with many message ids
        assertSuccessfulMessageProcessing(
            DeliveryReceiptMessage().also {
                it.receiptType = DELIVERYRECEIPT_MSGRECEIVED
                it.receiptMessageIds = Array(100) { MessageId() }
                it.messageId = MessageId(0)
            }.enrich(), contactA
        )
    }

    @Test
    fun testIncomingTypingIndicator() = runTest {
        assertSuccessfulMessageProcessing(
            TypingIndicatorMessage().also { it.isTyping = true }.enrich(),
            contactA
        )
        assertSuccessfulMessageProcessing(
            TypingIndicatorMessage().also { it.isTyping = false }.enrich(),
            contactA
        )
    }

    @Test
    fun testInvalidMessage() = runTest {
        val badMessage = TextMessage().also {
            it.fromIdentity = contactA.identity
            it.toIdentity = myContact.identity
            it.messageId = MessageId()
            it.date = Date()
            it.text = "" // Bad message; cannot be decoded due to invalid length
        }

        // Processing the message should not result in a crash, it should just ack the message
        // towards the server, discard it and no delivery receipt should be sent
        processMessage(badMessage, contactA.identityStore)

        // Assert that no messages are sent (also no delivery receipt, as it is an invalid message)
        assertTrue(sentMessagesNewTask.isEmpty())
        assertTrue(sentMessagesInsideTask.isEmpty())
    }

    @Test
    fun testMessageToSomeoneElse() = runTest {
        val messageToB = TextMessage().also {
            it.fromIdentity = contactA.identity
            it.toIdentity = contactB.identity
            it.messageId = MessageId()
            it.date = Date()
            it.text = "This message is for contact B!"
        }

        assertFailingMessageProcessing(messageToB, contactA)
    }

    private suspend fun assertSuccessfulMessageProcessing(
        message: AbstractMessage,
        fromContact: TestContact,
    ) {
        val messageId = message.messageId
        processMessage(
            message.also { it.fromIdentity = fromContact.identity },
            fromContact.identityStore
        )

        val expectDeliveryReceiptSent = message.sendAutomaticDeliveryReceipt()
                && !message.hasFlags(ProtocolDefines.MESSAGE_FLAG_NO_DELIVERY_RECEIPTS)
        if (expectDeliveryReceiptSent) {
            val deliveryReceiptMessage = sentMessagesInsideTask.poll()
            if (deliveryReceiptMessage is DeliveryReceiptMessage) {
                assertArrayEquals(messageId.messageId, deliveryReceiptMessage.receiptMessageIds[0].messageId)
                assertEquals(DELIVERYRECEIPT_MSGRECEIVED, deliveryReceiptMessage.receiptType)
            } else {
                fail("Instead of delivery receipt we got $deliveryReceiptMessage")
            }
        }

        assertTrue(sentMessagesInsideTask.isEmpty())
        assertTrue(sentMessagesNewTask.isEmpty())
    }

    private suspend fun assertFailingMessageProcessing(
        message: AbstractMessage,
        fromContact: TestContact,
    ) {
        processMessage(
            message.also { it.fromIdentity = fromContact.identity },
            fromContact.identityStore
        )

        assertTrue(sentMessagesInsideTask.isEmpty())
        assertTrue(sentMessagesNewTask.isEmpty())
    }

    private fun AbstractMessage.enrich(): AbstractMessage {
        toIdentity = myContact.identity
        date = Date()
        messageId = MessageId()
        return this
    }

}
