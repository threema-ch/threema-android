/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2024 Threema GmbH
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
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;
import ch.threema.app.dialogs.ContactEditDialog;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.ContactModel;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Process update/profile requests from the browser.
 */
@WorkerThread
public class ModifyProfileHandler extends MessageReceiver {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ModifyProfileHandler");

	private static final String FIELD_NICKNAME = "publicNickname";
	private static final String FIELD_AVATAR = "avatar";

	// Dispatchers
	private final MessageDispatcher responseDispatcher;

	// Services
	private final ContactService contactService;
	private final UserService userService;

	// Error codes
	@Retention(RetentionPolicy.SOURCE)
	@StringDef({
		Protocol.ERROR_INVALID_AVATAR,
		Protocol.ERROR_VALUE_TOO_LONG,
		Protocol.ERROR_INTERNAL,
	})
	private @interface ErrorCode {}

	private static class ModifyProfileException extends Exception {
		@ErrorCode String errorCode;
		ModifyProfileException(@ErrorCode String errorCode) {
			super();
			this.errorCode = errorCode;
		}
	}

	@AnyThread
	public ModifyProfileHandler(MessageDispatcher responseDispatcher,
	                            ContactService contactService,
	                            UserService userService) {
		super(Protocol.SUB_TYPE_PROFILE);
		this.responseDispatcher = responseDispatcher;
		this.contactService = contactService;
		this.userService = userService;
	}

	@Override
	protected void receive(Map<String, Value> message) throws MessagePackException {
		logger.debug("Received update profile message");

		// Get data and args
		final Map<String, Value> data = this.getData(message, false);
		final Map<String, Value> args = this.getArguments(message, false);

		// Process args
		if (!args.containsKey(Protocol.ARGUMENT_TEMPORARY_ID)) {
			logger.error("Invalid profile update request, temporaryId not set");
			return;
		}
		final String temporaryId = args.get(Protocol.ARGUMENT_TEMPORARY_ID).asStringValue().toString();

		try {
			if (data.containsKey(FIELD_NICKNAME)) {
				final String nickname = data.get(FIELD_NICKNAME).asStringValue().toString();
				this.processNickname(nickname);
			}

			if (data.containsKey(FIELD_AVATAR)) {
				final Value value = data.get(FIELD_AVATAR);
				if (value.isNilValue()) {
					this.processAvatar(null);
				} else {
					final byte[] avatar = value.asBinaryValue().asByteArray();
					this.processAvatar(avatar);
				}
			}
		} catch (ModifyProfileException e) {
			logger.error("Profile was not updated (" + e.errorCode + ")", e);
			this.sendConfirmActionFailure(this.responseDispatcher, temporaryId, e.errorCode);
		}

		logger.debug("Profile was updated");
		this.sendConfirmActionSuccess(this.responseDispatcher, temporaryId);
	}

	/**
	 * Update the nickname.
	 */
	private void processNickname(String nickname) throws ModifyProfileException {
		if (nickname.getBytes(UTF_8).length > Protocol.LIMIT_BYTES_PUBLIC_NICKNAME) {
			throw new ModifyProfileException(Protocol.ERROR_VALUE_TOO_LONG);
		}
		this.userService.setPublicNickname(nickname);
	}

	/**
	 * Update the avatar.
	 */
	private void processAvatar(@Nullable byte[] avatarBytes) throws ModifyProfileException {
		final ContactModel me = this.contactService.getMe();

		// If avatar bytes are null, delete own avatar.
		if (avatarBytes == null) {
			this.contactService.removeAvatar(me);
			return;
		}

		// Validate bytes
		if (avatarBytes.length == 0) {
			logger.warn("Avatar bytes are empty");
			throw new ModifyProfileException(Protocol.ERROR_INVALID_AVATAR);
		}

		// Decode avatar
		final Bitmap avatar = BitmapFactory
			.decodeByteArray(avatarBytes, 0, avatarBytes.length);

		// Resize to max allowed size
		final Bitmap resized = BitmapUtil.resizeBitmap(
			avatar,
			ContactEditDialog.CONTACT_AVATAR_WIDTH_PX,
			ContactEditDialog.CONTACT_AVATAR_HEIGHT_PX
		);

		// Set the avatar
		try {
			final byte[] converted = BitmapUtil
				.bitmapToByteArray(resized, Bitmap.CompressFormat.PNG, 100);
			this.contactService.setAvatar(this.contactService.getMe(), converted);
		} catch (Exception e) {
			logger.error("Could not update own avatar", e);
			throw new ModifyProfileException(Protocol.ERROR_INTERNAL);
		}
	}

	@Override
	protected boolean maybeNeedsConnection() {
		// We don't need to send any messages as a result of modifying the profile.
		return false;
	}
}
