/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2023 Threema GmbH
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

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

import androidx.annotation.AnyThread;
import androidx.annotation.WorkerThread;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.MessageService;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.Message;
import ch.threema.app.webclient.converter.MsgpackBuilder;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.filters.MessageFilter;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.AbstractMessageModel;

/**
 * Webclient is requesting messages.
 */
@WorkerThread
public class MessageRequestHandler extends MessageReceiver {
	private static final Logger logger = LoggingUtil.getThreemaLogger("MessageRequestHandler");

	private final MessageDispatcher dispatcher;
	private final MessageService messageService;
	private final DeadlineListService hiddenChatService;
	private final Listener listener;

	@WorkerThread
	public interface Listener {
		void onReceive(ch.threema.app.messagereceiver.MessageReceiver receiver);
	}

	@AnyThread
	public MessageRequestHandler(MessageDispatcher dispatcher,
	                             MessageService messageService,
	                             DeadlineListService hiddenChatService,
	                             Listener listener
	                             ) {
		super(Protocol.SUB_TYPE_MESSAGES);

		this.dispatcher = dispatcher;
		this.messageService = messageService;
		this.hiddenChatService = hiddenChatService;
		this.listener = listener;
	}

	@Override
	protected void receive(Map<String, Value> message) throws MessagePackException {
		logger.debug("Received message request");
		Map<String, Value> args = this.getArguments(message, false);

		// Get required arguments
		final String type = args.get(Protocol.ARGUMENT_RECEIVER_TYPE).asStringValue().asString();
		final String receiverId = args.get(Protocol.ARGUMENT_RECEIVER_ID).asStringValue().asString();

		// Get reference id
		Integer refMsgId = null;
		if (args.containsKey(Protocol.ARGUMENT_REFERENCE_MSG_ID)) {
			final String refMsgIdStr = args.get(Protocol.ARGUMENT_REFERENCE_MSG_ID).asStringValue().asString();
			refMsgId = Integer.valueOf(refMsgIdStr);
		}

		try {
			ch.threema.app.messagereceiver.MessageReceiver receiver = this.getReceiver(args);

			if(this.hiddenChatService.has(receiver.getUniqueIdString())) {
				//ignore it
				logger.debug("do not reply with messages on hidden chat");
				return;
			}
			if (this.listener != null) {
				this.listener.onReceive(receiver);
			}
			final MsgpackObjectBuilder responseArgs = new MsgpackObjectBuilder()
					.put(Protocol.ARGUMENT_RECEIVER_TYPE, type)
					.put(Protocol.ARGUMENT_RECEIVER_ID, receiverId)
					.maybePut(Protocol.ARGUMENT_REFERENCE_MSG_ID, String.valueOf(refMsgId), refMsgId != null);
			this.respond(receiver, refMsgId, responseArgs);
		} catch (ConversionException e) {
			logger.error("Exception", e);
		}
	}

	private void respond(ch.threema.app.messagereceiver.MessageReceiver receiver, Integer refMsgId, MsgpackObjectBuilder args) {
		// Set refMsgId in filter
		MessageFilter messageFilter = new MessageFilter();
		messageFilter.setPageReferenceId(refMsgId);

		try {
			// Get messages
			final List<AbstractMessageModel> messages = messageService.getMessagesForReceiver(
					receiver, messageFilter, false);

			// Set additional args
			boolean hasMore = messages != null
					//if the filter defined with a page size
					&& messageFilter.getPageSize() > 0
					&& messages.size() > messageFilter.getRealPageSize();
			args.put("more", hasMore);


			if(messages != null && hasMore) {
				messages.remove(messages.size()-1);
			}

			// Convert and send messages
			List<MsgpackBuilder> data = Message.convert(messages, receiver, true);
			logger.debug("Sending message response");
			this.send(this.dispatcher, data, args);
		} catch (ConversionException e) {
			logger.error("Exception", e);
		}
	}

	@Override
	protected boolean maybeNeedsConnection() {
		return false;
	}
}
