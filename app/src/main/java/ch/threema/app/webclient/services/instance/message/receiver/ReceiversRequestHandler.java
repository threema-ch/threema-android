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

package ch.threema.app.webclient.services.instance.message.receiver;

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;

import java.util.Map;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import ch.threema.app.services.ContactService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.GroupService;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.Contact;
import ch.threema.app.webclient.converter.Group;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.converter.Receiver;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import ch.threema.base.utils.LoggingUtil;

/**
 * Webclient is requesting list of receivers.
 */
@WorkerThread
public class ReceiversRequestHandler extends MessageReceiver {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ReceiversRequestHandler");

	private final MessageDispatcher dispatcher;
	private final ContactService contactService;
	private final GroupService groupService;
	private final DistributionListService distributionListService;

	private Listener listener;

	@WorkerThread
	public interface Listener {
		void onReceived();
		void onAnswered();
	}

	@AnyThread
	public ReceiversRequestHandler(@NonNull MessageDispatcher dispatcher,
	                               @NonNull ContactService contactService,
	                               @NonNull GroupService groupService,
	                               @NonNull DistributionListService distributionListService,
	                               @Nullable Listener listener) {
		super(Protocol.SUB_TYPE_RECEIVERS);
		this.dispatcher = dispatcher;
		this.contactService = contactService;
		this.groupService = groupService;
		this.distributionListService = distributionListService;
		this.listener = listener;
	}

	@Override
	protected void receive(Map<String, Value> message) throws MessagePackException {
		logger.debug("Received receivers request");
		this.respond();
	}

	private void respond() {
		try {
			final MsgpackObjectBuilder data = Receiver.convert(
					this.contactService.find(Contact.getContactFilter()),
					this.groupService.getAll(Group.getGroupFilter()),
					this.distributionListService.getAll()
			);

			// Notify listeners
			this.listener.onReceived();

			// Send response
			logger.debug("Sending receivers response");
			final MsgpackObjectBuilder args = new MsgpackObjectBuilder();
			this.send(this.dispatcher, data, args);

			// Notify listeners
			if (this.listener != null) {
				this.listener.onAnswered();
			}
		} catch (ConversionException | MessagePackException e) {
			logger.error("Exception", e);
		}
	}

	@Override
	protected boolean maybeNeedsConnection() {
		return false;
	}
}
