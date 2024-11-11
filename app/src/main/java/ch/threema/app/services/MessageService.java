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

import android.content.Context;
import android.database.sqlite.SQLiteException;
import android.location.Location;
import android.net.Uri;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.notification.NotificationService;
import ch.threema.app.ui.MediaItem;
import ch.threema.app.voip.groupcall.GroupCallDescription;
import ch.threema.base.ProgressListener;
import ch.threema.base.ThreemaException;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.csp.MessageTooLongException;
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.DeliveryReceiptMessage;
import ch.threema.domain.protocol.csp.messages.GroupDeliveryReceiptMessage;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.ServerMessageModel;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.data.DisplayTag;
import ch.threema.storage.models.data.MessageContentsType;
import ch.threema.storage.models.data.status.ForwardSecurityStatusDataModel;
import ch.threema.storage.models.data.status.GroupCallStatusDataModel;
import ch.threema.storage.models.data.status.GroupStatusDataModel;
import ch.threema.storage.models.data.status.VoipStatusDataModel;

/**
 * Handling methods for messages
 */
public interface MessageService {
    int FILTER_CHATS = 1;
    int FILTER_GROUPS = 1 << 1;
    int FILTER_INCLUDE_ARCHIVED = 1 << 2;
    int FILTER_STARRED_ONLY = 1 << 3;

    @IntDef(
        flag = true,
        value = {
            FILTER_CHATS,
            FILTER_GROUPS,
            FILTER_INCLUDE_ARCHIVED,
            FILTER_STARRED_ONLY
        }
    )
    @Retention(RetentionPolicy.SOURCE)
    @interface MessageFilterFlags {
    }

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

        @MessageContentsType
        int[] contentTypes();

        /* Messages can be tagged with a star or other attributes that affect how they are displayed.
        If the implementation returns an array of tags, the result will be filtered to contain only messages that have one or more of the specified tags set.
        If this method returns null, no filtering for display tags will be performed */
        @DisplayTag
        int[] displayTags();
    }

    class MessageString {
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
     * @deprecated use createStatusMessage new style
     */
    @Deprecated
    AbstractMessageModel createStatusMessage(String statusMessage, MessageReceiver receiver);

    AbstractMessageModel createVoipStatus(VoipStatusDataModel data,
                                          MessageReceiver receiver,
                                          boolean isOutbox,
                                          boolean isRead);

    AbstractMessageModel createGroupCallStatus(@NonNull GroupCallStatusDataModel data,
                                               @NonNull MessageReceiver receiver,
                                               @Nullable ContactModel contactModel,
                                               @Nullable GroupCallDescription call,
                                               boolean isOutbox,
                                               Date postedDate);

    AbstractMessageModel createForwardSecurityStatus(
        @NonNull MessageReceiver receiver,
        @ForwardSecurityStatusDataModel.ForwardSecurityStatusType int type,
        int quantity,
        @Nullable String staticText);

    /**
     * Create and save a group status message.
     *
     * @param receiver     the receiver
     * @param type         the type
     * @param identity     the identity that will be included in the message (needed for
     *                     MEMBER_ADDED, MEMBER_LEFT, MEMBER_KICKED, FIRST_VOTE, and RECEIVED_VOTE)
     * @param ballotName   the name of the ballot (needed for FIRST_VOTE, MODIFIED_VOTE,
     *                     RECEIVED_VOTE, and VOTES_COMPLETE)
     * @param newGroupName the new group name (needed for RENAMED)
     * @return the group status message model
     */
    AbstractMessageModel createGroupStatus(
        @NonNull GroupMessageReceiver receiver,
        @NonNull GroupStatusDataModel.GroupStatusType type,
        @Nullable String identity,
        @Nullable String ballotName,
        @Nullable String newGroupName
    );

    AbstractMessageModel sendText(String message, MessageReceiver receiver) throws Exception;

    AbstractMessageModel sendLocation(@NonNull Location location, String poiName, MessageReceiver receiver, CompletionHandler completionHandler) throws ThreemaException;

    /**
     * Edit a message's text, send it to a receiver and save the editet message as described in saveEditedMessageText.
     *
     * @param message original message to edit
     * @param newText new message text
     */
    void sendEditedMessageText(@NonNull AbstractMessageModel message, @NonNull String newText, @NonNull Date editedAt, @NonNull MessageReceiver receiver) throws Exception;

    /**
     * Save the edited text of a message. If editedAt is not null, an edit history entry will be created with the previous text of the message.
     * Note that if editedAt is null, the message will not be marked as edited
     *
     * @param message Message model containing the previous text of the message
     * @param text the new text for this message
     * @param editedAt the date when the message was edited or null
     */
    void saveEditedMessageText(@NonNull AbstractMessageModel message, String text, @Nullable Date editedAt);

    /**
     * Delete a message's content and any related edit history entries
     *
     * @param message original message to delete
     */
    void deleteMessageContentsAndEditHistory(@NonNull AbstractMessageModel message, Date deletedAt);

    String getCorrelationId();

    @AnyThread
    void sendMediaAsync(@NonNull List<MediaItem> mediaItems, @NonNull List<MessageReceiver> messageReceivers);

    @AnyThread
    void sendMediaAsync(@NonNull List<MediaItem> mediaItems, @NonNull List<MessageReceiver> messageReceivers, @Nullable MessageServiceImpl.SendResultListener sendResultListener);

    @AnyThread
    void sendMediaSingleThread(
        @NonNull List<MediaItem> mediaItems,
        @NonNull List<MessageReceiver> messageReceivers
    );

    @WorkerThread
    AbstractMessageModel sendMedia(@NonNull List<MediaItem> mediaItems, @NonNull List<MessageReceiver> messageReceivers, @Nullable MessageServiceImpl.SendResultListener sendResultListener);

    @WorkerThread
    boolean sendUserAcknowledgement(@NonNull AbstractMessageModel messageModel, boolean markAsRead);

    /**
     * Resend the message. Note that this is always triggered by a user interaction and therefore
     * creates a new task.
     *
     * @param messageModel      the message model of the failed message
     * @param receiver          the receiver of the message
     * @param completionHandler the completion handler that is triggered on completion
     */
    void resendMessage(
        @NonNull AbstractMessageModel messageModel,
        @NonNull MessageReceiver<AbstractMessageModel> receiver,
        @Nullable CompletionHandler completionHandler,
        @NonNull Collection<String> recipientIdentities
    ) throws Exception;

    AbstractMessageModel sendBallotMessage(BallotModel ballotModel) throws MessageTooLongException;

    @WorkerThread
    boolean sendUserDecline(@NonNull AbstractMessageModel messageModel, boolean markAsRead);

    void updateMessageState(@NonNull final MessageId apiMessageId, MessageState state, @NonNull DeliveryReceiptMessage stateMessage);

    void updateGroupMessageState(@NonNull final MessageId apiMessageId, @NonNull MessageState state, @NonNull GroupDeliveryReceiptMessage stateMessage);

    /**
     * Update the message state of a contact message. Currently only used for server acks.
     *
     * @param messageId         the message id of the message
     * @param recipientIdentity the recipient of the message
     * @param state             the new state
     * @param stateDate         the date of state change
     */
    boolean updateContactMessageState(
        @NonNull final MessageId messageId,
        @NonNull String recipientIdentity,
        @NonNull MessageState state,
        @Nullable Date stateDate
    );

    /**
     * Update message state of outgoing message. Currently only used for server acks.
     *
     * @param messageModel the message model that should be updated
     * @param state        the mew state
     * @param stateDate    the date of state change
     */
    boolean updateMessageState(@NonNull final AbstractMessageModel messageModel, @NonNull MessageState state, @Nullable Date stateDate);

    boolean markAsRead(AbstractMessageModel message, boolean silent) throws ThreemaException;

    @WorkerThread
    boolean markAsConsumed(AbstractMessageModel message) throws ThreemaException;

    void remove(AbstractMessageModel messageModel);

    /**
     * if silent is true, no event will be fired on delete
     */
    void remove(AbstractMessageModel messageModel, boolean silent);

    /**
     * Delete a message's content and send a delete message to a receiver. Any edit history entries
     * belonging to this message will also be deleted.
     */
    void sendDeleteMessage(@NonNull AbstractMessageModel messageModel, @NonNull MessageReceiver receiver) throws Exception;

    /**
     * Process an incoming contact message. Note that this method must not be used for voip and poll
     * vote messages.
     *
     * @param message the received contact message
     * @return true if processing the message was successful, false if the message should be discarded
     * @throws Exception if processing the message failed
     */
    boolean processIncomingContactMessage(AbstractMessage message) throws Exception;

    /**
     * Process an incoming group message. Note that this method must not be used for group control
     * messages. Additionally, the common group receive steps must be executed before calling this
     * method.
     *
     * @param message the received group message
     * @return true if processing the message was successful, false if the message should be discarded
     * @throws Exception if processing the message failed
     */
    boolean processIncomingGroupMessage(AbstractGroupMessage message) throws Exception;

    @WorkerThread
    List<AbstractMessageModel> getMessagesForReceiver(@NonNull MessageReceiver receiver, MessageFilter messageFilter, boolean appendUnreadMessage);

    @WorkerThread
    List<AbstractMessageModel> getMessagesForReceiver(@NonNull MessageReceiver receiver, MessageFilter messageFilter);

    @WorkerThread
    List<AbstractMessageModel> getMessagesForReceiver(@NonNull MessageReceiver receiver);

    List<AbstractMessageModel> getMessageForBallot(BallotModel ballotModel);

    @Nullable
    MessageModel getContactMessageModel(final Integer id);

    @Nullable
    GroupMessageModel getGroupMessageModel(final Integer id);

    @Nullable
    DistributionListMessageModel getDistributionListMessageModel(long id);

    MessageString getMessageString(AbstractMessageModel messageModel, int maxLength);

    MessageString getMessageString(AbstractMessageModel messageModel, int maxLength, boolean withPrefix);

    void saveIncomingServerMessage(ServerMessageModel msg);

    boolean downloadMediaMessage(AbstractMessageModel mediaMessageModel, ProgressListener progressListener) throws Exception;

    boolean cancelMessageDownload(AbstractMessageModel messageModel);

    void cancelMessageUpload(AbstractMessageModel messageModel);

    /**
     * Get all messages in any chat that match the specified criteria - excluding distribution lists
     *
     * @param queryString   Substring to match or null to match all messages
     * @param filterFlags   @MessageFilterFlags for this query
     * @param sortAscending Date sort order of results. true = oldest messages first, false = newest messages first
     * @return A list of matching message models
     */
    List<AbstractMessageModel> getMessagesForText(String queryString, @MessageService.MessageFilterFlags int filterFlags, boolean sortAscending);


    /**
     * Remove the "star" display tag from all messages
     *
     * @return number of affected messages
     */
    @WorkerThread
    int unstarAllMessages();

    @WorkerThread
    long countStarredMessages() throws SQLiteException;

    void removeAll() throws SQLException, IOException, ThreemaException;

    void save(AbstractMessageModel messageModel);

    void markConversationAsRead(MessageReceiver messageReceiver, NotificationService notificationService);

    /**
     * count all message records (normal, group and distribution lists)
     */
    long getTotalMessageCount();

    boolean shareMediaMessages(Context context, ArrayList<AbstractMessageModel> models, ArrayList<Uri> shareFileUris, String caption);

    boolean viewMediaMessage(Context context, AbstractMessageModel model, Uri uri);

    boolean shareTextMessage(Context context, AbstractMessageModel model);

    AbstractMessageModel getMessageModelFromId(int id, String type);

    @Nullable
    AbstractMessageModel getMessageModelByApiMessageIdAndReceiver(@Nullable String id, @NonNull MessageReceiver messageReceiver);

    void cancelVideoTranscoding(AbstractMessageModel messageModel);

    /**
     * Create a message receiver for the specified message model
     *
     * @param messageModel AbstractMessageModel to create a receiver for
     * @return MessageReceiver
     * @throws ThreemaException in case no MessageReceiver could be created or the AbstractMessageModel is none of the three possible message types
     */
    MessageReceiver getMessageReceiver(AbstractMessageModel messageModel) throws ThreemaException;
}
