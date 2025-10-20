/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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

package ch.threema.app.utils

import android.content.Context
import ch.threema.app.R
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.services.FileService
import ch.threema.app.services.MessageService
import ch.threema.app.services.UserService
import ch.threema.app.utils.QuoteUtil.QuoteContent
import ch.threema.domain.types.Identity
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageType
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuoteUtilTest {

    // Quotes V1

    @Test
    fun testParseQuoteV1Invalid() {
        assertNull(QuoteUtil.parseQuoteV1(""))
        assertNull(QuoteUtil.parseQuoteV1("asdf"))
        assertNull(QuoteUtil.parseQuoteV1("> asdf"))
        assertNull(QuoteUtil.parseQuoteV1("> ABCDEFG: Quote\n\nBody"))
        assertNull(QuoteUtil.parseQuoteV1("> ABCDEFGHI: Quote\n\nBody"))
    }

    private fun testQuoteV1(
        content: QuoteContent?,
        expectedIdentity: Identity,
        expectedQuotedText: String,
        expectedBodyText: String,
    ) {
        assertNotNull(content)
        assertTrue(content.isQuoteV1)
        assertFalse(content.isQuoteV2)
        assertEquals(expectedIdentity, content.identity)
        assertEquals(expectedQuotedText, content.quotedText)
        assertEquals(expectedBodyText, content.bodyText)
        assertNull(content.icon)
        assertNull(content.thumbnail)
        assertNull(content.quotedMessageId)
        assertNull(content.quotedMessageModel)
        assertNull(content.messageReceiver)
    }

    @Test
    fun testParseQuoteV1() {
        val content = QuoteUtil.parseQuoteV1("> ABCDEFGH: hello\n> goodbye\n\nyes indeed!")
        testQuoteV1(content, "ABCDEFGH", "hello\ngoodbye", "yes indeed!")
    }

    @Test
    fun testParseQuoteV1NoEmptyLine() {
        val content = QuoteUtil.parseQuoteV1("> ABCDEFGH: hello\ngoodbye\n\nyes indeed!")
        testQuoteV1(content, "ABCDEFGH", "hello", "goodbye\n\nyes indeed!")
    }

    @Test
    fun testParseQuoteV1GatewayId() {
        val content = QuoteUtil.parseQuoteV1("> *THREEMA: hello\n> goodbye\n\nyes indeed!")
        testQuoteV1(content, "*THREEMA", "hello\ngoodbye", "yes indeed!")
    }

    // Quotes V2

    private val mockContactReceiver = mockk<MessageReceiver<*>> {
        every { type } returns MessageReceiver.Type_CONTACT
    }

    @Test
    fun testExtractQuoteV2TextNoIncludeMessageModel() {
        // Test data
        val quoterIdentity = "AABBCCDD"
        val quotedIdentity = "AABBCCDD"
        val quotedMessageId = "0011223344556677"
        val quoteMessageId = "8899889988998899"
        val quotedBody = "Shall we eat lunch?"
        val quoteComment = "That's a great idea!"

        // Create quoted model
        val quotedModel: AbstractMessageModel = MessageModel(false)
        quotedModel.apiMessageId = quotedMessageId
        quotedModel.identity = quotedIdentity
        quotedModel.body = quotedBody
        quotedModel.type = MessageType.TEXT

        // Create quoter model
        val quoterModel: AbstractMessageModel = MessageModel(false)
        quoterModel.apiMessageId = quoteMessageId
        quoterModel.identity = quoterIdentity
        quoterModel.quotedMessageId = quotedMessageId
        quoterModel.body = quoteComment
        quoterModel.type = MessageType.TEXT

        // Verify
        val content = extractQuoteV2(quoterModel, quotedModel, false, mockContactReceiver)
        assertNotNull(content)
        assertTrue(content.isQuoteV2)
        assertFalse(content.isQuoteV1)
        assertEquals(quotedBody, content.quotedText)
        assertEquals(quoteComment, content.bodyText)
        assertEquals(quotedIdentity, content.identity)
        assertEquals(quotedMessageId, content.quotedMessageId)
        assertEquals(mockContactReceiver, content.messageReceiver)
        assertNull(content.quotedMessageModel)
        assertNull(content.thumbnail)
        assertNull(content.icon)
    }

    @Test
    fun testExtractQuoteV2TextNoIncludeMessageModelNoReceiverMatch() {
        // Test data
        val quoterIdentity = "AABBCCDD"
        val quotedIdentity = "CCDDEEFF"
        val quotedMessageId = "0011223344556677"
        val quoteMessageId = "8899889988998899"
        val quotedBody = "Shall we eat lunch?"
        val quoteComment = "That's a great idea!"

        // Create quoted model
        val quotedModel: AbstractMessageModel = MessageModel(false)
        quotedModel.apiMessageId = quotedMessageId
        quotedModel.identity = quotedIdentity
        quotedModel.body = quotedBody
        quotedModel.type = MessageType.TEXT

        // Create quoter model
        val quoterModel: AbstractMessageModel = MessageModel(false)
        quoterModel.apiMessageId = quoteMessageId
        quoterModel.identity = quoterIdentity
        quoterModel.quotedMessageId = quotedMessageId
        quoterModel.body = quoteComment
        quoterModel.type = MessageType.TEXT

        // Verify
        val content = extractQuoteV2(quoterModel, quotedModel, false, mockContactReceiver)
        assertNotNull(content)
        assertTrue(content.isQuoteV2)
        assertFalse(content.isQuoteV1)
        assertEquals("NoReceiverMatch", content.quotedText)
        assertEquals(quoteComment, content.bodyText)
        assertEquals(quotedMessageId, content.quotedMessageId)
        assertNotEquals(quotedIdentity, content.identity)
        assertNull(content.messageReceiver)
        assertNull(content.quotedMessageModel)
        assertNull(content.thumbnail)
        assertNull(content.icon)
    }

    @Test
    fun testExtractQuoteV2VideoIncludeMessageModel() {
        // Test data
        val quoterIdentity = "AABBCCDD"
        val quotedIdentity = "AABBCCDD"
        val quotedMessageId = "0011223344556677"
        val quoteMessageId = "8899889988998899"
        val quoteComment = "That's a great idea!"

        // Create quoted model
        val quotedModel: AbstractMessageModel = MessageModel(false)
        quotedModel.apiMessageId = quotedMessageId
        quotedModel.identity = quotedIdentity
        quotedModel.type = MessageType.VIDEO
        quotedModel.body = ""

        // Create quoter model
        val quoterModel: AbstractMessageModel = MessageModel(false)
        quoterModel.apiMessageId = quoteMessageId
        quoterModel.identity = quoterIdentity
        quoterModel.quotedMessageId = quotedMessageId
        quoterModel.body = quoteComment
        quoterModel.type = MessageType.TEXT

        // Verify
        val content = extractQuoteV2(quoterModel, quotedModel, true, mockContactReceiver)
        assertNotNull(content)
        assertTrue(content.isQuoteV2)
        assertFalse(content.isQuoteV1)
        assertEquals("Video", content.quotedText)
        assertEquals(quoteComment, content.bodyText)
        assertEquals(quotedIdentity, content.identity)
        assertEquals(quotedMessageId, content.quotedMessageId)
        assertEquals(quotedModel, content.quotedMessageModel)
        assertEquals(mockContactReceiver, content.messageReceiver)
        assertNull(content.thumbnail)
        assertEquals(R.drawable.ic_movie_filled, content.icon)
    }

    @Test
    fun testExtractQuoteV2Deleted() {
        // Test data
        val quoterIdentity = "AABBCCDD"
        val quotedMessageId = "0000000000000000"
        val quoteMessageId = "8899889988998899"
        val quoteComment = "That's a great idea!"

        // Create quoter model with an invalid message id
        val quoterModel: AbstractMessageModel = MessageModel(false)
        quoterModel.apiMessageId = quoteMessageId
        quoterModel.identity = quoterIdentity
        quoterModel.quotedMessageId = quotedMessageId
        quoterModel.body = quoteComment
        quoterModel.type = MessageType.TEXT

        // Verify
        val content = extractQuoteV2(quoterModel, null, true, mockContactReceiver)
        assertNotNull(content)
        assertTrue(content.isQuoteV2)
        assertFalse(content.isQuoteV1)
        assertEquals("Deleted", content.quotedText)
        assertEquals(quoteComment, content.bodyText)
        assertNull(content.identity)
        assertEquals(quotedMessageId, content.quotedMessageId)
        assertNull(content.quotedMessageModel)
        assertNull(content.messageReceiver)
        assertNull(content.thumbnail)
        assertNull(content.icon)
    }

    @Test
    fun testExtractQuoteV2Recursive() {
        // Test data
        val quoterIdentity = "AABBCCDD"
        val quoteMessageId = "8899889988998899"
        val quoteComment = "That's a great idea!"

        // Create model where the quote references itself
        val quoterModel: AbstractMessageModel = MessageModel(false)
        quoterModel.apiMessageId = quoteMessageId
        quoterModel.identity = quoterIdentity
        quoterModel.quotedMessageId = quoteMessageId
        quoterModel.body = quoteComment
        quoterModel.type = MessageType.TEXT

        // Verify
        val content = extractQuoteV2(quoterModel, null, false, mockContactReceiver)
        assertNotNull(content)
        assertTrue(content.isQuoteV2)
        assertFalse(content.isQuoteV1)
        assertEquals(quoteComment, content.quotedText)
        assertEquals(quoteComment, content.bodyText)
        assertEquals(quoterIdentity, content.identity)
        assertEquals(quoteMessageId, content.quotedMessageId)
        assertNull(content.quotedMessageModel)
        assertEquals(mockContactReceiver, content.messageReceiver)
        assertNull(content.thumbnail)
        assertNull(content.icon)
    }

    companion object {
        /**
         * Helper function for v2 quotes.
         *
         * @param quoterModel The model that contains the quote.
         * @param quotedModel The quoted model.
         */
        private fun extractQuoteV2(
            quoterModel: AbstractMessageModel,
            quotedModel: AbstractMessageModel?,
            includeMessageModel: Boolean,
            receiver: MessageReceiver<*>,
        ): QuoteContent {
            // Mocks
            val mockContext = mockk<Context>()
            val mockMessageService = mockk<MessageService>()
            val mockUserService = mockk<UserService>()
            val mockFileService = mockk<FileService>()

            // Ensure that message service returns the correct models
            every {
                mockMessageService.getMessageModelByApiMessageIdAndReceiver(any(), receiver)
            } returns null
            every {
                mockMessageService.getMessageModelByApiMessageIdAndReceiver(quoterModel.apiMessageId, receiver)
            } returns quoterModel
            if (quotedModel != null) {
                every {
                    mockMessageService.getMessageModelByApiMessageIdAndReceiver(quotedModel.apiMessageId, receiver)
                } returns quotedModel
            }

            // Ensure that certain strings used by the quote can be returned
            every { mockContext.getString(R.string.video_placeholder) } returns "Video"
            every { mockContext.getString(R.string.quoted_message_deleted) } returns "Deleted"
            every { mockContext.getString(R.string.quote_not_found) } returns "NoReceiverMatch"

            // Return quote contents
            return QuoteUtil.extractQuoteV2(
                quoterModel,
                receiver,
                includeMessageModel,
                null,
                mockContext,
                mockMessageService,
                mockUserService,
                mockFileService,
            )
        }
    }
}
