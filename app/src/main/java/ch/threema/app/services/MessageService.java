/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2021 Threema GmbH
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

import android.content.Context;
import android.location.Location;
import android.net.Uri;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.app.activities.RecipientListBaseActivity;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.ui.MediaItem;
import ch.threema.base.ThreemaException;
import ch.threema.client.AbstractGroupMessage;
import ch.threema.client.AbstractMessage;
import ch.threema.client.MessageId;
import ch.threema.client.MessageTooLongException;
import ch.threema.client.ProgressListener;
import ch.threema.localcrypto.MasterKey;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.ServerMessageModel;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.data.MessageContentsType;
import ch.threema.storage.models.data.status.VoipStatusDataModel;

/**
 * Handling methods for messages
 */
public interface MessageService {

	interface CompletionHandler {
		void sendComplete(AbstractMessageModel messageModel);
		void sendQueued(AbstractMessageModel messageModel);
		void sendError(int reason);
	}

	interface MessageFilter {
		/**
		 * Max number of messages that are returned with a response
		 */
		long getPageSize();

		/**
		 * If this returns a non-null value, then only messages with a message id smaller than the
		 * reference id will be returned.
		 */
		Integer getPageReferenceId();

		boolean withStatusMessages();
		boolean withUnsaved();
		boolean onlyUnread();
		boolean onlyDownloaded();
		MessageType[] types();
		@MessageContentsType int[] contentTypes();
	}

	public class MessageString {
		String message;
		String rawMessage;

		public MessageString(String message) {
			this.message = message;
			this.rawMessage = message;
		}

		MessageString(String message, String rawMessage) {
			this.message = message;
			this.rawMessage = rawMessage;
		}

		public String getMessage() {
			return message;
		}

		public String getRawMessage() {
			return rawMessage;
		}
	}

	/**
	 *
	 * @deprecated use createStatusMessage new style
	 */
	@Deprecated
	AbstractMessageModel createStatusMessage(String statusMessage, MessageReceiver receiver);
	AbstractMessageModel createVoipStatus(VoipStatusDataModel data,
	                                         MessageReceiver receiver,
	                                         boolean isOutbox,
	                                         boolean isRead);
	AbstractMessageModel sendText(String message, MessageReceiver receiver) throws Exception;
	AbstractMessageModel sendLocation(Location location, String poiName, MessageReceiver receiver, CompletionHandler completionHandler) throws ThreemaException, IOException;

	String getCorrelationId();

	@AnyThread
	void sendMediaAsync(@NonNull List<MediaItem> mediaItems, @NonNull List<MessageReceiver> messageReceivers);
	@AnyThread
	void sendMediaAsync(@NonNull List<MediaItem> mediaItems, @NonNull List<MessageReceiver> messageReceivers, @Nullable MessageServiceImpl.SendResultListener sendResultListener);
	@WorkerThread
	AbstractMessageModel sendMedia(@NonNull List<MediaItem> mediaItems, @NonNull List<MessageReceiver> messageReceivers, @Nullable MessageServiceImpl.SendResultListener sendResultListener);

	boolean sendUserAcknowledgement(AbstractMessageModel messageModel);
	boolean sendUserDecline(AbstractMessageModel messageModel);

	boolean sendProfilePicture(MessageReceiver[] messageReceivers);

	void resendMessage(AbstractMessageModel messageModel, MessageReceiver receiver, CompletionHandler completionHandler) throws Exception;

	AbstractMessageModel sendBallotMessage(BallotModel ballotModel) throws MessageTooLongException;
	void updateMessageState(final MessageId apiMessageId, final String identity, MessageState state, Date stateDate);
	void updateMessageStateAtOutboxed(final MessageId apiMessageId, MessageState state, Date stateDate);
	boolean markAsRead(AbstractMessageModel message, boolean silent) throws ThreemaException;
	void remove(AbstractMessageModel messageModel);

	/**
	 * if silent is true, no event will be fired on delete
	 */
	void remove(AbstractMessageModel messageModel, boolean silent);

	boolean processIncomingContactMessage(AbstractMessage message) throws Exception;
	boolean processIncomingGroupMessage(AbstractGroupMessage message) throws Exception;

	List<AbstractMessageModel> getMessagesForReceiver(MessageReceiver receiver, MessageFilter messageFilter, boolean appendUnreadMessage);
	List<AbstractMessageModel> getMessagesForReceiver(MessageReceiver receiver, MessageFilter messageFilter);
	List<AbstractMessageModel> getMessagesForReceiver(MessageReceiver receiver);
	List<AbstractMessageModel> getMessageForBallot(BallotModel ballotModel);
	List<AbstractMessageModel> getContactMessagesForText(String query);
	List<AbstractMessageModel> getGroupMessagesForText(String query);

	MessageModel getContactMessageModel(final Integer id, boolean lazy);
	GroupMessageModel getGroupMessageModel(final Integer id, boolean lazy);
	DistributionListMessageModel getDistributionListMessageModel(final Integer id, boolean lazy);

	MessageString getMessageString(AbstractMessageModel messageModel, int maxLength);
	MessageString getMessageString(AbstractMessageModel messageModel, int maxLength, boolean withPrefix);

	void saveIncomingServerMessage(ServerMessageModel msg);

	boolean downloadMediaMessage(AbstractMessageModel mediaMessageModel, ProgressListener progressListener) throws Exception;
	boolean cancelMessageDownload(AbstractMessageModel messageModel);
	void cancelMessageUpload(AbstractMessageModel messageModel);

	void saveMessageQueueAsync();
	void saveMessageQueue(@NonNull MasterKey masterKey);

	void removeAll() throws SQLException, IOException, ThreemaException;
	void save(AbstractMessageModel messageModel);

	void markConversationAsRead(MessageReceiver messageReceiver, NotificationService notificationService);
	void markMessageAsRead(AbstractMessageModel abstractMessageModel, NotificationService notificationService);

	/**
	 * count all message records (normal, group and distribution lists)
	 */
	long getTotalMessageCount();

	boolean shareMediaMessages(Context context, ArrayList<AbstractMessageModel> models, ArrayList<Uri> shareFileUris);
	boolean viewMediaMessage(Context context, AbstractMessageModel model, Uri uri);
	boolean shareTextMessage(Context context, AbstractMessageModel model);
	AbstractMessageModel getMessageModelFromId(int id, String type);
	AbstractMessageModel getMessageModelByApiMessageId(String id, @MessageReceiver.MessageReceiverType int type);

	void cancelVideoTranscoding(AbstractMessageModel messageModel);

	/**
	 * Create a message receiver for the specified message model
	 * @param messageModel AbstractMessageModel to create a receiver for
	 * @return MessageReceiver
	 * @throws ThreemaException in case no MessageReceiver could be created or the AbstractMessageModel is none of the three possible message types
	 */
	MessageReceiver getMessageReceiver(AbstractMessageModel messageModel) throws ThreemaException;
}
