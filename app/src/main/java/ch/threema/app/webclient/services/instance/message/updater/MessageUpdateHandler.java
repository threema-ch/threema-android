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

package ch.threema.app.webclient.services.instance.message.updater;

import org.msgpack.core.MessagePackException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;
import ch.threema.app.listeners.MessageListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.FileService;
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.utils.executor.HandlerExecutor;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.Message;
import ch.threema.app.webclient.converter.MsgpackArrayBuilder;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.converter.Receiver;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageUpdater;
import ch.threema.storage.models.AbstractMessageModel;

@WorkerThread
public class MessageUpdateHandler extends MessageUpdater {
	private static final Logger logger = LoggerFactory.getLogger(MessageUpdateHandler.class);

	@Retention(RetentionPolicy.SOURCE)
	@StringDef({
		Protocol.ARGUMENT_MODE_NEW,
		Protocol.ARGUMENT_MODE_MODIFIED,
		Protocol.ARGUMENT_MODE_REMOVED,
	})
	private @interface Mode {}

	// Handler
	private final @NonNull HandlerExecutor handler;

	// Listeners
	private final MessageListener listener;

	// Dispatchers
	private final MessageDispatcher dispatcher;

	// Services
	private final FileService fileService;

	// Local data
	private Set<MessageReceiver> receivers = new LinkedHashSet<>();

	// Ring buffer with 64 entries to keep track of messages where the
	// thumbnail has already been sent. This is done to sent the thumbnail
	// only once, to reduce the network traffic.
	// Implementation note: https://stackoverflow.com/a/1963881/284318
	private HashMap<Integer, Boolean> sentThumbnails = new LinkedHashMap<Integer, Boolean>() {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Integer, Boolean> eldest) {
			return this.size() > 64;
		}
	};

	@AnyThread
	public MessageUpdateHandler(@NonNull HandlerExecutor handler,
								MessageDispatcher dispatcher,
	                            DeadlineListService hiddenChatService,
	                            FileService fileService) {
		super(Protocol.SUB_TYPE_MESSAGES);
		this.handler = handler;
		this.dispatcher = dispatcher;
		this.listener = new Listener(hiddenChatService);
		this.fileService = fileService;
	}

	@Override
	public void register() {
		ListenerManager.messageListeners.add(this.listener);
	}

	/**
	 * This method can be safely called multiple times without any negative side effects
	 */
	@Override
	public void unregister() {
		ListenerManager.messageListeners.remove(this.listener);
	}

	public boolean register(ch.threema.app.messagereceiver.MessageReceiver receiver) {
		boolean added = this.receivers.add(receiver);
		this.register();
		return added;
	}

	public boolean unregister(ch.threema.app.messagereceiver.MessageReceiver receiver) {
		boolean removed = this.receivers.remove(receiver);
		// Unregister if no receivers exist
		if (this.receivers.size() == 0) {
			this.unregister();
		}
		return removed;
	}

	private boolean sendThumbnail(AbstractMessageModel message) {
		if (!MessageUtil.canHaveThumbnailFile(message)) {
			// This message type cannot possibly have a thumbnail
			return false;
		} else if (!fileService.hasMessageThumbnail(message)) {
			// No thumbnail file exists so far
			return false;
		} else if (sentThumbnails.containsKey(message.getId())) {
			// Thumbnail for this message was already sent
			return false;
		} else {
			// Thumbnail exists but wasn't sent yet!
			sentThumbnails.put(message.getId(), true);
			return true;
		}
	}

	private void update(
		@NonNull Map<MessageReceiver, List<AbstractMessageModel>> outbox,
		@NonNull @Mode String mode
	) {
		for (Map.Entry<MessageReceiver, List<AbstractMessageModel>> entry : outbox.entrySet()) {
			try {
				// Prepare arguments
				final MessageReceiver receiver = entry.getKey();
				final MsgpackObjectBuilder args = Receiver.getArguments(receiver).put(Protocol.ARGUMENT_MODE, mode);

				// Convert messages
				final MsgpackArrayBuilder data = new MsgpackArrayBuilder();
				for (AbstractMessageModel message : entry.getValue()) {
					data.put(Message.convert(
						message,
						receiver.getType(),
						this.sendThumbnail(message),
						Protocol.ARGUMENT_MODE_REMOVED.equals(mode) ? Message.DETAILS_MINIMAL : Message.DETAILS_FULL
					));
				}

				// Send message
				logger.debug("Sending messages update");
				send(dispatcher, data, args);
			} catch (ConversionException | MessagePackException e) {
				logger.error("Exception", e);
			}
		}
	}

	@AnyThread
	private class Listener implements MessageListener {
		private DeadlineListService hiddenChatService;

		public Listener(DeadlineListService hiddenChatService) {
			this.hiddenChatService = hiddenChatService;
		}

		@Override
		public void onNew(AbstractMessageModel newMessage) {
			this.dispatch(Collections.singletonList(newMessage), Protocol.ARGUMENT_MODE_NEW);
		}

		@Override
		public void onModified(List<AbstractMessageModel> modifiedMessageModels) {
			// TODO: Here we should probably batch update messages for the same receiver.
			// Also, if the same msg is updated multiple times, only send the last one.
			this.dispatch(modifiedMessageModels, Protocol.ARGUMENT_MODE_MODIFIED);
		}

		@Override
		public void onRemoved(AbstractMessageModel removedMessageModel) {
			this.dispatch(Collections.singletonList(removedMessageModel), Protocol.ARGUMENT_MODE_REMOVED);
		}

		@Override
		public void onRemoved(List<AbstractMessageModel> removedMessageModels) {
			this.dispatch(removedMessageModels, Protocol.ARGUMENT_MODE_REMOVED);
		}

		@Override
		public void onProgressChanged(AbstractMessageModel messageModel, int newProgress) {
			// Ignore
		}

		private void dispatch(List<AbstractMessageModel> messages, @Mode String mode) {
			handler.post(new Runnable() {
				@Override
				@WorkerThread
				public void run() {
					// Group messages by receiver
					final Map<MessageReceiver, List<AbstractMessageModel>> outbox = new HashMap<>();

					// Loop over messages
					for (AbstractMessageModel message : messages) {
						// Loop over registered receivers
						for (ch.threema.app.messagereceiver.MessageReceiver receiver : MessageUpdateHandler.this.receivers) {
							// If message belongs to a registered receiver, add it to the outbox.
							if (receiver.isMessageBelongsToMe(message)) {
								// Skip chat messages in hidden chats (#WEBC-75)
								if (!Listener.this.hiddenChatService.has(receiver.getUniqueIdString())) {
									if (outbox.containsKey(receiver)) {
										outbox.get(receiver).add(message);
									} else {
										final List<AbstractMessageModel> list = new ArrayList<>();
										list.add(message);
										outbox.put(receiver, list);
									}
								}
								break;
							}
						}
					}

					MessageUpdateHandler.this.update(outbox, mode);
				}
			});
		}
	}
}
