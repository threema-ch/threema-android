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

package ch.threema.app.webclient.services.instance;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import androidx.annotation.WorkerThread;
import ch.threema.app.services.LifetimeService;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.SendMode;
import ch.threema.app.webclient.converter.MsgpackBuilder;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.exceptions.DispatchException;
import ch.threema.client.MessageQueue;

/**
 * Dispatch incoming messages to the receivers or send outgoing messages to the webclient.
 *
 * An incoming message is offered to each receiver based on the subtype and message. The process is
 * done once a receiver accepts the message.
 */
@WorkerThread
public class MessageDispatcher {
	@NonNull private final static String TAG = "WebClientMessageDispatcher";

	@NonNull protected final SessionInstanceService service;
	@NonNull protected final Logger logger;
	@NonNull protected final MessageQueue messageQueue;
	@NonNull protected final LifetimeService lifetimeService;
	@NonNull protected final String type;
	@NonNull protected final Map<String, MessageReceiver> receivers = new ConcurrentHashMap<>();

	@AnyThread
	public MessageDispatcher(
		@NonNull final String type,
		@NonNull final SessionInstanceServiceImpl service,
		@NonNull final LifetimeService lifetimeService,
		@NonNull final MessageQueue messageQueue
	) {
		this.service = service;
		this.logger = service.logger;
		this.lifetimeService = lifetimeService;
		this.type = type;
		this.messageQueue = messageQueue;
	}

	/**
	 * Add a new message receiver.
	 */
	@AnyThread
	public void addReceiver(@NonNull final MessageReceiver receiver) {
		this.receivers.put(receiver.getSubType(), receiver);
	}

	/**
	 * Dispatch according to subtype and message.
	 */
	private void dispatch(@NonNull final String subType, @NonNull final Map<String, Value> message)
		throws DispatchException, MessagePackException {
		if (!this.receivers.containsKey(subType)) {
			throw new DispatchException("No receiver for type '" + this.type + "' with sub type '" + subType + "' found");
		}
		final MessageReceiver receiver = Objects.requireNonNull(this.receivers.get(subType));
		receiver.receive(message);

		// If the receiver indicates that messages might have been enqueued,
		// check whether the queue is empty. If it isn't, acquire the connection
		// for a short while to send those messages.
		if (receiver.maybeNeedsConnection()) {
			if (this.messageQueue.getQueueSize() > 0) {
				int timeoutMs = Math.min(30000, 5000 + 100 * this.messageQueue.getQueueSize());
				this.lifetimeService.acquireConnection(TAG);
				this.lifetimeService.releaseConnectionLinger(TAG, timeoutMs);
			}
		}
	}

	/**
	 * Dispatch according to type, subtype and message.
	 */
	public boolean dispatch(@NonNull final String type, @NonNull final String subType, @NonNull final Map<String, Value> message)
		throws DispatchException, MessagePackException {
		// Are we receiving this type?
		if (type.equals(this.type)) {
			this.dispatch(subType, message);
			return true;
		} else {
			return false;
		}
	}

	public void send(@NonNull final String subType, @NonNull final MsgpackBuilder data, @NonNull final MsgpackBuilder args) {
		this.send(this.type, subType, data, args);
	}
	public void send(@NonNull final String subType, @NonNull final List<MsgpackBuilder> data, @NonNull final MsgpackBuilder args) {
		this.send(this.type, subType, data, args);
	}

	public void send(@NonNull final String subType, @NonNull final String data, @NonNull final MsgpackBuilder args) {
		this.send(this.type, subType, data, args);
	}

	public void send(@NonNull final String subType, @NonNull final byte[] data, @NonNull final MsgpackBuilder args) {
		this.send(this.type, subType, data, args);
	}

	public void send(@NonNull final String type, @NonNull final String subType, @Nullable final MsgpackBuilder data, final @Nullable MsgpackBuilder args) {
		final MsgpackObjectBuilder message = this.createMessage(type, subType, args);
		message.maybePut(Protocol.FIELD_DATA, data);
		logger.debug("Sending {}/{}", type, subType);
		this.send(message);
	}

	public void send(@NonNull final String type, @NonNull final String subType, @Nullable final List<MsgpackBuilder> data, final @Nullable MsgpackBuilder args) {
		final MsgpackObjectBuilder message = this.createMessage(type, subType, args);
		message.maybePut(Protocol.FIELD_DATA, data);
		logger.debug("Sending {}/{}", type, subType);
		this.send(message);
	}

	public void send(@NonNull final String type, @NonNull final String subType, @Nullable final String data, final @Nullable MsgpackBuilder args) {
		final MsgpackObjectBuilder message = this.createMessage(type, subType, args);
		message.maybePut(Protocol.FIELD_DATA, data);
		logger.debug("Sending {}/{}", type, subType);
		this.send(message);
	}

	public void send(@NonNull final String type, @NonNull final String subType, @Nullable final byte[] data, final @Nullable MsgpackBuilder args) {
		final MsgpackObjectBuilder message = this.createMessage(type, subType, args);
		message.maybePut(Protocol.FIELD_DATA, data);
		logger.debug("Sending {}/{}", type, subType);
		this.send(message);
	}

	/**
	 * Send a message to the webclient.
	 */
	private void send(@NonNull final MsgpackObjectBuilder message) {
		try {
			this.service.send(message.consume(), SendMode.ASYNC);
		} catch (OutOfMemoryError error) {
			logger.error("Out of memory while encoding outgoing data channel message");
			this.service.stop(DisconnectContext.byUs(DisconnectContext.REASON_OUT_OF_MEMORY));
		}
	}

	/**
	 * Create a new message.
	 */
	private MsgpackObjectBuilder createMessage(
		@NonNull final String type,
		@NonNull final String subType,
		@Nullable final MsgpackBuilder args
	) {
		final MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
		builder.put(Protocol.FIELD_TYPE, type);
		builder.put(Protocol.FIELD_SUB_TYPE, subType);
		builder.maybePut(Protocol.FIELD_ARGUMENTS, args);
		return builder;
	}

}
