/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2021 Threema GmbH
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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.services.IdListService;
import ch.threema.app.services.LifetimeService;
import ch.threema.app.services.MessageService;
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.converter.Utils;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;

@WorkerThread
abstract public class MessageCreateHandler extends MessageReceiver {
	private static final Logger logger = LoggerFactory.getLogger(MessageCreateHandler.class);

	private static final String CONNECTION_SOURCE_TAG = "wc.createMessage";

	private final MessageDispatcher dispatcher;
	protected final MessageService messageService;
	private final LifetimeService lifetimeService;
	private final IdListService blacklistService;

	/**
	 * A validation error.
	 *
	 * If `propagate` is set to `true`, the error code will be returned to Threema Web.
	 */
	protected class MessageValidationException extends Exception {
		protected final String errorCode;
		protected final boolean propagate;

		protected MessageValidationException(String errorCode, boolean propagate) {
			this.errorCode = errorCode;
			this.propagate = propagate;
		}
	}

	@AnyThread
	public MessageCreateHandler(String subType,
	                            MessageDispatcher dispatcher,
	                            MessageService messageService,
	                            LifetimeService lifetimeService,
	                            IdListService blacklistService) {
		super(subType);
		this.dispatcher = dispatcher;
		this.messageService = messageService;
		this.lifetimeService = lifetimeService;
		this.blacklistService = blacklistService;

	}

	@Override
	protected void receive(Map<String, Value> message) throws MessagePackException {
		logger.debug("Received message create");
		Map<String, Value> args = this.getArguments(message, false);
		try {
			this.handle(this.getModel(args), message, args.containsKey(Protocol.ARGUMENT_TEMPORARY_ID)
				? args.get(Protocol.ARGUMENT_TEMPORARY_ID).asStringValue().toString()
				: null);
		} catch (ConversionException e) {
			logger.error("Exception", e);
		}
	}

	private void handle(
		@NonNull Utils.ModelWrapper receiverModel,
		@NonNull Map<String, Value> message,
		@NonNull String temporaryId
	) {
		logger.debug("Dispatching message create");
		try {
			this.lifetimeService.acquireConnection(CONNECTION_SOURCE_TAG);

			// Check if the contact is blocked
			ch.threema.app.messagereceiver.MessageReceiver receiver = receiverModel.getReceiver();
			if (receiver.getType() == receiver.Type_CONTACT) {
				ContactModel receiverContact = ((ContactMessageReceiver) receiver).getContact();
				if (receiverContact != null && this.blacklistService.has(receiverContact.getIdentity())) {
					throw new MessageCreateHandler.MessageValidationException("blocked", false);
				}
			}

			// Send message
			final AbstractMessageModel m = this.handle(
				MessageUtil.getAllReceivers(receiverModel.getReceiver()),
				message
			);
			if (m == null) {
				logger.warn("Message could not be sent");
				this.failed(message, "internalError");
				return;
			}

			// Send response
			final Map<String, Value> args = this.getArguments(message, false);
			this.send(this.dispatcher,
					// Data, including newly created message id
					new MsgpackObjectBuilder()
						.put(Protocol.ARGUMENT_MESSAGE_ID, String.valueOf(m.getId())),
					// Args
					new MsgpackObjectBuilder()
						.put(Protocol.ARGUMENT_SUCCESS, true)
						.put(Protocol.ARGUMENT_RECEIVER_TYPE, args.get(Protocol.ARGUMENT_RECEIVER_TYPE).asStringValue().toString())
						.put(Protocol.ARGUMENT_RECEIVER_ID, args.get(Protocol.ARGUMENT_RECEIVER_ID).asStringValue().toString())
						.put(Protocol.ARGUMENT_TEMPORARY_ID, temporaryId));
		} catch(MessageValidationException e) {
			logger.error("Exception", e);
			if (e.propagate) {
				this.failed(message, e.errorCode);
			} else {
				this.failed(message, "internalError");
			}
		} catch(Exception e) {
			logger.error("Exception", e);
			this.failed(message, "internalError");
		} finally {
			this.lifetimeService.releaseConnectionLinger(CONNECTION_SOURCE_TAG, 5000);
		}
	}

	/**
	 * respond with the a error code and success false
	 */
	private void failed(Map<String, Value> message, String errorCode) {
		logger.warn("Respond message create failed ({})", errorCode);
		Map<String, Value> args = this.getArguments(message, false);
		if(args.containsKey(Protocol.ARGUMENT_TEMPORARY_ID)) {
			this.send(this.dispatcher,
					new MsgpackObjectBuilder()
							.putNull(Protocol.ARGUMENT_MESSAGE_ID),
					new MsgpackObjectBuilder()
							.put(Protocol.ARGUMENT_SUCCESS, false)
							.put(Protocol.ARGUMENT_ERROR, errorCode)
							.put(Protocol.ARGUMENT_RECEIVER_TYPE, args.get(Protocol.ARGUMENT_RECEIVER_TYPE).asStringValue().toString())
							.put(Protocol.ARGUMENT_RECEIVER_ID, args.get(Protocol.ARGUMENT_RECEIVER_ID).asStringValue().toString())
							.put(Protocol.ARGUMENT_TEMPORARY_ID, args.get(Protocol.ARGUMENT_TEMPORARY_ID).asStringValue().toString())
			);
		}
	}

	abstract protected @Nullable AbstractMessageModel handle(
		List<ch.threema.app.messagereceiver.MessageReceiver> receivers,
		Map<String, Value> message
	) throws Exception;

	@Override
	final protected boolean maybeNeedsConnection() {
		// This is already handled using the lifetime service inside the `handle` method.
		return false;
	}
}
