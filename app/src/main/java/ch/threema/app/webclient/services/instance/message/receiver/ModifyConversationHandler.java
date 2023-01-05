/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2023 Threema GmbH
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

package ch.threema.app.webclient.services.instance.message.receiver;

import androidx.annotation.AnyThread;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;

import ch.threema.app.services.ConversationService;
import ch.threema.app.services.ConversationTagService;
import ch.threema.app.services.ConversationTagServiceImpl;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.Utils;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.TagModel;

/**
 * Process update/conversation requests from the browser.
 */
@WorkerThread
public class ModifyConversationHandler extends MessageReceiver {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ModifyConversationHandler");

	private static final String FIELD_IS_PINNED = "isStarred";

	@Retention(RetentionPolicy.SOURCE)
	@StringDef({
		Protocol.ERROR_INVALID_CONVERSATION,
		Protocol.ERROR_BAD_REQUEST,
	})
	private @interface ErrorCode {}

	private final MessageDispatcher responseDispatcher;
	private final ConversationService conversationService;
	private final ConversationTagService conversationTagService;

	@AnyThread
	public ModifyConversationHandler(MessageDispatcher responseDispatcher,
	                                 ConversationService conversationService,
	                                 ConversationTagService conversationTagService) {
		super(Protocol.SUB_TYPE_CONVERSATION);
		this.responseDispatcher = responseDispatcher;
		this.conversationService = conversationService;
		this.conversationTagService = conversationTagService;
	}

	@Override
	protected void receive(Map<String, Value> message) throws MessagePackException {
		logger.debug("Received update conversation message");

		// Process args
		final Map<String, Value> args = this.getArguments(message, false);
		if (!args.containsKey(Protocol.ARGUMENT_TEMPORARY_ID)
				|| !args.containsKey(Protocol.ARGUMENT_RECEIVER_ID)
				|| !args.containsKey(Protocol.ARGUMENT_RECEIVER_TYPE)) {
			logger.error("Invalid conversation update request, type, id or temporaryId not set");
			return;
		}
		final String temporaryId = args.get(Protocol.ARGUMENT_TEMPORARY_ID).asStringValue().toString();

		// Get conversation model
		final String id = args.get(Protocol.ARGUMENT_RECEIVER_ID).asStringValue().toString();
		final String type = args.get(Protocol.ARGUMENT_RECEIVER_TYPE).asStringValue().toString();
		final ch.threema.app.messagereceiver.MessageReceiver receiver;
		try {
			final Utils.ModelWrapper modelWrapper = new Utils.ModelWrapper(type, id);
			receiver = modelWrapper.getReceiver();
		} catch (ConversionException e) {
			logger.error("Conversion exception in ModifyConversationHandler", e);
			this.failed(temporaryId, Protocol.ERROR_INVALID_CONVERSATION);
			return;
		}
		final ConversationModel conversation = this.conversationService.refresh(receiver);

		// Process data
		final Map<String, Value> data = this.getData(message, true);
		if (data == null) {
			this.success(temporaryId);
			return;
		}
		if (data.containsKey(FIELD_IS_PINNED)) {
			// Get value
			final Value value = data.get(FIELD_IS_PINNED);
			if (!value.isBooleanValue()) {
				this.failed(temporaryId, Protocol.ERROR_BAD_REQUEST);
				return;
			}
			final boolean isPinned = value.asBooleanValue().getBoolean();

			// Tag model
			final TagModel pinTagModel = conversationTagService.getTagModel(ConversationTagServiceImpl.FIXED_TAG_PIN);
			if (isPinned) {
				this.conversationTagService.tag(conversation, pinTagModel);
			} else {
				this.conversationTagService.unTag(conversation, pinTagModel);
			}

			// TODO: This should be done at a central location whenever the data changes.
			this.conversationService.sort();
		}

		this.success(temporaryId);
	}

	/**
	 * Respond with confirmAction.
	 */
	private void success(String temporaryId) {
		logger.debug("Respond modify conversation success");
		this.sendConfirmActionSuccess(this.responseDispatcher, temporaryId);
	}

	/**
	 * Respond with an error code.
	 */
	private void failed(String temporaryId, @ErrorCode String errorCode) {
		logger.warn("Respond modify conversation failed ({})", errorCode);
		this.sendConfirmActionFailure(this.responseDispatcher, temporaryId, errorCode);
	}

	@Override
	protected boolean maybeNeedsConnection() {
		return false;
	}
}
