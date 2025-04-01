/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

package ch.threema.app.services.notification;

import android.annotation.TargetApi;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.util.Date;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.Person;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.emojis.EmojiMarkupUtil;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.MessageService;
import ch.threema.app.utils.TestUtil;
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

    interface FetchCacheUri {
        @Nullable
        Uri fetch();
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
            if (!TestUtil.isEmptyOrNull(message)) {
                this.message = emojiMarkupUtil.addTextSpans(message);
            } else {
                this.message = "";
            }
        }

        private void setRawMessage(String rawMessage) {
            if (!TestUtil.isEmptyOrNull(rawMessage)) {
                this.rawMessage = rawMessage;
            } else {
                this.rawMessage = "";
            }
        }

        public Person getSenderPerson() {
            return this.senderPerson;
        }

        public void setSenderPerson(Person person) {
            if (!TestUtil.isBlankOrNull(message)) {
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
            if (this.thumbnailUri == null && this.fetchThumbnailUri != null) {
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
     */
    void showConversationNotification(ConversationNotification conversationNotification, boolean updateExisting);

    /**
     * cancel a conversation notification
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

    void showPinLockedNewMessageNotification(NotificationSchema notificationSchema, String uid, String channelId);

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

    /**
     * Shows a notification that the safe backup has failed for the provided number of days. Note
     * that this method should only be called if safe backups are enabled and the number of days
     * that the backup has failed is at least one. Otherwise the notification may not make sense.
     *
     * @param numDays the number of days where a safe backup failed
     */
    void showSafeBackupFailed(int numDays);

    void cancelSafeBackupFailed();

    void cancelWorkSyncProgress();

    void showNewSyncedContactsNotification(List<ch.threema.data.models.ContactModel> contactModels);

    void showWebclientResumeFailed(String msg);

    void cancelRestartNotification();

    void cancelRestoreNotification();

    void showGroupJoinResponseNotification(@NonNull OutgoingGroupJoinRequestModel outgoingGroupJoinRequestModel,
                                           @NonNull OutgoingGroupJoinRequestModel.Status status,
                                           @NonNull DatabaseServiceNew databaseService);

    void showGroupJoinRequestNotification(@NonNull IncomingGroupJoinRequestModel incomingGroupJoinRequestModel, GroupModel groupModel);
}
