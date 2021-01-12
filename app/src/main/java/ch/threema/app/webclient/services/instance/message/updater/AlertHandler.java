/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2021 Threema GmbH
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


import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.msgpack.core.MessagePackException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.threema.app.managers.ListenerManager;
import ch.threema.app.utils.executor.HandlerExecutor;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageUpdater;
import ch.threema.storage.models.ServerMessageModel;

@WorkerThread
public class AlertHandler extends MessageUpdater {
	private static final Logger logger = LoggerFactory.getLogger(AlertHandler.class);

	public static final String SOURCE_SERVER = "server";
	public static final String SOURCE_DEVICE = "device";

	public static final String ALERT_TYPE_ERROR = "error";
	public static final String ALERT_TYPE_WARNING = "warning";
	public static final String ALERT_TYPE_INFO = "info";

	// Handler
	private final @NonNull HandlerExecutor handler;

	// Listeners
	private final ServerMessageListener serverMessageListener;

	// Dispatchers
	private MessageDispatcher updateDispatcher;

	@AnyThread
	public AlertHandler(@NonNull HandlerExecutor handler, MessageDispatcher updateDispatcher) {
		super(Protocol.SUB_TYPE_ALERT);
		this.handler = handler;
		this.updateDispatcher = updateDispatcher;
		this.serverMessageListener = new ServerMessageListener();
	}

	@Override
	public void register() {
		ListenerManager.serverMessageListeners.add(this.serverMessageListener);
	}

	@Override
	public void unregister() {
		ListenerManager.serverMessageListeners.remove(this.serverMessageListener);
	}

	private void update(final String source, final String type, final String message) {
		try {
			// Send message
			logger.debug("Sending alert");
			send(updateDispatcher,
					new MsgpackObjectBuilder()
						.put(Protocol.ARGUMENT_ALERT_MESSAGE, message),
					new MsgpackObjectBuilder()
						.put(Protocol.ARGUMENT_ALERT_TYPE, type)
						.put(Protocol.ARGUMENT_ALERT_SOURCE, source));

		} catch (MessagePackException e) {
			logger.error("Exception", e);
		}
	}

	@AnyThread
	private class ServerMessageListener implements ch.threema.app.listeners.ServerMessageListener
	{
		@Override
		public void onAlert(ServerMessageModel serverMessage) {
			handler.post(new Runnable() {
				@Override
				@WorkerThread
				public void run() {
					AlertHandler.this.update(SOURCE_SERVER, ALERT_TYPE_WARNING, serverMessage.getMessage());
				}
			});
		}

		@Override
		public void onError(ServerMessageModel serverMessage) {
			handler.post(new Runnable() {
				@Override
				@WorkerThread
				public void run() {
					AlertHandler.this.update(SOURCE_SERVER, ALERT_TYPE_ERROR, serverMessage.getMessage());
				}
			});
		}
	}
}
