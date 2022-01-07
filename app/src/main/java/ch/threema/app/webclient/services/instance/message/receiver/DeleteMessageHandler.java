/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2022 Threema GmbH
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
import org.slf4j.LoggerFactory;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;

import ch.threema.app.services.MessageService;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import ch.threema.storage.models.AbstractMessageModel;

@WorkerThread
public class DeleteMessageHandler extends MessageReceiver {
	private static final Logger logger = LoggerFactory.getLogger(DeleteMessageHandler.class);

	private final MessageService messageService;
	private final MessageDispatcher responseDispatcher;

	@Retention(RetentionPolicy.SOURCE)
	@StringDef({
		Protocol.ERROR_INVALID_MESSAGE,
		Protocol.ERROR_BAD_REQUEST,
	})
	private @interface ErrorCode {}

	@AnyThread
	public DeleteMessageHandler(MessageDispatcher responseDispatcher,
	                            MessageService messageService) {
		super(Protocol.SUB_TYPE_MESSAGE);
		this.messageService = messageService;
		this.responseDispatcher = responseDispatcher;
	}

	@Override
	protected void receive(Map<String, Value> message) throws MessagePackException {
		logger.debug("Received delete request");
		Map<String, Value> args = this.getArguments(message, false, new String[]{
				Protocol.ARGUMENT_RECEIVER_ID,
				Protocol.ARGUMENT_MESSAGE_ID,
				Protocol.ARGUMENT_TEMPORARY_ID,
		});

		final String temporaryId = args.get(Protocol.ARGUMENT_TEMPORARY_ID).asStringValue().asString();
		final String messageIdStr = args.get(Protocol.ARGUMENT_MESSAGE_ID).asStringValue().asString();
		final int messageId = Integer.valueOf(messageIdStr);

		final ch.threema.app.messagereceiver.MessageReceiver receiver;
		try {
			receiver = this.getReceiver(args);
		} catch (ConversionException e) {
			logger.error("Exception", e);
			return;
		}

		if(receiver == null) {
			logger.error("invalid receiver");
			return;
		}

		//load message
		AbstractMessageModel messageModel = null;
		switch (receiver.getType())
		{
			case ch.threema.app.messagereceiver.MessageReceiver.Type_CONTACT:
				messageModel = this.messageService.getContactMessageModel(messageId, true);
				break;
			case ch.threema.app.messagereceiver.MessageReceiver.Type_GROUP:
				messageModel = this.messageService.getGroupMessageModel(messageId, true);
				break;
			case ch.threema.app.messagereceiver.MessageReceiver.Type_DISTRIBUTION_LIST:
				messageModel = this.messageService.getDistributionListMessageModel(messageId, true);
				break;
		}

		if(messageModel == null) {
			logger.error("no valid message model to delete found");
			this.failed(temporaryId, Protocol.ERROR_INVALID_MESSAGE);
			return;
		}

		this.messageService.remove(messageModel);
		this.success(temporaryId);
	}

	/**
	 * Respond with success true.
	 */
	private void success(String temporaryId) {
		logger.debug("Respond with delete message success");
		this.sendConfirmActionSuccess(this.responseDispatcher, temporaryId);
	}

	/**
	 * Respond with an error code.
	 */
	private void failed(String temporaryId, @ErrorCode String errorCode) {
		logger.warn("Respond with delete message failed ({})", errorCode);
		this.sendConfirmActionFailure(this.responseDispatcher, temporaryId, errorCode);
	}

	@Override
	protected boolean maybeNeedsConnection() {
		return false;
	}
}
