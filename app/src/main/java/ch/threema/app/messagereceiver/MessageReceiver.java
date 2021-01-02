/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2020 Threema GmbH
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
import java.util.Date;
import java.util.List;

import androidx.annotation.IntDef;
import ch.threema.app.services.MessageService;
import ch.threema.base.ThreemaException;
import ch.threema.client.ballot.BallotData;
import ch.threema.client.ballot.BallotVote;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.data.MessageContentsType;

public interface MessageReceiver<T extends AbstractMessageModel> {
	int Type_CONTACT = 0;
	int Type_GROUP = 1;
	int Type_DISTRIBUTION_LIST = 2;

	// Receiver model type annotation
	@Retention(RetentionPolicy.SOURCE)
	@IntDef({ Type_CONTACT, Type_GROUP, Type_DISTRIBUTION_LIST })
	@interface MessageReceiverType {}

	/**
	 * result of a encryption
	 */
	interface EncryptResult {
		/**
		 * encrypted data as byte array
		 * @return
		 */
		byte[] getData();

		/**
		 * used encryption key
		 * @return
		 */
		byte[] getKey();

		/**
		 * used encryption nonce
		 * @return
		 */
		byte[] getNonce();

		/**
		 * size of the data
		 * @return
		 */
		int getSize();
	}

	interface OnSendingPermissionDenied {
		void denied(int errorResId);
	}

	/**
	 * all affected message receivers
	 * only useds in a distribution list
	 *
	 * TODO: refactor
	 * @return
	 */
	List<MessageReceiver> getAffectedMessageReceivers();

	/**
	 * create a (unsaved) db model for the given message type
	 * @param type
	 * @param postedAt
	 * @return
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
	 * @param save
	 */
	void saveLocalModel(T save);

	/**
	 * send a text message
	 *
	 * @param text
	 * @param messageModel
	 * @return
	 * @throws ThreemaException
	 */
	boolean createBoxedTextMessage(String text, T messageModel) throws ThreemaException;

	/**
	 * send a location message
	 *
	 * @param lat
	 * @param lng
	 * @param acc
	 * @param poiName
	 * @param messageModel
	 * @return
	 * @throws ThreemaException
	 */
	boolean createBoxedLocationMessage(double lat, double lng, float acc, String poiName, T messageModel) throws ThreemaException;

	/**
	 * send a file message
	 * @param thumbnailBlobId
	 * @param fileBlobId
	 * @param fileResult
	 * @param messageModel
	 * @return
	 * @throws ThreemaException
	 */
	boolean createBoxedFileMessage(byte[] thumbnailBlobId,
	                               byte[] fileBlobId, EncryptResult fileResult,
	                               T messageModel) throws ThreemaException;

	/**
	 * send a ballot (create) message
	 * @param ballotData
	 * @param ballotModel
	 * @param filteredIdentities
	 * @param abstractMessageModel
	 * @return
	 * @throws ThreemaException
	 */
	boolean createBoxedBallotMessage(
			final BallotData ballotData,
			final BallotModel ballotModel,
			final String[] filteredIdentities,
			T abstractMessageModel) throws ThreemaException;

	/**
	 * send a ballot vote message
	 * @param votes
	 * @param ballotModel
	 * @return
	 * @throws ThreemaException
	 */
	boolean createBoxedBallotVoteMessage(BallotVote[] votes, BallotModel ballotModel) throws ThreemaException;

	/**
	 * select and filter (if filter is set) all message models
	 * @param filter
	 * @return
	 * @throws SQLException
	 */
	List<T> loadMessages(MessageService.MessageFilter filter) throws SQLException;

	/**
	 * Count messages for this receiver
	 * @return
	 */
	long getMessagesCount();

	/**
	 * count the unread message
	 * @return
	 */
	long getUnreadMessagesCount();

	/**
	 * get all unread messages
	 * @return a list of unread messages
	 * @throws SQLException
	 */
	List<T> getUnreadMessages() throws SQLException;

	/**
	 * compare
	 * @param o
	 * @return
	 */
	boolean isEqual(MessageReceiver o);

	/**
	 * displaying name in gui
	 * @return
	 */
	String getDisplayName();

	/**
	 * short displaying name in gui
	 * @return
	 */
	String getShortName();

	/**
	 * TODO: move to IntentUtil
	 * @param intent
	 */
	void prepareIntent(Intent intent);

	/**
	 * get the bitmap of the avatar in the notification
	 * @return
	 */
	Bitmap getNotificationAvatar();

	/**
	 * a unique id
	 * @return
	 */
	@Deprecated
	int getUniqueId();

	String getUniqueIdString();

	/**
	 * Encrypt a file. This will encrypt data in place in order to save memory.
	 * @param fileData Content data to encrypt. WILL BE MODIFIED!
	 * @return Encrypted data and meta data
	 */
	EncryptResult encryptFileData(byte[] fileData);

	/**
	 * encrypt a thumbnail file
	 * @param fileData
	 * @param encryptionKey
	 * @return
	 * @throws Exception
	 */
	EncryptResult encryptFileThumbnailData(byte[] fileData, final byte[] encryptionKey) throws Exception;

	/**
	 * check, if the message model belongs to this receiver
	 * @param message
	 * @return
	 */
	boolean isMessageBelongsToMe(AbstractMessageModel message);

	/**
	 * check if media should really be sent to this receiver
	 * notable exceptions:
	 * - distribution lists
	 * - groups without members ("notes")
	 * @return
	 */
	boolean sendMediaData();

	/**
	 * check if we should offer the user a possibility to retry sending in the UI if the message was queued but there was an IO error in the sender thread
	 * @return
	 */
	boolean offerRetry();

	/**
	 * validate sending permission
	 * @param onSendingPermissionDenied
	 * @return
	 */
	boolean validateSendingPermission(OnSendingPermissionDenied onSendingPermissionDenied);

	/**
	 * type of the receiver
	 * @return
	 */
	@MessageReceiverType int getType();

	/**
	 * all receiving identities
	 * @return list of identities
	 */
	String[] getIdentities();

	String[] getIdentities(int requiredFeature);
}
