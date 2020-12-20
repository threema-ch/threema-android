/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2020 Threema GmbH
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import ch.threema.app.managers.ListenerManager;
import ch.threema.app.routines.SynchronizeContactsRoutine;
import ch.threema.app.services.ContactService;
import ch.threema.app.utils.executor.HandlerExecutor;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.Contact;
import ch.threema.app.webclient.converter.MsgpackBuilder;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.converter.Receiver;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageUpdater;

/**
 * Listen for changes that require the entire list of contacts to be refreshed in Threema Web.
 *
 * Example: When the name format of the contacts changes.
 */
@WorkerThread
public class ReceiversUpdateHandler extends MessageUpdater {
	private static final Logger logger = LoggerFactory.getLogger(ReceiversUpdateHandler.class);

	// Handler
	private final @NonNull HandlerExecutor handler;

	// Listeners
	private final ContactSettingsListener contactSettingsListener;
	private final SynchronizeContactsListener synchronizeContactsListener;

	// Dispatchers
	private MessageDispatcher updateDispatcher;

	// Services
	private ContactService contactService;

	@AnyThread
	public ReceiversUpdateHandler(@NonNull HandlerExecutor handler, MessageDispatcher updateDispatcher, ContactService contactService) {
		super(Protocol.SUB_TYPE_RECEIVERS);
		this.handler = handler;
		this.updateDispatcher = updateDispatcher;
		this.contactService = contactService;
		this.contactSettingsListener = new ContactSettingsListener();
		this.synchronizeContactsListener = new SynchronizeContactsListener();
	}

	@Override
	public void register() {
		logger.debug("register()");
		ListenerManager.contactSettingsListeners.add(this.contactSettingsListener);
		ListenerManager.synchronizeContactsListeners.add(this.synchronizeContactsListener);
	}

	/**
	 * This method can be safely called multiple times without any negative side effects
	 */
	@Override
	public void unregister() {
		logger.debug("unregister()");
		ListenerManager.contactSettingsListeners.remove(this.contactSettingsListener);
		ListenerManager.synchronizeContactsListeners.remove(this.synchronizeContactsListener);
	}

	/**
	 * Update the list of contacts.
	 */
	private void updateContacts() {
		try {
			// Prepare args
			final MsgpackObjectBuilder args = new MsgpackObjectBuilder()
				.put(Protocol.ARGUMENT_RECEIVER_TYPE, Receiver.Type.CONTACT);

			// Convert contacts
			final List<MsgpackBuilder> data = Contact.convert(
				contactService.find(Contact.getContactFilter())
			);

			// Send message
			logger.debug("Sending receivers update");
			this.send(this.updateDispatcher, data, args);
		} catch (ConversionException e) {
			logger.error("Exception", e);
		}
	}

	@AnyThread
	private class ContactSettingsListener implements ch.threema.app.listeners.ContactSettingsListener {
		@Override
		public void onSortingChanged() {
			logger.debug("Contact Listener: onSortingChanged");
			handler.post(new Runnable() {
				@Override
				@WorkerThread
				public void run() {
					ReceiversUpdateHandler.this.updateContacts();
				}
			});
		}

		@Override
		public void onNameFormatChanged() {
			logger.debug("Contact Listener: onNameFormatChanged");
			handler.post(new Runnable() {
				@Override
				@WorkerThread
				public void run() {
					ReceiversUpdateHandler.this.updateContacts();
				}
			});
		}

		@Override
		public void onAvatarSettingChanged() {
			logger.debug("Contact Listener: onAvatarSettingChanged");
			// TODO
		}

		@Override
		public void onInactiveContactsSettingChanged() {
			logger.debug("Contact Listener: onInactiveContactsSettingChanged");
			handler.post(new Runnable() {
				@Override
				@WorkerThread
				public void run() {
					ReceiversUpdateHandler.this.updateContacts();
				}
			});
		}

		@Override
		public void onNotificationSettingChanged(String uid) {
			logger.debug("Contact Listener: onNotificationSettingChanged");
			// TODO
		}
	}

	@AnyThread
	private class SynchronizeContactsListener implements ch.threema.app.listeners.SynchronizeContactsListener {
		@Override
		public void onStarted(SynchronizeContactsRoutine startedRoutine) {
			logger.debug("Contact sync started");
		}

		@Override
		public void onFinished(SynchronizeContactsRoutine finishedRoutine) {
			logger.debug("Contact sync finished, sending receivers update");
			handler.post(new Runnable() {
				@Override
				@WorkerThread
				public void run() {
					ReceiversUpdateHandler.this.updateContacts();
				}
			});
		}

		@Override
		public void onError(SynchronizeContactsRoutine finishedRoutine) {
			logger.warn("Contact sync error, sending receivers update");
			handler.post(new Runnable() {
				@Override
				@WorkerThread
				public void run() {
					ReceiversUpdateHandler.this.updateContacts();
				}
			});
		}
	}
}
