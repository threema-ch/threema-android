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

package ch.threema.app.webclient.services.instance.message.receiver;

import android.graphics.Bitmap;

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import ch.threema.app.services.FileService;
import ch.threema.app.services.MessageService;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.converter.Receiver;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import ch.threema.app.webclient.utils.ThumbnailUtils;
import ch.threema.storage.models.AbstractMessageModel;

@WorkerThread
public class ThumbnailRequestHandler extends MessageReceiver {
	private static final Logger logger = LoggerFactory.getLogger(ThumbnailRequestHandler.class);

	private final MessageDispatcher dispatcher;
	private final MessageService messageService;
	private final FileService fileService;

	@AnyThread
	public ThumbnailRequestHandler(MessageDispatcher dispatcher,
	                               MessageService messageService,
	                               FileService fileService) {
		super(Protocol.SUB_TYPE_THUMBNAIL);
		this.dispatcher = dispatcher;
		this.messageService = messageService;
		this.fileService = fileService;
	}

	@Override
	protected void receive(Map<String, Value> message) throws MessagePackException {
		logger.debug("Received thumbnail request");
		Map<String, Value> args = this.getArguments(message, false, new String[] {
				Protocol.ARGUMENT_RECEIVER_TYPE,
				Protocol.ARGUMENT_RECEIVER_ID,
				Protocol.ARGUMENT_MESSAGE_ID,
				Protocol.ARGUMENT_TEMPORARY_ID
		});

		final String type = args.get(Protocol.ARGUMENT_RECEIVER_TYPE).asStringValue().asString();
		final String receiverId = args.get(Protocol.ARGUMENT_RECEIVER_ID).asStringValue().asString();
		final String messageIdStr = args.get(Protocol.ARGUMENT_MESSAGE_ID).asStringValue().asString();
		final int messageId = Integer.valueOf(messageIdStr);
		final String temporaryId = args.get(Protocol.ARGUMENT_TEMPORARY_ID).asStringValue().toString();

		AbstractMessageModel messageModel = null;
		switch(type) {
			case Receiver.Type.GROUP:
				messageModel = this.messageService.getGroupMessageModel(messageId, true);
				break;
			case Receiver.Type.CONTACT:
				messageModel = this.messageService.getContactMessageModel(messageId, true);
				break;
			case Receiver.Type.DISTRIBUTION_LIST:
				messageModel = this.messageService.getDistributionListMessageModel(messageId, true);
				break;
		}

		// Send response
		final MsgpackObjectBuilder responseArgs = new MsgpackObjectBuilder()
				.put(Protocol.ARGUMENT_RECEIVER_TYPE, type)
				.put(Protocol.ARGUMENT_RECEIVER_ID, receiverId)
				.put(Protocol.ARGUMENT_MESSAGE_ID, String.valueOf(messageId))
				.put(Protocol.ARGUMENT_TEMPORARY_ID, temporaryId);
		this.respond(messageModel, responseArgs);
	}

	private void respond(AbstractMessageModel messageModel, MsgpackObjectBuilder args) {
		try {
			logger.debug("Sending thumbnail response");
			Bitmap thumbnail = null;
			try {
				thumbnail = this.fileService.getMessageThumbnailBitmap(messageModel, null);
			} catch (Exception x) {
				logger.error("Exception", x);
			}

			// Make sure that the thumbnail is not larger than a certain size
			if (thumbnail != null) {
				thumbnail = this.resizeThumbnail(thumbnail);
			}

			// Return null if no avatar is available
			byte[] data = null;
			if (thumbnail != null) {
				data = BitmapUtil.bitmapToByteArray(thumbnail, Protocol.FORMAT_THUMBNAIL, Protocol.QUALITY_THUMBNAIL);
			}
			this.send(this.dispatcher, data, args);
		} catch (MessagePackException e) {
			logger.error("Exception", e);
		}
	}

	/**
	 * Make sure that the thumbnail is not larger than a certain size,
	 * resizing if necessary.
	 */
	private Bitmap resizeThumbnail(final @NonNull Bitmap thumbnail) {
		return ThumbnailUtils.resize(thumbnail, Protocol.SIZE_THUMBNAIL_MAX_PX);
	}

	@Override
	protected boolean maybeNeedsConnection() {
		return false;
	}
}
