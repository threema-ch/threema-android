/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2024 Threema GmbH
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


import static ch.threema.app.messagereceiver.MessageReceiver.Type_CONTACT;
import static ch.threema.app.messagereceiver.MessageReceiver.Type_DISTRIBUTION_LIST;
import static ch.threema.app.messagereceiver.MessageReceiver.Type_GROUP;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.slf4j.Logger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.cache.ThumbnailCache;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.FileService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.UserService;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.data.MessageContentsType;

public class QuoteUtil {
	private static final Logger logger = LoggingUtil.getThreemaLogger("QuoteUtil");

	private static final Pattern bodyMatchPattern = Pattern.compile("(?sm)(\\A> .*?)^(?!> ).+");
	private static final Pattern quoteV1MatchPattern = Pattern.compile("(?sm)\\A> ([A-Z0-9*]{8}): (.*?)^(?!> ).+");
	private static final Pattern quoteV2MatchPattern = Pattern.compile("(?sm)\\A> quote #([0-9a-f]{16})\\n\\n(.*)");

	private static final String QUOTE_V2_PATTERN = "> quote #[0-9a-f]{16}\\n\\n";
	private static final int QUOTE_V2_SIGNATURE_LENGTH = 27;
	private static final String QUOTE_PREFIX = "> ";

	public static final int QUOTE_TYPE_NONE = 0;
	public static final int QUOTE_TYPE_V1 = 1;
	public static final int QUOTE_TYPE_V2 = 2;

	private static final int MAX_QUOTE_CONTENTS_LENGTH = 256;

	/**
	 * Get all content to be displayed in a message containing a quote
	 *
	 * @param includeMessageModel If set to `true`, the quoted message model is included in the `QuoteContent`
	 */
	public static @Nullable QuoteContent getQuoteContent(
		@NonNull AbstractMessageModel messageModel,
		@NonNull MessageReceiver messageReceiver,
		boolean includeMessageModel,
		@Nullable ThumbnailCache thumbnailCache,
		@NonNull Context context,
		@NonNull MessageService messageService,
		@NonNull UserService userService,
		@NonNull FileService fileService
	) {
		if (messageModel.getQuotedMessageId() != null) {
			return extractQuoteV2(
				messageModel, messageReceiver, includeMessageModel, thumbnailCache,
				context, messageService, userService, fileService
			);
		} else {
			String text = messageModel.getBody();
			if (!TestUtil.empty(text)) {
				return parseQuoteV1(text);
			}
			return null;
		}
	}

	/**
	 * Parse quote v1 contents.
	 *
	 *  A v1 quote message looks like this
	 *
	 *  > ABCDEFGH: Quoted text
	 *  > Quoted text ctd.
	 *
	 *  Body text
	 *  Body text ctd.
	 */
	static @Nullable QuoteContent parseQuoteV1(@NonNull String text) {
		final Matcher match = quoteV1MatchPattern.matcher(text);
		try {
			if (match.find() && match.groupCount() == 2) {
				final String identity = match.group(1);
				final String quotedTextRaw = match.group(2);
				if (identity != null && quotedTextRaw != null) {
					final String bodyText = text.substring(match.end(2)).trim();
					final String quotedText = quotedTextRaw
						.replace("\n" + QUOTE_PREFIX, "\n")
						.trim();
					return QuoteContent.createV1(identity, quotedText, bodyText);
				}
			}
		} catch (Exception e) {
			logger.error("Could not process v1 quote", e);
		}
		return null;
	}

	/**
	 * Extract quote v2 contents.
	 *
	 * @param includeMessageModel If set to `true`, the quoted message model is included in the `QuoteContent`
	 */
	static @NonNull QuoteContent extractQuoteV2(
		@NonNull AbstractMessageModel messageModel,
		MessageReceiver messageReceiver,
		boolean includeMessageModel,
		@Nullable ThumbnailCache thumbnailCache,
		@NonNull Context context,
		@NonNull MessageService messageService,
		@NonNull UserService userService,
		@NonNull FileService fileService
	) {
		final String quotedMessageId = messageModel.getQuotedMessageId();
		final String bodyText = messageModel.getBody();
		final String placeholder;

		// Retrieve message model referenced by quote
		final AbstractMessageModel quotedMessageModel = messageService.getMessageModelByApiMessageIdAndReceiver(
			quotedMessageId,
			messageReceiver
		);

		if (quotedMessageModel != null) {
			boolean receiverMatch = false;
			switch (messageReceiver.getType()) {
				case Type_CONTACT:
					receiverMatch = quotedMessageModel.getIdentity().equals(messageModel.getIdentity());
					break;
				case Type_GROUP:
					receiverMatch = ((GroupMessageModel) quotedMessageModel).getGroupId() == ((GroupMessageModel) messageModel).getGroupId();
					break;
				case Type_DISTRIBUTION_LIST:
					receiverMatch = ((DistributionListMessageModel) quotedMessageModel).getDistributionListId() == ((DistributionListMessageModel) messageModel).getDistributionListId();
					break;
			}

			if (receiverMatch) {
				final MessageUtil.MessageViewElement viewElement = MessageUtil.getViewElement(context, quotedMessageModel);
				final String identity = quotedMessageModel.isOutbox() ? userService.getIdentity() : quotedMessageModel.getIdentity();
				final @NonNull String quotedText = TestUtil.empty(viewElement.text) ? (viewElement.placeholder != null ? viewElement.placeholder : "") : viewElement.text;
				final @DrawableRes Integer icon = viewElement.icon;
				Bitmap thumbnail = null;
				if (quotedMessageModel.getMessageContentsType() != MessageContentsType.VOICE_MESSAGE) {
					// ignore thumbnails of voice messages
					try {
						thumbnail = fileService.getMessageThumbnailBitmap(quotedMessageModel, thumbnailCache);
					} catch (Exception ignore) {
					}
				}
				return QuoteContent.createV2(
						identity,
						quotedText,
						bodyText,
						quotedMessageId,
						includeMessageModel ? quotedMessageModel : null,
						messageReceiver,
						thumbnail,
						icon
				);
			} else {
				placeholder = context.getString(R.string.quote_not_found);
			}
		} else {
			placeholder = context.getString(R.string.quoted_message_deleted);
		}
		return QuoteContent.createV2Deleted(quotedMessageId, placeholder, bodyText);
	}

	/**
	 * Extract body text and quoted message reference from text string containing a quote v2 signature and add to MessageModel
	 * If no valid quote v2 signature is found, add full input text to body
	 * @param messageModel where to add extracted information
	 * @param text source text containing a quote v2 signature
	 */
	public static void addBodyAndQuotedMessageId(@NonNull AbstractMessageModel messageModel, @Nullable String text) {
		if (!TestUtil.empty(text)) {
			Matcher match = quoteV2MatchPattern.matcher(text);
			try {
				if (match.find()) {
					if (match.groupCount() == 2) {
						messageModel.setQuotedMessageId(match.group(1));
						if (!TestUtil.empty(match.group(2))) {
							messageModel.setBody(match.group(2).trim());
						} else {
							messageModel.setBody("");
						}
						return;
					}
				}
			} catch (Exception e) {
				//
			}
		}
		messageModel.setBody(text);
	}

	/**
	 * Check if the body of this message model contains a quote signature
	 * @param messageModel message model to check for quote signature
	 * @return quote type or QUOTE_TYPE_NONE if no quote was found
	 */
	public static int getQuoteType(AbstractMessageModel messageModel) {
		if (messageModel != null) {
			if (!TestUtil.empty(messageModel.getQuotedMessageId())) {
				return QUOTE_TYPE_V2;
			}
			if (!TestUtil.empty(messageModel.getBody())) {
				if (isQuoteV1(messageModel.getBody())) {
					return QUOTE_TYPE_V1;
				}
			}
		}
		return QUOTE_TYPE_NONE;
	}

	public static boolean isQuoteV1(String body) {
		return body != null && body.length() > 10 && body.startsWith(QUOTE_PREFIX) && body.charAt(10) == ':' && body.contains("\n");
	}

	public static boolean isQuoteV2(String body) {
		return body != null && body.length() > QUOTE_V2_SIGNATURE_LENGTH && body.substring(0, QUOTE_V2_SIGNATURE_LENGTH).matches(QUOTE_V2_PATTERN);
	}

	/**
	 * get body text of a message containing a quote
	 * this is safe to call on message models without a quote
	 * @param messageModel
	 * @param substituteAndTruncate if true, result is truncated at MAX_QUOTE_CONTENTS_LENGTH and ellipsis is added.
	 *                              if no body text is present, alternative text sources such as captions or file names are considered
	 * @return body text
	 */
	public static String getMessageBody(AbstractMessageModel messageModel, boolean substituteAndTruncate) {
		String text;
		if (messageModel.getType() == MessageType.TEXT) {
			text = messageModel.getBody();
		} else {
			text = messageModel.getCaption();
		}

		if (substituteAndTruncate && TestUtil.empty(text)) {
			text = messageModel.getCaption();
			if (TestUtil.empty(text)) {
				MessageUtil.MessageViewElement viewElement = MessageUtil.getViewElement(ThreemaApplication.getAppContext(), messageModel);
				text = viewElement.text;
				if (text == null) {
					text = viewElement.placeholder;
				}
			}
		}

		if (text != null) {
			if (QuoteUtil.isQuoteV1(text)) {
				Matcher match = bodyMatchPattern.matcher(messageModel.getBody());
				try {
					if (match.find()) {
						if (match.groupCount() == 1) {
							text = messageModel.getBody().substring(match.end(1)).trim();
						}
					}
				} catch (Exception e) {
					//
				}
			}

			if (substituteAndTruncate) {
				text = truncateQuote(text);
			}
		}

		return text;
	}

	private static String truncateQuote(String text) {
		if (text.length() > MAX_QUOTE_CONTENTS_LENGTH) {
			text = Utils.truncateUTF8String(text, MAX_QUOTE_CONTENTS_LENGTH);
			text += "…";
		}
		return text;
	}


	/**
	 * Check if the supplied message can be quoted
	 * @param messageModel
	 * @return true if the message can be quoted, false otherwise
	 */
	public static boolean isQuoteable(@NonNull AbstractMessageModel messageModel) {
		MessageType messageType = messageModel.getType();
		if (messageType != null) {
			switch (messageModel.getType()) {
				case IMAGE:
				case FILE:
				case VIDEO:
				case VOICEMESSAGE:
				case TEXT:
				case BALLOT:
				case LOCATION:
					return messageModel.getApiMessageId() != null;
				default:
					return false;
			}
		}
		return false;
	}

	/**
	 * Append quoting to text
	 *
	 * TODO: create unit tests!
	 *
	 * @param text
	 * @param quoteIdentity
	 * @param quoteText
	 * @return
	 */
	public static String quote(String text, @Nullable String quoteIdentity, @Nullable String quoteText, @NonNull AbstractMessageModel messageModel) {
		//do not quote if identity or quoting text is empty or null
		if(TestUtil.empty(quoteIdentity, quoteText)) {
			return text;
		}

		return "> quote #" + messageModel.getApiMessageId() + "\n\n" + text;
	}

	public static class QuoteContent {
		public @NonNull String quotedText;
		public @NonNull String bodyText;
		public @Nullable String identity;
		public @Nullable String quotedMessageId;
		public @Nullable AbstractMessageModel quotedMessageModel;
		public @Nullable MessageReceiver messageReceiver;
		public @Nullable Bitmap thumbnail;
		public @Nullable @DrawableRes Integer icon;

		private QuoteContent(
			@NonNull String quotedText,
			@NonNull String bodyText
		) {
			this.quotedText = quotedText;
			this.bodyText = bodyText;
		}

		/**
		 * Create a v1 quote.
		 */
		public static @NonNull QuoteContent createV1(
			@NonNull String identity,
			@NonNull String quotedText,
			@NonNull String bodyText
		) {
			final QuoteContent content = new QuoteContent(quotedText, bodyText);
			content.identity = identity;
			return content;
		}

		/**
		 * Create a v2 quote for a known message.
		 */
		public static @NonNull QuoteContent createV2(
			@NonNull String identity,
			@NonNull String quotedText,
			@NonNull String bodyText,
			@NonNull String quotedMessageId,
			@Nullable AbstractMessageModel quotedMessageModel,
			@Nullable MessageReceiver messageReceiver,
			@Nullable Bitmap thumbnail,
			@Nullable @DrawableRes Integer icon
		) {
			final QuoteContent content = new QuoteContent(quotedText, bodyText);
			content.identity = identity;
			content.quotedMessageId = quotedMessageId;
			content.quotedMessageModel = quotedMessageModel;
			content.messageReceiver = messageReceiver;
			content.thumbnail = thumbnail;
			content.icon = icon;
			return content;
		}

		/**
		 * Create a v2 quote for a deleted target message.
		 *
		 * Thie `quotedText` should be set to `R.string.quoted_message_deleted`.
		 */
		public static @NonNull QuoteContent createV2Deleted(
			@NonNull String quotedMessageId,
			@NonNull String quotedText,
			@NonNull String bodyText
		) {
			final QuoteContent content = new QuoteContent(quotedText, bodyText);
			content.quotedMessageId = quotedMessageId;
			return content;
		}

		public boolean isQuoteV1() {
			return this.quotedMessageId == null;
		}

		public boolean isQuoteV2() {
			return this.quotedMessageId != null;
		}
	}
}
