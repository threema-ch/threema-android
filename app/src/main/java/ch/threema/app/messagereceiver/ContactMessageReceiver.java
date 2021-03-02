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

package ch.threema.app.messagereceiver;

import android.content.Intent;
import android.graphics.Bitmap;

import com.neilalexander.jnacl.NaCl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import androidx.annotation.Nullable;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.IdListService;
import ch.threema.app.services.MessageService;
import ch.threema.app.stores.IdentityStore;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.client.AbstractMessage;
import ch.threema.client.BlobUploader;
import ch.threema.client.BoxLocationMessage;
import ch.threema.client.BoxTextMessage;
import ch.threema.client.BoxedMessage;
import ch.threema.client.MessageId;
import ch.threema.client.MessageQueue;
import ch.threema.client.ProtocolDefines;
import ch.threema.client.ThreemaFeature;
import ch.threema.client.Utils;
import ch.threema.client.ballot.BallotCreateMessage;
import ch.threema.client.ballot.BallotData;
import ch.threema.client.ballot.BallotId;
import ch.threema.client.ballot.BallotVote;
import ch.threema.client.ballot.BallotVoteMessage;
import ch.threema.client.file.FileData;
import ch.threema.client.file.FileMessage;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.data.MessageContentsType;
import ch.threema.storage.models.data.media.FileDataModel;

public class ContactMessageReceiver implements MessageReceiver<MessageModel> {
	private static final Logger logger = LoggerFactory.getLogger(ContactMessageReceiver.class);
	private static final Logger validationLogger = LoggerFactory.getLogger("Validation");

	private final ContactModel contactModel;
	private final ContactService contactService;
	private Bitmap avatar = null;
	private final DatabaseServiceNew databaseServiceNew;
	private final MessageQueue messageQueue;
	private final IdentityStore identityStore;
	private IdListService blackListIdentityService;

	public ContactMessageReceiver(ContactModel contactModel,
								  ContactService contactService,
								  DatabaseServiceNew databaseServiceNew,
								  MessageQueue messageQueue,
								  IdentityStore identityStore,
								  IdListService blackListIdentityService) {
		this.contactModel = contactModel;
		this.contactService = contactService;
		this.databaseServiceNew = databaseServiceNew;
		this.messageQueue = messageQueue;
		this.identityStore = identityStore;
		this.blackListIdentityService = blackListIdentityService;
	}

	@Override
	public List<MessageReceiver> getAffectedMessageReceivers() {
		return null;
	}

	@Override
	public MessageModel createLocalModel(MessageType type, @MessageContentsType int contentsType, Date postedAt) {
		MessageModel m = new MessageModel();
		m.setType(type);
		m.setMessageContentsType(contentsType);
		m.setPostedAt(postedAt);
		m.setCreatedAt(new Date());
		m.setSaved(false);
		m.setUid(UUID.randomUUID().toString());
		m.setIdentity(this.contactModel.getIdentity());
		return m;
	}

	/**
	 * @deprecated use createAndSaveStatusDataModel instead.
	 */
	@Override
	@Deprecated
	public MessageModel createAndSaveStatusModel(String statusBody, Date postedAt) {
		MessageModel m = new MessageModel(true);
		m.setType(MessageType.TEXT);
		m.setPostedAt(postedAt);
		m.setCreatedAt(new Date());
		m.setSaved(true);
		m.setUid(UUID.randomUUID().toString());
		m.setIdentity(this.contactModel.getIdentity());
		m.setBody(statusBody);

		this.saveLocalModel(m);

		return m;
	}

	@Override
	public void saveLocalModel(MessageModel save) {
		this.databaseServiceNew.getMessageModelFactory().createOrUpdate(save);
	}

	@Override
	public boolean createBoxedTextMessage(String text, MessageModel messageModel) throws ThreemaException {
		BoxTextMessage msg = new BoxTextMessage();
		msg.setText(text);
		msg.setToIdentity(this.contactModel.getIdentity());

		//fix #ANDR-512
		//save model after receiving a new message id
		this.initNewAbstractMessage(messageModel, msg);

		logger.info("Enqueue text message ID {} to {}", msg.getMessageId(), msg.getToIdentity());
		BoxedMessage boxmsg = this.messageQueue.enqueue(msg);
		if (boxmsg != null) {
			messageModel.setIsQueued(true);
			MessageId id = boxmsg.getMessageId();

			logger.info("Outgoing message {} from {} to {} (type {})",
				id,
				boxmsg.getFromIdentity(),
				boxmsg.getToIdentity(),
				Utils.byteToHex((byte) msg.getType(), true, true)
			);
			if (validationLogger.isInfoEnabled()) {
				validationLogger.info("> Nonce: {}", Utils.byteArrayToHexString(boxmsg.getNonce()));
				validationLogger.info("> Data: {}", Utils.byteArrayToHexString(boxmsg.getBox()));
				validationLogger.info("> Public key ({}): {}",
					msg.getToIdentity(), Utils.byteArrayToHexString(this.contactModel.getPublicKey()));
			}

			if(id != null) {
				messageModel.setApiMessageId(id.toString());
				contactService.setIsHidden(msg.getToIdentity(), false);
				contactService.setIsArchived(msg.getToIdentity(), false);
				return true;
			}
		}
		logger.error("createBoxedTextMessage failed");
		return false;
	}

	@Override
	public boolean createBoxedLocationMessage(double lat, double lng, float acc, String poiName, MessageModel messageModel) throws ThreemaException {

		BoxLocationMessage msg = new BoxLocationMessage();
		msg.setLatitude(lat);
		msg.setLongitude(lng);
		msg.setAccuracy(acc);
		msg.setToIdentity(this.contactModel.getIdentity());
		msg.setPoiName(poiName);

		//fix #ANDR-512
		//save model after receiving a new message id
		this.initNewAbstractMessage(messageModel, msg);

		logger.info("Enqueue location message ID {} to {}", msg.getMessageId(), msg.getToIdentity());
		BoxedMessage boxmsg = this.messageQueue.enqueue(msg);

		if (boxmsg != null) {
			messageModel.setIsQueued(true);
			MessageId id = boxmsg.getMessageId();
			if (id!= null) {
				messageModel.setApiMessageId(id.toString());
				contactService.setIsHidden(msg.getToIdentity(), false);
				contactService.setIsArchived(msg.getToIdentity(), false);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean createBoxedFileMessage(byte[] thumbnailBlobId,
											 byte[] fileBlobId, EncryptResult fileResult,
											MessageModel messageModel) throws ThreemaException {
		FileDataModel modelFileData = messageModel.getFileData();
		FileMessage fileMessage = new FileMessage();
		FileData fileData = new FileData();
		fileData
				.setFileBlobId(fileBlobId)
				.setThumbnailBlobId(thumbnailBlobId)
				.setEncryptionKey(fileResult.getKey())
				.setMimeType(modelFileData.getMimeType())
				.setThumbnailMimeType(modelFileData.getThumbnailMimeType())
				.setFileSize(modelFileData.getFileSize())
				.setFileName(modelFileData.getFileName())
				.setRenderingType(modelFileData.getRenderingType())
				.setDescription(modelFileData.getCaption())
				.setCorrelationId(messageModel.getCorrelationId())
				.setMetaData(modelFileData.getMetaData());

		fileMessage.setData(fileData);
		fileMessage.setToIdentity(this.contactModel.getIdentity());

		//fix #ANDR-512
		//save model after receiving a new message id
		this.initNewAbstractMessage(messageModel, fileMessage);

		logger.info("Enqueue file message ID {} to {}",
			fileMessage.getMessageId(), fileMessage.getToIdentity());
		BoxedMessage boxedMessage = this.messageQueue.enqueue(fileMessage);
		if(boxedMessage != null) {
			messageModel.setIsQueued(true);
			MessageId id = boxedMessage.getMessageId();
			if (id!= null) {
				messageModel.setApiMessageId(id.toString());
				contactService.setIsHidden(fileMessage.getToIdentity(), false);
				contactService.setIsArchived(fileMessage.getToIdentity(), false);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean createBoxedBallotMessage(
											BallotData ballotData,
											BallotModel ballotModel,
											final String[] filteredIdentities,
											MessageModel messageModel) throws ThreemaException {

		final BallotId ballotId = new BallotId(Utils.hexStringToByteArray(ballotModel.getApiBallotId()));

		BallotCreateMessage msg = new BallotCreateMessage();
		msg.setToIdentity(this.contactModel.getIdentity());
		msg.setBallotCreator(this.identityStore.getIdentity());
		msg.setBallotId(ballotId);
		msg.setData(ballotData);

		//fix #ANDR-512
		//save model after receiving a new message id
		this.initNewAbstractMessage(messageModel, msg);

		logger.info("Enqueue ballot message ID {} to {}", msg.getMessageId(), msg.getToIdentity());
		BoxedMessage boxedMessage = this.messageQueue.enqueue(msg);
		if(boxedMessage != null) {
			messageModel.setIsQueued(true);
			messageModel.setApiMessageId(boxedMessage.getMessageId().toString());
			contactService.setIsHidden(msg.getToIdentity(), false);
			contactService.setIsArchived(msg.getToIdentity(), false);
			return true;
		}
		return false;
	}

	@Override
	public boolean createBoxedBallotVoteMessage(BallotVote[] votes, BallotModel ballotModel) throws ThreemaException {
		final BallotId ballotId = new BallotId(Utils.hexStringToByteArray(ballotModel.getApiBallotId()));

		switch (ballotModel.getType()) {
			case RESULT_ON_CLOSE:
				//if i am the creator do not send anything
				if(TestUtil.compare(ballotModel.getCreatorIdentity(), this.identityStore.getIdentity())) {
					return true;
				}

				break;
		}

		BallotVoteMessage msg = new BallotVoteMessage();

		msg.setBallotCreator(ballotModel.getCreatorIdentity());
		msg.setBallotId(ballotId);
		msg.setToIdentity(this.getContact().getIdentity());
		for(BallotVote v: votes) {
			msg.getBallotVotes().add(v);
		}

		logger.info("Enqueue ballot vote message ID {} to {}", msg.getMessageId(), msg.getToIdentity());
		BoxedMessage boxedMessage = this.messageQueue.enqueue(msg);
		if (boxedMessage != null) {
			contactService.setIsHidden(msg.getToIdentity(), false);
			contactService.setIsArchived(msg.getToIdentity(), false);
			return true;
		}
		return false;

	}

	@Override
	public List<MessageModel> loadMessages(MessageService.MessageFilter filter) throws SQLException {
		return this.databaseServiceNew.getMessageModelFactory().find(
				this.contactModel.getIdentity(),
				filter);
	}

	@Override
	public long getMessagesCount() {
		return this.databaseServiceNew.getMessageModelFactory().countMessages(
			this.contactModel.getIdentity());
	}

	@Override
	public long getUnreadMessagesCount() {
		return this.databaseServiceNew.getMessageModelFactory().countUnreadMessages(
				this.contactModel.getIdentity());
	}

	@Override
	public List<MessageModel> getUnreadMessages() {
		return this.databaseServiceNew.getMessageModelFactory().getUnreadMessages(
			this.contactModel.getIdentity());
	}

	public MessageModel getLastMessage() {
		return this.databaseServiceNew.getMessageModelFactory().getLastMessage(
				this.contactModel.getIdentity());
	}

	public ContactModel getContact() {
		return this.contactModel;
	}

	@Override
	public boolean isEqual(MessageReceiver o) {
		return o instanceof ContactMessageReceiver && ((ContactMessageReceiver) o).getContact().getIdentity().equals(this.getContact().getIdentity());
	}

	@Override
	public String getDisplayName() {
		return NameUtil.getDisplayNameOrNickname(this.contactModel, true);
	}

	@Override
	public String getShortName() {
		return NameUtil.getShortName(this.contactModel);
	}

	@Override
	public void prepareIntent(Intent intent) {
		intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT, this.contactModel.getIdentity());
	}

	@Override
	@Nullable
	public Bitmap getNotificationAvatar() {
		if(this.avatar == null && this.contactService != null) {
			this.avatar = this.contactService.getAvatar(this.contactModel, false);
		}
		return this.avatar;
	}

	@Deprecated
	@Override
	public int getUniqueId() {
		return this.contactService.getUniqueId(this.contactModel);
	}

	@Override
	public String getUniqueIdString() {
		return this.contactService.getUniqueIdString(this.contactModel);
	}

	@Override
	public EncryptResult encryptFileThumbnailData(byte[] fileThumbnailData, final byte[] encryptionKey) {
		final byte[] thumbnailBoxed = NaCl.symmetricEncryptData(fileThumbnailData, encryptionKey, ProtocolDefines.FILE_THUMBNAIL_NONCE);
		BlobUploader blobUploaderThumbnail = new BlobUploader(ConfigUtils::getSSLSocketFactory, thumbnailBoxed);
		blobUploaderThumbnail.setVersion(ThreemaApplication.getAppVersion());
		blobUploaderThumbnail.setServerUrls(ThreemaApplication.getIPv6());

		return new EncryptResult() {
			@Override
			public byte[] getData() {
				return thumbnailBoxed;
			}

			@Override
			public byte[] getKey() {
				return encryptionKey;
			}

			@Override
			public byte[] getNonce() {
				return ProtocolDefines.FILE_THUMBNAIL_NONCE;
			}

			@Override
			public int getSize() {
				return thumbnailBoxed.length;
			}
		};
	}

	@Override
	public EncryptResult encryptFileData(final byte[] fileData) {
		//generate random symmetric key for file encryption
		SecureRandom rnd = new SecureRandom();
		final byte[] encryptionKey = new byte[NaCl.SYMMKEYBYTES];
		rnd.nextBytes(encryptionKey);

		NaCl.symmetricEncryptDataInplace(fileData, encryptionKey, ProtocolDefines.FILE_NONCE);
		BlobUploader blobUploaderThumbnail = new BlobUploader(ConfigUtils::getSSLSocketFactory, fileData);
		blobUploaderThumbnail.setVersion(ThreemaApplication.getAppVersion());
		blobUploaderThumbnail.setServerUrls(ThreemaApplication.getIPv6());

		return new EncryptResult() {
			@Override
			public byte[] getData() {
				return fileData;
			}

			@Override
			public byte[] getKey() {
				return encryptionKey;
			}

			@Override
			public byte[] getNonce() {
				return ProtocolDefines.FILE_NONCE;
			}

			@Override
			public int getSize() {
				return fileData.length;
			}
		};
	}

	@Override
	public boolean isMessageBelongsToMe(AbstractMessageModel message) {
		return message instanceof MessageModel
			&& message.getIdentity().equals(this.contactModel.getIdentity());
	}

	@Override
	public boolean sendMediaData() {
		return true;
	}

	@Override
	public boolean offerRetry() {
		return true;
	}

	@Override
	public boolean validateSendingPermission(OnSendingPermissionDenied onSendingPermissionDenied) {
		int cannotSendResId = 0;
		if(this.blackListIdentityService.has(this.contactModel.getIdentity())) {
			cannotSendResId = R.string.blocked_cannot_send;
		}
		else {
			if (this.contactModel.getState() != null) {
				switch (this.contactModel.getState()) {
					case INVALID:
						cannotSendResId = R.string.invalid_cannot_send;
						break;
					case INACTIVE:
						//inactive allowed
						break;
				}
			} else {
				cannotSendResId = R.string.invalid_cannot_send;
			}
		}

		if(cannotSendResId > 0) {
			if(onSendingPermissionDenied != null) {
				onSendingPermissionDenied.denied(cannotSendResId);
			}
			return false;
		}
		return true;
	}

	@Override
	@MessageReceiverType
	public int getType() {
		return Type_CONTACT;
	}

	@Override
	public String[] getIdentities() {
		return new String[]{this.contactModel.getIdentity()};
	}

	@Override
	public String[] getIdentities(int requiredFeature) {
		if(ThreemaFeature.hasFeature(this.contactModel.getFeatureMask(), requiredFeature)) {
			return new String[] {this.contactModel.getIdentity()};
		}
		return new String[0];
	}

	@Override
	public String toString() {
		return "ContactMessageReceiver (identity = " + this.contactModel.getIdentity() + ")";
	}

	private void initNewAbstractMessage(MessageModel messageModel, AbstractMessage abstractMessage) {
		if(messageModel != null
				&& abstractMessage != null
				&& abstractMessage.getMessageId() != null
				&& TestUtil.empty(messageModel.getApiMessageId())) {
			messageModel.setApiMessageId(abstractMessage.getMessageId().toString());
			this.saveLocalModel(messageModel);
		}
	}
}
