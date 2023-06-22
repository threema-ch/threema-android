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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import androidx.annotation.AnyThread;
import androidx.annotation.WorkerThread;
import ch.threema.app.BuildConfig;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.services.IdListService;
import ch.threema.app.services.LifetimeService;
import ch.threema.app.services.MessageService;
import ch.threema.app.utils.QuoteUtil;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ServerMessageModel;

@WorkerThread
public class TextMessageCreateHandler extends MessageCreateHandler {
	private static final Logger logger = LoggingUtil.getThreemaLogger("TextMessageCreateHandler");

	private static final String FIELD_TEXT = "text";
	private static final String FIELD_QUOTE = "quote";
	private static final String FIELD_QUOTE_IDENTITY = "identity";
	private static final String FIELD_QUOTE_TEXT = "text";
	private static final String FIELD_QUOTE_MESSAGE_ID = "messageId";

	@AnyThread
	public TextMessageCreateHandler(MessageDispatcher dispatcher,
	                                MessageService messageService,
	                                LifetimeService lifetimeService,
	                                IdListService blackListService) {
		super(Protocol.SUB_TYPE_TEXT_MESSAGE, dispatcher, messageService, lifetimeService, blackListService);
	}

	@Override
	protected AbstractMessageModel handle(List<ch.threema.app.messagereceiver.MessageReceiver> receivers, Map<String, Value> message) throws Exception {
		final Map<String, Value> messageData = this.getData(message, false, new String[] {
				TextMessageCreateHandler.FIELD_TEXT,
		});

		// Get text
		String text = messageData.get(FIELD_TEXT).asStringValue().asString();

		// Handle quoted messages
		if (messageData.containsKey(FIELD_QUOTE)) {
			try {
				final Map<String, Value> quoteMap = this.getMap(
					messageData,
					FIELD_QUOTE,
					false,
					new String[]{
						FIELD_QUOTE_IDENTITY,
						FIELD_QUOTE_TEXT,
						FIELD_QUOTE_MESSAGE_ID,
					}
				);

				AbstractMessageModel quotedMessageModel = null;
				int quotedMessageID = Integer.valueOf(quoteMap.get(FIELD_QUOTE_MESSAGE_ID).toString());
				if (receivers.get(0) instanceof ContactMessageReceiver) {
					quotedMessageModel = messageService.getContactMessageModel(quotedMessageID, true);
				} else if (receivers.get(0) instanceof GroupMessageReceiver) {
					quotedMessageModel = messageService.getGroupMessageModel(quotedMessageID, true);
				} else {
					logger.info("Unsupported receiver type for quotes");
				}

				if (quotedMessageModel != null) {
					text = QuoteUtil.quote(text,
						quoteMap.get(FIELD_QUOTE_IDENTITY).toString(),
						quoteMap.get(FIELD_QUOTE_TEXT).toString(),
						quotedMessageModel);
				}
			}
			catch (MessagePackException x) {
				logger.error("Ignoring MessagePackException when handling quote", x);
			}
		}

		// Validate message length
		if (text.getBytes(StandardCharsets.UTF_8).length > ProtocolDefines.MAX_TEXT_MESSAGE_LEN) {
			throw new MessageCreateHandler
				.MessageValidationException(Protocol.ERROR_VALUE_TOO_LONG, true);
		}

		AbstractMessageModel firstMessageModel = null;

		for (ch.threema.app.messagereceiver.MessageReceiver receiver: receivers) {
			AbstractMessageModel model = this.messageService.sendText(text, receiver);

			if(firstMessageModel == null) {
				firstMessageModel = model;
			}

			//enabled only in debug version
			if(BuildConfig.DEBUG) {
				if (receiver instanceof ContactMessageReceiver) {
					if (((ContactMessageReceiver) receiver).getContact().getIdentity().equals(ThreemaApplication.ECHO_USER_IDENTITY)
							&& text.startsWith("alert")) {
						String[] pieces = text.split("\\s+");
						if (pieces.length >= 2) {

							StringBuilder alertMessageTmp = new StringBuilder();
							for (int n = 2; n < pieces.length; n++) {
								alertMessageTmp.append(pieces[n]).append(n == pieces.length -1 ? "" : " ");
							}
							ServiceManager serviceManager = ThreemaApplication.getServiceManager();
							DatabaseServiceNew databaseService = null;
							if (serviceManager != null) {
								databaseService = serviceManager.getDatabaseServiceNew();
							}
							final String alertMessage;
							ServerMessageModel serverMessageModel;
							switch (pieces[1]) {
								case "error":
									if (alertMessageTmp.length() == 0) {
										alertMessage = "test error message";
									} else {
										alertMessage = alertMessageTmp.toString();
									}
									// Store server message into database
									serverMessageModel = new ServerMessageModel(alertMessage, ServerMessageModel.TYPE_ERROR);
									if (databaseService != null) {
										databaseService.getServerMessageModelFactory().storeServerMessageModel(serverMessageModel);
									}
									ListenerManager.serverMessageListeners.handle(listener -> listener.onError(serverMessageModel));
									break;
								case "alert":
									if (alertMessageTmp.length() == 0) {
										alertMessage = "test alert message";
									} else {
										alertMessage = alertMessageTmp.toString();
									}
									serverMessageModel = new ServerMessageModel(alertMessage, ServerMessageModel.TYPE_ALERT);
									if (databaseService != null) {
										databaseService.getServerMessageModelFactory().storeServerMessageModel(serverMessageModel);
									}
									ListenerManager.serverMessageListeners.handle(listener -> listener.onError(serverMessageModel));
									break;
							}
						}
					}
				}
			}
		}

		return firstMessageModel;
	}
}
