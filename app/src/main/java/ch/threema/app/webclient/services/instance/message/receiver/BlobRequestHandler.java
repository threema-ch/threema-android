/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2020 Threema GmbH
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

import org.apache.commons.io.FileUtils;
import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.FileService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.messageplayer.MessagePlayer;
import ch.threema.app.services.messageplayer.WebClientMessagePlayer;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.executor.HandlerExecutor;
import ch.threema.app.voicemessage.VoiceRecorderActivity;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.Message;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import ch.threema.storage.models.AbstractMessageModel;

@WorkerThread
public class BlobRequestHandler extends MessageReceiver {
	private static final Logger logger = LoggerFactory.getLogger(BlobRequestHandler.class);

	@Retention(RetentionPolicy.SOURCE)
	@StringDef({
		Protocol.ERROR_BLOB_DOWNLOAD_FAILED,
		Protocol.ERROR_BLOB_DECRYPT_FAILED,
		Protocol.ERROR_INTERNAL,
	})
	private @interface ErrorCode {}

	// Handler
	private final @NonNull HandlerExecutor handler;

	// Dispatchers
	private final MessageDispatcher dispatcher;

	// Services
	private final MessageService messageService;
	private final FileService fileService;

	@WorkerThread
	public interface Listener {
		void onReceive(ch.threema.app.messagereceiver.MessageReceiver receiver);
	}

	@AnyThread
	public BlobRequestHandler(@NonNull HandlerExecutor handler,
							  MessageDispatcher dispatcher,
	                          MessageService messageService,
	                          FileService fileService) {
		super(Protocol.SUB_TYPE_BLOB);
		this.handler = handler;
		this.dispatcher = dispatcher;
		this.messageService = messageService;
		this.fileService = fileService;
	}

	@Override
	protected void receive(Map<String, Value> message) throws MessagePackException {
		logger.debug("Received blob request");
		final Map<String, Value> args = this.getArguments(message, false);

		final ch.threema.app.messagereceiver.MessageReceiver receiver;
		try {
			receiver = this.getReceiver(args);
		} catch (ConversionException e) {
			logger.error("Exception", e);
			return;
		}

		// Get required arguments
		final String receiverType = args.get(Protocol.ARGUMENT_RECEIVER_TYPE).asStringValue().asString();
		final String receiverId = args.get(Protocol.ARGUMENT_RECEIVER_ID).asStringValue().asString();
		final String temporaryId = args.get(Protocol.ARGUMENT_TEMPORARY_ID).asStringValue().toString();

		// Find related message
		final String messageIdStr = args.get(Protocol.ARGUMENT_MESSAGE_ID).asStringValue().asString();
		final int messageId = Integer.parseInt(messageIdStr);
		final AbstractMessageModel messageModel;
		switch (receiver.getType())
		{
			case ch.threema.app.messagereceiver.MessageReceiver.Type_CONTACT:
				messageModel = this.messageService.getContactMessageModel(messageId, true);
				break;
			case ch.threema.app.messagereceiver.MessageReceiver.Type_GROUP:
				messageModel = this.messageService.getGroupMessageModel(messageId, true);
				break;
			case ch.threema.app.messagereceiver.MessageReceiver.Type_DISTRIBUTION_LIST:
				messageModel = this.messageService.getDistributionListMessageModel(messageId, true);
				break;
			default:
				logger.error("no valid type for blob download found");
				return;
		}

		final WebClientMessagePlayer player = new WebClientMessagePlayer(
			ThreemaApplication.getAppContext(),
			this.messageService,
			this.fileService,
			receiver,
			messageModel
		);

		// Handle download events
		player.addListener("wc", new MessagePlayer.DownloadListener() {
			@Override
			public void onEnd(AbstractMessageModel messageModel, boolean success, String message) {
				if (!success) {
					logger.debug("Could not download blob: {}", message);
					postFailed(receiverType, receiverId, temporaryId, messageId, Protocol.ERROR_BLOB_DOWNLOAD_FAILED);
				} else {
					logger.debug("File downloaded");
				}
			}
		});

		// Handle decryption events
		player.addListener("wc", new MessagePlayer.DecryptionListener() {
			@Override
			public void onStart(AbstractMessageModel messageModel) {
				logger.debug("Starting to decrypt file");
			}

			@Override
			public void onEnd(AbstractMessageModel messageModel, boolean success, String message, File decryptedFile) {
				if (decryptedFile == null || !success) {
					postFailed(receiverType, receiverId, temporaryId, messageId, Protocol.ERROR_BLOB_DOWNLOAD_FAILED);
					return;
				}

				try {
					String mime;
					String name;

					final String filename = FileUtil.getMediaFilenamePrefix(messageModel);

					//noinspection EnumSwitchStatementWhichMissesCases
					switch (messageModel.getType()) {
						case VOICEMESSAGE:
							mime = "audio/aac";
							name = filename + VoiceRecorderActivity.VOICEMESSAGE_FILE_EXTENSION;
							break;
						case FILE:
							mime = messageModel.getFileData().getMimeType();
							final String ownFileName = messageModel.getFileData().getFileName();
							name = Message.fixFileName(ownFileName == null ? filename : ownFileName, mime);
							break;
						case VIDEO:
							mime = "video/mp4";
							name = filename + ".mp4";
							break;
						case IMAGE:
							mime = "image/jpeg";
							name = filename + ".jpg";
							break;
						default:
							//ignore
							return;
					}
					logger.debug("File decrypted: {}", decryptedFile.getPath());
					logger.debug("Reading file to byte array");
					final byte[] data = FileUtils.readFileToByteArray(decryptedFile);
					logger.debug("Sending blob to Threema Web");
					postSuccess(
						receiverType, receiverId, temporaryId, messageId,
						data, mime, name
					);
					if (decryptedFile.delete()) {
						logger.debug("Could not delete file");
					}
				} catch (IOException x) {
					logger.error("Exception", x);
				}
			}
		});

		final boolean downloaded = player.open();

		if (!downloaded) {
			this.failed(receiverType, receiverId, temporaryId, messageId, Protocol.ERROR_BLOB_DOWNLOAD_FAILED);
		}
	}

	private void success(@NonNull String receiverType,
	                     @NonNull String receiverId,
	                     @NonNull String temporaryId,
	                     int messageId,
	                     @NonNull byte[] blob,
	                     @NonNull String mime,
	                     @NonNull String name) {
		logger.debug("Blob download success");

		final MsgpackObjectBuilder args = new MsgpackObjectBuilder()
			.put(Protocol.ARGUMENT_RECEIVER_TYPE, receiverType)
			.put(Protocol.ARGUMENT_RECEIVER_ID, receiverId)
			.put(Protocol.ARGUMENT_TEMPORARY_ID, temporaryId)
			.put(Protocol.ARGUMENT_MESSAGE_ID, String.valueOf(messageId))
			.put(Protocol.ARGUMENT_SUCCESS, true);
		final MsgpackObjectBuilder data = new MsgpackObjectBuilder()
			.put(Protocol.ARGUMENT_BLOB_BLOB, blob)
			.put(Protocol.ARGUMENT_BLOB_TYPE, mime)
			.put(Protocol.ARGUMENT_BLOB_NAME, name);
		this.send(this.dispatcher, data, args);
	}

	private void failed(@NonNull String receiverType,
	                    @NonNull String receiverId,
	                    @NonNull String temporaryId,
	                    int messageId,
	                    @ErrorCode String errorCode) {
		logger.debug("Blob download failed ({})", errorCode);

		final MsgpackObjectBuilder args = new MsgpackObjectBuilder()
			.put(Protocol.ARGUMENT_RECEIVER_TYPE, receiverType)
			.put(Protocol.ARGUMENT_RECEIVER_ID, receiverId)
			.put(Protocol.ARGUMENT_TEMPORARY_ID, temporaryId)
			.put(Protocol.ARGUMENT_MESSAGE_ID, String.valueOf(messageId))
			.put(Protocol.ARGUMENT_SUCCESS, false)
			.put(Protocol.ARGUMENT_ERROR, errorCode);

		this.send(this.dispatcher, (MsgpackObjectBuilder) null, args);
	}

	/**
	 * Run `success()` on the worker thread.
	 */
	@AnyThread
	private void postSuccess(@NonNull String receiverType,
	                         @NonNull String receiverId,
	                         @NonNull String temporaryId,
	                         int messageId,
	                         @NonNull byte[] blob,
	                         @NonNull String mime,
	                         @NonNull String name) {
		this.handler.post(new Runnable() {
			@Override
			@WorkerThread
			public void run() {
				BlobRequestHandler.this.success(receiverType, receiverId, temporaryId, messageId, blob, mime, name);
			}
		});
	}

	/**
	 * Run `failed()` on the worker thread.
	 */
	@AnyThread
	private void postFailed(@NonNull String receiverType,
	                        @NonNull String receiverId,
	                        @NonNull String temporaryId,
	                        int messageId,
	                        @ErrorCode String errorCode) {
		this.handler.post(new Runnable() {
			@Override
			@WorkerThread
			public void run() {
				BlobRequestHandler.this.failed(receiverType, receiverId, temporaryId, messageId, errorCode);
			}
		});
	}

	@Override
	protected boolean maybeNeedsConnection() {
		return false;
	}
}
