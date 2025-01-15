/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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

package ch.threema.app.services;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.text.format.DateUtils;
import android.util.SparseIntArray;
import android.widget.Toast;

import com.neilalexander.jnacl.NaCl;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.collection.ArrayMap;
import androidx.core.app.NotificationManagerCompat;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.emojis.EmojiUtil;
import ch.threema.app.exceptions.NotAllowedException;
import ch.threema.app.exceptions.TranscodeCanceledException;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.DistributionListMessageReceiver;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.multidevice.MultiDeviceManager;
import ch.threema.app.routines.ReadMessagesRoutine;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.services.ballot.BallotUpdateResult;
import ch.threema.app.services.messageplayer.MessagePlayerService;
import ch.threema.app.services.notification.NotificationService;
import ch.threema.app.stores.IdentityStore;
import ch.threema.app.tasks.OutgoingGroupDeliveryReceiptMessageTask;
import ch.threema.app.ui.MediaItem;
import ch.threema.app.utils.BallotUtil;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.ExifInterface;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.GeoLocationUtil;
import ch.threema.app.utils.IconUtil;
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.QuoteUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.StringConversionUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.ThumbnailUtil;
import ch.threema.app.utils.VideoUtil;
import ch.threema.app.video.transcoder.VideoConfig;
import ch.threema.app.video.transcoder.VideoTranscoder;
import ch.threema.app.voip.groupcall.GroupCallDescription;
import ch.threema.base.ProgressListener;
import ch.threema.base.ThreemaException;
import ch.threema.base.crypto.SymmetricEncryptionResult;
import ch.threema.base.crypto.SymmetricEncryptionService;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;
import ch.threema.data.models.EmojiReactionData;
import ch.threema.data.repositories.EditHistoryRepository;
import ch.threema.data.repositories.EmojiReactionEntryCreateException;
import ch.threema.data.repositories.EmojiReactionEntryRemoveException;
import ch.threema.data.repositories.EmojiReactionsRepository;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.blob.BlobScope;
import ch.threema.domain.protocol.blob.BlobUploader;
import ch.threema.domain.protocol.csp.MessageTooLongException;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.AudioMessage;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.domain.protocol.csp.messages.DeleteMessage;
import ch.threema.domain.protocol.csp.messages.EditMessage;
import ch.threema.domain.protocol.csp.messages.GroupAudioMessage;
import ch.threema.domain.protocol.csp.messages.GroupImageMessage;
import ch.threema.domain.protocol.csp.messages.location.GroupLocationMessage;
import ch.threema.domain.protocol.csp.messages.GroupTextMessage;
import ch.threema.domain.protocol.csp.messages.GroupVideoMessage;
import ch.threema.domain.protocol.csp.messages.ImageMessage;
import ch.threema.domain.protocol.csp.messages.location.LocationMessage;
import ch.threema.domain.protocol.csp.messages.TextMessage;
import ch.threema.domain.protocol.csp.messages.VideoMessage;
import ch.threema.domain.protocol.csp.messages.ballot.BallotSetupInterface;
import ch.threema.domain.protocol.csp.messages.ballot.GroupPollSetupMessage;
import ch.threema.domain.protocol.csp.messages.ballot.PollSetupMessage;
import ch.threema.domain.protocol.csp.messages.file.FileData;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityMode;
import ch.threema.domain.protocol.csp.messages.location.Poi;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.protobuf.csp.e2e.Reaction;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.factories.GroupMessageModelFactory;
import ch.threema.storage.factories.MessageModelFactory;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.FirstUnreadMessageModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.ServerMessageModel;
import ch.threema.storage.models.access.GroupAccessModel;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.data.LocationDataModel;
import ch.threema.storage.models.data.MessageContentsType;
import ch.threema.storage.models.data.media.AudioDataModel;
import ch.threema.storage.models.data.media.BallotDataModel;
import ch.threema.storage.models.data.media.FileDataModel;
import ch.threema.storage.models.data.media.ImageDataModel;
import ch.threema.storage.models.data.media.MediaMessageDataInterface;
import ch.threema.storage.models.data.media.VideoDataModel;
import ch.threema.storage.models.data.status.ForwardSecurityStatusDataModel;
import ch.threema.storage.models.data.status.GroupCallStatusDataModel;
import ch.threema.storage.models.data.status.GroupStatusDataModel;
import ch.threema.storage.models.data.status.VoipStatusDataModel;

import static ch.threema.app.ThreemaApplication.MAX_BLOB_SIZE;
import static ch.threema.app.ThreemaApplication.MAX_BLOB_SIZE_MB;
import static ch.threema.app.services.PreferenceService.ImageScale_DEFAULT;
import static ch.threema.app.ui.MediaItem.TIME_UNDEFINED;
import static ch.threema.app.ui.MediaItem.TYPE_FILE;
import static ch.threema.app.ui.MediaItem.TYPE_IMAGE;
import static ch.threema.app.ui.MediaItem.TYPE_IMAGE_ANIMATED;
import static ch.threema.app.ui.MediaItem.TYPE_IMAGE_CAM;
import static ch.threema.app.ui.MediaItem.TYPE_LOCATION;
import static ch.threema.app.ui.MediaItem.TYPE_TEXT;
import static ch.threema.app.ui.MediaItem.TYPE_VIDEO;
import static ch.threema.app.ui.MediaItem.TYPE_VIDEO_CAM;
import static ch.threema.app.ui.MediaItem.TYPE_VOICEMESSAGE;
import static ch.threema.app.utils.StreamUtilKt.getFromUri;
import static ch.threema.domain.protocol.csp.messages.file.FileData.RENDERING_STICKER;

public class MessageServiceImpl implements MessageService {
	private static final Logger logger = LoggingUtil.getThreemaLogger("MessageServiceImpl");

	public static final long FILE_AUTO_DOWNLOAD_MAX_SIZE_M = 5; // MB
	public static final long FILE_AUTO_DOWNLOAD_MAX_SIZE_ISO = FILE_AUTO_DOWNLOAD_MAX_SIZE_M * 1024 * 1024; // used for calculations
	public static final long FILE_AUTO_DOWNLOAD_MAX_SIZE_SI = FILE_AUTO_DOWNLOAD_MAX_SIZE_M * 1000 * 1000; // used for presentation only
	public static final int THUMBNAIL_SIZE_PX = 512;

	private final @NonNull Context context;

	// Services
	private final MessageSendingService messageSendingService;
	private final DatabaseServiceNew databaseServiceNew;
	private final ContactService contactService;
	private final FileService fileService;
	private final IdentityStore identityStore;
	private final BallotService ballotService;
	private final PreferenceService preferenceService;
	private final LockAppService appLockService;
	private final GroupService groupService;
	private final ApiService apiService;
	private final DownloadService downloadService;
	private final DeadlineListService hiddenChatsListService;
    @NonNull
	private final BlockedIdentitiesService blockedIdentitiesService;
	private final SymmetricEncryptionService symmetricEncryptionService;

    // Repositories
    private final EditHistoryRepository editHistoryRepository;
	private final EmojiReactionsRepository emojiReactionsRepository;

    // Managers
    private final MultiDeviceManager multiDeviceManager;

	// Caches
	private final Collection<MessageModel> contactMessageCache;
	private final Collection<GroupMessageModel> groupMessageCache;
	private final Collection<DistributionListMessageModel> distributionListMessageCache;

	private final SparseIntArray loadingProgress = new SparseIntArray();

	public MessageServiceImpl(
        @NonNull
        Context context,
        CacheService cacheService,
        DatabaseServiceNew databaseServiceNew,
        ContactService contactService,
        FileService fileService,
        IdentityStore identityStore,
        SymmetricEncryptionService symmetricEncryptionService,
        PreferenceService preferenceService,
        LockAppService appLockService,
        BallotService ballotService,
        GroupService groupService,
        ApiService apiService,
        DownloadService downloadService,
        DeadlineListService hiddenChatsListService,
        @NonNull BlockedIdentitiesService blockedIdentitiesService,
		MultiDeviceManager multiDeviceManager,
        EditHistoryRepository editHistoryRepository,
		EmojiReactionsRepository emojiReactionsRepository
    ) {
		this.context = context;
		this.databaseServiceNew = databaseServiceNew;
		this.contactService = contactService;
		this.fileService = fileService;
		this.identityStore = identityStore;
		this.symmetricEncryptionService = symmetricEncryptionService;
		this.preferenceService = preferenceService;
		this.appLockService = appLockService;
		this.ballotService = ballotService;
		this.groupService = groupService;
		this.apiService = apiService;
		this.downloadService = downloadService;
		this.hiddenChatsListService = hiddenChatsListService;
		this.blockedIdentitiesService = blockedIdentitiesService;

		contactMessageCache = cacheService.getMessageModelCache();
		groupMessageCache = cacheService.getGroupMessageModelCache();
		distributionListMessageCache = cacheService.getDistributionListMessageCache();

		this.multiDeviceManager = multiDeviceManager;

		this.editHistoryRepository = editHistoryRepository;
		this.emojiReactionsRepository = emojiReactionsRepository;

        // init queue
		messageSendingService = new MessageSendingServiceExponentialBackOff(new MessageSendingService.MessageSendingServiceState() {
			@Override
			public void processingFailed(AbstractMessageModel messageModel, MessageReceiver<AbstractMessageModel> receiver) {
				//remove send machine
				removeSendMachine(messageModel);
				updateOutgoingMessageState(messageModel, MessageState.SENDFAILED, new Date());
			}

			@Override
			public void exception(Exception x, int tries) {
				if (tries >= 5) {
					logger.error("Exception", x);
				}
			}
		});
	}

	private void cache(AbstractMessageModel m) {
		if (m instanceof GroupMessageModel) {
			synchronized (groupMessageCache) {
				groupMessageCache.add((GroupMessageModel) m);
			}
		} else if (m instanceof MessageModel) {
			synchronized (contactMessageCache) {
				contactMessageCache.add((MessageModel) m);
			}
		}
	}

	@Override
	public AbstractMessageModel createStatusMessage(String statusMessage, MessageReceiver receiver) {
		AbstractMessageModel model = receiver.createAndSaveStatusModel(statusMessage, new Date());
		fireOnCreatedMessage(model);
		return model;
	}

	@Override
	public AbstractMessageModel createVoipStatus(
		@NonNull VoipStatusDataModel data,
		@NonNull MessageReceiver receiver,
		boolean isOutbox,
		boolean isRead
	) {
		logger.info("Storing voip status message (outbox={}, status={}, reason={})",
			isOutbox, data.getStatus(), data.getReason());
		final AbstractMessageModel model = receiver.createLocalModel(
			MessageType.VOIP_STATUS,
			MessageContentsType.VOIP_STATUS,
			data.getDate() != null ? data.getDate() : new Date()
		);
		model.setOutbox(isOutbox);
		model.setVoipStatusData(data);
		model.setSaved(true);
		model.setRead(isRead);
		receiver.saveLocalModel(model);
		fireOnCreatedMessage(model);
		return model;
	}

	@Override
	@Nullable
	public AbstractMessageModel createGroupCallStatus(
		@NonNull GroupCallStatusDataModel data,
		@NonNull MessageReceiver receiver,
		@Nullable ContactModel callerContactModel,
		@Nullable GroupCallDescription call,
		boolean isOutbox,
		Date postedDate) {
		if (receiver instanceof GroupMessageReceiver && ((GroupMessageReceiver) receiver).getGroup() == null) {
			logger.info("Unable to store group call status message. Group no longer exists");
			return null;
		}

		logger.info("Storing group call status message for call={}", call != null ? call.getCallId() : "n/a");
		final AbstractMessageModel model = receiver.createLocalModel(
			MessageType.GROUP_CALL_STATUS,
			MessageContentsType.GROUP_CALL_STATUS,
			new Date()
		);
		model.setPostedAt(postedDate);
		model.setOutbox(isOutbox);
		model.setGroupCallStatusData(data);
		model.setSaved(true);
		model.setIsStatusMessage(true);
		model.setRead(data.getStatus() != GroupCallStatusDataModel.STATUS_STARTED);
		receiver.saveLocalModel(model);
		fireOnCreatedMessage(model);
		return model;
	}

	@Override
	public AbstractMessageModel createForwardSecurityStatus(
		@NonNull MessageReceiver receiver,
		@ForwardSecurityStatusDataModel.ForwardSecurityStatusType int type,
		int quantity,
		@Nullable String staticText) {
		logger.info("Storing forward security status message of type {}", type);

		final AbstractMessageModel model = receiver.createLocalModel(
			MessageType.FORWARD_SECURITY_STATUS,
			MessageContentsType.FORWARD_SECURITY_STATUS,
			new Date()
		);
		model.setOutbox(false);
		model.setForwardSecurityStatusData(ForwardSecurityStatusDataModel.create(type, quantity, staticText));
		model.setSaved(true);
		model.setIsStatusMessage(true);
		model.setRead(true);
		receiver.saveLocalModel(model);
		fireOnCreatedMessage(model);
		return model;
	}

	@Override
	public AbstractMessageModel createGroupStatus(
		@NonNull GroupMessageReceiver receiver,
		@NonNull GroupStatusDataModel.GroupStatusType type,
		@Nullable String identity,
		@Nullable String ballotName,
		@Nullable String newGroupName
	) {
		logger.info("Storing group status message of type {}", type.getType());

		final GroupMessageModel model = receiver.createLocalModel(
			MessageType.GROUP_STATUS,
			MessageContentsType.GROUP_STATUS,
			new Date()
		);
		model.setOutbox(false);
		model.setGroupStatusData(GroupStatusDataModel.create(type, identity, ballotName, newGroupName));
		model.setSaved(true);
		model.setIsStatusMessage(true);
		model.setRead(true);
		receiver.saveLocalModel(model);
		fireOnCreatedMessage(model);
		return model;
	}

	public AbstractMessageModel createNewBallotMessage(
			MessageId messageId,
			BallotModel ballotModel,
			BallotDataModel.Type type,
			MessageReceiver receiver,
			int messageFlags,
			ForwardSecurityMode forwardSecurityMode) {
		AbstractMessageModel model = receiver.createLocalModel(MessageType.BALLOT, MessageContentsType.BALLOT, new Date());
		if (model != null) {
			//hack: save ballot id into body string
			model.setIdentity(ballotModel.getCreatorIdentity());
			model.setSaved(true);
			model.setBallotData(new BallotDataModel(type, ballotModel.getId()));
			model.setOutbox(ballotModel.getCreatorIdentity().equals(identityStore.getIdentity()));
			model.setApiMessageId(messageId.toString());
			model.setMessageFlags(messageFlags);
			model.setForwardSecurityMode(forwardSecurityMode);
			receiver.saveLocalModel(model);
			cache(model);
			fireOnCreatedMessage(model);
		}

		return model;
	}

	/**
	 * Send a text message to the specified receiver.
	 *
	 * @param message The message text. May not be longer than {@link ProtocolDefines#MAX_TEXT_MESSAGE_LEN} UTF-8 bytes.
	 * @param messageReceiver The receiver for this message.
	 * @return the model of the sent message
	 * @throws MessageTooLongException if the message is too long.
	 * @throws ThreemaException if the message text is empty after trimming.
	 */
	@Override
	public AbstractMessageModel sendText(
		@NonNull String message,
		@NonNull MessageReceiver messageReceiver
	) throws ThreemaException {
		final String tag = "sendTextMessage";

		logger.info("{}: start", tag);

		String trimmedMessage = validateTextMessage(message);

		logger.debug("{}: create model instance", tag);
		final AbstractMessageModel messageModel = messageReceiver.createLocalModel(MessageType.TEXT, MessageContentsType.TEXT, new Date());
		logger.debug("{}: cache", tag);
		cache(messageModel);

		messageModel.setOutbox(true);
		messageModel.setBodyAndQuotedMessageId(trimmedMessage);
		messageModel.setState(MessageState.SENDING);
		messageModel.setSaved(true);

		logger.debug("{}: save db", tag);
		messageReceiver.saveLocalModel(messageModel);
		logger.debug("{}: fire create message", tag);
		fireOnCreatedMessage(messageModel);

		messageReceiver.createAndSendTextMessage(messageModel);
		String messageId = messageModel.getApiMessageId();
		logger.info("{}: message {} successfully queued", tag, (messageId != null ? messageId : messageModel.getId()));
		messageReceiver.saveLocalModel(messageModel);

		fireOnModifiedMessage(messageModel);

		return messageModel;
	}

	@Override
	public void sendEditedMessageText(
		@NonNull AbstractMessageModel message, // Let `message` be the referred message.
		@NonNull String newText,
		@NonNull Date editedAt,
		@NonNull MessageReceiver receiver
	) throws ThreemaException {
		logger.debug("editText message = {}", message.getApiMessageId());

		if (!message.isOutbox()) {
			throw new ThreemaException("Tried editing a message that is not outgoing. message = " + message.getApiMessageId());
		}

		String trimmedNewText = validateTextMessage(newText);

		if (Objects.equals(message.getBody(), trimmedNewText)) {
			throw new ThreemaException("Tried editing a message with no changes. message = " + message.getApiMessageId());
		}

		if (message.getPostedAt() == null) {
			logger.error("postedAt is null for messageId={}}", message.getId());
			return;
		}

		// Let `created-at` be the current timestamp to be applied to the edit
		//  message.
		long deltaTime = editedAt.getTime() - message.getPostedAt().getTime();
		// If the referred message has been sent (`sent-at`) more than 6 hours ago,
		//  prevent creation and abort these steps.
		if (deltaTime > EditMessage.EDIT_MESSAGES_MAX_AGE) {
			logger.error("Cannot edit message older than {}ms", EditMessage.EDIT_MESSAGES_MAX_AGE);
			return;
		}

		if (receiver instanceof ContactMessageReceiver) {
			((ContactMessageReceiver) receiver).sendEditMessage(
				message.getId(),
				trimmedNewText,
				editedAt
			);
		} else if (receiver instanceof GroupMessageReceiver) {
			((GroupMessageReceiver) receiver).sendEditMessage(
				message.getId(),
				trimmedNewText,
				editedAt
			);
		} else {
			throw new ThreemaException("Unsupported receiver type of: " + receiver.getClass());
		}

        saveEditedMessageText(message, newText, editedAt);
	}

	@Override
	public void saveEditedMessageText(@NonNull AbstractMessageModel message, String text, @Nullable Date editedAt) {
		logger.info("Save edited message = {}", message.getApiMessageId());

        if (editedAt != null) {
            editHistoryRepository.createEntry(message);
        }

        // Edit `message` as defined by the associated _Edit applies to_ property and
        //  add an indicator to `message`, informing the user that the message has
        //  been edited by the user at `created-at`.
		switch (message.getType()) {
			case TEXT:
				message.setBody(text);
				break;
			case FILE:
				message.setCaption(text);
				message.getFileData().setCaption(text);
				message.setBody(message.getFileData().toString());
				break;
			default:
				logger.error("Tried saving an edited message of unsupported type {} for messageId = {}}", message.getType(), message.getId());
				return;
		}

		message.setEditedAt(editedAt);

		save(message);
		fireOnModifiedMessage(message);
		fireOnEditMessage(message);
	}

	@Override
	public void saveEmojiReactionMessage(@NonNull AbstractMessageModel targetMessage, @NonNull String senderIdentity, Reaction.ActionCase actionCase, @NonNull String emojiSequence) {
		logger.debug("saving emoji reaction of type {} to message {}", actionCase, targetMessage.getApiMessageId());

		if (actionCase == Reaction.ActionCase.APPLY) {
			try {
				emojiReactionsRepository.createEntry(targetMessage, senderIdentity, emojiSequence);
			} catch (EmojiReactionEntryCreateException|IllegalStateException e) {
				logger.error("Unable to create emoji reaction.");
				return;
			}
		} else if (actionCase == Reaction.ActionCase.WITHDRAW) {
			try {
				emojiReactionsRepository.removeEntry(targetMessage, senderIdentity, emojiSequence);
			} catch (EmojiReactionEntryRemoveException|IllegalStateException e) {
				logger.error("Unable to remove emoji reaction.");
				return;
			}
		} else {
			logger.debug("Unsupported emoji reaction action case {}. ignoring.", actionCase);
			return;
		}
		fireOnModifiedMessage(targetMessage);
	}

	@Override
	public void clearMessageState(@NonNull AbstractMessageModel targetMessage) {
		if (targetMessage.getState() != MessageState.USERACK && targetMessage.getState() != MessageState.USERDEC) {
			return;
		}

		MessageState newMessageState;
		String myIdentity = identityStore != null ? identityStore.getIdentity() : null;

		if (targetMessage.isRead()) {
			newMessageState = MessageState.READ;
		} else if (targetMessage.getDeliveredAt() != null) {
			newMessageState = MessageState.DELIVERED;
		} else {
			newMessageState = MessageState.SENT;
		}

		targetMessage.setState(newMessageState);
		if (targetMessage instanceof GroupMessageModel && myIdentity != null) {
			groupService.removeGroupMessageState((GroupMessageModel) targetMessage, myIdentity);
		}
		save(targetMessage);
	}

	@WorkerThread
	@Override
	public synchronized boolean sendEmojiReaction(@NonNull AbstractMessageModel message, @NonNull String emojiSequence, @NonNull MessageReceiver receiver, boolean markAsRead) throws ThreemaException {
		logger.debug("Send emoji reaction to message {}", message.getApiMessageId());
		logger.trace("Reaction: '{}'", emojiSequence);

        if (!EmojiUtil.isFullyQualifiedEmoji(emojiSequence)) {
            logger.warn("Attempt to send non fully-qualified emoji sequence '{}'", emojiSequence);
            // Return true, as the return value only indicates whether this failed due to
            // compatibility issues when a phase 1 client tries to send an emoji sequence
            // to a client without reactions support.
            return true;
        }

        @MessageReceiver.EmojiReactionsSupport
        final int reactionSupport = receiver.getEmojiReactionSupport();

		if (reactionSupport != MessageReceiver.Reactions_NONE) {
			final String myIdentity = identityStore.getIdentity();
			List<EmojiReactionData> emojiReactionData = emojiReactionsRepository.safeGetReactionsByMessage(message);

			Reaction.ActionCase actionCase = Reaction.ActionCase.APPLY;

			// check if there's already an identical reaction with us as the sender. if yes, withdraw it.
			if (containsEmojiSequence(emojiReactionData, emojiSequence, identityStore.getIdentity())) {
				actionCase = Reaction.ActionCase.WITHDRAW;
			}

			if (receiver instanceof ContactMessageReceiver) {
				((ContactMessageReceiver) receiver).sendReactionMessage(
					message,
					actionCase,
					emojiSequence,
					new Date() // use current timestamp for reaction message
				);
			} else if (receiver instanceof GroupMessageReceiver) {
				((GroupMessageReceiver) receiver).sendReactionMessage(
					message,
					actionCase,
					emojiSequence,
					new Date() // use current timestamp for reaction message
				);
			} else {
				throw new ThreemaException("Unsupported receiver type of: " + receiver.getClass());
			}

			if (actionCase == Reaction.ActionCase.APPLY) {
				emojiReactionsRepository.createEntry(message, myIdentity, emojiSequence);
			} else {
				emojiReactionsRepository.removeEntry(message, myIdentity, emojiSequence);
			}

            showToastOnPartialReactionSupport(
                reactionSupport,
                actionCase,
                emojiSequence
            );

			fireOnModifiedMessage(message);
		} else {
			// fallback to ack/dec
			if (EmojiUtil.isThumbsUpEmoji(emojiSequence)) {
				if (MessageUtil.canSendUserAcknowledge(message)) {
					sendDeliveryReceiptAckDec(message, markAsRead, ProtocolDefines.DELIVERYRECEIPT_MSGUSERACK, MessageState.USERACK);
				} else {
					logger.error("Unable to send ack message.");
				}
			} else if (EmojiUtil.isThumbsDownEmoji(emojiSequence)) {
				if (MessageUtil.canSendUserDecline(message)) {
					sendDeliveryReceiptAckDec(message, markAsRead, ProtocolDefines.DELIVERYRECEIPT_MSGUSERDEC, MessageState.USERDEC);
				} else {
					logger.error("Unable to send dec message");
				}
			} else {
				// TODO(ANDR-3492): Remove else branch and probably set method's return type to `void`
				// unsupported emoji sequence (V1 to V2)
				return false;
			}
		}
		return true;
	}

    /**
     * Show a toast if reaction support is only partial for the current receiver.
     * Also note that for now a toast is only shown if the action is APPLY.
     * If the reaction can be mapped to ACK/DEC, no toast is shown.
     */
    @AnyThread
    private void showToastOnPartialReactionSupport(
        @MessageReceiver.EmojiReactionsSupport int reactionSupport,
        @NonNull Reaction.ActionCase actionCase,
        @NonNull String emojiSequence
    ) {
        if (reactionSupport == MessageReceiver.Reactions_PARTIAL
            && actionCase == Reaction.ActionCase.APPLY
            && !EmojiUtil.isThumbsUpOrDownEmoji(emojiSequence)) {
            RuntimeUtil.runOnUiThread(() ->
                Toast.makeText(context, R.string.group_emoji_reactions_partially_supported, Toast.LENGTH_SHORT).show());
        }
    }

	private boolean containsEmojiSequence(@Nullable List<EmojiReactionData> emojiReactionData, @NonNull String emojiSequence, @NonNull String senderIdentity) {
		return
			emojiReactionData != null &&
			emojiReactionData.stream().anyMatch(a -> a.senderIdentity.equals(senderIdentity) && a.emojiSequence.equals(emojiSequence));
	}

	@Override
	public void sendDeleteMessage(
		@NonNull AbstractMessageModel message, // Let `message` be the referred message.
		@NonNull MessageReceiver receiver
	) throws Exception {
		logger.debug("sendDeleteMessage        message = {}", message.getApiMessageId());

		if (!message.isOutbox()) {
			logger.error("Tried deleting a message that is not outgoing. message = {}", message.getId());
		}

		if (message.getPostedAt() == null) {
			logger.error("postedAt is null for messageId={}}", message.getId());
			return;
		}

		// 3. Let `created-at` be the current timestamp to be applied to the delete
		//    message.
		Date createdAt = new Date();
		long deltaTime = createdAt.getTime() - message.getPostedAt().getTime();
		// 2. If the referred message has been sent (`sent-at`) more than 6 hours ago,
		//    prevent creation and abort these steps.
		if (deltaTime > DeleteMessage.DELETE_MESSAGES_MAX_AGE) {
			logger.error("Cannot delete message older than {}}ms", DeleteMessage.DELETE_MESSAGES_MAX_AGE);
		}

		// 4. Replace `message` with a message informing the user that the message of
		//    the user has been removed at `created-at`.
		deleteMessageContentsAndRelatedData(message, createdAt);

		if (receiver instanceof ContactMessageReceiver) {
			((ContactMessageReceiver) receiver).sendDeleteMessage(
				message.getId(),
				createdAt
			);
		} else if (receiver instanceof GroupMessageReceiver) {
			((GroupMessageReceiver) receiver).sendDeleteMessage(
				message.getId(),
				createdAt
			);
		} else {
			throw new ThreemaException("Unsupported receiver type of: " + receiver.getClass());
		}
	}

	@Override
	public void deleteMessageContentsAndRelatedData(@NonNull AbstractMessageModel message, Date deletedAt) {
		logger.info("deleteMessageContents = {}", message.getApiMessageId());

		fileService.removeMessageFiles(message, true);

		message.setBody(null);
		message.setCaption(null);

		message.setState(null);
		if (message instanceof GroupMessageModel) {
			((GroupMessageModel) message).setGroupMessageStates(null);
		}

		message.setDeletedAt(deletedAt);

		save(message);

        // Delete the edit history and emoji reactions. Note that the foreign keys do not work in this case, as the
        // original message entry is not removed from the database.
        editHistoryRepository.deleteByMessageUid(message.getUid());

        emojiReactionsRepository.deleteAllReactionsForMessage(message);

		fireOnModifiedMessage(message);
		fireOnMessageDeletedForAll(message);
	}

	@Override
	public AbstractMessageModel sendLocation(@NonNull Location location, @Nullable String poiName, MessageReceiver receiver, final CompletionHandler completionHandler) throws ThreemaException {
		final String tag = "sendLocationMessage";
		logger.info("{}: start", tag);

		AbstractMessageModel messageModel = receiver.createLocalModel(MessageType.LOCATION, MessageContentsType.LOCATION, new Date());
		cache(messageModel);

        @Nullable Poi poi = null;
		try {
			final @NonNull String lookedUpPoiAddress = GeoLocationUtil.getAddressFromLocation(
                context,
                location.getLatitude(),
                location.getLongitude()
            );
            if (poiName != null && !poiName.isBlank()) {
                poi = new Poi.Named(poiName, lookedUpPoiAddress);
            } else {
                poi = new Poi.Unnamed(lookedUpPoiAddress);
            }
		} catch (IOException e) {
			logger.error("Exception", e);
			//do not show this error!
		}

		messageModel.setLocationData(
            new LocationDataModel(
				location.getLatitude(),
				location.getLongitude(),
                (double) location.getAccuracy(),
                poi
		    )
        );

		messageModel.setOutbox(true);
		messageModel.setState(MessageState.PENDING);
		messageModel.setSaved(true);
		receiver.saveLocalModel(messageModel);

		fireOnCreatedMessage(messageModel);

		receiver.createAndSendLocationMessage(messageModel);

		fireOnModifiedMessage(messageModel);

		if (completionHandler != null)
			completionHandler.sendQueued(messageModel);

		return messageModel;
	}

	@Override
	@WorkerThread
	public void resendMessage(
		@NonNull AbstractMessageModel messageModel,
		@NonNull MessageReceiver<AbstractMessageModel> receiver,
		@Nullable CompletionHandler completionHandler,
		@NonNull Collection<String> recipientIdentities,
        @NonNull MessageId messageId,
        @NonNull TriggerSource triggerSource
	) throws Exception {
		NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
		notificationManager.cancel(ThreemaApplication.UNSENT_MESSAGE_NOTIFICATION_ID);

		if (messageModel.getState() == MessageState.SENDFAILED || messageModel.getState() == MessageState.FS_KEY_MISMATCH) {
			if (messageModel.getType() == MessageType.FILE) {
				resendFileMessage(messageModel, receiver, completionHandler, recipientIdentities);
			} else if (messageModel.getType() == MessageType.BALLOT) {
				BallotModel ballotModel = ballotService.get(messageModel.getBallotData().getBallotId());
				if (ballotModel != null) {
					resendBallotMessage(messageModel, ballotModel, receiver, messageId, triggerSource);
				}
			} else if (messageModel.getType() == MessageType.TEXT) {
				resendTextMessage(messageModel, receiver, recipientIdentities);
			} else if (messageModel.getType() == MessageType.LOCATION) {
				resendLocationMessage(messageModel, receiver, completionHandler, recipientIdentities);
			}
		}
	}

	@WorkerThread
	private void resendTextMessage(
		final @NonNull AbstractMessageModel messageModel,
		final MessageReceiver receiver,
		final Collection<String> recipientIdentities
	) {
		if (receiver instanceof ContactMessageReceiver && messageModel instanceof MessageModel) {
			((ContactMessageReceiver) receiver).resendTextMessage((MessageModel) messageModel);
		} else if (receiver instanceof GroupMessageReceiver && messageModel instanceof GroupMessageModel) {
			((GroupMessageReceiver) receiver).resendTextMessage(
				(GroupMessageModel) messageModel,
				recipientIdentities
			);
		} else if (receiver instanceof DistributionListMessageReceiver) {
			logger.warn("Cannot resend messages in a distribution list");
			return;
		} else {
			logger.warn("Incompatible message receiver and message model type");
			return;
		}

		updateOutgoingMessageState(messageModel, MessageState.SENDING, new Date());
		fireOnModifiedMessage(messageModel);
	}

	@WorkerThread
	private void resendLocationMessage(
		@NonNull AbstractMessageModel messageModel,
		@NonNull MessageReceiver receiver,
		final @Nullable CompletionHandler completionHandler,
		@NonNull Collection<String> recipientIdentities
	) {
		if (receiver instanceof ContactMessageReceiver && messageModel instanceof MessageModel) {
			((ContactMessageReceiver) receiver).resendLocationMessage((MessageModel) messageModel);
		} else if (receiver instanceof GroupMessageReceiver && messageModel instanceof GroupMessageModel) {
			((GroupMessageReceiver) receiver).resendLocationMessage(
				(GroupMessageModel) messageModel,
				recipientIdentities
			);
		} else if (receiver instanceof DistributionListMessageReceiver) {
			logger.warn("Cannot resend messages in a distribution list");
			return;
		} else {
			logger.error("Incompatible message receiver and message model type");
			return;
		}

		updateOutgoingMessageState(messageModel, MessageState.SENDING, new Date());
		fireOnModifiedMessage(messageModel);
		if (completionHandler != null) {
			completionHandler.sendQueued(messageModel);
		}
	}

	@WorkerThread
	private void resendFileMessage(
		final @NonNull AbstractMessageModel messageModel,
	    final @NonNull MessageReceiver<AbstractMessageModel> receiver,
	    final @Nullable CompletionHandler completionHandler,
		final @NonNull Collection<String> recipientIdentities
	) throws Exception {

		// check if a message file exists that could be resent or abort immediately
		File file = fileService.getMessageFile(messageModel);
		if (file == null || !file.exists()) {
			throw new ThreemaException("Message file not present");
		}

		updateOutgoingMessageState(messageModel, MessageState.PENDING, new Date());

		//enqueue processing and uploading stuff...
		messageSendingService.addToQueue(new MessageSendingService.MessageSendingProcess() {
			public byte[] blobIdThumbnail;
			public byte[] blobId;
			public byte[] thumbnailData;
			public byte[] fileData;
			public int fileDataBoxedLength;

			private SymmetricEncryptionResult contentEncryptResult;
			private SymmetricEncryptionResult thumbnailEncryptResult;

			public boolean success = false;

			@Override
			public MessageReceiver<AbstractMessageModel> getReceiver() {
				return receiver;
			}

			@Override
			public AbstractMessageModel getMessageModel() {
				return messageModel;
			}

			@Override
			public boolean send() throws Exception {
				SendMachine sendMachine = getSendMachine(messageModel);
				sendMachine.reset()
					.next(() -> {
						// get file data
						File decryptedMessageFile = fileService.getDecryptedMessageFile(messageModel);

						if (decryptedMessageFile != null) {

							try (FileInputStream inputStream = new FileInputStream(decryptedMessageFile)) {
								fileDataBoxedLength = inputStream.available();
								fileData = new byte[fileDataBoxedLength + NaCl.BOXOVERHEAD];
								IOUtils.readFully(inputStream,
									fileData,
									NaCl.BOXOVERHEAD,
									fileDataBoxedLength);
							}
						} else {
							throw new ThreemaException("Message file not present");
						}
					})
					.next(() -> {
						// encrypt file data
						contentEncryptResult = symmetricEncryptionService.encryptInplace(fileData, ProtocolDefines.FILE_NONCE);
						if (contentEncryptResult.isEmpty()) {
							throw new ThreemaException("File data encrypt failed");
						}
					})
					.next(() -> {
						// get thumbnail data
						try (InputStream is = fileService.getDecryptedMessageThumbnailStream(messageModel)) {
							if (is != null) {
								thumbnailData = IOUtils.toByteArray(is);
							} else {
								thumbnailData = null;
							}
						} catch (Exception e) {
							logger.debug("No thumbnail for file message");
						}
					})
					.next(() -> {
						// upload (encrypted) file data
						BlobUploader blobUploader = initUploader(
                            getMessageModel(),
                            contentEncryptResult.getData(),
                            getReceiver()
                        );
						blobUploader.progressListener = new ProgressListener() {
							@Override
							public void updateProgress(int progress) {
								updateMessageLoadingProgress(messageModel, progress);
							}

							@Override
							public void onFinished(boolean success) {
								setMessageLoadingFinished(messageModel);
							}
						};
						blobId = blobUploader.upload();
					})
					.next(() -> {
						if (thumbnailData != null) {
							// encrypt and upload thumbnail data
							thumbnailEncryptResult = symmetricEncryptionService.encrypt(thumbnailData, contentEncryptResult.getKey(), ProtocolDefines.FILE_THUMBNAIL_NONCE);

							if (thumbnailEncryptResult.isEmpty()) {
								throw new ThreemaException("Thumbnail encryption failed");
							} else {
								BlobUploader blobUploader = initUploader(
                                    getMessageModel(),
                                    thumbnailEncryptResult.getData(),
                                    getReceiver()
                                );
								blobUploader.progressListener = new ProgressListener() {
									@Override
									public void updateProgress(int progress) {
										updateMessageLoadingProgress(messageModel, progress);
									}

									@Override
									public void onFinished(boolean success) {
										setMessageLoadingFinished(messageModel);
									}
								};
								blobIdThumbnail = blobUploader.upload();
							}
						}
					})
					.next(() -> {
						String messageId = messageModel.getApiMessageId();
						getReceiver().createAndSendFileMessage(
							blobIdThumbnail,
							blobId,
							contentEncryptResult,
							messageModel,
							messageId != null ? MessageId.fromString(messageId) : null,
							recipientIdentities
						);
						save(messageModel);
					})
					.next(() -> {
						updateOutgoingMessageState(messageModel, MessageState.SENDING, new Date());

						if (completionHandler != null)
							completionHandler.sendComplete(messageModel);

						success = true;
					});

				if (success) {
					removeSendMachine(sendMachine);
				}
				return success;
			}
		});
	}

	@Override
	public AbstractMessageModel sendBallotMessage(
        @NonNull BallotModel ballotModel,
        @NonNull MessageId messageId,
        @NonNull TriggerSource triggerSource
    ) throws MessageTooLongException {
		//create a new ballot model
			MessageReceiver receiver = ballotService.getReceiver(ballotModel);

        if (receiver != null) {
            //ok...
            logger.debug("sendBallotMessage to {}", receiver);
					final AbstractMessageModel messageModel = receiver.createLocalModel(MessageType.BALLOT, MessageContentsType.BALLOT, new Date());
            cache(messageModel);

            messageModel.setOutbox(true);
            messageModel.setState(MessageState.PENDING);

            messageModel.setBallotData(new BallotDataModel(
                ballotModel.getState() == BallotModel.State.OPEN ?
                    BallotDataModel.Type.BALLOT_CREATED :
									BallotDataModel.Type.BALLOT_CLOSED,
                ballotModel.getId()));

            messageModel.setSaved(true);
            receiver.saveLocalModel(messageModel);
            fireOnCreatedMessage(messageModel);
            resendBallotMessage(messageModel, ballotModel, receiver, messageId, triggerSource);

            return messageModel;
        }

		return null;
	}

	private void resendBallotMessage(
        AbstractMessageModel messageModel,
        BallotModel ballotModel,
        MessageReceiver<?> receiver,
        @NonNull MessageId messageId,
        @NonNull TriggerSource triggerSource
    ) throws MessageTooLongException {
		//get ballot data
		if(!TestUtil.required(messageModel, ballotModel, receiver)) {
			return;
		}
		updateOutgoingMessageState(messageModel, MessageState.PENDING, new Date());
		try {
			ballotService.publish(receiver, ballotModel, messageModel, messageId, triggerSource);
		}
		catch (NotAllowedException | MessageTooLongException x) {
			logger.error("Exception", x);
			if (x instanceof MessageTooLongException) {
				remove(messageModel);
				fireOnRemovedMessage(messageModel);
				throw new MessageTooLongException();
			} else {
				updateOutgoingMessageState(messageModel, MessageState.SENDFAILED, new Date());
			}
		}
	}

	private boolean sendDeliveryReceipts(@NonNull AbstractMessageModel messageModel, int type) throws ThreemaException {
		if (messageModel instanceof GroupMessageModel) {
			return sendGroupDeliveryReceipt((GroupMessageModel) messageModel, type);
		} else {
			return sendDeliveryReceipt(messageModel, type);
		}
	}

	private boolean sendDeliveryReceipt(@NonNull AbstractMessageModel messageModel, int type) throws ThreemaException {
		ContactModel contactModel = contactService.getByIdentity(messageModel.getIdentity());
		if (contactModel == null) {
			logger.error("Cannot get contact {} for sending the delivery receipt", messageModel.getIdentity());
			return false;
		}

		contactService.createReceiver(contactModel).sendDeliveryReceipt(
			type,
			new MessageId[]{MessageId.fromString(messageModel.getApiMessageId())},
			new Date().getTime()
		);
		return true;
	}

	/**
	 * Send a delivery receipt to all members of a group
	 * @param messageModel GroupMessageModel for which a receipt should be sent
	 * @param receiptType Type of receipt (currently only ACK and DEC are supported for groups)
	 */
	private boolean sendGroupDeliveryReceipt(@NonNull GroupMessageModel messageModel, int receiptType) {
		GroupModel group = groupService.getById(messageModel.getGroupId());
		if (group == null) {
			logger.warn("Cannot send group delivery receipt for unknown group");
			return false;
		}
		return sendGroupDeliveryReceipt(Set.of(groupService.getGroupIdentities(group)), messageModel, receiptType);
	}

	/**
	 * Send a delivery receipt to the identities specified
	 * @param identities Identities to send the receipt to
	 * @param messageModel GroupMessageModel for which a receipt should be sent
	 * @param receiptType Type of receipt (currently only ACK and DEC are supported for groups)
	 */
	@Override
	public boolean sendGroupDeliveryReceipt(@NonNull Set<String> identities, GroupMessageModel messageModel, int receiptType) {
		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager == null) {
			logger.warn("Service manager is null: cannot send group delivery receipt");
			return false;
		}
		serviceManager.getTaskManager().schedule(
			new OutgoingGroupDeliveryReceiptMessageTask(
				messageModel.getId(),
				receiptType,
				identities,
				serviceManager
			)
		);
		return true;
	}

	private void sendDeliveryReceiptAckDec(@NonNull AbstractMessageModel messageModel, boolean markAsRead, int receiptType, @NonNull MessageState newMessageState) {
		try {
			if (markAsRead) {
				markAsRead(messageModel, true);
			}

			if (!sendDeliveryReceipts(messageModel, receiptType)) {
				logger.error("Failed to send delivery receipt");
				return;
			}

			// clear any existing ack/dec message state
			clearMessageState(messageModel); // TODO(ANDR-3325): Remove ACK/DEC compatibility

			updateAckDecState(messageModel, newMessageState, identityStore.getIdentity());

			fireOnModifiedMessage(messageModel);
		} catch (ThreemaException e) {
			logger.error("Exception", e);
		}
	}

	@Nullable
	@Override
	public MessageModel getContactMessageModel(
		@NonNull final MessageId apiMessageId,
		@NonNull final String identity
	) {
		// Check contact message cache first
		synchronized (contactMessageCache) {
			MessageModel messageModel = Functional.select(contactMessageCache, m -> m.getApiMessageId() != null
				&& m.getApiMessageId().equals(apiMessageId.toString())
				&& TestUtil.compare(m.getIdentity(), identity));
			if (messageModel != null) {
				return messageModel;
			}
		}

		// If not cached, load from database (and cache it)
		MessageModel contactMessageModel = databaseServiceNew.getMessageModelFactory().getByApiMessageIdAndIdentity(
				apiMessageId,
				identity);
		if (contactMessageModel != null) {
			cache(contactMessageModel);
		}

		return contactMessageModel;
	}

	/**
	 * Get the AbstractMessageModel of a group message referenced by messageId, creatorId, and groupId
	 * @param messageId the message
	 * @param creatorIdentity the creator of the group
	 * @param groupId the group id
	 * @return a GroupMessageModel of the matching message or null in case a message could not be found
	 */
	@Override
	@Nullable
	public GroupMessageModel getGroupMessageModel(
		@NonNull final MessageId messageId,
		@NonNull final String creatorIdentity,
		@NonNull final GroupId groupId
	) {
		String apiMessageIdString = messageId.toString();
		if (apiMessageIdString == null) {
			return null;
		}

		GroupModel groupModel = groupService.getByApiGroupIdAndCreator(groupId, creatorIdentity);
		if (groupModel == null) {
			return null;
		}

		// check group message cache first
		synchronized (groupMessageCache) {
			GroupMessageModel messageModel = Functional.select(groupMessageCache, m -> (apiMessageIdString.equals(m.getApiMessageId()) && groupModel.getId() == m.getGroupId()));
			if (messageModel != null) {
				return messageModel;
			}
		}

		// retrieve from database
		GroupMessageModel groupMessageModel = databaseServiceNew.getGroupMessageModelFactory().getByApiMessageIdAndGroupId(
				messageId,
				groupModel.getId());

		if (groupMessageModel != null) {
			cache(groupMessageModel);
			return groupMessageModel;
		}

		return null;
	}

	@Override
	public void updateOutgoingMessageState(
			@NonNull AbstractMessageModel messageModel,
			@NonNull MessageState state,
			@NonNull Date date
	) {
		if (!messageModel.isOutbox()) {
			throw new IllegalArgumentException("Updating outgoing message state on incoming message " + messageModel.getApiMessageId());
		}

		if (MessageUtil.isReaction(state)) {
			throw new IllegalArgumentException("The given message state is a reaction: " + state);
		}

		if (messageModel.isDeleted()) {
			return;
		}

		synchronized (this) {
			logger.debug(
					"Updating message state from {} to {} at {}",
					messageModel.getState(), state, date.getTime()
			);

			boolean hasChanges = true;

			// Save date of state change
			switch (state) {
				case SENT:
					// Note that we do not check whether the posted at time already exists as this
					// value is already set when the message model has been created. We just update
					// it when the message actually has been sent.
					messageModel.setPostedAt(date);
					messageModel.setModifiedAt(date);
					break;
				case DELIVERED:
					if (messageModel.getDeliveredAt() != null) {
						logger.warn("'Delivered at' already set for message {}", messageModel.getApiMessageId());
					}
					messageModel.setDeliveredAt(date);
					messageModel.setModifiedAt(date);
					break;
				case READ:
					if (messageModel.getReadAt() != null) {
						logger.warn("'Read at' already set for message {}", messageModel.getApiMessageId());
					}
					messageModel.setReadAt(date);
					messageModel.setModifiedAt(date);
					break;
				case SENDFAILED:
				case FS_KEY_MISMATCH:
				case CONSUMED:
					messageModel.setModifiedAt(date);
					break;
				default:
					hasChanges = false;
			}

			// Change the state only if it is possible
			if (MessageUtil.canChangeToState(messageModel.getState(), state, messageModel instanceof GroupMessageModel)) {
				messageModel.setState(state);
				hasChanges = true;
			} else {
				logger.warn(
						"State transition from {} to {}, ignoring",
						messageModel.getState(), state
				);
			}

			if (hasChanges) {
				save(messageModel);
				fireOnModifiedMessage(messageModel);
			}
		}
	}

	@Override
	public void addMessageReaction(
			@NonNull AbstractMessageModel messageModel,
			@NonNull MessageState state,
			@NonNull String fromIdentity,
			@NonNull Date date
	) {
		if (!MessageUtil.isReaction(state)) {
			throw new IllegalArgumentException("The given message state is not a reaction: " + state);
		}
		updateAckDecState(messageModel, state, fromIdentity);
	}

	/**
	 * Special compatibility handling for state changes to ACK and DEC. Saves these messages to the reactions database
	 * @param messageModel The target message model of this state change / reaction
	 * @param newState The desired new state (ACK or DEC)
	 * @param senderIdentity The identity of the sender who sent this state change / reaction
	 */
	private void updateAckDecState(@NonNull AbstractMessageModel messageModel, @NonNull MessageState newState, @Nullable String senderIdentity) {
		if (newState != MessageState.USERACK && newState != MessageState.USERDEC) {
			return;
		}

		if (senderIdentity == null) {
			senderIdentity = identityStore.getIdentity();
		}

		clearMessageState(messageModel); // TODO(ANDR-3325): Remove

		handleEmojiReaction(messageModel, newState, senderIdentity);
	}

	/**
	 * Map state changes (acknowledge and decline) to their emoji reaction equivalents keeping in account
	 * the mutual exclusivity of acks and decs
	 * @param messageModel The AbstractMessageModel of the target message
	 * @param state The desired new state
	 * @param fromIdentity The identity of the sender of this ack/dec reaction
	 */
	private void handleEmojiReaction(AbstractMessageModel messageModel, MessageState state, String fromIdentity) {
		if (state == MessageState.USERACK) {
			saveEmojiReactionMessage(messageModel, fromIdentity, Reaction.ActionCase.WITHDRAW, EmojiUtil.THUMBS_DOWN_SEQUENCE);
			saveEmojiReactionMessage(messageModel, fromIdentity, Reaction.ActionCase.APPLY, EmojiUtil.THUMBS_UP_SEQUENCE);
		} else if (state == MessageState.USERDEC) {
			saveEmojiReactionMessage(messageModel, fromIdentity, Reaction.ActionCase.WITHDRAW, EmojiUtil.THUMBS_UP_SEQUENCE);
			saveEmojiReactionMessage(messageModel, fromIdentity, Reaction.ActionCase.APPLY, EmojiUtil.THUMBS_DOWN_SEQUENCE);
		}
	}

	@Override
	public boolean markAsRead(AbstractMessageModel message, boolean silent) throws ThreemaException {
		logger.debug("markAsRead message = {} silent = {}", message.getApiMessageId(), silent);
		boolean saved = false;

		if (MessageUtil.canMarkAsRead(message)) {
			ContactModel contactModel = contactService.getByIdentity(message.getIdentity());

			// Check whether the message allows read receipt before setting the message to read
			// because a message only allows a read receipt if has not been marked as read yet.
			boolean messageAllowsDeliveryReceipt = MessageUtil.canSendDeliveryReceipt(message, ProtocolDefines.DELIVERYRECEIPT_MSGREAD);

			Date readAt = new Date();

			//save is read
			message.setRead(true);
			message.setReadAt(readAt);
			message.setModifiedAt(readAt);

			save(message);

			if (!silent) {
				//fire on modified if not silent
				fireOnModifiedMessage(message);
			}

			saved = true;

			if (contactModel == null) {
				return saved;
			}

            if (message.getApiMessageId() == null) {
                logger.info("Message id is null; cannot send read receipt or reflect message update");
                return saved;
            }

            boolean receiverAllowsDeliveryReceipt;
            switch (contactModel.getReadReceipts()) {
                case ContactModel.SEND:
                    receiverAllowsDeliveryReceipt = true;
                    break;
                case ContactModel.DONT_SEND:
                    receiverAllowsDeliveryReceipt = false;
                    break;
                default:
                    receiverAllowsDeliveryReceipt = preferenceService.isReadReceipts();
                    break;
            }

			if (messageAllowsDeliveryReceipt && receiverAllowsDeliveryReceipt) {
				contactService.createReceiver(contactModel).sendDeliveryReceipt(
					ProtocolDefines.DELIVERYRECEIPT_MSGREAD,
					new MessageId[]{MessageId.fromString(message.getApiMessageId())},
					readAt.getTime()
				);
				logger.info("Enqueued delivery receipt (read) message for message ID {} from {}",
					message.getApiMessageId(), contactModel.getIdentity());
			} else {
				if (message instanceof MessageModel) {
					contactService.createReceiver(contactModel).sendIncomingMessageUpdateRead(
						Set.of(MessageId.fromString(message.getApiMessageId())), readAt.getTime()
					);
				} else if (message instanceof GroupMessageModel) {
					int localGroupId = ((GroupMessageModel) message).getGroupId();
					GroupModel groupModel = groupService.getById(localGroupId);
					if (groupModel != null) {
						groupService.createReceiver(groupModel).sendIncomingMessageUpdateRead(
							Set.of(MessageId.fromString(message.getApiMessageId())),
							readAt.getTime()
						);
					} else {
						logger.warn("Could not find group with local group id {}", localGroupId);
					}
				}
			}
		}

		return saved;
	}

	@Override
	@WorkerThread
	public boolean markAsConsumed(AbstractMessageModel message) throws ThreemaException {
		logger.debug("markAsConsumed message = {}", message.getApiMessageId());
		boolean saved = false;

		if (MessageUtil.canMarkAsConsumed(message)) {
			// save consumed state
			message.setState(MessageState.CONSUMED);
			message.setModifiedAt(new Date());

			save(message);

			saved = true;

			fireOnModifiedMessage(message);
		}

		return saved;
	}

	@Override
	public void remove(AbstractMessageModel messageModel) {
		remove(messageModel, false);
	}

	@Override
	public void remove(final AbstractMessageModel messageModel, boolean silent) {

		SendMachine machine = getSendMachine(messageModel, false);
		if(machine != null) {
			//abort pending send machine
			//do not remove SendMachine (fix ANDR-522)
			machine.abort();
		}

		//remove pending uploads
		cancelUploader(messageModel);

		//remove from sdcard
		fileService.removeMessageFiles(messageModel, true);

		//remove from dao
		if(messageModel instanceof GroupMessageModel) {
			databaseServiceNew.getGroupMessageModelFactory().delete(
					(GroupMessageModel) messageModel
			);

			//remove from cache
			synchronized (groupMessageCache) {
				Iterator<GroupMessageModel> i = groupMessageCache.iterator();
				while(i.hasNext()) {
					if(i.next().getId() == messageModel.getId()) {
						i.remove();
					}
				}
			}
		}
		else if (messageModel instanceof DistributionListMessageModel) {
			databaseServiceNew.getDistributionListMessageModelFactory().delete(
					(DistributionListMessageModel) messageModel
			);

			//remove from cache
			synchronized (distributionListMessageCache) {
				Iterator<DistributionListMessageModel> i = distributionListMessageCache.iterator();
				while(i.hasNext()) {
					if(i.next().getId() == messageModel.getId()) {
						i.remove();
					}
				}
			}
		}

		else if (messageModel instanceof MessageModel) {
			databaseServiceNew.getMessageModelFactory().delete((MessageModel) messageModel);

			//remove from cache
			synchronized (contactMessageCache) {
				Iterator<MessageModel> i = contactMessageCache.iterator();
				while(i.hasNext()) {
					if(i.next().getId() == messageModel.getId()) {
						i.remove();
					}
				}
			}
		}

		if(!silent) {
			fireOnRemovedMessage(messageModel);
		}
	}

	@Override
	public boolean processIncomingContactMessage(final AbstractMessage message) throws Exception {
		logger.info("processIncomingContactMessage: {}", message.getMessageId());

		final String senderIdentity = message.getFromIdentity();
		if (senderIdentity == null) {
			logger.error("Could not process a message of type {} without a sender identity", message.getType());
			return false;
		}

		MessageModel messageModel = null;

		MessageModel existingModel = databaseServiceNew.getMessageModelFactory()
				.getByApiMessageIdAndIdentity(message.getMessageId(), message.getFromIdentity());

		if (existingModel != null) {
			//first search in cache
			MessageModel savedMessageModel;
			logger.info("processIncomingContactMessage: {} check contact message cache", message.getMessageId());
			synchronized (contactMessageCache) {
				savedMessageModel = Functional.select(contactMessageCache, messageModel1 -> messageModel1.getApiMessageId() != null &&
						messageModel1.getApiMessageId().equals(message.getMessageId().toString())
						&& senderIdentity.equals(messageModel1.getIdentity()));
			}
			logger.info("processIncomingContactMessage: {} check contact message cache end", message.getMessageId());

			if(savedMessageModel == null) {
				//get from sql result
				savedMessageModel = existingModel;
			}

			if(savedMessageModel.isSaved()) {
				//do nothing!
				// TODO don't we need to send a delivery receipt here as well?
				return true;
			}
			else {
				messageModel = savedMessageModel;
			}
		}

		// Look up contact model
		//
		// Note: At this point, the contact should have been created by the IncomingMessageProcessor.
		final ContactModel contactModel = contactService.getByIdentity(senderIdentity);
		if (contactModel == null) {
			logger.error("Could not process a message of type {} from an unknown contact", message.getType());
			logger.info("processIncomingContactMessage: {} FAILED", message.getMessageId());
			return false;
		}

        // As soon as we get a direct message, unhide and unarchive the contact
        contactService.setAcquaintanceLevel(senderIdentity, ContactModel.AcquaintanceLevel.DIRECT);
        contactService.setIsArchived(senderIdentity, false);

        // Bump "lastUpdate" if necessary, depending on the message type. Note that due to the
        // listeners, we should bump the last update before saving the box message. Saving the box
        // message will trigger the listeners that, among other things, update the webclient. For
        // this purpose it is important that the last update flag has already been bumped.
        if (message.bumpLastUpdate()) {
           contactService.bumpLastUpdate(senderIdentity);
        }

		// Handle message depending on subtype
		final Class<? extends AbstractMessage> messageClass = message.getClass();
		if (messageClass.equals(TextMessage.class)) {
			messageModel = saveBoxMessage((TextMessage) message, messageModel, contactModel);
		}
		else if (messageClass.equals(ImageMessage.class)) {
			messageModel = saveBoxMessage((ImageMessage) message, messageModel, contactModel);
			// silently save to gallery if enabled
			if (
				preferenceService != null
				&& preferenceService.isSaveMedia()
				&& messageModel.getImageData().isDownloaded()
				&& !hiddenChatsListService.has(ContactUtil.getUniqueIdString(messageModel.getIdentity()))
			) {
				fileService.saveMedia(null, null, new CopyOnWriteArrayList<>(Collections.singletonList(messageModel)), true);
			}
		}
		else if (messageClass.equals(VideoMessage.class)) {
			messageModel = saveBoxMessage((VideoMessage) message, messageModel, contactModel);
		}
		else if (messageClass.equals(LocationMessage.class)) {
			messageModel = saveBoxMessage((LocationMessage) message, messageModel, contactModel);
		}
		else if (messageClass.equals(AudioMessage.class)) {
			messageModel = saveBoxMessage((AudioMessage) message, messageModel, contactModel);
		}
		else if (messageClass.equals(PollSetupMessage.class)) {
			messageModel = saveBoxMessage((PollSetupMessage) message, messageModel, contactModel);
		}

		if (messageModel == null) {
			logger.info("processIncomingContactMessage: {} FAILED", message.getMessageId());
			return false;
		}

		logger.info("processIncomingContactMessage: {} SUCCESS - Message ID = {}", message.getMessageId(), messageModel.getId());
		return true;
	}

	@Override
	public boolean processIncomingGroupMessage(@NonNull AbstractGroupMessage message) throws Exception {
		logger.info("processIncomingGroupMessage: {}", message.getMessageId());
		GroupMessageModel messageModel = null;

		// First of all, check if i can receive messages. Note that the common group receive steps
		// must have been executed at this point.
		GroupModel groupModel = groupService.getByGroupMessage(message);
		if(groupModel == null) {
			logger.error("GroupMessage {}: error: no groupModel", message.getMessageId());
			return false;
		}

		//is allowed?
		GroupAccessModel access = groupService.getAccess(groupModel, false);
		if(access == null ||
				!access.getCanReceiveMessageAccess().isAllowed()) {
			//not allowed to receive a message, ignore message but
			//set success to true (remove from server)
			logger.error("GroupMessage {}: error: not allowed", message.getMessageId());
			return true;
		}

		// is the user blocked?
		if (blockedIdentitiesService.isBlocked(message.getFromIdentity())) {
			//set success to true (remove from server)
			logger.info("GroupMessage {}: Sender is blocked, ignoring", message.getMessageId());
			return true;
		}

		// reset archived status
		groupService.setIsArchived(groupModel, false);

		// Bump "lastUpdate" if necessary, depending on the message type
		//
		// Note: Do this before the message is saved! Saving the message will trigger listeners
		// that will re-sort the conversation list. At that point in time, lastUpdate should already
		// be correct.
		if (message.bumpLastUpdate()) {
			groupService.bumpLastUpdate(groupModel);
		}

		GroupMessageModel existingModel = databaseServiceNew.getGroupMessageModelFactory().getByApiMessageIdAndIdentity(
				message.getMessageId(),
				message.getFromIdentity()
		);

		if (existingModel != null) {
			if (existingModel.isSaved()) {
				//do nothing!
				logger.error("GroupMessage {}: error: message already exists", message.getMessageId());
				return true;
			} else {
				//use the first non saved model to edit!
				logger.error("GroupMessage {}: error: reusing unsaved model", message.getMessageId());
				messageModel = existingModel;
			}
		}

		if (message.getClass().equals(GroupTextMessage.class)) {
			messageModel = saveGroupMessage((GroupTextMessage) message, messageModel);
		}
		else if (message.getClass().equals(GroupImageMessage.class)) {
			messageModel = saveGroupMessage((GroupImageMessage) message, messageModel);
			// silently save to gallery if enabled
			if (messageModel != null
					&& preferenceService != null
					&& preferenceService.isSaveMedia()
					&& messageModel.getImageData().isDownloaded()
					&& !hiddenChatsListService.has(groupService.getUniqueIdString(groupModel))) {
				fileService.saveMedia(null, null, new CopyOnWriteArrayList<>(Collections.singletonList(messageModel)), true);
			}
		}
		else if (message.getClass().equals(GroupVideoMessage.class)) {
			messageModel = saveGroupMessage((GroupVideoMessage) message, messageModel);
		}
		else if (message.getClass().equals(GroupLocationMessage.class)) {
			messageModel = saveGroupMessage((GroupLocationMessage) message, messageModel);
		}
		else if (message.getClass().equals(GroupAudioMessage.class)) {
			messageModel = saveGroupMessage((GroupAudioMessage) message, messageModel);
		}
		else if (message.getClass().equals(GroupPollSetupMessage.class)) {
			messageModel = saveGroupMessage((GroupPollSetupMessage) message, messageModel);
			// This is only used for debugging
			if (ConfigUtils.isDevBuild()) {
				logger.info("Processed GroupBallotCreateMessage {}", ((GroupPollSetupMessage) message).rawBallotData);
			}
		}

		if (messageModel != null) {
			logger.info("processIncomingGroupMessage: {} SUCCESS - Message ID = {}", message.getMessageId(), messageModel.getId());
			return true;
		} else {
			logger.info("processIncomingGroupMessage: {} FAILED", message.getMessageId());
			return false;
		}
	}

	/**
	 * Process a 1:1 text message (0x01).
	 */
	private MessageModel saveBoxMessage(
		@NonNull TextMessage message,
		MessageModel messageModel,
		@NonNull ContactModel contactModel
	) {
		if (messageModel == null) {
			ContactMessageReceiver r = contactService.createReceiver(contactModel);
			messageModel = r.createLocalModel(MessageType.TEXT, MessageContentsType.TEXT, message.getDate());
			cache(messageModel);

			messageModel.setApiMessageId(message.getMessageId().toString());
			messageModel.setMessageFlags(message.getMessageFlags());
			messageModel.setOutbox(false);
			// replace CR by LF for Window$ Phone compatibility - me be removed soon.
			String body = message.getText() != null ? message.getText().replace("\r", "\n") : null;

			messageModel.setBodyAndQuotedMessageId(body);
			messageModel.setIdentity(contactModel.getIdentity());
			messageModel.setForwardSecurityMode(message.getForwardSecurityMode());
			messageModel.setSaved(true);

			databaseServiceNew.getMessageModelFactory().create(messageModel);

			fireOnNewMessage(messageModel);
		}

		return messageModel;
	}

	/**
	 * Process a 1:1 poll setup message (0x15).
	 */
	private MessageModel saveBoxMessage(
		@NonNull PollSetupMessage message,
		MessageModel messageModel,
		@NonNull ContactModel contactModel
	) throws Exception {
		MessageReceiver messageReceiver = contactService.createReceiver(contactModel);
        return (MessageModel) saveBallotCreateMessage(
            messageReceiver,
            message.getMessageId(),
            message,
            messageModel,
            message.getMessageFlags(),
            message.getForwardSecurityMode(),
            // Note that this may also be remote, but it is certainly never local. To be safe,
            // we use sync as this will prevent sending any csp messages.
            TriggerSource.SYNC
        );
	}

	private GroupMessageModel saveGroupMessage(GroupPollSetupMessage message, GroupMessageModel messageModel) throws Exception {
		GroupModel groupModel = groupService.getByGroupMessage(message);

		if(groupModel == null) {
			return null;
		}

		MessageReceiver messageReceiver = groupService.createReceiver(groupModel);

        return (GroupMessageModel) saveBallotCreateMessage(
            messageReceiver,
            message.getMessageId(),
            message,
            messageModel,
            message.getMessageFlags(),
            message.getForwardSecurityMode(),
            // Note that this may also be remote, but it is certainly never local. To be safe,
            // we use sync as this will prevent sending any csp messages.
            TriggerSource.SYNC
        );
	}

    @Override
	public AbstractMessageModel saveBallotCreateMessage(
        @NonNull MessageReceiver<?> receiver,
        @NonNull MessageId messageId,
        @NonNull BallotSetupInterface message,
        @Nullable AbstractMessageModel messageModel,
        int messageFlags,
        @Nullable ForwardSecurityMode forwardSecurityMode,
        @NonNull TriggerSource triggerSource
    ) throws ThreemaException, BadMessageException {
		BallotUpdateResult result = ballotService.update(message, messageId, triggerSource);

		if(result.getBallotModel() == null) {
			throw new ThreemaException("could not create ballot model");
		}

		switch (result.getOperation()) {
			case CREATE:
			case CLOSE:
				messageModel = createNewBallotMessage(
						messageId,
						result.getBallotModel(),
						(result.getOperation() == BallotUpdateResult.Operation.CREATE ?
							BallotDataModel.Type.BALLOT_CREATED:
							BallotDataModel.Type.BALLOT_CLOSED),
						receiver,
						messageFlags,
						forwardSecurityMode);
		}

		return messageModel;
	}

	@Deprecated
	private AbstractMessageModel saveAudioMessage(@NonNull MessageReceiver receiver,
	                                              AbstractMessage message,
	                                              AbstractMessageModel messageModel) {
		boolean newModel = false;
		int duration;
		byte[] encryptionKey, audioBlobId;

		if (message instanceof GroupAudioMessage) {
			duration = ((GroupAudioMessage) message).getDuration();
			encryptionKey = ((GroupAudioMessage) message).getEncryptionKey();
			audioBlobId = ((GroupAudioMessage) message).getAudioBlobId();
		} else if (message instanceof AudioMessage) {
			duration = ((AudioMessage) message).getDuration();
			encryptionKey = ((AudioMessage) message).getEncryptionKey();
			audioBlobId = ((AudioMessage) message).getAudioBlobId();
		} else {
			return null;
		}

		if (messageModel == null) {
			newModel = true;
			messageModel = receiver.createLocalModel(MessageType.VOICEMESSAGE, MessageContentsType.VOICE_MESSAGE, message.getDate());
			cache(messageModel);

			messageModel.setApiMessageId(message.getMessageId().toString());
			messageModel.setMessageFlags(message.getMessageFlags());
			messageModel.setOutbox(false);
			messageModel.setIdentity(message.getFromIdentity());
			messageModel.setAudioData(new AudioDataModel(duration, audioBlobId, encryptionKey));
			messageModel.setForwardSecurityMode(message.getForwardSecurityMode());

			//create the record
			receiver.saveLocalModel(messageModel);
		}

		messageModel.setSaved(true);
		receiver.saveLocalModel(messageModel);

		if (newModel) {
			fireOnCreatedMessage(messageModel);

			if (shouldAutoDownload(MessageType.VOICEMESSAGE)) {
				try {
					downloadMediaMessage(messageModel, null);
				} catch (Exception e) {
					// a failed blob auto-download should not be considered a failure as the user can try again manually
					logger.error("Unable to auto-download blob", e);
				}
			}
		}
		else {
			fireOnModifiedMessage(messageModel);
		}
		return messageModel;
	}

	@Deprecated
	private AbstractMessageModel saveVideoMessage(
        @NonNull MessageReceiver receiver,
	    AbstractMessage message,
	    AbstractMessageModel messageModel
    ) throws Exception {
		boolean newModel = false;
		int duration, videoSize;
		byte[] encryptionKey, videoBlobId, thumbnailBlobId;

		if (message instanceof GroupVideoMessage) {
			duration = ((GroupVideoMessage) message).getDuration();
			videoSize = ((GroupVideoMessage) message).getVideoSize();
			encryptionKey = ((GroupVideoMessage) message).getEncryptionKey();
			videoBlobId = ((GroupVideoMessage) message).getVideoBlobId();
			thumbnailBlobId = ((GroupVideoMessage) message).getThumbnailBlobId();
		} else if (message instanceof VideoMessage) {
			duration = ((VideoMessage) message).getDuration();
			videoSize = ((VideoMessage) message).getVideoSize();
			encryptionKey = ((VideoMessage) message).getEncryptionKey();
			videoBlobId = ((VideoMessage) message).getVideoBlobId();
			thumbnailBlobId = ((VideoMessage) message).getThumbnailBlobId();
		} else {
			return null;
		}

		if (messageModel == null) {
			newModel = true;
			messageModel = receiver.createLocalModel(MessageType.VIDEO, MessageContentsType.VIDEO, message.getDate());
			cache(messageModel);

			messageModel.setApiMessageId(message.getMessageId().toString());
			messageModel.setMessageFlags(message.getMessageFlags());
			messageModel.setOutbox(false);
			messageModel.setIdentity(message.getFromIdentity());
			messageModel.setVideoData(new VideoDataModel(duration, videoSize, videoBlobId, encryptionKey));
			messageModel.setForwardSecurityMode(message.getForwardSecurityMode());

			//create the record
			receiver.saveLocalModel(messageModel);
		}

		//download thumbnail
		final AbstractMessageModel messageModel1 = messageModel;

		//use download service!
		logger.info("Downloading blob for message {} id = {}", messageModel.getApiMessageId(), messageModel.getId());

        // If multi-device is active, we always mark as done. Otherwise we do not mark as done if its a group message
        boolean shouldMarkAsDone = multiDeviceManager.isMultiDeviceActive() || !(message instanceof AbstractGroupMessage);
        @Nullable BlobScope blobScopeMarkAsDone = null;
        if (shouldMarkAsDone) {
            blobScopeMarkAsDone = messageModel.getBlobScopeForMarkAsDone();
        }

        byte[] thumbnailBlob = downloadService.download(
            messageModel.getId(),
            thumbnailBlobId,
            messageModel.getBlobScopeForDownload(),
            blobScopeMarkAsDone,
            new ProgressListener() {
                @Override
                public void updateProgress(int progress) {
                    updateMessageLoadingProgress(messageModel1, progress);
                }

                @Override
                public void onFinished(boolean success) {
                    setMessageLoadingFinished(messageModel1);
                }
            }
        );

		if (thumbnailBlob != null && thumbnailBlob.length > NaCl.BOXOVERHEAD) {
			byte[] thumbnail = symmetricEncryptionService.decrypt(thumbnailBlob, encryptionKey, ProtocolDefines.THUMBNAIL_NONCE);

			if (thumbnail != null) {
				try {
					fileService.writeConversationMediaThumbnail(messageModel, thumbnail);
				} catch (Exception e) {
					downloadService.error(messageModel.getId());
					throw e;
				}
			}

			messageModel.setSaved(true);
			receiver.saveLocalModel(messageModel);

			downloadService.complete(messageModel.getId(), thumbnailBlobId);

			if (newModel) {
				fireOnCreatedMessage(messageModel);

				if (shouldAutoDownload(MessageType.VIDEO)) {
					if (videoSize <= FILE_AUTO_DOWNLOAD_MAX_SIZE_ISO) {
						try {
							downloadMediaMessage(messageModel, null);
						} catch (Exception e) {
							// a failed blob auto-download should not be considered a failure as the user can try again manually
							logger.error("Unable to auto-download blob", e);
						}
					}
				}
			} else {
				fireOnModifiedMessage(messageModel);
			}

			return messageModel;
		}

		downloadService.error(messageModel.getId());
		return null;
	}

    /**
     * @return {@code true} if the thumbnail was downloaded and saved
     */
    @Override
	public boolean downloadThumbnailIfPresent(@NonNull FileData fileData, @NonNull AbstractMessageModel messageModel) throws Exception {

        if (fileData.getThumbnailBlobId() == null) {
            return false;
        }

        logger.info("Downloading thumbnail of message {}", messageModel.getApiMessageId());
        final AbstractMessageModel messageModel1 = messageModel;

        // If multi-device is active, we always mark as done. Otherwise we do not mark as done if its a group message
        boolean shouldMarkAsDone = multiDeviceManager.isMultiDeviceActive() || !(messageModel instanceof GroupMessageModel);
        @Nullable BlobScope blobScopeMarkAsDone = null;
        if (shouldMarkAsDone)            {
            blobScopeMarkAsDone = messageModel.getBlobScopeForMarkAsDone();
        }

        byte[] thumbnailBlob = downloadService.download(
            messageModel.getId(),
            fileData.getThumbnailBlobId(),
            messageModel.getBlobScopeForDownload(),
            blobScopeMarkAsDone,
            new ProgressListener() {
                @Override
                public void updateProgress(int progress) {
                    updateMessageLoadingProgress(messageModel1, progress);
                }

                @Override
                public void onFinished(boolean success) {
                    setMessageLoadingFinished(messageModel1);
                }
            });

        if (thumbnailBlob == null) {
            downloadService.error(messageModel.getId());
            logger.info("Error downloading thumbnail for message {}", messageModel.getApiMessageId());
            throw new ThreemaException("Error downloading thumbnail");
        }

        byte[] thumbnail = symmetricEncryptionService.decrypt(thumbnailBlob, fileData.getEncryptionKey(), ProtocolDefines.FILE_THUMBNAIL_NONCE);

        if (thumbnail != null) {
            try {
                fileService.writeConversationMediaThumbnail(messageModel, thumbnail);
            } catch (Exception exception) {
                downloadService.error(messageModel.getId());
                logger.info("Error writing thumbnail for message {}", messageModel.getApiMessageId());
                throw exception;
            }
        }

        downloadService.complete(messageModel.getId(), fileData.getThumbnailBlobId());
        return true;
	}

	private GroupMessageModel saveGroupMessage(GroupTextMessage message, GroupMessageModel messageModel) {
		GroupModel groupModel = groupService.getByGroupMessage(message);

		if(groupModel == null) {
			return null;
		}

		if (messageModel == null) {
			GroupMessageReceiver r = groupService.createReceiver(groupModel);
			messageModel = r.createLocalModel(MessageType.TEXT, MessageContentsType.TEXT, message.getDate());
			cache(messageModel);

			messageModel.setApiMessageId(message.getMessageId().toString());
			messageModel.setMessageFlags(message.getMessageFlags());
			messageModel.setOutbox(false);
			// replace CR by LF for Window$ Phone compatibility - me be removed soon.
			String body = message.getText() != null ? message.getText().replace("\r", "\n") : null;

			messageModel.setBodyAndQuotedMessageId(body);
			messageModel.setSaved(true);
			messageModel.setIdentity(message.getFromIdentity());
			messageModel.setForwardSecurityMode(message.getForwardSecurityMode());

			r.saveLocalModel(messageModel);

			fireOnNewMessage(messageModel);
		}

		return messageModel;
	}

	private boolean shouldAutoDownload(MessageType type) {
		if (preferenceService != null) {
			ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
			if (activeNetwork != null) {
				switch (activeNetwork.getType()) {
					case ConnectivityManager.TYPE_ETHERNET:
						// fallthrough
					case ConnectivityManager.TYPE_WIFI:
						return preferenceService.getWifiAutoDownload().contains(String.valueOf(type.ordinal()));
					case ConnectivityManager.TYPE_MOBILE:
						return preferenceService.getMobileAutoDownload().contains(String.valueOf(type.ordinal()));
					default:
						break;
				}
			}
		}
		return false;
	}

	/**
	 * Check if the file in question should be auto-downloaded or not
	 * This depends on file type, file size and user preference (settings)
	 * @param messageModel AbstractMessageModel to check
	 * @return true if file should be downloaded immediately, false otherwise
	 */
    @Override
	public boolean shouldAutoDownload(@NonNull AbstractMessageModel messageModel) {
		MessageType type = MessageType.FILE;
		FileDataModel fileDataModel = messageModel.getFileData();

		if (fileDataModel.getRenderingType() != FileData.RENDERING_DEFAULT) {
			// treat media with default (file) rendering like a file for the sake of auto-download
			if (messageModel.getMessageContentsType() == MessageContentsType.IMAGE) {
				type = MessageType.IMAGE;
			} else if (messageModel.getMessageContentsType() == MessageContentsType.VIDEO) {
				type = MessageType.VIDEO;
			} else if (messageModel.getMessageContentsType() == MessageContentsType.VOICE_MESSAGE) {
				type = MessageType.VOICEMESSAGE;
			}
		}

		if (preferenceService != null) {
			ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
			if (activeNetwork != null) {
				boolean canDownload = false;

				switch (activeNetwork.getType()) {
					case ConnectivityManager.TYPE_ETHERNET:
						// fallthrough
					case ConnectivityManager.TYPE_WIFI:
						canDownload = preferenceService.getWifiAutoDownload().contains(String.valueOf(type.ordinal()));
						break;
					case ConnectivityManager.TYPE_MOBILE:
						canDownload = preferenceService.getMobileAutoDownload().contains(String.valueOf(type.ordinal()));
						break;
					default:
						break;
				}

				if (canDownload) {
					// images and voice messages are always auto-downloaded regardless of size
					return
						type == MessageType.IMAGE ||
						type == MessageType.VOICEMESSAGE ||
						fileDataModel.getFileSize() <= FILE_AUTO_DOWNLOAD_MAX_SIZE_ISO;
				}
			}
		}
		return false;
	}

	@Deprecated
	private GroupMessageModel saveGroupMessage(GroupImageMessage message, GroupMessageModel messageModel) {
		GroupModel groupModel = groupService.getByGroupMessage(message);

		if(groupModel == null) {
			return null;
		}

		GroupMessageModelFactory messageModelFactory = databaseServiceNew.getGroupMessageModelFactory();

		//download thumbnail
		if (messageModel == null) {
			MessageReceiver r = groupService.createReceiver(groupModel);
			messageModel = (GroupMessageModel)r.createLocalModel(MessageType.IMAGE, MessageContentsType.IMAGE, message.getDate());
			cache(messageModel);

			messageModel.setApiMessageId(message.getMessageId().toString());
			messageModel.setMessageFlags(message.getMessageFlags());
			messageModel.setOutbox(false);
			messageModel.setIdentity(message.getFromIdentity());

			messageModel.setImageData(new ImageDataModel(
				message.getBlobId(),
				message.getEncryptionKey(),
				ProtocolDefines.IMAGE_NONCE
			));

			// Mark as saved to show message without image e.g.
			messageModel.setSaved(true);
			r.saveLocalModel(messageModel);
		}

		fireOnNewMessage(messageModel);

		final GroupMessageModel messageModel1 = messageModel;

		if (shouldAutoDownload(MessageType.IMAGE) && !messageModel.getImageData().isDownloaded()) {

            // If multi-device is active, we always mark as done (even for a group message)
            boolean shouldMarkAsDone = multiDeviceManager.isMultiDeviceActive();
            @Nullable BlobScope blobScopeMarkAsDone = null;
            if (shouldMarkAsDone) {
                blobScopeMarkAsDone = messageModel.getBlobScopeForMarkAsDone();
            }

            byte[] blob = downloadService.download(
                messageModel.getId(),
                message.getBlobId(),
                messageModel.getBlobScopeForDownload(),
                blobScopeMarkAsDone,
                new ProgressListener() {

                    // do we really need a progress listener for images?
                    @Override
                    public void updateProgress(int progress) {
                        updateMessageLoadingProgress(messageModel1, progress);
                    }

                    @Override
                    public void onFinished(boolean success) {
                        setMessageLoadingFinished(messageModel1);
                    }
                }
            );

			if (blob != null && messageModel.getImageData().getEncryptionKey().length > 0) {
				try {
					blob = symmetricEncryptionService.decrypt(
						blob,
						messageModel.getImageData().getEncryptionKey(),
						messageModel.getImageData().getNonce()
					);
				} catch (Exception e) {
					blob = null;
					logger.error("Exception", e);
				}

				if (blob != null && blob.length > 0) {

					try {
						if (saveStrippedImage(blob, messageModel)) {

							messageModel.getImageData().isDownloaded(true);
							messageModel.writeDataModelToBody();
							messageModelFactory.update(messageModel);

							fireOnModifiedMessage(messageModel);

							downloadService.complete(messageModel.getId(), message.getBlobId());

							return messageModel;
						}
					} catch (Exception e) {
						logger.error("Image save failed", e);
					}
				} else {
					logger.error("Invalid blob");
				}
			} else {
				logger.error("Blob is null");
			}
			downloadService.error(messageModel.getId());
		}

		messageModel.setSaved(true);
		messageModelFactory.update(messageModel);

		// download failed...let adapter know
		fireOnModifiedMessage(messageModel);

		return messageModel;
	}

	@Deprecated
	private GroupMessageModel saveGroupMessage(GroupVideoMessage message, GroupMessageModel messageModel) throws Exception {
		GroupModel groupModel = groupService.getByGroupMessage(message);

		if(groupModel == null) {
			logger.error("could not save a group message from an unknown group");
			return null;
		}

		MessageReceiver messageReceiver = groupService.createReceiver(groupModel);

		return (GroupMessageModel) saveVideoMessage(
				messageReceiver,
				message,
				messageModel);
	}

	private GroupMessageModel saveGroupMessage(GroupAudioMessage message, GroupMessageModel messageModel) {
		GroupModel groupModel = groupService.getByGroupMessage(message);

		if(groupModel == null) {
			return null;
		}

		MessageReceiver messageReceiver = groupService.createReceiver(groupModel);

		return (GroupMessageModel) saveAudioMessage(
				messageReceiver,
				message,
				messageModel);
	}

	@Nullable
    @WorkerThread
	private GroupMessageModel saveGroupMessage(GroupLocationMessage message, GroupMessageModel messageModel) {
		GroupModel groupModel = groupService.getByGroupMessage(message);
		boolean isNewMessage = false;
		if(groupModel == null) {
			return null;
		}

		MessageReceiver r = groupService.createReceiver(groupModel);

		if (messageModel == null) {
			messageModel = (GroupMessageModel)r.createLocalModel(MessageType.LOCATION, MessageContentsType.LOCATION, message.getDate());
			cache(messageModel);

			messageModel.setApiMessageId(message.getMessageId().toString());
			messageModel.setMessageFlags(message.getMessageFlags());
			messageModel.setOutbox(false);
			messageModel.setIdentity(message.getFromIdentity());

			r.saveLocalModel(messageModel);
			isNewMessage = true;
		}

        // If the location model is missing an address, we perform a lookup based on the coordinates
        @Nullable Poi effectivePoi = message.getPoi();
		if (effectivePoi == null) {
			try {
                // Will result in "Unknown address" as a fallback value
				final @NonNull String lookedUpPoiAddress = GeoLocationUtil.getAddressFromLocation(
                    context,
                    message.getLatitude(),
                    message.getLongitude()
                );
                effectivePoi = new Poi.Unnamed(lookedUpPoiAddress);
			} catch (IOException ioException) {
				logger.error("Exception", ioException);
			}
		}

		messageModel.setLocationData(new LocationDataModel(
            message.getLatitude(),
            message.getLongitude(),
            message.getAccuracy(),
            effectivePoi
		));

		messageModel.setSaved(true);

		r.saveLocalModel(messageModel);
		if(isNewMessage) {
			fireOnNewMessage(messageModel);
		}
		else {
			fireOnModifiedMessage(messageModel);
		}

		return messageModel;
	}

	/**
	 * Process a 1:1 image message (0x02).
	 */
	@Deprecated
	private MessageModel saveBoxMessage(
		@NonNull ImageMessage message,
		MessageModel messageModel,
		@NonNull ContactModel contactModel
	) {
		logger.info("saveBoxMessage: {}", message.getMessageId());

		logger.info("saveBoxMessage: {} - A", message.getMessageId());

		MessageModelFactory messageModelFactory = databaseServiceNew.getMessageModelFactory();

		logger.info("saveBoxMessage: {} - B", message.getMessageId());

		if (messageModel == null) {
			ContactMessageReceiver r = contactService.createReceiver(contactModel);

			logger.info("saveBoxMessage: {} - C", message.getMessageId());

			messageModel = r.createLocalModel(MessageType.IMAGE, MessageContentsType.IMAGE, message.getDate());

			logger.info("saveBoxMessage: {} - D", message.getMessageId());

			messageModel.setApiMessageId(message.getMessageId().toString());
			messageModel.setMessageFlags(message.getMessageFlags());
			messageModel.setOutbox(false);
			messageModel.setIdentity(contactModel.getIdentity());
			// Do not set an encryption key (asymmetric style)
			messageModel.setImageData(new ImageDataModel(message.getBlobId(), contactModel.getPublicKey(), message.getNonce()));
			messageModel.setForwardSecurityMode(message.getForwardSecurityMode());

			// Mark as saved to show message without image e.g.
			messageModel.setSaved(true);
			r.saveLocalModel(messageModel);
/*
			//create the record
			messageModelFactory.create(messageModel);
*/
			logger.info("saveBoxMessage: {} - E", message.getMessageId());

			cache(messageModel);
		}

		fireOnNewMessage(messageModel);

		logger.info("saveBoxMessage: {} - F", message.getMessageId());

		if (shouldAutoDownload(MessageType.IMAGE) && !messageModel.getImageData().isDownloaded()) {

            // Use download class to handle failures after downloads
			byte[] imageBlob = downloadService.download(
                messageModel.getId(),
                message.getBlobId(),
                messageModel.getBlobScopeForDownload(),
                messageModel.getBlobScopeForMarkAsDone(),
                null
            );
			if (imageBlob != null) {
				byte[] image = identityStore.decryptData(imageBlob, message.getNonce(), contactModel.getPublicKey());
				if (image != null) {
					try {
						if (saveStrippedImage(image, messageModel)) {

							// Mark as downloaded
							messageModel.getImageData().isDownloaded(true);
							messageModel.writeDataModelToBody();
							messageModelFactory.update(messageModel);

							//fire on new
							fireOnModifiedMessage(messageModel);

							// remove blob
							downloadService.complete(messageModel.getId(), message.getBlobId());

							return messageModel;
						}
					} catch (Exception e) {
						logger.error("Image save failed", e);
					}
				} else {
					logger.error("Unable to decrypt blob for message {}", messageModel.getId());
				}
			} else {
				logger.error("Blob is null");
			}
			downloadService.error(messageModel.getId());
		}

		messageModel.setSaved(true);
		messageModelFactory.update(messageModel);

		// download failed...let adapter know
		fireOnModifiedMessage(messageModel);

		return messageModel;
	}

	/**
	 * Process a 1:1 video message (0x13).
	 */
	@Deprecated
	private MessageModel saveBoxMessage(
		@NonNull VideoMessage message,
		MessageModel messageModel,
		@NonNull ContactModel contactModel
	) throws Exception {
		MessageReceiver messageReceiver = contactService.createReceiver(contactModel);
		return (MessageModel) saveVideoMessage(
				messageReceiver,
				message,
				messageModel);
	}

	/**
	 * Process a 1:1 audio message (0x14).
	 */
	@Deprecated
	private MessageModel saveBoxMessage(
		@NonNull AudioMessage message,
		MessageModel messageModel,
		@NonNull ContactModel contactModel
	) {
		MessageReceiver messageReceiver = contactService.createReceiver(contactModel);
		return (MessageModel) saveAudioMessage(
				messageReceiver,
				message,
				messageModel);
	}

	private boolean saveStrippedImage(byte[] image, AbstractMessageModel messageModel) throws Exception {
		boolean success = true;

		// extract caption from exif data (legacy image format only) and strip all metadata, if any
		try (ByteArrayOutputStream strippedImageOS = new ByteArrayOutputStream()) {
			try (ByteArrayInputStream originalImageIS = new ByteArrayInputStream(image)) {
				ExifInterface originalImageExif = new ExifInterface(originalImageIS);
				if (messageModel.getType() == MessageType.IMAGE) {
					String caption = originalImageExif.getUTF8StringAttribute(ExifInterface.TAG_ARTIST);

					if (TestUtil.isEmptyOrNull(caption)) {
						caption = originalImageExif.getUTF8StringAttribute(ExifInterface.TAG_USER_COMMENT);
					}

					if (!TestUtil.isEmptyOrNull(caption)) {
						// strip trailing zero character from EXIF, if any
						if (caption.charAt(caption.length() - 1) == '\u0000') {
							caption = caption.substring(0, caption.length() - 1);
						}
						messageModel.setCaption(caption);
					}

					originalImageIS.reset();
				}
				// strip all exif data while saving
				originalImageExif.saveAttributes(originalImageIS, strippedImageOS, true);
			} catch (IOException e) {
				logger.error("Exception", e);
				success = false;
			}

			// check if a file already exist
			fileService.removeMessageFiles(messageModel, true);

			logger.info("Writing image file...");
			if (success) {
				// write stripped file
				success = fileService.writeConversationMedia(messageModel, strippedImageOS.toByteArray());
			} else{
				// write original file
				success = fileService.writeConversationMedia(messageModel, image);
			}
			if (success) {
				logger.info("Image file successfully saved.");
			} else {
				logger.error("Image file save failed.");
			}
			messageModel.setSaved(true);
		}
		return success;
	}

	/**
	 * Process a 1:1 location message (0x10).
	 */
	@WorkerThread
	private MessageModel saveBoxMessage(
		@NonNull LocationMessage message,
		MessageModel messageModel,
		@NonNull ContactModel contactModel
	) {
		ContactMessageReceiver r = contactService.createReceiver(contactModel);
		if (messageModel == null) {
			messageModel = r.createLocalModel(MessageType.LOCATION, MessageContentsType.LOCATION, message.getDate());
			cache(messageModel);
			messageModel.setApiMessageId(message.getMessageId().toString());
			messageModel.setMessageFlags(message.getMessageFlags());
			messageModel.setOutbox(false);
		}

		messageModel.setIdentity(contactModel.getIdentity());
		messageModel.setForwardSecurityMode(message.getForwardSecurityMode());
		messageModel.setSaved(true);

		messageModel.setLocationData(
            new LocationDataModel(
                message.getLatitude(),
                message.getLongitude(),
                message.getAccuracy(),
			    message.getPoi()
		    )
        );

		// We save the message model already here to ensure it is in the database in case the app
		// gets killed before resolving the address.
		databaseServiceNew.getMessageModelFactory().create(messageModel);

        // If the location model is missing an address, we perform a lookup based on the coordinates
		if (message.getPoi() == null) {
			try {
                // Will result in "Unknown address" as a fallback value
                final @NonNull String lookedUpPoiAddress = GeoLocationUtil.getAddressFromLocation(
                    context,
                    message.getLatitude(),
                    message.getLongitude()
                );

                           messageModel.setLocationData(
                    new LocationDataModel(
                        message.getLatitude(),
                        message.getLongitude(),
                        message.getAccuracy(),
                        new Poi.Unnamed(lookedUpPoiAddress)
                    )
                );

                // Update the db record
                databaseServiceNew.getMessageModelFactory().update(messageModel);

			} catch (IOException ioException) {
				logger.error("Exception", ioException);
			}
		}

		fireOnNewMessage(messageModel);

		return messageModel;
	}

	@Override
	public List<AbstractMessageModel> getMessagesForReceiver(@NonNull MessageReceiver receiver, MessageFilter messageFilter) {
		return getMessagesForReceiver(receiver, messageFilter, true);
	}

	@Override
	public List<AbstractMessageModel> getMessagesForReceiver(@NonNull MessageReceiver receiver, MessageFilter messageFilter, boolean appendUnreadMessage) {
		List<AbstractMessageModel> messages = receiver.loadMessages(messageFilter);
		if (!appendUnreadMessage) {
			return messages;
		}
		switch (receiver.getType()) {
			case MessageReceiver.Type_GROUP:
			case MessageReceiver.Type_CONTACT:
				return markFirstUnread(messages);
			default:
				return messages;
		}
	}

	/**
	 * Mark the first unread Message
	 *
	 * @param messageModels Message Models
	 */
	private List<AbstractMessageModel> markFirstUnread(List<AbstractMessageModel> messageModels) {
		synchronized (messageModels) {
			int firstUnreadMessagePosition = -1;
			for(int n = 0; n < messageModels.size(); n++) {
				AbstractMessageModel m = messageModels.get(n);

				if(m != null) {
					if (m.isDeleted()) {
						continue;
					}
					if(m.isOutbox()) {
						break;
					}
					else {
						if(m.isRead()) {
							break;
						}
						else if(!m.isStatusMessage()) {
							firstUnreadMessagePosition = n;
						}
					}
				}
			}

			if(firstUnreadMessagePosition > -1) {
				FirstUnreadMessageModel firstUnreadMessageModel = new FirstUnreadMessageModel();
				firstUnreadMessageModel.setCreatedAt(messageModels.get(firstUnreadMessagePosition).getCreatedAt());
				messageModels.add(firstUnreadMessagePosition+1, firstUnreadMessageModel);
			}
		}

		return messageModels;
	}

	@Override
	public List<AbstractMessageModel> getMessagesForReceiver(@NonNull MessageReceiver receiver) {
		return getMessagesForReceiver(receiver, null);
	}

	@Override
	public List<AbstractMessageModel> getMessageForBallot(final BallotModel ballotModel) {
		MessageReceiver receiver = ballotService.getReceiver(ballotModel);

		if (receiver != null) {
			List<AbstractMessageModel> ballotMessages = receiver.loadMessages(new MessageFilter() {
				@Override
				public long getPageSize() {
					return 0;
				}

				@Override
				public Integer getPageReferenceId() {
					return null;
				}

				@Override
				public boolean withStatusMessages() {
					return false;
				}

				@Override
				public boolean withUnsaved() {
					return true;
				}

				@Override
				public boolean onlyUnread() {
					return false;
				}

				@Override
				public boolean onlyDownloaded() {
					return false;
				}

				@Override
				public MessageType[] types() {
					return new MessageType[]{
						MessageType.BALLOT
					};
				}

				@Override
				public int[] contentTypes() {
					return null;
				}

				@Override
				public int[] displayTags() {
					return null;
				}
			});

			return Functional.filter(ballotMessages, (IPredicateNonNull<AbstractMessageModel>) type -> type.getBallotData().getBallotId() == ballotModel.getId());
		}
		return null;
	}

	private List<AbstractMessageModel> getContactMessagesForText(String query, boolean includeArchived, boolean starredOnly, boolean sortAscending) {
		return databaseServiceNew.getMessageModelFactory().getMessagesByText(query, includeArchived, starredOnly, sortAscending);
	}

	private List<AbstractMessageModel> getGroupMessagesForText(String query, boolean includeArchived, boolean starredOnly, boolean sortAscending) {
		return databaseServiceNew.getGroupMessageModelFactory().getMessagesByText(query, includeArchived, starredOnly, sortAscending);
	}

	@Override
	public List<AbstractMessageModel> getMessagesForText(@Nullable String queryString, @MessageFilterFlags int filterFlags, boolean sortAscending) {
		List<AbstractMessageModel> messageModels = new ArrayList<>();

		boolean includeArchived = (filterFlags & FILTER_INCLUDE_ARCHIVED) == FILTER_INCLUDE_ARCHIVED;
		boolean starredOnly = (filterFlags & FILTER_STARRED_ONLY) == FILTER_STARRED_ONLY;

		if ((filterFlags & FILTER_CHATS) == FILTER_CHATS) {
			messageModels.addAll(getContactMessagesForText(queryString, includeArchived,
				starredOnly,
				sortAscending));
		}

		if ((filterFlags & FILTER_GROUPS) == FILTER_GROUPS) {
			messageModels.addAll(getGroupMessagesForText(queryString, includeArchived,
				starredOnly,
				sortAscending));
		}

		if (!messageModels.isEmpty()) {
			if (sortAscending) {
				Collections.sort(messageModels, (o1, o2) -> o1.getCreatedAt().compareTo(o2.getCreatedAt()));
			} else {
				Collections.sort(messageModels, (o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()));
			}
		}
		return messageModels;
	}

	@Override
	@WorkerThread
	public int unstarAllMessages() {
		return
			databaseServiceNew.getMessageModelFactory().unstarAllMessages() +
			databaseServiceNew.getGroupMessageModelFactory().unstarAllMessages();
	}

	@Override
	@WorkerThread
	public long countStarredMessages() throws SQLiteException {
		return
			databaseServiceNew.getMessageModelFactory().countStarredMessages() +
			databaseServiceNew.getGroupMessageModelFactory().countStarredMessages();
	}

	@Override
	@Nullable
	public MessageModel getContactMessageModel(final Integer id) {
		MessageModel model;
		synchronized (contactMessageCache) {
			model = Functional.select(contactMessageCache, type -> type.getId() == id);
		}
		if (model == null) {
			model = databaseServiceNew.getMessageModelFactory().getById(id);
			if (model != null) {
				synchronized (contactMessageCache) {
					contactMessageCache.add(model);
				}
			}
		}
		return model;
	}

	private @Nullable MessageModel getContactMessageModel(
		@NonNull final String apiMessageId,
		@NonNull ContactMessageReceiver messageReceiver
	) {
		MessageModel model;
		synchronized (contactMessageCache) {
			model = Functional.select(
				contactMessageCache,
				messageModel -> apiMessageId.equals(messageModel.getApiMessageId())
					&& messageReceiver.getContact().getIdentity().equals(messageModel.getIdentity())
			);
		}
		if (model == null) {
			try {
				model = databaseServiceNew.getMessageModelFactory().getByApiMessageIdAndIdentity(
					new MessageId(Utils.hexStringToByteArray(apiMessageId)),
					messageReceiver.getContact().getIdentity()
				);
				if (model != null) {
					synchronized (contactMessageCache) {
						contactMessageCache.add(model);
					}
				}
			}
			catch (ThreemaException ignore) {}
		}
		return model;
	}

	@Nullable
	@Override
	public MessageModel getContactMessageModel(final String uid) {
		return databaseServiceNew.getMessageModelFactory().getByUid(uid);
	}

	@Override
	@Nullable
	public GroupMessageModel getGroupMessageModel(final Integer id) {
		synchronized (groupMessageCache) {
			GroupMessageModel model = Functional.select(groupMessageCache, type -> type.getId() == id);

			if (model == null) {
				model = databaseServiceNew.getGroupMessageModelFactory().getById(id);
				if (model != null) {
					groupMessageCache.add(model);
				}
			}
			return model;
		}
	}

	@Nullable
	@Override
	public GroupMessageModel getGroupMessageModel(final String uid) {
		return databaseServiceNew.getGroupMessageModelFactory().getByUid(uid);
	}

	private GroupMessageModel getGroupMessageModel(
		@NonNull final String apiMessageId,
		@NonNull GroupMessageReceiver messageReceiver
	) {
		int groupId = messageReceiver.getGroup().getId();
		synchronized (groupMessageCache) {
			GroupMessageModel model = Functional.select(
				groupMessageCache,
				messageModel -> apiMessageId.equals(messageModel.getApiMessageId())
					&& groupId == messageModel.getGroupId()
			);

			if (model == null) {
				try {
					model = databaseServiceNew.getGroupMessageModelFactory().getByApiMessageIdAndGroupId(new MessageId(Utils.hexStringToByteArray(apiMessageId)), groupId);
					if (model != null) {
						groupMessageCache.add(model);
					}
				}
				catch (ThreemaException ignore) {}
			}
			return model;
		}
	}

	@Override
	@Nullable
	public DistributionListMessageModel getDistributionListMessageModel(long id) {
		return databaseServiceNew.getDistributionListMessageModelFactory().getById(id);
	}

	private void fireOnNewMessage(final AbstractMessageModel messageModel) {

		if(appLockService.isLocked()) {

			//do not fire messages, wait until app is unlocked
			appLockService.addOnLockAppStateChanged(locked -> !locked);

		}
		fireOnCreatedMessage(messageModel);
	}

	@Override
	public MessageString getMessageString(AbstractMessageModel messageModel, int maxLength) {
		return getMessageString(messageModel, maxLength,true);
	}

	@NonNull
	@Override
	public MessageString getMessageString(AbstractMessageModel messageModel, int maxLength, boolean withPrefix) {
		boolean isHidden;
		String prefix = "";

		if (messageModel instanceof GroupMessageModel) {
			//append Username
			if (withPrefix) {
				prefix = NameUtil.getShortName(context, messageModel, contactService) + ": ";
			}
			final GroupModel groupModel = groupService.getById(((GroupMessageModel)messageModel).getGroupId());
			isHidden = hiddenChatsListService.has(groupService.getUniqueIdString(groupModel));
		} else {
			final ContactModel contactModel = contactService.getByIdentity(messageModel.getIdentity());
			isHidden = hiddenChatsListService.has(ContactUtil.getUniqueIdString(messageModel.getIdentity()));
		}

		if (isHidden) {
			return new MessageString(context.getString(R.string.new_messages_locked));
		}

		switch (messageModel.getType()) {
			case TEXT:
				String messageText, rawMessageText;

				messageText = QuoteUtil.getMessageBody(messageModel, false);
				rawMessageText = prefix + messageText;
				if ((maxLength > 0) && (messageText.length() > maxLength)) {
					messageText = messageText.substring(0, maxLength - 3) + "...";
				}
				return new MessageString(messageText, rawMessageText);
			case VIDEO:
				return new MessageString(prefix + context.getResources().getString(R.string.video_placeholder));
			case LOCATION:
				String locationString = prefix + context.getResources().getString(R.string.location_placeholder);
				final @NonNull LocationDataModel locationDataModel = messageModel.getLocationData();
                if (locationDataModel.poiNameOrNull != null) {
					locationString += ": " + locationDataModel.poiNameOrNull;
				}
				return new MessageString(locationString);
			case VOICEMESSAGE:
				String messageString = prefix + context.getResources().getString(R.string.audio_placeholder);
				messageString += " (" + StringConversionUtil.secondsToString(messageModel.getAudioData().getDuration(), false) + ")";
				return new MessageString(messageString);
			case FILE:
				if (MimeUtil.isImageFile(messageModel.getFileData().getMimeType())) {
					if (TestUtil.isEmptyOrNull(messageModel.getCaption())) {
						return new MessageString(prefix + context.getResources().getString(R.string.image_placeholder));
					} else {
						return new MessageString(prefix + context.getResources().getString(R.string.image_placeholder) + ": " + messageModel.getFileData().getCaption());
					}
				} else if (MimeUtil.isVideoFile(messageModel.getFileData().getMimeType())) {
					if (TestUtil.isEmptyOrNull(messageModel.getFileData().getCaption())) {
						String durationString = messageModel.getFileData().getDurationString();
						return new MessageString(prefix + context.getResources().getString(R.string.video_placeholder) + " (" + durationString + ")");
					} else {
						return new MessageString(prefix + context.getResources().getString(R.string.video_placeholder) + ": " + messageModel.getFileData().getCaption());
					}
				} else if (MimeUtil.isAudioFile(messageModel.getFileData().getMimeType())) {
					if (TestUtil.isEmptyOrNull(messageModel.getFileData().getCaption())) {
						String durationString = messageModel.getFileData().getDurationString();
						if ("00:00".equals(durationString)) {
							return new MessageString(prefix + context.getResources().getString(R.string.audio_placeholder));
						} else {
							return new MessageString(prefix + context.getResources().getString(R.string.audio_placeholder) + " (" + durationString + ")");
						}
					} else {
						return new MessageString(prefix + context.getResources().getString(R.string.audio_placeholder) + ": " + messageModel.getFileData().getCaption());
					}
				} else {
					if (TestUtil.isEmptyOrNull(messageModel.getFileData().getCaption())) {
						return new MessageString(prefix + context.getResources().getString(R.string.file_placeholder) + ": " + messageModel.getFileData().getFileName());
					} else {
						return new MessageString(prefix + context.getResources().getString(R.string.file_placeholder) + ": " + messageModel.getFileData().getCaption());
					}
				}
			case IMAGE:
				if (TestUtil.isEmptyOrNull(messageModel.getCaption())) {
					return new MessageString(prefix + context.getResources().getString(R.string.image_placeholder));
				} else {
					return new MessageString(prefix + context.getResources().getString(R.string.image_placeholder) + ": " + messageModel.getCaption());
				}
			case BALLOT:
				return new MessageString(prefix + context.getResources().getString(R.string.ballot_placeholder) + ":" + BallotUtil.getNotificationString(context, messageModel));
			case VOIP_STATUS:
				return new MessageString(prefix + MessageUtil.getViewElement(context, messageModel).placeholder);
			default:
				return new MessageString(prefix);
		}
	}

	@Override
	public void saveIncomingServerMessage(final ServerMessageModel msg) {
		// Store server message into database
		databaseServiceNew.getServerMessageModelFactory().storeServerMessageModel(msg);
		// Show as alert
		ListenerManager.serverMessageListeners.handle(listener -> {
			if (msg.getType() == ServerMessageModel.TYPE_ALERT) {
				listener.onAlert(msg);
			} else {
				listener.onError(msg);
			}
		});
	}

	@Override
	public boolean downloadMediaMessage(
		@Nullable AbstractMessageModel mediaMessageModel,
		@Nullable ProgressListener progressListener
	) throws Exception {
		//TODO: create messageutil can download file method and unit test
		if (!MessageUtil.hasDataFile(mediaMessageModel)) {
			throw new ThreemaException("message is not a media message");
		}

		MediaMessageDataInterface data = getDataForMessageType(mediaMessageModel);

		if (data != null && !data.isDownloaded()) {
			if (downloadAndWriteMediaData(mediaMessageModel, data, progressListener)) {
				setDownloadCompleted(mediaMessageModel, data);
				saveImagesAndVideosToGalleryIfEnabled(mediaMessageModel, data);
				return true;
			} else {
				logger.error("Decryption failed");
				this.downloadService.error(mediaMessageModel.getId());
				throw new ThreemaException("Decryption failed");
			}
		}
		return false;
	}

	private @Nullable MediaMessageDataInterface getDataForMessageType(
		@NonNull AbstractMessageModel mediaMessageModel
	) {
		switch (mediaMessageModel.getType()) {
			case IMAGE:
				return mediaMessageModel.getImageData();
			case VIDEO:
				return mediaMessageModel.getVideoData();
			case VOICEMESSAGE:
				return mediaMessageModel.getAudioData();
			case FILE:
				return mediaMessageModel.getFileData();
			default:
				return null;
		}
	}

	private @NonNull
	byte[] getNonceForMessageType(@NonNull MessageType messageType) throws ThreemaException {
		switch (messageType) {
			case IMAGE:
				return ProtocolDefines.IMAGE_NONCE;
			case VIDEO:
				return ProtocolDefines.VIDEO_NONCE;
			case VOICEMESSAGE:
				return ProtocolDefines.AUDIO_NONCE;
			case FILE:
				return ProtocolDefines.FILE_NONCE;
			default:
				throw new ThreemaException("Could not get nonce for messageType=" + messageType);
		}
	}

	private boolean downloadAndWriteMediaData(
		@NonNull AbstractMessageModel mediaMessageModel,
		@NonNull MediaMessageDataInterface data,
		@Nullable ProgressListener progressListener
	) throws ThreemaException {
		if (mediaMessageModel.getType() != MessageType.IMAGE) {
			File messageFile = fileService.getMessageFile(mediaMessageModel);
			if (messageFile != null && messageFile.exists() && messageFile.length() > NaCl.BOXOVERHEAD) {
				// hack: do not re-download a blob that's already present on the file system
				return true;
			}
		}

        // If multi-device is active, we always mark as done. Otherwise we do not mark as done if its a group message
        boolean shouldMarkAsDone = multiDeviceManager.isMultiDeviceActive() || !(mediaMessageModel instanceof GroupMessageModel);
        @Nullable BlobScope blobScopeMarkAsDone = null;
        if (shouldMarkAsDone) {
            blobScopeMarkAsDone = mediaMessageModel.getBlobScopeForMarkAsDone();
        }

		byte[] blob = downloadService.download(
			mediaMessageModel.getId(),
			data.getBlobId(),
            mediaMessageModel.getBlobScopeForDownload(),
            blobScopeMarkAsDone,
			progressListener
		);
		if (blob == null || blob.length < NaCl.BOXOVERHEAD) {
			logger.error("Blob for message {} is empty", mediaMessageModel.getApiMessageId());

			downloadService.error(mediaMessageModel.getId());
			// blob download failed or empty or canceled
			throw new ThreemaException("failed to download message");
		}

		boolean success = mediaMessageModel.getType() != MessageType.IMAGE
			? decryptNonImageMediaDataAndWriteConversationMedia(mediaMessageModel, data, blob)
			: decryptImageAndWriteConversationMedia(mediaMessageModel, blob);

		if (success && !fileService.hasMessageThumbnail(mediaMessageModel)) {
			createAndWriteMediaThumbnail(mediaMessageModel);
		}
		return success;
	}

	private void setDownloadCompleted(@NonNull AbstractMessageModel mediaMessageModel, @NonNull MediaMessageDataInterface data) {
		if (mediaMessageModel.getType() == MessageType.IMAGE) {
			mediaMessageModel.getImageData().isDownloaded(true);
		} else if (mediaMessageModel.getType() == MessageType.VIDEO) {
			mediaMessageModel.getVideoData().isDownloaded(true);
		} else if (mediaMessageModel.getType() == MessageType.VOICEMESSAGE) {
			mediaMessageModel.getAudioData().isDownloaded(true);
		} else if (mediaMessageModel.getType() == MessageType.FILE) {
			mediaMessageModel.getFileData().isDownloaded(true);
		}
		mediaMessageModel.writeDataModelToBody();

		save(mediaMessageModel);

		fireOnModifiedMessage(mediaMessageModel);

		downloadService.complete(mediaMessageModel.getId(), data.getBlobId());
	}

	private void saveImagesAndVideosToGalleryIfEnabled(@NonNull AbstractMessageModel mediaMessageModel, @NonNull MediaMessageDataInterface data) {
		if (preferenceService != null
			&& preferenceService.isSaveMedia()
			&& isImageOrVideoFile(mediaMessageModel, data)) {
			boolean isHidden = mediaMessageModel instanceof GroupMessageModel
				? hiddenChatsListService.has(groupService.getUniqueIdString(((GroupMessageModel) mediaMessageModel).getGroupId()))
				: hiddenChatsListService.has(ContactUtil.getUniqueIdString(mediaMessageModel.getIdentity()));

			if (!isHidden) {
				fileService.saveMedia(null, null, new CopyOnWriteArrayList<>(Collections.singletonList(mediaMessageModel)), true);
			}
		}
	}

	private boolean isImageOrVideoFile(@NonNull AbstractMessageModel mediaMessageModel, @NonNull MediaMessageDataInterface data) {
		MessageType type = mediaMessageModel.getType();
		return type == MessageType.IMAGE
			|| type == MessageType.VIDEO
			|| (type == MessageType.FILE && FileUtil.isImageOrVideoFile((FileDataModel) data));
	}

	private boolean decryptNonImageMediaDataAndWriteConversationMedia(
		@NonNull AbstractMessageModel messageModel,
		@NonNull MediaMessageDataInterface data,
		@NonNull byte[] blob
	) throws ThreemaException {
		logger.info("Decrypting blob for message {}", messageModel.getApiMessageId());

		byte[] nonce = getNonceForMessageType(messageModel.getType());
		if (symmetricEncryptionService.decryptInplace(blob, data.getEncryptionKey(), nonce)) {
			logger.info("Write conversation media for message {}", messageModel.getApiMessageId());

			// save the file
			try {
				if (fileService.writeConversationMedia(messageModel, blob, 0, blob.length - NaCl.BOXOVERHEAD, true)) {
					logger.info("Media for message {} successfully saved.", messageModel.getApiMessageId());
					return true;
				}
			} catch (Exception e) {
				logger.warn("Unable to save media");

				downloadService.error(messageModel.getId());

				throw new ThreemaException("Unable to save media");
			}
		}
		return false;
	}

	private boolean decryptImageAndWriteConversationMedia(
		@NonNull AbstractMessageModel messageModel,
		@NonNull byte[] blob
	) {
		ImageDataModel imageData = messageModel.getImageData();
		byte[] image = messageModel instanceof GroupMessageModel
			? NaCl.symmetricDecryptData(blob, imageData.getEncryptionKey(), ProtocolDefines.IMAGE_NONCE)
			: identityStore.decryptData(blob, imageData.getNonce(), imageData.getEncryptionKey());

		if (image != null && image.length > 0) {
			try {
				// save the file
				return saveStrippedImage(image, messageModel);
			} catch (Exception e) {
				logger.error("Exception", e);
			}
		}
		return false;
	}

	private void createAndWriteMediaThumbnail(@NonNull AbstractMessageModel messageModel) {
		if (!MessageUtil.canHaveThumbnailFile(messageModel)) {
			// ignore messages that cannot have a thumbnail
			return;
		}

		try {
			File file = fileService.getDecryptedMessageFile(messageModel);
			byte[] thumbnailData = ThumbnailUtil.generateThumbnailData(context, getMimeTypeString(messageModel), file);
			if (thumbnailData != null) {
				fileService.writeConversationMediaThumbnail(messageModel, thumbnailData);
			}
		} catch (Exception e) {
			logger.error("Could not write conversation media thumbnail", e);
		}
	}

	@Override
	public boolean cancelMessageDownload(AbstractMessageModel messageModel) {
		return downloadService.cancel(messageModel.getId());
	}

	private void fireOnCreatedMessage(final AbstractMessageModel messageModel) {
		logger.debug("fireOnCreatedMessage for message {}", messageModel.getApiMessageId());
		ListenerManager.messageListeners.handle(listener -> listener.onNew(messageModel));
	}

	private void fireOnModifiedMessage(final AbstractMessageModel messageModel) {
		ListenerManager.messageListeners.handle(listener -> {
			List<AbstractMessageModel> list = new ArrayList<>();
			list.add(messageModel);

			listener.onModified(list);
		});
	}

	private void fireOnMessageDeletedForAll(final AbstractMessageModel messageModel) {
		ListenerManager.messageDeletedForAllListener.handle(listener -> listener.onDeletedForAll(messageModel));
	}

	private void fireOnEditMessage(final AbstractMessageModel messageModel) {
		ListenerManager.editMessageListener.handle(listener -> listener.onEdit(messageModel));
	}

	private void fireOnRemovedMessage(final AbstractMessageModel messageModel) {
		ListenerManager.messageListeners.handle(listener -> listener.onRemoved(messageModel));
	}

	private void setMessageLoadingFinished(AbstractMessageModel messageModel) {
		loadingProgress.delete(messageModel.getId());
		cancelUploader(messageModel);
	}

	private void updateMessageLoadingProgress(final AbstractMessageModel messageModel, final int progress) {
		loadingProgress.put(messageModel.getId(), progress);

		//handle progress
		ListenerManager.messageListeners.handle(listener -> listener.onProgressChanged(messageModel, progress));
	}

	@Override
	public void removeAll() throws SQLException, IOException, ThreemaException {
		//use the fast way
		databaseServiceNew.getMessageModelFactory().deleteAll();
		databaseServiceNew.getGroupMessageModelFactory().deleteAll();
		databaseServiceNew.getDistributionListMessageModelFactory().deleteAll();

		//clear all caches
		synchronized (contactMessageCache) {
			contactMessageCache.clear();
		}

		//clear all caches
		synchronized (groupMessageCache) {
			groupMessageCache.clear();
		}

		//clear all caches
		synchronized (distributionListMessageCache) {
			distributionListMessageCache.clear();
		}

		//clear all files in app Path
		fileService.clearDirectory(fileService.getAppDataPath(), false);
	}

	@Override
	public void save(final AbstractMessageModel messageModel) {
		if(messageModel != null) {
			if(messageModel instanceof MessageModel) {
				synchronized (contactMessageCache) {
					databaseServiceNew.getMessageModelFactory().createOrUpdate(
							(MessageModel) messageModel
					);

					// Update the cache
					Iterator<MessageModel> iterator = contactMessageCache.iterator();
					while (iterator.hasNext()) {
						MessageModel cached = iterator.next();
						if (cached.getId()== messageModel.getId() && cached != messageModel) {
							// Remove old message model from cache if not the same object
							iterator.remove();
						}
					}

				}
			}
			else if(messageModel instanceof GroupMessageModel) {
				synchronized (groupMessageCache) {
					databaseServiceNew.getGroupMessageModelFactory().createOrUpdate(
							(GroupMessageModel) messageModel);

					//remove "old" message models from cache
					for(GroupMessageModel m: Functional.filter(groupMessageCache, (IPredicateNonNull<GroupMessageModel>) type -> type.getId() == messageModel.getId() && messageModel != type)){
						logger.debug("Updating cached data for group message model {}", messageModel.getApiMessageId());
						m.copyFrom((GroupMessageModel) messageModel);
					}
				}
			}
			else if(messageModel instanceof DistributionListMessageModel) {
				synchronized (distributionListMessageCache) {

					databaseServiceNew.getDistributionListMessageModelFactory().createOrUpdate(
							(DistributionListMessageModel) messageModel);

					//remove "old" message models from cache
					for(DistributionListMessageModel m: Functional.filter(distributionListMessageCache, (IPredicateNonNull<DistributionListMessageModel>) type -> type.getId() == messageModel.getId() && messageModel != type)){
						//remove cached unsaved object

						logger.debug("copy from distribution list message model fix");
						m.copyFrom(messageModel);
					}
				}
			}

			// Cache the element for more actions
			cache(messageModel);
		}
	}

	@Override
	public long getTotalMessageCount() {
		//simple count
		return databaseServiceNew.getMessageModelFactory().count()
				+ databaseServiceNew.getGroupMessageModelFactory().count()
				+ databaseServiceNew.getDistributionListMessageModelFactory().count();

	}

	@NonNull
	private String getMimeTypeString(AbstractMessageModel model) {
		switch (model.getType()) {
			case VIDEO:
				return MimeUtil.MIME_TYPE_VIDEO;
			case FILE:
				return model.getFileData().getMimeType();
			case VOICEMESSAGE:
				return MimeUtil.MIME_TYPE_AUDIO;
			case IMAGE:
				return MimeUtil.MIME_TYPE_IMAGE_JPEG;
			default:
				return MimeUtil.MIME_TYPE_ANY;
		}
	}

	private String getLeastCommonDenominatorMimeType(ArrayList<AbstractMessageModel> models) {
		String mimeType = getMimeTypeString(models.get(0));

		if (models.size() > 1) {
			for (int i = 1; i < models.size(); i++) {
				mimeType = MimeUtil.getCommonMimeType(mimeType, getMimeTypeString(models.get(i)));
			}
		}

		return mimeType;
	}

	@Override
	public boolean shareMediaMessages(final Context context, ArrayList<AbstractMessageModel> models, ArrayList<Uri> shareFileUris, String caption) {
		if (TestUtil.required(context, models, shareFileUris)) {
			if (models.size() > 0 && shareFileUris.size() > 0) {
				Intent intent;
				if (models.size() == 1) {
					AbstractMessageModel model = models.get(0);
					Uri shareFileUri = shareFileUris.get(0);

					if (shareFileUri == null) {
						logger.info("No file to share");
						return false;
					}

					intent = new Intent(Intent.ACTION_SEND);
					intent.putExtra(Intent.EXTRA_STREAM, shareFileUri);
					intent.setType(getMimeTypeString(model));
					if (ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(shareFileUri.getScheme())) {
						intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
					}
					if (!TestUtil.isEmptyOrNull(caption)) {
						intent.putExtra(Intent.EXTRA_TEXT, caption);
					}
				} else {
					intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
					intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, shareFileUris);

					Uri firstShareFileUri = shareFileUris.get(0);

					intent.setType(getLeastCommonDenominatorMimeType(models));
					if (firstShareFileUri != null) {
						if (ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(firstShareFileUri.getScheme())) {
							intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
						}
					}
				}

				try {
					context.startActivity(Intent.createChooser(intent, context.getResources().getText(R.string.share_via)));

					return true;
				} catch (ActivityNotFoundException e) {
					// make sure Toast runs in UI thread
					RuntimeUtil.runOnUiThread(() -> Toast.makeText(context, R.string.no_activity_for_mime_type, Toast.LENGTH_SHORT).show());
				}
			}
		}
		return false;
	}

	@Override
	public boolean viewMediaMessage(final Context context, AbstractMessageModel model, Uri uri) {
		if (TestUtil.required(context, model, uri)) {
			Intent intent = new Intent(Intent.ACTION_VIEW);

			String mimeType = getMimeTypeString(model);
			if (MimeUtil.isImageFile(mimeType)) {
				// some viewers cannot handle image/gif - give them a generic mime type
				mimeType = MimeUtil.MIME_TYPE_IMAGE;
			}
			intent.setDataAndType(uri, mimeType);
			if (ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(uri.getScheme())) {
				intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);
				if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
					intent.setClipData(ClipData.newRawUri("", uri));
					intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
				}
			} else if (!(context instanceof Activity)) {
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			}

			try {
				context.startActivity(intent);
			} catch (ActivityNotFoundException e) {
				// make sure Toast runs in UI thread
				RuntimeUtil.runOnUiThread(() -> Toast.makeText(context, R.string.no_activity_for_mime_type, Toast.LENGTH_SHORT).show());
			} catch (SecurityException e) {
				logger.error("Error firing ACTION_VIEW intent", e);
				RuntimeUtil.runOnUiThread(() -> Toast.makeText(context, context.getString(R.string.no_activity_for_mime_type) + " " + e.getMessage(), Toast.LENGTH_SHORT).show());
			}
		}
		return false;
	}

	@Override
	public boolean shareTextMessage(Context context, AbstractMessageModel messageModel) {
		if (messageModel != null) {
			String text = "";

			Intent intent = new Intent();
			if (messageModel.getType() == MessageType.LOCATION) {
				Uri locationUri = GeoLocationUtil.getLocationUri(messageModel);

                final @NonNull LocationDataModel locationDataModel = messageModel.getLocationData();
				if (locationDataModel.poiAddressOrNull != null) {
					text = locationDataModel.poiAddressOrNull + " - ";
				}
				text += locationUri.toString();
			} else {
				text = QuoteUtil.getMessageBody(messageModel, false);
			}

			intent.setAction(Intent.ACTION_SEND);
			intent.putExtra(android.content.Intent.EXTRA_TEXT, text);
			intent.setType(MimeUtil.MIME_TYPE_TEXT);

			try {
				context.startActivity(Intent.createChooser(intent, context.getResources().getText(R.string.share_via)));
			} catch (Exception e) {
				Toast.makeText(context, R.string.no_activity_for_mime_type, Toast.LENGTH_LONG).show();
				logger.error("Exception", e);
			}
		}
		return false;
	}

	@Override
	public void markConversationAsRead(MessageReceiver messageReceiver, NotificationService notificationService) {
		@SuppressWarnings("unchecked")
		List<AbstractMessageModel> unreadMessages = messageReceiver.loadMessages(new MessageService.MessageFilter() {
			@Override
			public long getPageSize() {
				return 0;
			}

			@Override
			public Integer getPageReferenceId() {
				return null;
			}

			@Override
			public boolean withStatusMessages() {
				return false;
			}

			@Override
			public boolean withUnsaved() {
				return false;
			}

			@Override
			public boolean onlyUnread() {
				return true;
			}

			@Override
			public boolean onlyDownloaded() {
				return false;
			}

			@Override
			public MessageType[] types() {
				return null;
			}

			@Override
			public int[] contentTypes() {
				return null;
			}

			@Override
			public int[] displayTags() {
				return null;
			}
		});

		if (unreadMessages != null && unreadMessages.size() > 0) {
			//do not run on a own thread, create a new thread outside!
			new ReadMessagesRoutine(unreadMessages, this, notificationService).run();
		}
		notificationService.cancel(messageReceiver);
	}

	@Override
	public AbstractMessageModel getMessageModelFromId(int id, String type) {
		if (id != 0 && !TestUtil.isEmptyOrNull(type)) {
			if (type.equals(MessageModel.class.toString())) {
				return getContactMessageModel(id);
			} else if (type.equals(GroupMessageModel.class.toString())) {
				return getGroupMessageModel(id);
			} else if (type.equals(DistributionListMessageModel.class.toString())) {
				return getDistributionListMessageModel(id);
			}
		}
		return null;
	}

	@Override
	@Nullable
	public AbstractMessageModel getMessageModelByApiMessageIdAndReceiver(
		@Nullable String apiMessageId,
		@NonNull MessageReceiver messageReceiver
	) {
		if (apiMessageId != null) {
			if (messageReceiver instanceof ContactMessageReceiver) {
				return getContactMessageModel(apiMessageId, (ContactMessageReceiver) messageReceiver);
			} else if (messageReceiver instanceof  GroupMessageReceiver) {
				return getGroupMessageModel(apiMessageId, (GroupMessageReceiver) messageReceiver);
			} else if (messageReceiver instanceof DistributionListMessageReceiver) {
				// We cannot return a message model with a certain api message id for distribution
				// lists, because the api message id is null for all distribution list messages
				return null;
			}
		}
		return null;
	}

	/*******************************************************************************************
	 * Uploader Cache (used to cancel running downloads)
	 *******************************************************************************************/

	private final Map<String, BlobUploader> uploaders = new ArrayMap<>();
	private final Map<String, WeakReference<VideoTranscoder>> videoTranscoders = new ArrayMap<>();


	/**
	 * create a new AbstractMessageModel uploader
	 * a existing uploader will be canceled
	 */
	@NonNull
    private BlobUploader initUploader(
        AbstractMessageModel messageModel,
        byte[] data,
        @NonNull MessageReceiver<?> messageReceiver
    ) throws ThreemaException {
		synchronized (uploaders) {
			String key = cancelUploader(messageModel);

            boolean shouldPersist = shouldPersistUploadForMessage(messageModel);

            @NonNull BlobScope blobScope = BlobScope.Public.INSTANCE;

            // If the message is send to a notes group, the blob scope must not be "public"
            if (messageReceiver instanceof GroupMessageReceiver) {
                if (groupService.isNotesGroup(((GroupMessageReceiver) messageReceiver).getGroup())) {
                    blobScope = BlobScope.Local.INSTANCE;
                }
            }

            BlobUploader up = apiService.createUploader(
                data,
                shouldPersist,
                blobScope
            );
			uploaders.put(key, up);

			logger.debug("create new uploader for message {}, persist={}", key, shouldPersist);
			return up;
		}
	}

	private boolean shouldPersistUploadForMessage(AbstractMessageModel messageModel) {
		return messageModel instanceof DistributionListMessageModel;
	}

	private String getLoaderKey(AbstractMessageModel messageModel) {
		return messageModel.getClass() + "-" + messageModel.getUid();
	}

	/**
	 * cancel an existing AbstractMessageModel uploader
	 */
	private String cancelUploader(AbstractMessageModel messageModel) {
		synchronized (uploaders) {
			String key = getLoaderKey(messageModel);

			BlobUploader blobUploader = uploaders.get(key);
			if (blobUploader != null) {
				logger.debug("cancel upload of message {}", key);
				blobUploader.cancel();
				uploaders.remove(key);
			}

			return key;
		}
	}

	/**
	 * cancel an existing video transcoding
	 */
	private String cancelTranscoding(AbstractMessageModel messageModel) {
		synchronized (videoTranscoders) {
			String key = getLoaderKey(messageModel);

			if (videoTranscoders.containsKey(key)) {
				logger.debug("cancel transcoding of message {}", key);
				WeakReference<VideoTranscoder> videoTranscoderRef = videoTranscoders.get(key);
				if (videoTranscoderRef != null) {
					if (videoTranscoderRef.get() != null) {
						videoTranscoderRef.get().cancel();
					}
				}
				videoTranscoders.remove(key);
			}
			return key;
		}
	}

	@Override
	public void cancelMessageUpload(AbstractMessageModel messageModel) {
		updateOutgoingMessageState(messageModel, MessageState.SENDFAILED, new Date());

		if (messageSendingService != null) {
			messageSendingService.abort(messageModel.getUid());
		}
		removeSendMachine(messageModel);
		cancelUploader(messageModel);
	}

	@Override
	public void cancelVideoTranscoding(AbstractMessageModel messageModel) {
		updateOutgoingMessageState(messageModel, MessageState.SENDFAILED, new Date());

		removeSendMachine(messageModel);
		cancelTranscoding(messageModel);
	}

	/******************************************************************************************
	 * Sending Message Machine
	 * * Handling sending steps of image/video/audio or file messages
	 * * Can be aborted
	 ******************************************************************************************/

	public final Map<String, SendMachine> sendMachineInstances = new HashMap<>();

	/**
	 * Remove a instantiated sendmachine if exists
	 */
	public void removeSendMachine(SendMachine sendMachine) {
		if(sendMachine != null) {
			sendMachine.abort();

			//remove from instances
			synchronized (sendMachineInstances) {
				for(Iterator<Map.Entry<String, SendMachine>> it = sendMachineInstances.entrySet().iterator(); it.hasNext(); ) {
					Map.Entry<String, SendMachine> entry = it.next();
					if(entry.getValue() == sendMachine) {
						logger.debug("remove send machine from instance map");
						it.remove();
					}
				}
			}
		}
	}
	public void removeSendMachine(AbstractMessageModel messageModel) {
		if(messageModel == null) {
			//ignore
			return;
		}

		removeSendMachine(getSendMachine(messageModel, false));
	}

	/**
	 * get or create a existing send machine
	 */
	public SendMachine getSendMachine(AbstractMessageModel abstractMessageModel) {
		return getSendMachine(abstractMessageModel, true);
	}

	/**
	 * get a send machine or create one (and cache into machine instances)
	 * can return NULL
	 */
	public SendMachine getSendMachine(AbstractMessageModel abstractMessageModel, boolean createIfNotExists) {
		synchronized (sendMachineInstances) {
			//be sure to "generate" a unique key
			String key = abstractMessageModel.getClass() + "-" + abstractMessageModel.getUid();

			SendMachine instance = null;
			if(sendMachineInstances.containsKey(key)) {
				instance = sendMachineInstances.get(key);
			}
			else if(createIfNotExists) {
				instance = new SendMachine();
				sendMachineInstances.put(key, instance);
			}
			return instance;
		}
	}

	interface SendMachineProcess {
		void run() throws Exception;
	}

	private static class SendMachine {
		private int nextStep = 0;
		private int currentStep = 0;
		private boolean aborted = false;

		public SendMachine reset() {
			currentStep = 0;
			return this;
		}

		public SendMachine abort() {
			logger.debug("SendMachine: Aborted");
			aborted = true;
			return this;
		}

		public SendMachine next(SendMachineProcess process) throws Exception {
			if(aborted) {
				logger.debug("SendMachine: Ignore step, aborted");
				//do nothing
				return this;
			}

			if (nextStep == currentStep++) {
				try {
					if(process != null) {
						process.run();
					}

					nextStep++;
				}
				catch (Exception x) {
					logger.error("SendMachine: Exception", x);
					throw x;
				}
			}
			return this;
		}
	}

	@Override
	public MessageReceiver getMessageReceiver(AbstractMessageModel messageModel) throws ThreemaException {
		if (messageModel instanceof MessageModel) {
			return contactService.createReceiver(contactService.getByIdentity(messageModel.getIdentity()));
		} else if (messageModel instanceof GroupMessageModel) {
			return groupService.createReceiver(groupService.getById(((GroupMessageModel) messageModel).getGroupId()));
		} else if (messageModel instanceof DistributionListMessageModel) {
			DistributionListService ds = ThreemaApplication.requireServiceManager().getDistributionListService();
			if (ds != null) {
				return ds.createReceiver(ds.getById(((DistributionListMessageModel) messageModel).getDistributionListId()));
			}
		}
		throw new ThreemaException("No receiver for this message");
	}


	/******************************************************************************************************/

	public interface SendResultListener {
		void onError(String errorMessage);
		void onCompleted();
	}

	/**
	 * Send media messages of any kind to an arbitrary number of receivers using a thread pool
	 * @param mediaItems List of MediaItems to be sent
	 * @param messageReceivers List of MessageReceivers
	 */
	@AnyThread
	@Override
	public void sendMediaAsync(@NonNull List<MediaItem> mediaItems, @NonNull List<MessageReceiver> messageReceivers) {
		sendMediaAsync(mediaItems, messageReceivers, null);
	}

	/**
	 * Send media messages of any kind to an arbitrary number of receivers using a thread pool
	 * @param mediaItems List of MediaItems to be sent
	 * @param messageReceivers List of MessageReceivers
	 * @param sendResultListener Listener to notify when messages are queued
	 */
	@AnyThread
	@Override
	public void sendMediaAsync(
		@NonNull final List<MediaItem> mediaItems,
		@NonNull final List<MessageReceiver> messageReceivers,
		@Nullable final SendResultListener sendResultListener
	) {
		ThreemaApplication.sendMessageExecutorService.submit(() -> {
			sendMedia(mediaItems, messageReceivers, sendResultListener);
		});
	}

	/**
	 * Send media messages of any kind to an arbitrary number of receivers in a single thread i.e. one message after the other
	 * @param mediaItems List of MediaItems to be sent
	 * @param messageReceivers List of MessageReceivers
	 */
	@AnyThread
	@Override
	public void sendMediaSingleThread(
		@NonNull final List<MediaItem> mediaItems,
		@NonNull final List<MessageReceiver> messageReceivers) {
		ThreemaApplication.sendMessageSingleThreadExecutorService.submit(() -> {
			sendMedia(mediaItems, messageReceivers, null);
		});
	}

	/**
	 * Send media messages of any kind to an arbitrary number of receivers
	 * @param mediaItems List of MediaItems to be sent
	 * @param messageReceivers List of MessageReceivers
	 * @param sendResultListener Listener to notify when messages are queued
	 * @return AbstractMessageModel of a successfully queued message, null if no message could be queued
	 */
	@WorkerThread
	@Override
	public @Nullable AbstractMessageModel sendMedia(
		@NonNull final List<MediaItem> mediaItems,
		@NonNull final List<MessageReceiver> messageReceivers,
		@Nullable final SendResultListener sendResultListener
	) {
		AbstractMessageModel successfulMessageModel = null;
		int failedCounter = 0;

		// resolve receivers to account for distribution lists
		final MessageReceiver[] resolvedReceivers = MessageUtil.addDistributionListReceivers(messageReceivers.toArray(new MessageReceiver[0]));

		logger.info("sendMedia: Sending {} items to {} receivers", mediaItems.size(), resolvedReceivers.length);

		String correlationId = getCorrelationId();

		for (MediaItem mediaItem : mediaItems) {
			logger.info("sendMedia: Now sending item of type {}", mediaItem.getType());
			if (TYPE_TEXT == mediaItem.getType()) {
				String text = mediaItem.getCaption();
				if (!TestUtil.isEmptyOrNull(text)) {
					for (MessageReceiver messageReceiver : resolvedReceivers) {
						try {
							successfulMessageModel = sendText(text, messageReceiver);
							if (successfulMessageModel != null) {
								logger.info("Text successfully sent");
							} else {
								failedCounter++;
								logger.info("Text send failed");
							}
						} catch (Exception e) {
							failedCounter++;
							logger.error("Could not send text message", e);
						}
					}
				} else {
					failedCounter++;
					logger.info("Text is empty");
				}
				continue;
			} else if (TYPE_LOCATION == mediaItem.getType()) {
				Location location = GeoLocationUtil.getLocationFromUri(mediaItem.getUri());
				if (location != null) {
					for (MessageReceiver messageReceiver : resolvedReceivers) {
						try {
							successfulMessageModel = sendLocation(location, "", messageReceiver, null);
						} catch (Exception e) {
							failedCounter++;
							logger.error("Could not send location message");
						}
					}
				} else {
					failedCounter++;
					logger.info("Sending location failed: invalid location");
				}
				continue;
			}

			final Map<MessageReceiver, AbstractMessageModel> messageModels = new HashMap<>();

			final FileDataModel fileDataModel = createFileDataModel(context, mediaItem);
			if (fileDataModel == null) {
				logger.info("Unable to create FileDataModel");
				failedCounter++;
				continue;
			}

			if (!createFileMessagesAndSetPending(correlationId, mediaItem, resolvedReceivers, messageModels, fileDataModel)) {
				logger.info("Unable to create messages");
				failedCounter++;
				continue;
			}

			if (!allChatsArePrivate(resolvedReceivers)) {
				saveToGallery(mediaItem);
			}

			try {
				final Map<String, Object> metaData = new HashMap<>();
				final byte[] contentData = generateContentData(mediaItem, resolvedReceivers, messageModels, fileDataModel, metaData);
				final byte[] thumbnailData = generateThumbnailData(mediaItem, fileDataModel, metaData);
				fileDataModel.setMetaData(metaData);

				if (thumbnailData != null) {
					writeThumbnails(messageModels, resolvedReceivers, thumbnailData);
				} else {
					logger.info("Unable to generate thumbnails");
				}

				if (contentData != null) {
					if (encryptAndSend(resolvedReceivers, messageModels, fileDataModel, thumbnailData, contentData)) {
						successfulMessageModel = messageModels.get(resolvedReceivers[0]);
					} else {
						throw new ThreemaException("Error encrypting and sending");
					}
				} else {
					logger.info("Error encrypting and sending");
					failedCounter++;
					markAsTerminallyFailed(resolvedReceivers, messageModels);
				}
			} catch (ThreemaException e) {
				if (e instanceof TranscodeCanceledException) {
					logger.info("Video transcoding canceled");
					// canceling is not really a failure
				} else {
					logger.error("Exception", e);
					failedCounter++;
				}
				markAsTerminallyFailed(resolvedReceivers, messageModels);
			}
		}

		if (failedCounter == 0) {
			logger.info("sendMedia: Successfully queued.");
			if (sendResultListener != null) {
				sendResultListener.onCompleted();
			}
		} else {
			logger.warn("sendMedia: Did not complete successfully, failedCounter={}", failedCounter);
			final String errorString = context.getString(R.string.an_error_occurred_during_send);
			logger.info(errorString);
			RuntimeUtil.runOnUiThread(() -> Toast.makeText(context, errorString, Toast.LENGTH_LONG).show());
			if (sendResultListener != null) {
				sendResultListener.onError(errorString);
			}
		}
		return successfulMessageModel;
	}

	/**
	 * Write thumbnails to local storage
	 */
	private void writeThumbnails(Map<MessageReceiver, AbstractMessageModel> messageModels, MessageReceiver[] resolvedReceivers, byte[] thumbnailData) {
		for (MessageReceiver messageReceiver : resolvedReceivers) {
			if (thumbnailData != null) {
				try {
					fileService.writeConversationMediaThumbnail(messageModels.get(messageReceiver), thumbnailData);
					fireOnModifiedMessage(messageModels.get(messageReceiver));
				} catch (Exception ignored) {
					// having no thumbnail is not really fatal
				}
			}
		}
	}

	/**
	 * Generate content data for this MediaItem
	 *
	 * @return content data as a byte array or null if content data could not be generated
	 */
	@WorkerThread
	private @Nullable byte[] generateContentData(
		@NonNull MediaItem mediaItem,
		@NonNull MessageReceiver[] resolvedReceivers,
		@NonNull Map<MessageReceiver, AbstractMessageModel> messageModels,
		@NonNull FileDataModel fileDataModel,
		@NonNull Map <String, Object> metaData
	) throws ThreemaException {
		switch (mediaItem.getType()) {
			case TYPE_VIDEO:
				// fallthrough
			case TYPE_VIDEO_CAM:
				@VideoTranscoder.TranscoderResult int result = transcodeVideo(mediaItem, resolvedReceivers, messageModels);
				if (result == VideoTranscoder.SUCCESS) {
					return getContentData(mediaItem);
				} else if (result == VideoTranscoder.CANCELED) {
					throw new TranscodeCanceledException();
				}
				break;
			case TYPE_IMAGE:
				// scale and rotate / flip images
				int maxSize = ConfigUtils.getPreferredImageDimensions(mediaItem.getImageScale() == ImageScale_DEFAULT ?
					preferenceService.getImageScale() : mediaItem.getImageScale());

				Bitmap bitmap = null;
				try {
					boolean hasNoTransparency = MimeUtil.MIME_TYPE_IMAGE_JPEG.equals(mediaItem.getMimeType());
					bitmap = BitmapUtil.safeGetBitmapFromUri(context, mediaItem.getUri(), maxSize, false, false, false);
					if (bitmap != null) {
						bitmap = adjustBitmapOrientation(bitmap, mediaItem, metaData);

						final byte[] imageByteArray;
						if (hasNoTransparency) {
							imageByteArray = BitmapUtil.getJpegByteArray(bitmap, mediaItem.getRotation(), mediaItem.getFlip());
						} else {
							imageByteArray = BitmapUtil.getPngByteArray(bitmap, mediaItem.getRotation(), mediaItem.getFlip());

							if (!MimeUtil.MIME_TYPE_IMAGE_PNG.equals(mediaItem.getMimeType())) {
								fileDataModel.setMimeType(MimeUtil.MIME_TYPE_IMAGE_PNG);

								if (fileDataModel.getFileName() != null) {
									int dot = fileDataModel.getFileName().lastIndexOf(".");
									if (dot > 1) {
										String filenamePart = fileDataModel.getFileName().substring(0, dot);
										fileDataModel.setFileName(filenamePart + ".png");
									}
								}
							}
						}
						if (imageByteArray != null) {
							fileDataModel.setFileSize(imageByteArray.length);
							ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
							outputStream.write(new byte[NaCl.BOXOVERHEAD]);
							outputStream.write(imageByteArray);

							return outputStream.toByteArray();
						}
					}
				} catch (Exception e) {
					logger.error("Exception", e);
				} finally {
					if (bitmap != null && !bitmap.isRecycled()) {
						bitmap.recycle();
					}
				}
				break;
			case TYPE_IMAGE_CAM:
				// cam images will always be sent in their original size. no scaling needed but possibly rotate and flip
				try (InputStream inputStream = getFromUri(context, mediaItem.getUri())) {
					if (inputStream != null && inputStream.available() > 0) {
						bitmap = BitmapFactory.decodeStream(new BufferedInputStream(inputStream), null, null);
						if (bitmap != null) {
							bitmap = adjustBitmapOrientation(bitmap, mediaItem, metaData);

							final byte[] imageByteArray = BitmapUtil.getJpegByteArray(bitmap, mediaItem.getRotation(), mediaItem.getFlip());
							if (imageByteArray != null) {
								ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
								outputStream.write(new byte[NaCl.BOXOVERHEAD]);
								outputStream.write(imageByteArray);

								return outputStream.toByteArray();
							}
						}
					}
				} catch (Exception e) {
					logger.error("Exception", e);
				}
				break;
			case TYPE_IMAGE_ANIMATED:
				metaData.put(FileDataModel.METADATA_KEY_ANIMATED, true);
				// fallthrough
			case TYPE_VOICEMESSAGE:
				// fallthrough
			case TYPE_FILE:
				// "regular" file messages
				return getContentData(mediaItem);
			default:
				// media type currently not supported
				break;
		}
		return null;
	}

	/**
	 * Rotate/flip bitmap according to exif information and add final dimensions to the file message's meta data also keeping in
	 * account local orientation (if any)
	 * @param bitmap The Bitmap
	 * @param mediaItem The MediaItem instance that contains orientation info about this particular item
	 * @param metaData A map with meta data that is going to be added to a file message
	 *
	 * @return A new bitmap with adjusted orientation
	 */
	@NonNull
	private Bitmap adjustBitmapOrientation(
		@NonNull Bitmap bitmap,
		@NonNull MediaItem mediaItem,
		@NonNull Map<String, Object> metaData
	) {
		bitmap = BitmapUtil.rotateBitmap(
			bitmap,
			mediaItem.getExifRotation(),
			mediaItem.getExifFlip());

		boolean isRotated = mediaItem.getRotation() == 90 || mediaItem.getRotation() == 270;
		metaData.put(FileDataModel.METADATA_KEY_WIDTH, isRotated ? bitmap.getHeight() : bitmap.getWidth());
		metaData.put(FileDataModel.METADATA_KEY_HEIGHT, isRotated ? bitmap.getWidth() : bitmap.getHeight());

		return bitmap;
	}

	/**
	 * Generate thumbnail data for this MediaItem
	 *
	 * @return byte array of the thumbnail bitmap, null if thumbnail could not be generated
	 */
	@WorkerThread
	private @Nullable byte[] generateThumbnailData(
		@NonNull MediaItem mediaItem,
		@NonNull FileDataModel fileDataModel,
		@NonNull Map <String, Object> metaData
	) {
		Bitmap thumbnailBitmap = null;

		int mediaType = mediaItem.getType();

		// we want thumbnails for images and videos even if they are to be sent as files
		if (MimeUtil.isSupportedImageFile(fileDataModel.getMimeType()))  {
			mediaType = TYPE_IMAGE;
		} else if (MimeUtil.isVideoFile(fileDataModel.getMimeType())) {
			mediaType = TYPE_VIDEO;
		}

		switch (mediaType) {
			case MediaItem.TYPE_VIDEO:
				// fallthrough
			case MediaItem.TYPE_VIDEO_CAM:
				// add duration to metadata
				long trimmedDuration = mediaItem.getDurationMs();
				if (mediaItem.getEndTimeMs() != TIME_UNDEFINED && (mediaItem.getEndTimeMs() != 0L || mediaItem.getStartTimeMs() != 0L)) {
					trimmedDuration = mediaItem.getEndTimeMs() - mediaItem.getStartTimeMs();
				} else {
					if (mediaItem.getDurationMs() == 0) {
						// empty duration means full video
						trimmedDuration = VideoUtil.getVideoDuration(context, mediaItem.getUri());
						mediaItem.setDurationMs(trimmedDuration);
					}
				}
				metaData.put(FileDataModel.METADATA_KEY_DURATION, (float) trimmedDuration / (float) DateUtils.SECOND_IN_MILLIS);
				thumbnailBitmap = IconUtil.getVideoThumbnailFromUri(context, mediaItem);
				fileDataModel.setThumbnailMimeType(MimeUtil.MIME_TYPE_IMAGE_JPEG);
				break;
			case MediaItem.TYPE_IMAGE:
				BitmapUtil.ExifOrientation exifOrientation = BitmapUtil.getExifOrientation(context, mediaItem.getUri());
				mediaItem.setExifRotation((int) exifOrientation.getRotation());
				mediaItem.setExifFlip(exifOrientation.getFlip());
				boolean hasNoTransparency = MimeUtil.MIME_TYPE_IMAGE_JPEG.equals(mediaItem.getMimeType());
				if (hasNoTransparency && mediaItem.getRenderingType() != RENDERING_STICKER) {
					fileDataModel.setThumbnailMimeType(MimeUtil.MIME_TYPE_IMAGE_JPEG);
				} else {
					fileDataModel.setThumbnailMimeType(MimeUtil.MIME_TYPE_IMAGE_PNG);
				}
				thumbnailBitmap = BitmapUtil.safeGetBitmapFromUri(context, mediaItem.getUri(), THUMBNAIL_SIZE_PX, false, true, false);
				if (thumbnailBitmap != null) {
					thumbnailBitmap = BitmapUtil.rotateBitmap(BitmapUtil.rotateBitmap(
						thumbnailBitmap,
						mediaItem.getExifRotation(),
						mediaItem.getExifFlip()), mediaItem.getRotation(), mediaItem.getFlip());
				}
				break;
			case MediaItem.TYPE_IMAGE_CAM:
				// camera images are always sent as JPGs
				fileDataModel.setThumbnailMimeType(MimeUtil.MIME_TYPE_IMAGE_JPEG);
				thumbnailBitmap = BitmapUtil.safeGetBitmapFromUri(context, mediaItem.getUri(), THUMBNAIL_SIZE_PX, false, true, false);
				if (thumbnailBitmap != null) {
					thumbnailBitmap = BitmapUtil.rotateBitmap(BitmapUtil.rotateBitmap(
						thumbnailBitmap,
						mediaItem.getExifRotation(),
						mediaItem.getExifFlip()), mediaItem.getRotation(), mediaItem.getFlip());
				}
				break;
			case TYPE_IMAGE_ANIMATED:
				fileDataModel.setThumbnailMimeType(MimeUtil.MIME_TYPE_IMAGE_PNG);
				thumbnailBitmap = IconUtil.getThumbnailFromUri(context, mediaItem.getUri(), THUMBNAIL_SIZE_PX, fileDataModel.getMimeType(), true);
				break;
			case MediaItem.TYPE_VOICEMESSAGE:
				metaData.put(FileDataModel.METADATA_KEY_DURATION, (float) mediaItem.getDurationMs() / (float) DateUtils.SECOND_IN_MILLIS);
				// voice messages do not have thumbnails
				thumbnailBitmap = null;
				break;
			case MediaItem.TYPE_FILE:
				// just an arbitrary file
				thumbnailBitmap = null;
				break;
			default:
				break;
		}

		final byte[] thumbnailData;
		if (thumbnailBitmap != null) {
			// convert bitmap to byte array
			if (MimeUtil.MIME_TYPE_IMAGE_JPEG.equals(fileDataModel.getThumbnailMimeType())) {
				thumbnailData = BitmapUtil.bitmapToJpegByteArray(thumbnailBitmap);
				fileDataModel.setThumbnailMimeType(MimeUtil.MIME_TYPE_IMAGE_JPEG);
			} else {
				thumbnailData = BitmapUtil.bitmapToPngByteArray(thumbnailBitmap);
				fileDataModel.setThumbnailMimeType(MimeUtil.MIME_TYPE_IMAGE_PNG);
			}
			thumbnailBitmap.recycle();
		} else {
			thumbnailData = null;
		}
		return thumbnailData;
	}

	/**
	 * Encrypt content and thumbnail data, upload blobs and queue messages for the specified MediaItem
	 * @param resolvedReceivers MessageReceivers to send the MediaItem to
	 * @param messageModels MessageModels for above MessageReceivers
	 * @param fileDataModel fileDataModel for this message
	 * @param thumbnailData Byte Array of thumbnail bitmap to be uploaded as a blob
	 * @param contentData Byte Array of Content to be uploaded as a blob
	 * @return true if the message was queued successfully, false otherwise. Note that errors that occur during sending are not handled here.
	 */
	@WorkerThread
	private boolean encryptAndSend(
		@NonNull MessageReceiver<AbstractMessageModel>[] resolvedReceivers,
		@NonNull Map<MessageReceiver, AbstractMessageModel> messageModels,
		@NonNull FileDataModel fileDataModel,
		@Nullable byte[] thumbnailData,
		@NonNull byte[] contentData
	) {
		final SymmetricEncryptionResult[] contentEncryptResult = new SymmetricEncryptionResult[1];
		final SymmetricEncryptionResult[] thumbnailEncryptResult = new SymmetricEncryptionResult[1];
		thumbnailEncryptResult[0] = null;
		contentEncryptResult[0] = null;

		for (MessageReceiver messageReceiver : resolvedReceivers) {
			// save content first as it will be modified later on
			AbstractMessageModel messageModel = messageModels.get(messageReceiver);
			if (messageModel == null) {
				// no messagemodel has been created for this receiver - skip
				continue;
			}

			if (messageReceiver instanceof GroupMessageReceiver
				&& groupService.isNotesGroup(((GroupMessageReceiver) messageReceiver).getGroup())
			) {
				// In case of a notes group, we set the message state directly to read
				messageModel.setState(MessageState.READ);
			} else {
				// Otherwise we initialize the message model with pending to show a progress bar
				messageModel.setState(MessageState.PENDING); // shows a progress bar
			}
			save(messageModel);

			try {
				fileService.writeConversationMedia(messageModel, contentData, NaCl.BOXOVERHEAD, contentData.length - NaCl.BOXOVERHEAD);
			} catch (Exception e) {
				// Failure to write local media is not necessarily fatal, continue
				logger.debug("Exception", e);
			}
		}

		for (MessageReceiver<AbstractMessageModel> messageReceiver : resolvedReceivers) {
			//enqueue processing and uploading stuff...
			AbstractMessageModel messageModel = messageModels.get(messageReceiver);
			if (messageModel == null) {
				// no messagemodel has been created for this receiver - skip
				logger.info("Mo MessageModel could be created for this receiver - skip");
				continue;
			}

			messageSendingService.addToQueue(new MessageSendingService.MessageSendingProcess() {
				private byte[] thumbnailBlobId;
				private byte[] contentBlobId;

				public boolean success = false;

				@Override
				public MessageReceiver<AbstractMessageModel> getReceiver() {
					return messageReceiver;
				}

				@Override
				public AbstractMessageModel getMessageModel() {
					return messageModel;
				}

				@Override
				public boolean send() throws Exception {
					SendMachine sendMachine = getSendMachine(messageModel);
					sendMachine.reset()
						.next(() -> {
							if (getReceiver().sendMediaData()) {
								// encrypt file data
								// note that encryptFileData() will overwrite contents of provided content data!
								if (contentEncryptResult[0] == null) {
									contentEncryptResult[0] = symmetricEncryptionService.encryptInplace(contentData, ProtocolDefines.FILE_NONCE);
									if (contentEncryptResult[0].isEmpty()) {
										throw new ThreemaException("File data encrypt failed");
									}
								}
								messageModel.setState(MessageState.UPLOADING);
								save(messageModel);
							}
							fileDataModel.setFileSize(contentData.length - NaCl.BOXOVERHEAD);
							messageModel.setFileDataModel(fileDataModel);
							fireOnModifiedMessage(messageModel);
						})
						.next(() -> {
							if (getReceiver().sendMediaData()) {
								// upload file data
								BlobUploader blobUploader = initUploader(
                                    getMessageModel(),
                                    contentEncryptResult[0].getData(),
                                    getReceiver()
                                );
								blobUploader.progressListener = new ProgressListener() {
									@Override
									public void updateProgress(int progress) {
										updateMessageLoadingProgress(messageModel, progress);
									}

									@Override
									public void onFinished(boolean success) {
										setMessageLoadingFinished(messageModel);
									}
								};
								contentBlobId = blobUploader.upload();
							}
						})
						.next(() -> {
							if (getReceiver().sendMediaData()) {
								// encrypt and upload thumbnail
								if (thumbnailData != null) {
									thumbnailEncryptResult[0] = symmetricEncryptionService
										.encrypt(thumbnailData, contentEncryptResult[0].getKey(), ProtocolDefines.FILE_THUMBNAIL_NONCE);

									if (thumbnailEncryptResult[0].isEmpty()) {
										throw new ThreemaException("Thumbnail encrypt failed");
									} else {
										BlobUploader blobUploader = initUploader(
                                            getMessageModel(),
                                            thumbnailEncryptResult[0].getData(),
                                            getReceiver()
                                        );
										blobUploader.progressListener = new ProgressListener() {
											@Override
											public void updateProgress(int progress) {
												updateMessageLoadingProgress(messageModel, progress);
											}

											@Override
											public void onFinished(boolean success) {
												setMessageLoadingFinished(messageModel);
											}
										};
										thumbnailBlobId = blobUploader.upload();
										fireOnModifiedMessage(messageModel);
									}
								}
							}
						})
						.next(() -> {
							getReceiver().createAndSendFileMessage(
								thumbnailBlobId,
								contentBlobId,
								contentEncryptResult[0],
								messageModel,
								null,
								null
							);
							updateOutgoingMessageState(messageModel,
								getReceiver().sendMediaData() && getReceiver().offerRetry() ?
									MessageState.SENDING :
									MessageState.SENT, new Date());

							messageModel.setFileDataModel(fileDataModel);
							// save updated model
							save(messageModel);
						})
						.next(() -> {
							messageModel.setSaved(true);
							// Verify current saved state
							updateOutgoingMessageState(messageModel,
								getReceiver().sendMediaData() && getReceiver().offerRetry() ?
									MessageState.SENDING :
									MessageState.SENT, new Date());

							if (!getReceiver().sendMediaData()) {
								// update status for message that stay local
								fireOnModifiedMessage(messageModel);
							}
							success = true;
						});

					if (success) {
						removeSendMachine(sendMachine);
					}
					return success;
				}
			});
		}
		return true;
	}

	/**
	 * Create MessageModels for all receivers, save local thumbnail and set MessageModels to PENDING for instant UI feedback
	 * @return true if all was hunky dory, false if an error occurred
	 */
	@WorkerThread
	private boolean createFileMessagesAndSetPending(
		String correlationId,
		MediaItem mediaItem,
		MessageReceiver[] resolvedReceivers,
		Map<MessageReceiver, AbstractMessageModel> messageModels,
		FileDataModel fileDataModel
	) {
		for (MessageReceiver messageReceiver : resolvedReceivers) {

			final AbstractMessageModel messageModel = messageReceiver.createLocalModel(MessageType.FILE, MimeUtil.getContentTypeFromFileData(fileDataModel), new Date());
			cache(messageModel);

			messageModel.setOutbox(true);
			messageModel.setState(MessageState.PENDING); // shows a progress bar
			messageModel.setFileDataModel(fileDataModel);
			messageModel.setCorrelationId(correlationId);
			String trimmedCaption = mediaItem.getTrimmedCaption();
			if (trimmedCaption != null && !trimmedCaption.isBlank()) {
				messageModel.setCaption(trimmedCaption);
			}
			messageModel.setSaved(true);

			messageReceiver.saveLocalModel(messageModel);

			messageReceiver.bumpLastUpdate();

			messageModels.put(messageReceiver, messageModel);

			fireOnCreatedMessage(messageModel);
		}
		return true;
	}

	@SuppressLint("Range")
	public @Nullable FileDataModel createFileDataModel(Context context, MediaItem mediaItem) {
		ContentResolver contentResolver = context.getContentResolver();
		String mimeType = mediaItem.getMimeType();
		String filename = mediaItem.getFilename();

		if (mediaItem.getUri() == null) {
			return null;
		}

		if (ContentResolver.SCHEME_FILE.equalsIgnoreCase(mediaItem.getUri().getScheme())) {
			if (TestUtil.isEmptyOrNull(filename)) {
				File file = new File(mediaItem.getUri().getPath());

				filename = file.getName();
			}
		} else {
			if (TestUtil.isEmptyOrNull(filename) || TestUtil.isEmptyOrNull(mimeType)) {
				String[] proj = {
					DocumentsContract.Document.COLUMN_DISPLAY_NAME,
					DocumentsContract.Document.COLUMN_MIME_TYPE
				};

				try (Cursor cursor = contentResolver.query(mediaItem.getUri(), proj, null, null, null)) {
					if (cursor != null && cursor.moveToFirst()) {
						if (TestUtil.isEmptyOrNull(filename)) {
							filename = cursor.getString(
								cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
						}
						if (TestUtil.isEmptyOrNull(mimeType) || MimeUtil.MIME_TYPE_DEFAULT.equals(mimeType)) {
							mimeType = cursor.getString(
								cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE));
						}
					}
				} catch (Exception e) {
					logger.error("Unable to query content provider", e);
				}
			}
		}

		if (TestUtil.isEmptyOrNull(mimeType) || MimeUtil.MIME_TYPE_DEFAULT.equals(mimeType)) {
			mimeType = FileUtil.getMimeTypeFromUri(context, mediaItem.getUri());
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			// non-animated images are being sent as png files
			// we should fix the mime type before creating a local message model in order not to confuse the chat adapter
			if (MimeUtil.isAnimatedImageFormat(mimeType)
				&& mediaItem.getType() != TYPE_IMAGE_ANIMATED
				&& mediaItem.getType() != TYPE_FILE
				&& mediaItem.getImageScale() != PreferenceService.ImageScale_SEND_AS_FILE) {
				mimeType = MimeUtil.MIME_TYPE_IMAGE_PNG;
			}
		}

		@FileData.RenderingType int renderingType = mediaItem.getRenderingType();

		// rendering type overrides
		switch (mediaItem.getType()) {
			case TYPE_VOICEMESSAGE:
				filename = FileUtil.getDefaultFilename(mimeType); // the internal temporary file name is of no use to the recipient
				renderingType = FileData.RENDERING_MEDIA;
				break;
			case TYPE_IMAGE_ANIMATED:
				if (renderingType == FileData.RENDERING_DEFAULT) {
					// do not override stickers
					renderingType = FileData.RENDERING_MEDIA;
				}
				break;
			case TYPE_FILE:
				// "regular" file messages
				renderingType = FileData.RENDERING_DEFAULT;
				break;
			case TYPE_VIDEO:
				if (renderingType == FileData.RENDERING_MEDIA) {
					// videos in formats other than MP4 are always transcoded and result in an MP4 file
					mimeType = MimeUtil.MIME_TYPE_VIDEO_MP4;
				}
				// fallthrough
			default:
				if (mediaItem.getImageScale() == PreferenceService.ImageScale_SEND_AS_FILE || mediaItem.getVideoSize() == PreferenceService.VideoSize_SEND_AS_FILE) {
					// images with scale type "send as file" get the default rendering type and a file name
					renderingType = FileData.RENDERING_DEFAULT;
					mediaItem.setType(TYPE_FILE);
				} else {
					// unlike with "real" files we override the filename for regular (RENDERING_MEDIA) images and videos with a generic one to prevent privacy leaks
					// this mimics the behavior of traditional image messages that did not have a filename at all
					filename = FileUtil.getDefaultFilename(mimeType);
				}
				break;
		}

		if (TestUtil.isEmptyOrNull(filename)) {
			filename = FileUtil.getDefaultFilename(mimeType);
		}

		String caption = mediaItem.getTrimmedCaption();
		if (caption != null && caption.isBlank()) {
			caption = null;
		}

		return new FileDataModel(mimeType,
			null,
			0,
			filename,
			renderingType,
			caption,
			true,
			null);
	}

	/**
	 * Transcode and trim this video according to the parameters set in the MediaItem object
	 * @return Result of transcoding
	 */
	@WorkerThread
	private @VideoTranscoder.TranscoderResult int transcodeVideo(MediaItem mediaItem, MessageReceiver[] resolvedReceivers, Map<MessageReceiver, AbstractMessageModel> messageModels) {
		final MessagePlayerService messagePlayerService;
		try {
			messagePlayerService = ThreemaApplication.requireServiceManager().getMessagePlayerService();
		} catch (ThreemaException e) {
			logger.error("Exception", e);
			return VideoTranscoder.FAILURE;
		}

		int targetBitrate;
		@PreferenceService.VideoSize int desiredVideoSize = preferenceService.getVideoSize();
		if (mediaItem.getVideoSize() != PreferenceService.VideoSize_DEFAULT) {
			desiredVideoSize = mediaItem.getVideoSize();
		}

		try {
			targetBitrate = VideoConfig.getTargetVideoBitrate(context, mediaItem, desiredVideoSize);
		} catch (ThreemaException e) {
			logger.error("Error getting target bitrate", e);
			// skip this MediaItem
			markAsTerminallyFailed(resolvedReceivers, messageModels);
			return VideoTranscoder.FAILURE;
		}

		if (targetBitrate == -1) {
			// will not fit
			logger.info("Video file ist too large");
			RuntimeUtil.runOnUiThread(() -> Toast.makeText(context, context.getString(R.string.file_too_large, MAX_BLOB_SIZE_MB), Toast.LENGTH_SHORT).show());
			// skip this MediaItem
			markAsTerminallyFailed(resolvedReceivers, messageModels);
			return VideoTranscoder.FAILURE;
		}

		logger.info("Target bitrate = {}", targetBitrate);

		if (mediaItem.hasChanges() ||
			targetBitrate > 0 ||
			!MimeUtil.MIME_TYPE_VIDEO_MP4.equalsIgnoreCase(mediaItem.getMimeType())) {

			logger.info("Video needs transcoding");

			// set models to TRANSCODING state
			for (Map.Entry<MessageReceiver, AbstractMessageModel> entry : messageModels.entrySet()) {
				AbstractMessageModel messageModel = entry.getValue();
				messageModel.setState(MessageState.TRANSCODING);
				save(messageModel);
				fireOnModifiedMessage(messageModel);
			}

			File outputFile;
			try {
				outputFile = fileService.createTempFile(".trans", ".mp4", false);
			} catch (IOException e) {
				logger.error("Unable to open temp file");
				// skip this MediaItem
				markAsTerminallyFailed(resolvedReceivers, messageModels);
				return VideoTranscoder.FAILURE;
			}

			final VideoTranscoder.Builder transcoderBuilder = new VideoTranscoder.Builder(mediaItem.getUri(), outputFile);
			transcoderBuilder.includeAudio(!mediaItem.isMuted());

			if (mediaItem.needsTrimming()) {
				transcoderBuilder.trim(mediaItem.getStartTimeMs(), mediaItem.getEndTimeMs());
			}

			if (targetBitrate > 0) {
				int maxSize = VideoConfig.getMaxSizeFromBitrate(targetBitrate);
				transcoderBuilder.maxFrameHeight(maxSize);
				transcoderBuilder.maxFrameWidth(maxSize);
				transcoderBuilder.videoBitRate(targetBitrate);
				transcoderBuilder.iFrameInterval(2);
				transcoderBuilder.frameRate(25); // TODO: variable frame rate
			}

			final VideoTranscoder videoTranscoder = transcoderBuilder.build(context);

			synchronized (videoTranscoders) {
				for (Map.Entry<MessageReceiver, AbstractMessageModel> entry : messageModels.entrySet()) {
					AbstractMessageModel messageModel = entry.getValue();
					String key = cancelTranscoding(messageModel);
					videoTranscoders.put(key, new WeakReference<>(videoTranscoder));
				}
			}

			final @VideoTranscoder.TranscoderResult int transcoderResult = videoTranscoder.startSync(new VideoTranscoder.Listener() {
				@Override
				public void onStart() {
					for (Map.Entry<MessageReceiver, AbstractMessageModel> entry : messageModels.entrySet()) {
						AbstractMessageModel messageModel = entry.getValue();
						messagePlayerService.setTranscodeStart(messageModel);
					}
				}

				@Override
				public void onProgress(int progress) {
					for (Map.Entry<MessageReceiver, AbstractMessageModel> entry : messageModels.entrySet()) {
						AbstractMessageModel messageModel = entry.getValue();
						messagePlayerService.setTranscodeProgress(messageModel, progress);
					}
				}

				@Override
				public void onCanceled() {
					for (Map.Entry<MessageReceiver, AbstractMessageModel> entry : messageModels.entrySet()) {
						AbstractMessageModel messageModel = entry.getValue();
						messagePlayerService.setTranscodeFinished(messageModel, true, null);
					}
				}

				@Override
				public void onSuccess(VideoTranscoder.Stats stats) {
					if (stats != null) {
						logger.debug(stats.toString());
					}
					for (Map.Entry<MessageReceiver, AbstractMessageModel> entry : messageModels.entrySet()) {
						AbstractMessageModel messageModel = entry.getValue();
						messagePlayerService.setTranscodeFinished(messageModel, true, null);
					}
				}

				@Override
				public void onFailure() {
					for (Map.Entry<MessageReceiver, AbstractMessageModel> entry : messageModels.entrySet()) {
						AbstractMessageModel messageModel = entry.getValue();
						messagePlayerService.setTranscodeFinished(messageModel, false, "Failure");
					}
				}
			});

			if (transcoderResult != VideoTranscoder.SUCCESS) {
				// failure
				logger.info("Transcoding failure");
				return transcoderResult;
			}

			if (videoTranscoder.hasAudioTranscodingError()) {
				final int errorMessageResource;
				if(videoTranscoder.audioFormatUnsupported()) {
					errorMessageResource = R.string.transcoder_unsupported_audio_format;
				} else {
					errorMessageResource = R.string.transcoder_unknown_audio_error;
				}

				RuntimeUtil.runOnUiThread(() -> Toast.makeText(
					ThreemaApplication.getAppContext(),
					context.getString(errorMessageResource),
					Toast.LENGTH_LONG
				).show());
			}

			// remove original file and set transcoded file as new source file
			deleteTemporaryFile(mediaItem);
			mediaItem.setUri(Uri.fromFile(outputFile));
			mediaItem.setMimeType(MimeUtil.MIME_TYPE_VIDEO_MP4);
		} else {
			logger.info("No transcoding necessary");
		}
		return VideoTranscoder.SUCCESS;
	}

	/**
	 * Generate a random correlation ID that identifies all media sent in one batch
	 * @return correlation Id
	 */
	@Override
	public String getCorrelationId() {
		final byte[] random = new byte[16];
		new SecureRandom().nextBytes(random);
		return Utils.byteArrayToHexString(random);
	}

	@WorkerThread
	private void deleteTemporaryFile(MediaItem mediaItem) {
		if (mediaItem.getDeleteAfterUse()) {
			if (mediaItem.getUri() != null && ContentResolver.SCHEME_FILE.equalsIgnoreCase(mediaItem.getUri().getScheme())) {
				if (mediaItem.getUri().getPath() != null) {
					FileUtil.deleteFileOrWarn(mediaItem.getUri().getPath(), null, logger);
				}
			}
		}
	}

	/**
	 * Check if all chats in the supplied list of MessageReceivers are set to "hidden"
	 * @return true if all chats are hidden (i.e. marked as "private"), false if there is at least one chat that is always visible
	 */
	private boolean allChatsArePrivate(MessageReceiver[] messageReceivers) {
		for (MessageReceiver messageReceiver : messageReceivers) {
			if (!hiddenChatsListService.has(messageReceiver.getUniqueIdString())) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Delete message models for specified receivers
	 */
	private void markAsTerminallyFailed(
		MessageReceiver<AbstractMessageModel>[] resolvedReceivers,
		Map<MessageReceiver, AbstractMessageModel> messageModels
	) {
		for (MessageReceiver messageReceiver : resolvedReceivers) {
			remove(messageModels.get(messageReceiver));
		}
	}

	/**
	 * Get a byte array for the media represented by the MediaItem leaving room for NaCl Box header
	 * @param mediaItem MediaItem containing the Uri of the media
	 * @return byte array of the media data or null if error occured
	 */
	@WorkerThread
	private byte[] getContentData(MediaItem mediaItem) {
		try (InputStream inputStream = getFromUri(context, mediaItem.getUri())) {
			if (inputStream != null) {
 				int fileLength = inputStream.available();

				if (fileLength > MAX_BLOB_SIZE) {
					String errorMessage = context.getString(R.string.file_too_large, MAX_BLOB_SIZE_MB);
					logger.info(errorMessage);
					RuntimeUtil.runOnUiThread(() -> Toast.makeText(ThreemaApplication.getAppContext(), errorMessage, Toast.LENGTH_LONG).show());
					return null;
				}

				if (fileLength == 0) {
					// InputStream may not provide size
					fileLength = MAX_BLOB_SIZE + 1;
				}

				if (ConfigUtils.checkAvailableMemory(fileLength + NaCl.BOXOVERHEAD)) {
					byte[] fileData = new byte[fileLength + NaCl.BOXOVERHEAD];

					try {
						int readCount = 0;
						try {
							readCount = IOUtils.read(inputStream, fileData, NaCl.BOXOVERHEAD, fileLength);
						} catch (Exception e) {
							// it's OK to get an EOF
						}

						if (readCount > MAX_BLOB_SIZE) {
							String errorMessage = context.getString(R.string.file_too_large, MAX_BLOB_SIZE_MB);
							logger.info(errorMessage);
							RuntimeUtil.runOnUiThread(() -> Toast.makeText(ThreemaApplication.getAppContext(), errorMessage, Toast.LENGTH_LONG).show());
							return null;
						}

						if (readCount < fileLength) {
							return Arrays.copyOf(fileData, readCount + NaCl.BOXOVERHEAD);
						}

						return fileData;
					} catch (OutOfMemoryError e) {
						logger.error("Unable to create byte array", e);
					}
				} else {
					logger.info("Not enough memory to create byte array.");
				}
			} else {
				logger.info("Not enough memory to create byte array.");
			}
		} catch (IOException e) {
			logger.error("Unable to open file to send", e);
		}
		return null;
	}

	/**
	 * Save outgoing media item recorded from within the app to gallery if enabled
	 */
	@WorkerThread
	private void saveToGallery(MediaItem item) {
		if (item.getType() == MediaItem.TYPE_IMAGE_CAM || item.getType() == MediaItem.TYPE_VIDEO_CAM) {
			if (preferenceService.isSaveMedia()) {
				try {
					AbstractMessageModel messageModel = new MessageModel();
					messageModel.setType(item.getType() == TYPE_VIDEO_CAM ? MessageType.VIDEO : MessageType.IMAGE);
					messageModel.setCreatedAt(new Date());
					messageModel.setId(0);

					fileService.copyDecryptedFileIntoGallery(item.getUri(), messageModel);
				} catch (Exception e) {
					logger.error("Exception", e);
				}
			}
		}
	}

	/**
	 *
	 * @param message the text message user input
	 * @return trimmed message
	 */
	private String validateTextMessage(@NonNull String message) throws ThreemaException {
		// Strip leading/trailing whitespace and throw if nothing is left
		String trimmedMessage = message.trim();

		if (trimmedMessage.isEmpty()) {
			throw new ThreemaException("Tried to send empty message");
		}

		// Check maximum length in UTF-8 bytes (can be reached quickly with Unicode emojis etc.)
		if (message.getBytes(StandardCharsets.UTF_8).length > ProtocolDefines.MAX_TEXT_MESSAGE_LEN) {
			throw new MessageTooLongException();
		}

		return trimmedMessage;
	}
}
