/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2023 Threema GmbH
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

package ch.threema.app.utils;

import android.content.Context;

import org.junit.Test;
import org.mockito.Mockito;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.R;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver.MessageReceiverType;
import ch.threema.app.services.FileService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.QuoteUtil.QuoteContent;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.MessageType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class QuoteUtilTest {

	// Quotes V1

	@Test
	public void testParseQuoteV1Invalid() {
		assertNull(QuoteUtil.parseQuoteV1(""));
		assertNull(QuoteUtil.parseQuoteV1("asdf"));
		assertNull(QuoteUtil.parseQuoteV1("> asdf"));
		assertNull(QuoteUtil.parseQuoteV1("> ABCDEFG: Quote\n\nBody"));
		assertNull(QuoteUtil.parseQuoteV1("> ABCDEFGHI: Quote\n\nBody"));
	}

	private void testQuoteV1(
		@Nullable QuoteContent content,
		String expectedIdentity,
		String expectedQuotedText,
		String expectedBodyText
	) {
		assertNotNull(content);
		assertTrue(content.isQuoteV1());
		assertFalse(content.isQuoteV2());
		assertEquals(expectedIdentity, content.identity);
		assertEquals(expectedQuotedText, content.quotedText);
		assertEquals(expectedBodyText, content.bodyText);
		assertNull(content.icon);
		assertNull(content.thumbnail);
		assertNull(content.quotedMessageId);
		assertNull(content.quotedMessageModel);
		assertNull(content.receiverType);
	}

	@Test
	public void testParseQuoteV1() {
		QuoteContent content = QuoteUtil.parseQuoteV1("> ABCDEFGH: hello\n> goodbye\n\nyes indeed!");
		testQuoteV1(content, "ABCDEFGH", "hello\ngoodbye", "yes indeed!");
	}

	@Test
	public void testParseQuoteV1NoEmptyLine() {
		QuoteContent content = QuoteUtil.parseQuoteV1("> ABCDEFGH: hello\ngoodbye\n\nyes indeed!");
		testQuoteV1(content, "ABCDEFGH", "hello", "goodbye\n\nyes indeed!");
	}

	@Test
	public void testParseQuoteV1GatewayId() {
		QuoteContent content = QuoteUtil.parseQuoteV1("> *THREEMA: hello\n> goodbye\n\nyes indeed!");
		testQuoteV1(content, "*THREEMA", "hello\ngoodbye", "yes indeed!");
	}

	// Quotes V2

	/**
	 * Helper function for v2 quotes.
	 *
	 * @param quoterModel The model that contains the quote.
	 * @param quotedModel The quoted model.
	 * @param receiverType The receiver type.
	 */
	private static QuoteContent extractQuoteV2(
		@NonNull AbstractMessageModel quoterModel,
		@Nullable AbstractMessageModel quotedModel,
		boolean includeMessageModel,
		@MessageReceiverType int receiverType
	) {
		// Mocks
		final Context mockContext = Mockito.mock(Context.class);
		final MessageService mockMessageService = Mockito.mock(MessageService.class);
		final UserService mockUserService = Mockito.mock(UserService.class);
		final FileService mockFileService = Mockito.mock(FileService.class);

		// Ensure that message service returns the correct models
		Mockito.when(mockMessageService.getMessageModelByApiMessageId(quoterModel.getApiMessageId(), receiverType))
			.thenReturn(quoterModel);
		if (quotedModel != null) {
			Mockito.when(mockMessageService.getMessageModelByApiMessageId(quotedModel.getApiMessageId(), receiverType))
				.thenReturn(quotedModel);
		}

		// Ensure that certain strings used by the quote can be returned
		Mockito.when(mockContext.getString(R.string.video_placeholder)).thenReturn("Video");
		Mockito.when(mockContext.getString(R.string.quoted_message_deleted)).thenReturn("Deleted");
		Mockito.when(mockContext.getString(R.string.quote_not_found)).thenReturn("NoReceiverMatch");

		// Return quote contents
		return QuoteUtil.extractQuoteV2(
			quoterModel, receiverType, includeMessageModel, null,
			mockContext, mockMessageService, mockUserService, mockFileService
		);
	}

	@Test
	public void testExtractQuoteV2TextNoIncludeMessageModel() {
		// Test data
		final String quoterIdentity = "AABBCCDD";
		final String quotedIdentity = "AABBCCDD";
		final String quotedMessageId = "0011223344556677";
		final String quoteMessageId = "8899889988998899";
		final String quotedBody = "Shall we eat lunch?";
		final String quoteComment = "That's a great idea!";
		final int receiverType = MessageReceiver.Type_CONTACT;

		// Create quoted model
		final AbstractMessageModel quotedModel = new MessageModel(false);
		quotedModel.setApiMessageId(quotedMessageId);
		quotedModel.setIdentity(quotedIdentity);
		quotedModel.setBody(quotedBody);
		quotedModel.setType(MessageType.TEXT);

		// Create quoter model
		final AbstractMessageModel quoterModel = new MessageModel(false);
		quoterModel.setApiMessageId(quoteMessageId);
		quoterModel.setIdentity(quoterIdentity);
		quoterModel.setQuotedMessageId(quotedMessageId);
		quoterModel.setBody(quoteComment);
		quoterModel.setType(MessageType.TEXT);

		// Verify
		final QuoteContent content = extractQuoteV2(quoterModel, quotedModel, false, receiverType);
		assertNotNull(content);
		assertTrue(content.isQuoteV2());
		assertFalse(content.isQuoteV1());
		assertEquals(content.quotedText, quotedBody);
		assertEquals(content.bodyText, quoteComment);
		assertEquals(content.identity, quotedIdentity);
		assertEquals(content.quotedMessageId, quotedMessageId);
		assertEquals(content.receiverType, (Integer) receiverType);
		assertNull(content.quotedMessageModel);
		assertNull(content.thumbnail);
		assertNull(content.icon);
	}

	@Test
	public void testExtractQuoteV2TextNoIncludeMessageModelNoReceiverMatch() {
		// Test data
		final String quoterIdentity = "AABBCCDD";
		final String quotedIdentity = "CCDDEEFF";
		final String quotedMessageId = "0011223344556677";
		final String quoteMessageId = "8899889988998899";
		final String quotedBody = "Shall we eat lunch?";
		final String quoteComment = "That's a great idea!";
		final int receiverType = MessageReceiver.Type_CONTACT;

		// Create quoted model
		final AbstractMessageModel quotedModel = new MessageModel(false);
		quotedModel.setApiMessageId(quotedMessageId);
		quotedModel.setIdentity(quotedIdentity);
		quotedModel.setBody(quotedBody);
		quotedModel.setType(MessageType.TEXT);

		// Create quoter model
		final AbstractMessageModel quoterModel = new MessageModel(false);
		quoterModel.setApiMessageId(quoteMessageId);
		quoterModel.setIdentity(quoterIdentity);
		quoterModel.setQuotedMessageId(quotedMessageId);
		quoterModel.setBody(quoteComment);
		quoterModel.setType(MessageType.TEXT);

		// Verify
		final QuoteContent content = extractQuoteV2(quoterModel, quotedModel, false, receiverType);
		assertNotNull(content);
		assertTrue(content.isQuoteV2());
		assertFalse(content.isQuoteV1());
		assertEquals(content.quotedText, "NoReceiverMatch");
		assertEquals(content.bodyText, quoteComment);
		assertEquals(content.quotedMessageId, quotedMessageId);
		assertNotEquals(content.identity, quotedIdentity);
		assertNull(content.receiverType);
		assertNull(content.quotedMessageModel);
		assertNull(content.thumbnail);
		assertNull(content.icon);
	}

	@Test
	public void testExtractQuoteV2VideoIncludeMessageModel() {
		// Test data
		final String quoterIdentity = "AABBCCDD";
		final String quotedIdentity = "AABBCCDD";
		final String quotedMessageId = "0011223344556677";
		final String quoteMessageId = "8899889988998899";
		final String quoteComment = "That's a great idea!";
		final int receiverType = MessageReceiver.Type_CONTACT;

		// Create quoted model
		final AbstractMessageModel quotedModel = new MessageModel(false);
		quotedModel.setApiMessageId(quotedMessageId);
		quotedModel.setIdentity(quotedIdentity);
		quotedModel.setType(MessageType.VIDEO);
		quotedModel.setBody("");

		// Create quoter model
		final AbstractMessageModel quoterModel = new MessageModel(false);
		quoterModel.setApiMessageId(quoteMessageId);
		quoterModel.setIdentity(quoterIdentity);
		quoterModel.setQuotedMessageId(quotedMessageId);
		quoterModel.setBody(quoteComment);
		quoterModel.setType(MessageType.TEXT);

		// Verify
		final QuoteContent content = extractQuoteV2(quoterModel, quotedModel, true, receiverType);
		assertNotNull(content);
		assertTrue(content.isQuoteV2());
		assertFalse(content.isQuoteV1());
		assertEquals("Video", content.quotedText);
		assertEquals(quoteComment, content.bodyText);
		assertEquals(quotedIdentity, content.identity);
		assertEquals(quotedMessageId, content.quotedMessageId);
		assertEquals(quotedModel, content.quotedMessageModel);
		assertEquals(content.receiverType, (Integer) receiverType);
		assertNull(content.thumbnail);
		assertEquals((Integer) R.drawable.ic_movie_filled, content.icon);
	}

	@Test
	public void testExtractQuoteV2Deleted() {
		// Test data
		final String quoterIdentity = "AABBCCDD";
		final String quotedMessageId = "0000000000000000";
		final String quoteMessageId = "8899889988998899";
		final String quoteComment = "That's a great idea!";
		final int receiverType = MessageReceiver.Type_CONTACT;

		// Create quoter model with an invalid message id
		final AbstractMessageModel quoterModel = new MessageModel(false);
		quoterModel.setApiMessageId(quoteMessageId);
		quoterModel.setIdentity(quoterIdentity);
		quoterModel.setQuotedMessageId(quotedMessageId);
		quoterModel.setBody(quoteComment);
		quoterModel.setType(MessageType.TEXT);

		// Verify
		final QuoteContent content = extractQuoteV2(quoterModel, null, true, receiverType);
		assertNotNull(content);
		assertTrue(content.isQuoteV2());
		assertFalse(content.isQuoteV1());
		assertEquals(content.quotedText, "Deleted");
		assertEquals(content.bodyText, quoteComment);
		assertNull(content.identity);
		assertEquals(content.quotedMessageId, quotedMessageId);
		assertNull(content.quotedMessageModel);
		assertNull(content.receiverType);
		assertNull(content.thumbnail);
		assertNull(content.icon);
	}

	@Test
	public void testExtractQuoteV2Recursive() {
		// Test data
		final String quoterIdentity = "AABBCCDD";
		final String quoteMessageId = "8899889988998899";
		final String quoteComment = "That's a great idea!";
		final int receiverType = MessageReceiver.Type_CONTACT;

		// Create model where the quote references itself
		final AbstractMessageModel quoterModel = new MessageModel(false);
		quoterModel.setApiMessageId(quoteMessageId);
		quoterModel.setIdentity(quoterIdentity);
		quoterModel.setQuotedMessageId(quoteMessageId);
		quoterModel.setBody(quoteComment);
		quoterModel.setType(MessageType.TEXT);

		// Verify
		final QuoteContent content = extractQuoteV2(quoterModel, null, false, receiverType);
		assertNotNull(content);
		assertTrue(content.isQuoteV2());
		assertFalse(content.isQuoteV1());
		assertEquals(content.quotedText, quoteComment);
		assertEquals(content.bodyText, quoteComment);
		assertEquals(content.identity, quoterIdentity);
		assertEquals(content.quotedMessageId, quoteMessageId);
		assertNull(content.quotedMessageModel);
		assertEquals((Integer) receiverType, content.receiverType);
		assertNull(content.thumbnail);
		assertNull(content.icon);
	}
}
