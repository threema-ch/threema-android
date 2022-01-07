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

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;

import androidx.annotation.AnyThread;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;

import ch.threema.app.services.ContactService;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.Contact;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import ch.threema.storage.models.ContactModel;

/**
 * Request detail information about the identity (e.g. system contact)
 */
@WorkerThread
public class ContactDetailRequestHandler extends MessageReceiver {
	private static final Logger logger = LoggerFactory.getLogger(ContactDetailRequestHandler.class);

	private final MessageDispatcher dispatcher;
	private final ContactService contactService;

	// Error codes
	@Retention(RetentionPolicy.SOURCE)
	@StringDef({
		Protocol.ERROR_INVALID_CONTACT,
	})
	private @interface ErrorCode {}

	@AnyThread
	public ContactDetailRequestHandler(MessageDispatcher dispatcher,
	                                   ContactService contactService) {
		super(Protocol.SUB_TYPE_CONTACT_DETAIL);
		this.dispatcher = dispatcher;
		this.contactService = contactService;
	}

	@Override
	protected void receive(Map<String, Value> message) throws MessagePackException {
		logger.debug("Received contact detail request");

		// Parse args
		final Map<String, Value> args = this.getArguments(message, false, new String[]{
				Protocol.ARGUMENT_IDENTITY
		});
		if(!args.containsKey(Protocol.ARGUMENT_IDENTITY)
				|| !args.containsKey(Protocol.ARGUMENT_TEMPORARY_ID)) {
			logger.error("invalid response, threemaId or temporaryId not set");
			return;
		}

		// Fetch
		final String threemaId = args.get(Protocol.ARGUMENT_IDENTITY).asStringValue().toString()
				.toUpperCase().trim();
		final String temporaryId = args.get(Protocol.ARGUMENT_TEMPORARY_ID).asStringValue().toString();

		final ContactModel contactModel = this.contactService.getByIdentity(threemaId);
		if (contactModel == null) {
			this.failed(threemaId, temporaryId, Protocol.ERROR_INVALID_CONTACT);
		}
		else {
			this.success(threemaId, temporaryId, contactModel);
		}
	}

	/**
	 * Respond with the contact model.
	 */
	private void success(String threemaId, String temporaryId, ContactModel contact) {
		logger.debug("Respond with contact details success");
		try {
			this.send(this.dispatcher,
					new MsgpackObjectBuilder()
							.put(Protocol.SUB_TYPE_RECEIVER, Contact.convertDetails(contact)),
					new MsgpackObjectBuilder()
							.put(Protocol.ARGUMENT_IDENTITY, threemaId)
							.put(Protocol.ARGUMENT_TEMPORARY_ID, temporaryId)
							.put(Protocol.ARGUMENT_SUCCESS, true)
			);
		} catch (ConversionException e) {
			logger.error("Exception", e);
		}
	}

	/**
	 * Respond with the a error code and success false.
	 */
	private void failed(String threemaId, String temporaryId, @ErrorCode String errorCode) {
		logger.warn("Respond with contact details failed ({})", errorCode);
		this.send(this.dispatcher,
				new MsgpackObjectBuilder()
						.putNull(Protocol.SUB_TYPE_RECEIVER),
				new MsgpackObjectBuilder()
						.put(Protocol.ARGUMENT_IDENTITY, threemaId)
						.put(Protocol.ARGUMENT_SUCCESS, false)
						.put(Protocol.ARGUMENT_ERROR, errorCode)
						.put(Protocol.ARGUMENT_TEMPORARY_ID, temporaryId)
		);
	}

	@Override
	protected boolean maybeNeedsConnection() {
		return false;
	}

}
