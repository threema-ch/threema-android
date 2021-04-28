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

package ch.threema.app.webclient.services.instance.message.receiver;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import androidx.annotation.AnyThread;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;
import ch.threema.app.dialogs.ContactEditDialog;
import ch.threema.app.exceptions.PolicyViolationException;
import ch.threema.app.services.GroupService;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.Group;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import ch.threema.storage.models.GroupModel;

@WorkerThread
public class CreateGroupHandler extends MessageReceiver {
	private static final Logger logger = LoggerFactory.getLogger(CreateGroupHandler.class);

	private final MessageDispatcher dispatcher;
	private final GroupService groupService;

	@Retention(RetentionPolicy.SOURCE)
	@StringDef({
		Protocol.ERROR_DISABLED_BY_POLICY,
		Protocol.ERROR_BAD_REQUEST,
		Protocol.ERROR_VALUE_TOO_LONG,
		Protocol.ERROR_INTERNAL,
	})
	private @interface ErrorCode {}

	@AnyThread
	public CreateGroupHandler(MessageDispatcher dispatcher,
	                          GroupService groupService) {
		super(Protocol.SUB_TYPE_GROUP);
		this.dispatcher = dispatcher;
		this.groupService = groupService;
	}

	@Override
	protected void receive(Map<String, Value> message) throws MessagePackException {
		logger.debug("Received create group create");
		final Map<String, Value> args = this.getArguments(message, false, new String[]{
			Protocol.ARGUMENT_TEMPORARY_ID,
		});
		final Map<String, Value> data = this.getData(message, false);

		final String temporaryId = args.get(Protocol.ARGUMENT_TEMPORARY_ID).asStringValue().toString();

		// Parse members
		if (!data.containsKey(Protocol.ARGUMENT_MEMBERS)) {
			logger.error("Invalid request, members not set");
			this.failed(temporaryId, Protocol.ERROR_BAD_REQUEST);
			return;
		}
		final List<Value> members = data.get(Protocol.ARGUMENT_MEMBERS).asArrayValue().list();
		final String[] identities = new String[members.size()];
		for (int n = 0; n < members.size(); n++) {
			identities[n] = members.get(n).asStringValue().toString();
		}

		// Parse group name
		String name = null;
		if(data.containsKey(Protocol.ARGUMENT_NAME)
			&& !data.get(Protocol.ARGUMENT_NAME).isNilValue()) {
			name = data.get(Protocol.ARGUMENT_NAME).asStringValue().toString();
			if (name.getBytes(StandardCharsets.UTF_8).length > Protocol.LIMIT_BYTES_GROUP_NAME) {
				this.failed(temporaryId, Protocol.ERROR_VALUE_TOO_LONG);
				return;
			}
		}

		// Parse avatar
		Bitmap avatar = null;
		if (data.containsKey(Protocol.ARGUMENT_AVATAR)
				&& !data.get(Protocol.ARGUMENT_AVATAR).isNilValue()) {
			byte[] bmp = data.get(Protocol.ARGUMENT_AVATAR).asBinaryValue().asByteArray();
			if (bmp.length > 0) {
				avatar = BitmapFactory.decodeByteArray(bmp, 0, bmp.length);

				// Resize to max allowed size
				avatar = BitmapUtil.resizeBitmap(avatar, ContactEditDialog.CONTACT_AVATAR_WIDTH_PX,
						ContactEditDialog.CONTACT_AVATAR_HEIGHT_PX);
			}
		}

		// Greate group
		try {
			final GroupModel groupModel = this.groupService.createGroup(name, identities, avatar);
			this.success(temporaryId, groupModel);
		} catch (PolicyViolationException e) {
			this.failed(temporaryId, Protocol.ERROR_DISABLED_BY_POLICY);
		} catch (Exception e) {
			this.failed(temporaryId, Protocol.ERROR_INTERNAL);
		}
	}

	private void success(String temporaryId, GroupModel group) {
		logger.debug("Respond create group success");
		try {
			this.send(this.dispatcher,
					new MsgpackObjectBuilder()
						.put(Protocol.SUB_TYPE_RECEIVER, Group.convert(group)),
					new MsgpackObjectBuilder()
						.put(Protocol.ARGUMENT_SUCCESS, true)
						.put(Protocol.ARGUMENT_TEMPORARY_ID, temporaryId)
			);
		} catch (ConversionException e) {
			logger.error("Exception", e);
		}
	}

	private void failed(String temporaryId, @ErrorCode String errorCode) {
		logger.warn("Respond create group failed ({})", errorCode);
		this.send(this.dispatcher,
				(MsgpackObjectBuilder) null,
				new MsgpackObjectBuilder()
						.put(Protocol.ARGUMENT_SUCCESS, false)
						.put(Protocol.ARGUMENT_ERROR, errorCode)
						.put(Protocol.ARGUMENT_TEMPORARY_ID, temporaryId)
		);
	}

	@Override
	protected boolean maybeNeedsConnection() {
		return true;
	}
}
