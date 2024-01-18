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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;

import androidx.annotation.AnyThread;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;
import ch.threema.app.dialogs.ContactEditDialog;
import ch.threema.app.services.ContactService;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.Contact;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.ContactModel;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Process update/contact requests from the browser.
 */
@WorkerThread
public class ModifyContactHandler extends MessageReceiver {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ModifyContactHandler");

	private static final String FIELD_FIRST_NAME = "firstName";
	private static final String FIELD_LAST_NAME = "lastName";

	@Retention(RetentionPolicy.SOURCE)
	@StringDef({
		Protocol.ERROR_INVALID_CONTACT,
		Protocol.ERROR_NOT_ALLOWED_LINKED,
		Protocol.ERROR_NOT_ALLOWED_BUSINESS,
		Protocol.ERROR_VALUE_TOO_LONG,
		Protocol.ERROR_INTERNAL,
	})
	private @interface ErrorCode {}

	private final MessageDispatcher dispatcher;
	private final ContactService contactService;

	@AnyThread
	public ModifyContactHandler(MessageDispatcher dispatcher,
	                            ContactService contactService) {
		super(Protocol.SUB_TYPE_CONTACT);
		this.dispatcher = dispatcher;
		this.contactService = contactService;
	}

	@Override
	protected void receive(Map<String, Value> message) throws MessagePackException {
		logger.debug("Received update contact message");

		// Process args
		final Map<String, Value> args = this.getArguments(message, false);
		if (!args.containsKey(Protocol.ARGUMENT_TEMPORARY_ID)
				|| !args.containsKey(Protocol.ARGUMENT_IDENTITY)) {
			logger.error("Invalid contact update request, identity or temporaryId not set");
			return;
		}
		final String identity = args.get(Protocol.ARGUMENT_IDENTITY).asStringValue().toString();
		final String temporaryId = args.get(Protocol.ARGUMENT_TEMPORARY_ID).asStringValue().toString();

		// Validate identity
		final ContactModel contactModel = this.contactService.getByIdentity(identity);
		if (contactModel == null) {
			this.failed(identity, temporaryId, Protocol.ERROR_INVALID_CONTACT);
			return;
		}
		if (ContactUtil.isLinked(contactModel)) {
			this.failed(identity, temporaryId, Protocol.ERROR_NOT_ALLOWED_LINKED);
			return;
		}

		contactModel.setIsHidden(false);

		// Process data
		final Map<String, Value> data = this.getData(message, false);

		if (data.containsKey(FIELD_FIRST_NAME)) {
			final String firstName = this.getValueString(data.get(FIELD_FIRST_NAME));
			if (firstName.getBytes(UTF_8).length > Protocol.LIMIT_BYTES_FIRST_NAME) {
				this.failed(identity, temporaryId, Protocol.ERROR_VALUE_TOO_LONG);
				return;
			}
			contactModel.setFirstName(firstName);
		}
		if (data.containsKey(FIELD_LAST_NAME)) {
			final String lastName = this.getValueString(data.get(FIELD_LAST_NAME));
			if (lastName.getBytes(UTF_8).length > Protocol.LIMIT_BYTES_LAST_NAME) {
				this.failed(identity, temporaryId, Protocol.ERROR_VALUE_TOO_LONG);
				return;
			}
			contactModel.setLastName(lastName);
		}

		// Update avatar
		if (data.containsKey(Protocol.ARGUMENT_AVATAR)) {
			if (ContactUtil.isChannelContact(contactModel)) {
				this.failed(identity, temporaryId, Protocol.ERROR_NOT_ALLOWED_BUSINESS);
				return;
			} else {
				try {
					final Value avatarValue = data.get(Protocol.ARGUMENT_AVATAR);
					if (avatarValue == null || avatarValue.isNilValue()) {
						// Clear avatar
						this.contactService.removeAvatar(contactModel);
					} else {
						// Set avatar
						final byte[] bmp = avatarValue.asBinaryValue().asByteArray();
						if (bmp.length > 0) {
							Bitmap avatar = BitmapFactory.decodeByteArray(bmp, 0, bmp.length);
							// Resize to max allowed size
							avatar = BitmapUtil.resizeBitmap(avatar,
									ContactEditDialog.CONTACT_AVATAR_WIDTH_PX,
									ContactEditDialog.CONTACT_AVATAR_HEIGHT_PX);
							this.contactService.setAvatar(
									contactModel,
									// Without quality loss
									BitmapUtil.bitmapToByteArray(avatar, Bitmap.CompressFormat.PNG, 100)
							);
						}
					}
				} catch (Exception e) {
					logger.error("Failed to save avatar", e);
					this.failed(identity, temporaryId, Protocol.ERROR_INTERNAL);
					return;
				}
			}
		}

		// Save the contact model
		this.contactService.save(contactModel);

		// Return updated contact
		this.success(identity, temporaryId, contactModel);
	}

	/**
	 * Respond with the modified contact model.
	 */
	private void success(String threemaId, String temporaryId, ContactModel contact) {
		logger.debug("Respond modify contact success");
		try {
			this.send(this.dispatcher,
					new MsgpackObjectBuilder()
						.put(Protocol.SUB_TYPE_RECEIVER, Contact.convert(contact)),
					new MsgpackObjectBuilder()
						.put(Protocol.ARGUMENT_SUCCESS, true)
						.put(Protocol.ARGUMENT_IDENTITY, threemaId)
						.put(Protocol.ARGUMENT_TEMPORARY_ID, temporaryId)
			);
		} catch (ConversionException e) {
			logger.error("Exception", e);
		}
	}

	/**
	 * Respond with an error code.
	 */
	private void failed(String threemaId, String temporaryId, @ErrorCode String errorCode) {
		logger.warn("Respond modify contact failed ({})", errorCode);
		this.send(this.dispatcher,
				new MsgpackObjectBuilder()
						.putNull(Protocol.SUB_TYPE_RECEIVER),
				new MsgpackObjectBuilder()
						.put(Protocol.ARGUMENT_SUCCESS, false)
						.put(Protocol.ARGUMENT_ERROR, errorCode)
						.put(Protocol.ARGUMENT_IDENTITY, threemaId)
						.put(Protocol.ARGUMENT_TEMPORARY_ID, temporaryId)
		);
	}

	@Override
	protected boolean maybeNeedsConnection() {
		return false;
	}
}
