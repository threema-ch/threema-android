/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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
import android.content.pm.ShortcutInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import androidx.core.app.Person;
import ch.threema.app.emojis.EmojiMarkupUtil;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.ServerMessageModel;

public interface NotificationService {
	Logger logger = LoggerFactory.getLogger(NotificationService.class);

	String NOTIFICATION_CHANNEL_PASSPHRASE = "ps";
	String NOTIFICATION_CHANNEL_WEBCLIENT =  "wc";
	String NOTIFICATION_CHANNEL_CHAT =  "cc";
	String NOTIFICATION_CHANNEL_CALL =  "ca";
	String NOTIFICATION_CHANNEL_IN_CALL =  "ic";
	String NOTIFICATION_CHANNEL_ALERT =  "al";
	String NOTIFICATION_CHANNEL_NOTICE =  "no";
	String NOTIFICATION_CHANNEL_WORK_SYNC =  "ws";
	String NOTIFICATION_CHANNEL_BACKUP_RESTORE_IN_PROGRESS =  "bk";
	String NOTIFICATION_CHANNEL_CHAT_UPDATE =  "cu";
	String NOTIFICATION_CHANNEL_NEW_SYNCED_CONTACTS = "nc";

	String NOTIFICATION_CHANNELGROUP_CHAT = "group";
	String NOTIFICATION_CHANNELGROUP_VOIP = "vgroup";
	String NOTIFICATION_CHANNELGROUP_CHAT_UPDATE = "ugroup";

	String NOTIFICATION_CHANNEL_CHAT_ID_PREFIX = "ch";
	String NOTIFICATION_CHANNEL_VOIP_ID_PREFIX = "voip";
	String NOTIFICATION_CHANNEL_CHAT_UPDATE_ID_PREFIX = "chu";

	interface NotificationSchema {
		boolean vibrate();
		int getRingerMode();
		Uri getSoundUri();
		int getColor();
	}

	interface FetchBitmap {
		Bitmap fetch();
	}

	class ConversationNotificationGroup {
		private String name, shortName;
		private final String groupUid;
		private final FetchBitmap fetchAvatar;
		private final MessageReceiver messageReceiver;
		private final String lookupUri;

		//reference to conversations
		protected List<ConversationNotification> conversations = new ArrayList<>();

		public ConversationNotificationGroup(String groupUid, String name, String shortName, MessageReceiver receiver, FetchBitmap fetchAvatar, String lookupUri) {
			this.groupUid = groupUid;
			this.name = name;
			this.shortName = shortName;
			this.fetchAvatar = fetchAvatar;
			this.messageReceiver = receiver;
			this.lookupUri = lookupUri;
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

		public String getLookupUri() {
			return lookupUri;
		}

		public void destroy() {
			//do not recycle
			/*synchronized (this.conversations) {
				if (this.conversations.size() == 0 && this.avatar != null) {
					logger.debug("destroy ConversationNotificationGroup");
					BitmapUtil.recycle(this.avatar);
				}
				else {
					logger.debug("do not destroy ConversationNotification (conversationCount = " + this.conversations.size()+ ")");
				}
			}*/
		}
	}

	class ConversationNotification {
		private CharSequence message;
		private CharSequence rawMessage;
		private Person senderPerson;
		private final Date when;
		private final String uid;
		private final ConversationNotificationGroup group;
		private final FetchBitmap fetchThumbnail;
		private final int id;
		private Bitmap thumbnail = null;
		private ShortcutInfo shortcutInfo;
		private MessageType messageType;
		private EmojiMarkupUtil emojiMarkupUtil;

		public ConversationNotification(MessageService.MessageString messageString, Date when, int id, String uid,
		                                ConversationNotificationGroup group, FetchBitmap fetchThumbnail,
		                                ShortcutInfo shortcutInfo, Person senderPerson, MessageType messageType) {
			this.when = when;
			this.uid = uid;
			this.id = id;
			this.group = group;
			this.fetchThumbnail = fetchThumbnail;
			this.shortcutInfo = shortcutInfo;
			this.emojiMarkupUtil = EmojiMarkupUtil.getInstance();
			this.messageType = messageType;
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

		public ShortcutInfo getShortcutInfo(){
			return shortcutInfo;
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

		public Bitmap getThumbnail() {
			if(this.thumbnail == null && this.fetchThumbnail != null) {
				this.thumbnail = this.fetchThumbnail.fetch();
			}
			return this.thumbnail;
		}

		public MessageType getMessageType() {
			return this.messageType;
		}

		public void destroy() {
			if(this.thumbnail != null) {
				logger.debug("destroy ConversationNotification");
				BitmapUtil.recycle(this.thumbnail);
				this.group.conversations.remove(this);
				this.group.destroy();
			}
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

	/**
	 * add a new conversation notification
	 * @param conversationNotification
	 */
	void addConversationNotification(ConversationNotification conversationNotification, boolean updateExisting);

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

	void updateConversationNotifications();

	void cancel(ConversationModel conversationModel);
	void cancel(MessageReceiver receiver);
	void cancel(int notificationId);

	void showMasterKeyLockedNewMessageNotification();
	void showPinLockedNewMessageNotification(NotificationSchema notificationSchema, String uid, boolean quiet);

	void showNetworkBlockedNotification(boolean noisy);
	void cancelNetworkBlockedNotification();

	void showServerMessage(ServerMessageModel m);
	void cancelServerMessage();

	/**
	 * show the "not enough disk space"
	 * @param availableSpace
	 * @param requiredSpace
	 */
	void showNotEnoughDiskSpace(long availableSpace, long requiredSpace);

	void showUnsentMessageNotification(ArrayList<AbstractMessageModel> failedMessages);

	void showSafeBackupFailed(int numDays);

	void showWorkSyncProgress();
	void cancelWorkSyncProgress();

	void showNewSyncedContactsNotification(List<ContactModel> contactModels);

	void showWebclientResumeFailed(String msg);
	void cancelRestartNotification();
	void resetConversationNotifications();
}
