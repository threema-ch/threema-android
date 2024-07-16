/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2024 Threema GmbH
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

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.Person;

import org.slf4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ch.threema.app.ThreemaApplication;
import ch.threema.app.emojis.EmojiMarkupUtil;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.ServerMessageModel;
import ch.threema.storage.models.group.IncomingGroupJoinRequestModel;
import ch.threema.storage.models.group.OutgoingGroupJoinRequestModel;

public interface NotificationService {
	Logger logger = LoggingUtil.getThreemaLogger("NotificationService");

	String NOTIFICATION_CHANNEL_PASSPHRASE = "ps";
	String NOTIFICATION_CHANNEL_WEBCLIENT =  "wc";
	String NOTIFICATION_CHANNEL_CHAT =  "cc"; // virtual notification channel used by wrapper
	String NOTIFICATION_CHANNEL_CALL =  "ca"; // virtual notification channel used by wrapper
	String NOTIFICATION_CHANNEL_IN_CALL =  "ic";
	String NOTIFICATION_CHANNEL_ALERT =  "al";
	String NOTIFICATION_CHANNEL_NOTICE =  "no";
	String NOTIFICATION_CHANNEL_WORK_SYNC =  "ws";
	String NOTIFICATION_CHANNEL_IDENTITY_SYNC =  "is"; // TODO: reference to this channel may be removed after Sep. 2024
	String NOTIFICATION_CHANNEL_BACKUP_RESTORE_IN_PROGRESS =  "bk";
	String NOTIFICATION_CHANNEL_CHAT_UPDATE =  "cu"; // virtual notification channel used by wrapper
	String NOTIFICATION_CHANNEL_NEW_SYNCED_CONTACTS = "nc";
	String NOTIFICATION_CHANNEL_GROUP_JOIN_RESPONSE = "jres";
	String NOTIFICATION_CHANNEL_GROUP_JOIN_REQUEST = "jreq";
	String NOTIFICATION_CHANNEL_THREEMA_PUSH = "tpush";
	String NOTIFICATION_CHANNEL_GROUP_CALL = "gcall"; // virtual notification channel used by wrapper
	String NOTIFICATION_CHANNEL_VOICE_MSG_PLAYER = "vmp";
	String NOTIFICATION_CHANNEL_FORWARD_SECURITY = "fs";

	String NOTIFICATION_CHANNELGROUP_CHAT = "group";
	String NOTIFICATION_CHANNELGROUP_VOIP = "vgroup";
	String NOTIFICATION_CHANNELGROUP_CHAT_UPDATE = "ugroup";
	String NOTIFICATION_CHANNELGROUP_GROUP_CALLS = "group_calls";

	String NOTIFICATION_CHANNEL_CHAT_ID_PREFIX = "ch";
	String NOTIFICATION_CHANNEL_VOIP_ID_PREFIX = "voip";
	String NOTIFICATION_CHANNEL_CHAT_UPDATE_ID_PREFIX = "chu";
	String NOTIFICATION_CHANNEL_GROUP_CALLS_ID_PREFIX = "gc";

	interface NotificationSchema {
		boolean vibrate();
		int getRingerMode();
		Uri getSoundUri();
		int getColor();
	}

	interface FetchBitmap {
		Bitmap fetch();
	}

	interface FetchCacheUri {
		@Nullable Uri fetch();
	}

	class ConversationNotificationGroup {
		private String name, shortName;
		private final String groupUid;
		private final FetchBitmap fetchAvatar;
		private final MessageReceiver messageReceiver;
		private long lastNotificationDate = 0;

		//reference to conversations
		protected List<ConversationNotification> conversations = new ArrayList<>();

		public ConversationNotificationGroup(String groupUid, String name, String shortName, MessageReceiver receiver, FetchBitmap fetchAvatar) {
			this.groupUid = groupUid;
			this.name = name;
			this.shortName = shortName;
			this.fetchAvatar = fetchAvatar;
			this.messageReceiver = receiver;
		}

		public String getGroupUid(){
			return this.groupUid;
		}

		public int getNotificationId() {
			return this.messageReceiver.getUniqueId();
		}

		public String getName(){
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getShortName(){
			return this.shortName;
		}

		public void setShortName(String shortName) {
			this.shortName = shortName;
		}

		public MessageReceiver getMessageReceiver() {
			return this.messageReceiver;
		}

		public Bitmap getAvatar() {
			return this.fetchAvatar.fetch();
		}

		public void setLastNotificationDate(long lastNotificationDate) {
			this.lastNotificationDate = lastNotificationDate;
		}

		public long getLastNotificationDate() {
			return this.lastNotificationDate;
		}
	}

	class ConversationNotification {
		private CharSequence message;
		private CharSequence rawMessage;
		private Person senderPerson;
		private final Date when;
		private final String uid;
		private final ConversationNotificationGroup group;
		private final FetchCacheUri fetchThumbnailUri;
		private final int id;
		private Uri thumbnailUri = null;
		private final String thumbnailMimeType;
		private final MessageType messageType;
		private final EmojiMarkupUtil emojiMarkupUtil;
		private final boolean isMessageDeleted;

		public ConversationNotification(MessageService.MessageString messageString, Date when, int id, String uid,
		                                ConversationNotificationGroup group, FetchCacheUri fetchThumbnailUri, String thumbnailMimeType,
		                                Person senderPerson, MessageType messageType, boolean isMessageDeleted) {
			this.when = when;
			this.uid = uid;
			this.id = id;
			this.group = group;
			this.fetchThumbnailUri = fetchThumbnailUri;
			this.thumbnailMimeType = thumbnailMimeType;
			this.emojiMarkupUtil = EmojiMarkupUtil.getInstance();
			this.messageType = messageType;
			this.isMessageDeleted = isMessageDeleted;
			setMessage(messageString.getMessage());
			setRawMessage(messageString.getRawMessage());
			setSenderPerson(senderPerson);

			this.group.conversations.add(this);
		}

		public CharSequence getMessage() {
			return this.message;
		}

		public CharSequence getRawMessage() {
			return this.rawMessage;
		}

		private void setMessage(String message) {
			if (!TestUtil.empty(message)) {
				this.message = emojiMarkupUtil.addTextSpans(message);
			} else {
				this.message = "";
			}
		}

		private void setRawMessage(String rawMessage) {
			if (!TestUtil.empty(rawMessage)) {
				this.rawMessage = rawMessage;
			} else {
				this.rawMessage = "";
			}
		}

		public Person getSenderPerson() {
			return this.senderPerson;
		}

		public void setSenderPerson(Person person) {
			if (!TestUtil.empty(message)) {
				this.senderPerson = person;
			} else {
				this.senderPerson = null;
			}
		}

		public Date getWhen() {
			return this.when;
		}

		public int getId() {
			return this.id;
		}
		public String getUid() {
			return this.uid;
		}

		public ConversationNotificationGroup getGroup() {
			return this.group;
		}

		@Nullable
		public Uri getThumbnailUri() {
			if(this.thumbnailUri == null && this.fetchThumbnailUri != null) {
				this.thumbnailUri = this.fetchThumbnailUri.fetch();
			}
			return this.thumbnailUri;
		}

		public MessageType getMessageType() {
			return this.messageType;
		}


		public boolean isMessageDeleted() {
			return this.isMessageDeleted;
		}

		public void destroy() {
			if (this.thumbnailUri != null) {
				logger.debug("destroy ConversationNotification");

				File thumbnailFile = new File(ThreemaApplication.getAppContext().getCacheDir(), thumbnailUri.getLastPathSegment());
				if (thumbnailFile.exists()) {
					//noinspection ResultOfMethodCallIgnored
					thumbnailFile.delete();
				}

				this.group.conversations.remove(this);
			}
		}

		@Nullable
		public String getThumbnailMimeType() {
			return thumbnailMimeType;
		}
	}

	@TargetApi(Build.VERSION_CODES.O)
	void deleteNotificationChannels();

	@TargetApi(Build.VERSION_CODES.O)
	void createNotificationChannels();

	/**
	 * Set the identity for which a conversation is currently visible (there can only be
	 * one at any given time). No notifications will created for messages from this identity.
	 *
	 * @param receiver visible conversation identity or group (or null)
	 */
	void setVisibleReceiver(MessageReceiver receiver);

	void addGroupCallNotification(@NonNull GroupModel group, @NonNull ContactModel contactModel);
	void cancelGroupCallNotification(int groupId);

	/**
	 * add a new conversation notification
	 * @param conversationNotification
	 */
	void showConversationNotification(ConversationNotification conversationNotification, boolean updateExisting);

	/**
	 * cancel a conversation notification
	 * @param uids
	 */
	void cancelConversationNotification(String... uids);

	/**
	 * cancel all conversation notifications saved in notificationsService.conversationNotifications synchronously
	 * called when pin lock is called
	 */
	void cancelCachedConversationNotifications();

	/**
	 * cancel all conversation notifications saved in notificationsService.conversationNotifications
	 */
	void cancelAllCachedConversationNotifications();

	/**
	 * cancel all conversation notifications of category Notification.CATEGORY_MESSAGE
	 * returns true if any have been cancelled
	 */
	boolean cancelAllMessageCategoryNotifications();

	/**
	 * handle conversation cancellation upon pin lock depending on sdk version and saved notifications
	 */
	void cancelConversationNotificationsOnLockApp();

	/**
	 * helper that returns true if there are currently held conversations notifications.
	 * called when pin lock is called on SDK < 23 to check if we should show a pinLockedNotification
	 */
	boolean isConversationNotificationVisible();

	void cancel(ConversationModel conversationModel);
	void cancel(MessageReceiver receiver);
	void cancel(int notificationId);
	void cancel(@NonNull String identity);

	void showMasterKeyLockedNewMessageNotification();
	void showPinLockedNewMessageNotification(NotificationSchema notificationSchema, String uid, boolean quiet);

	void showServerMessage(ServerMessageModel m);

	/**
	 * Show a notification that a message could not be sent. Note that this is should not be used
	 * for messages that were rejected because of forward security.
	 *
	 * @param failedMessages the failed message models
	 */
	void showUnsentMessageNotification(@NonNull List<AbstractMessageModel> failedMessages);

	/**
	 * Show a forward security message rejected notification for the given receiver. Note that for
	 * every receiver only one notification is shown. If a notification is already shown, this call
	 * has no effect. The notification remains visible until the user cancels (or clicks) it.
	 */
	void showForwardSecurityMessageRejectedNotification(
		@NonNull MessageReceiver<?> messageReceiver
	);

	void showSafeBackupFailed(int numDays);

	void cancelWorkSyncProgress();

	void showNewSyncedContactsNotification(List<ContactModel> contactModels);

	void showWebclientResumeFailed(String msg);
	void cancelRestartNotification();
	void cancelRestoreNotification();

	void showGroupJoinResponseNotification(@NonNull OutgoingGroupJoinRequestModel outgoingGroupJoinRequestModel,
	                                       @NonNull OutgoingGroupJoinRequestModel.Status status,
	                                       @NonNull DatabaseServiceNew databaseService);

	void showGroupJoinRequestNotification(@NonNull IncomingGroupJoinRequestModel incomingGroupJoinRequestModel, GroupModel groupModel);
}
