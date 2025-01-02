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

package ch.threema.app.messagereceiver;

import android.content.Intent;
import android.graphics.Bitmap;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.services.MessageService;
import ch.threema.base.ThreemaException;
import ch.threema.base.crypto.SymmetricEncryptionResult;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.csp.messages.ballot.BallotData;
import ch.threema.domain.protocol.csp.messages.ballot.BallotVote;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.data.MessageContentsType;

public interface MessageReceiver<M extends AbstractMessageModel> {
	int Type_CONTACT = 0;
	int Type_GROUP = 1;
	int Type_DISTRIBUTION_LIST = 2;

	// Receiver model type annotation
	@Retention(RetentionPolicy.SOURCE)
	@IntDef({ Type_CONTACT, Type_GROUP, Type_DISTRIBUTION_LIST })
	@interface MessageReceiverType {}

	interface OnSendingPermissionDenied {
		void denied(int errorResId);
	}

	/**
	 * Return all affected contact message receivers.
	 *
	 * Note: Only used in a distribution list, other subtypes should return null.
	 *
	 * TODO: refactor
	 */
	default @Nullable List<ContactMessageReceiver> getAffectedMessageReceivers() {
		return null;
	}

	/**
	 * create a local (unsaved) db model for the given message type
	 */
	AbstractMessageModel createLocalModel(MessageType type, @MessageContentsType int contentsType, Date postedAt);

	/**
	 * create a db model for the given message type and save it
	 * @deprecated use createAndSaveStatusDataModel instead.
	 */
	@Deprecated
	AbstractMessageModel createAndSaveStatusModel(String statusBody, Date postedAt);

	/**
	 * save a message model to the database
	 */
	void saveLocalModel(M messageModel);

	/**
	 * send a text message
	 */
	void createAndSendTextMessage(@NonNull M messageModel);

	/**
	 * send a location message
	 */
	void createAndSendLocationMessage(@NonNull M messageModel);

	/**
	 * send a file message
	 */
	void createAndSendFileMessage(
		@Nullable byte[] thumbnailBlobId,
		@Nullable byte[] fileBlobId,
		@Nullable SymmetricEncryptionResult encryptionResult,
		@NonNull M messageModel,
		@Nullable MessageId messageId,
		@Nullable Collection<String> recipientIdentities
	) throws ThreemaException;

	/**
	 * send a ballot (create) message
	 */
	void createAndSendBallotSetupMessage(
			final BallotData ballotData,
			final BallotModel ballotModel,
			M abstractMessageModel,
			@Nullable MessageId messageId,
			@Nullable Collection<String> recipientIdentities
	) throws ThreemaException;

	/**
	 * send a ballot vote message
	 */
	void createAndSendBallotVoteMessage(BallotVote[] votes, BallotModel ballotModel) throws ThreemaException;

	/**
	 * select and filter (if filter is set) all message models
	 */
	List<M> loadMessages(MessageService.MessageFilter filter);

	/**
	 * Count messages for this receiver
	 */
	long getMessagesCount();

	/**
	 * count the unread message
	 */
	long getUnreadMessagesCount();

	/**
	 * get all unread messages
	 * @return a list of unread messages
	 */
	List<M> getUnreadMessages() throws SQLException;

	/**
	 * compare
	 */
	boolean isEqual(MessageReceiver o);

	/**
	 * displaying name in gui
	 */
	String getDisplayName();

	/**
	 * short displaying name in gui
	 */
	String getShortName();

	/**
	 * TODO: move to IntentUtil
	 */
	void prepareIntent(Intent intent);

	/**
	 * @return the bitmap of the avatar in the notification
	 */
	Bitmap getNotificationAvatar();

	@Nullable
	Bitmap getAvatar();

	/**
	 * @return a unique id
	 */
	@Deprecated
	int getUniqueId();

	String getUniqueIdString();

	/**
	 * check, if the message model belongs to this receiver
	 */
	boolean isMessageBelongsToMe(AbstractMessageModel message);

	/**
	 * check if media should really be sent to this receiver
	 * notable exceptions:
	 * - distribution lists
	 * - groups without members ("notes")
	 */
	boolean sendMediaData();

	/**
	 * check if we should offer the user a possibility to retry sending in the UI if the message was queued but there was an IO error in the sender thread
	 */
	boolean offerRetry();

	/**
	 * validate sending permission
	 */
    @NonNull
    SendingPermissionValidationResult validateSendingPermission();

	/**
	 * type of the receiver
	 */
	@MessageReceiverType int getType();

	/**
	 * all receiving identities
	 * @return list of identities
	 */
	String[] getIdentities();

	/**
	 * Set the `lastUpdate` field of the specified contact to the current date.
	 * This will also save the model and notify listeners.
	 *
	 * Not that this method only has an effect if it is supported by the implementing receiver.
	 */
	void bumpLastUpdate();
}
