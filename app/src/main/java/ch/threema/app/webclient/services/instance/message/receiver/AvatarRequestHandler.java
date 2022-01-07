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

import androidx.annotation.AnyThread;
import androidx.annotation.WorkerThread;

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.converter.Utils;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageReceiver;

@WorkerThread
public class AvatarRequestHandler extends MessageReceiver {
	private static final Logger logger = LoggerFactory.getLogger(AvatarRequestHandler.class);

	private final MessageDispatcher dispatcher;

	@AnyThread
	public AvatarRequestHandler(MessageDispatcher dispatcher) {
		super(Protocol.SUB_TYPE_AVATAR);
		this.dispatcher = dispatcher;
	}

	@Override
	protected void receive(Map<String, Value> message) throws MessagePackException {
		logger.debug("Received avatar request");
		Map<String, Value> args = this.getArguments(message, false, new String[] {
				Protocol.ARGUMENT_RECEIVER_TYPE,
				Protocol.ARGUMENT_RECEIVER_ID,
				Protocol.ARGUMENT_AVATAR_HIGH_RESOLUTION,
				Protocol.ARGUMENT_TEMPORARY_ID
		});

		final String type = args.get(Protocol.ARGUMENT_RECEIVER_TYPE).asStringValue().asString();
		final String receiverId = args.get(Protocol.ARGUMENT_RECEIVER_ID).asStringValue().asString();
		final boolean highResolution = args.get(Protocol.ARGUMENT_AVATAR_HIGH_RESOLUTION).asBooleanValue().getBoolean();
		final String temporaryId = args.get(Protocol.ARGUMENT_TEMPORARY_ID).asStringValue().toString();

		// read optionally avatar size field
		Integer maxAvatarSize = null;
		if (args.containsKey(Protocol.ARGUMENT_MAX_SIZE)) {
			maxAvatarSize = args.get(Protocol.ARGUMENT_MAX_SIZE).asIntegerValue().toInt();
		}
		try {
			// Send response
			final MsgpackObjectBuilder responseArgs = new MsgpackObjectBuilder()
					.put(Protocol.ARGUMENT_RECEIVER_TYPE, type)
					.put(Protocol.ARGUMENT_RECEIVER_ID, receiverId)
					.put(Protocol.ARGUMENT_AVATAR_HIGH_RESOLUTION, highResolution)
					.put(Protocol.ARGUMENT_TEMPORARY_ID, temporaryId);
			this.respond(this.getModel(args), highResolution, responseArgs, maxAvatarSize);
		} catch (ConversionException e) {
			logger.error("Exception", e);
		}
	}

	private void respond(Utils.ModelWrapper model, boolean highResolution, MsgpackObjectBuilder args, Integer maxAvatarSize) {
		try {
			logger.debug("Sending avatar response");
			this.send(this.dispatcher, model.getAvatar(highResolution, maxAvatarSize), args);
		} catch (ConversionException | MessagePackException e) {
			logger.error("Exception", e);
		}
	}

	@Override
	protected boolean maybeNeedsConnection() {
		return false;
	}
}
