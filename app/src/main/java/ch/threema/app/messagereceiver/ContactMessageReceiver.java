/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2023 Threema GmbH
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

import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import androidx.annotation.NonNull;
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
import ch.threema.base.crypto.SymmetricEncryptionResult;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor;
import ch.threema.base.utils.Utils;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.ThreemaFeature;
import ch.threema.domain.protocol.csp.coders.MessageBox;
import ch.threema.domain.protocol.csp.connection.MessageQueue;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.BoxLocationMessage;
import ch.threema.domain.protocol.csp.messages.BoxTextMessage;
import ch.threema.domain.protocol.csp.messages.ballot.BallotCreateMessage;
import ch.threema.domain.protocol.csp.messages.ballot.BallotData;
import ch.threema.domain.protocol.csp.messages.ballot.BallotId;
import ch.threema.domain.protocol.csp.messages.ballot.BallotVote;
import ch.threema.domain.protocol.csp.messages.ballot.BallotVoteMessage;
import ch.threema.domain.protocol.csp.messages.file.FileData;
import ch.threema.domain.protocol.csp.messages.file.FileMessage;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.data.LocationDataModel;
import ch.threema.storage.models.data.MessageContentsType;
import ch.threema.storage.models.data.media.FileDataModel;

public class ContactMessageReceiver implements MessageReceiver<MessageModel> {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ContactMessageReceiver");

	private final ContactModel contactModel;
	private final ContactService contactService;
	private Bitmap avatar = null;
	private final DatabaseServiceNew databaseServiceNew;
	private final MessageQueue messageQueue;
	private final IdentityStore identityStore;
	private final IdListService blackListIdentityService;
	private final ForwardSecurityMessageProcessor fsmp;

	public ContactMessageReceiver(ContactModel contactModel,
								  ContactService contactService,
								  DatabaseServiceNew databaseServiceNew,
								  MessageQueue messageQueue,
								  IdentityStore identityStore,
								  IdListService blackListIdentityService,
	                              ForwardSecurityMessageProcessor fsmp) {
		this.contactModel = contactModel;
		this.contactService = contactService;
		this.databaseServiceNew = databaseServiceNew;
		this.messageQueue = messageQueue;
		this.identityStore = identityStore;
		this.blackListIdentityService = blackListIdentityService;
		this.fsmp = fsmp;
	}

	protected ContactMessageReceiver(ContactMessageReceiver contactMessageReceiver) {
		this(
			contactMessageReceiver.contactModel,
			contactMessageReceiver.contactService,
			contactMessageReceiver.databaseServiceNew,
			contactMessageReceiver.messageQueue,
			contactMessageReceiver.identityStore,
			contactMessageReceiver.blackListIdentityService,
			contactMessageReceiver.fsmp
		);
		avatar = contactMessageReceiver.avatar;
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
		m.setIdentity(contactModel.getIdentity());
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
		m.setIdentity(contactModel.getIdentity());
		m.setBody(statusBody);

		saveLocalModel(m);

		return m;
	}

	@Override
	public void saveLocalModel(MessageModel save) {
		databaseServiceNew.getMessageModelFactory().createOrUpdate(save);
	}

	@Override
	public boolean createBoxedTextMessage(String text, MessageModel messageModel) throws ThreemaException {
		BoxTextMessage innerMsg = new BoxTextMessage();
		innerMsg.setText(text);
		innerMsg.setToIdentity(this.contactModel.getIdentity());

		MessageBox boxmsg = wrapAndEnqueueMessage(innerMsg, messageModel);
		if (boxmsg != null) {
			messageModel.setIsQueued(true);
			MessageId id = boxmsg.getMessageId();

			if(id != null) {
				messageModel.setApiMessageId(id.toString());
				contactService.setIsHidden(innerMsg.getToIdentity(), false);
				contactService.setIsArchived(innerMsg.getToIdentity(), false);
				return true;
			}
		}
		logger.error("createBoxedTextMessage failed");
		return false;
	}

	@Override
	public boolean createBoxedLocationMessage(@NonNull MessageModel messageModel) throws ThreemaException {

		LocationDataModel locationDataModel = messageModel.getLocationData();

		BoxLocationMessage innerMsg = new BoxLocationMessage();
		innerMsg.setLatitude(locationDataModel.getLatitude());
		innerMsg.setLongitude(locationDataModel.getLongitude());
		innerMsg.setAccuracy(locationDataModel.getAccuracy());
		innerMsg.setToIdentity(contactModel.getIdentity());
		innerMsg.setPoiName(locationDataModel.getPoi());
		innerMsg.setPoiAddress(locationDataModel.getAddress());

		MessageBox boxmsg = wrapAndEnqueueMessage(innerMsg, messageModel);
		if (boxmsg != null) {
			messageModel.setIsQueued(true);
			MessageId id = boxmsg.getMessageId();
			if (id!= null) {
				messageModel.setApiMessageId(id.toString());
				contactService.setIsHidden(innerMsg.getToIdentity(), false);
				contactService.setIsArchived(innerMsg.getToIdentity(), false);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean createBoxedFileMessage(
		byte[] thumbnailBlobId,
		byte[] fileBlobId,
		SymmetricEncryptionResult encryptionResult,
		MessageModel messageModel
	) throws ThreemaException {
		FileDataModel modelFileData = messageModel.getFileData();
		FileMessage fileMessage = new FileMessage();
		FileData fileData = new FileData();
		fileData
				.setFileBlobId(fileBlobId)
				.setThumbnailBlobId(thumbnailBlobId)
				.setEncryptionKey(encryptionResult.getKey())
				.setMimeType(modelFileData.getMimeType())
				.setThumbnailMimeType(modelFileData.getThumbnailMimeType())
				.setFileSize(modelFileData.getFileSize())
				.setFileName(modelFileData.getFileName())
				.setRenderingType(modelFileData.getRenderingType())
				.setCaption(modelFileData.getCaption())
				.setCorrelationId(messageModel.getCorrelationId())
				.setMetaData(modelFileData.getMetaData());

		fileMessage.setData(fileData);
		fileMessage.setToIdentity(contactModel.getIdentity());

		MessageBox messageBox = wrapAndEnqueueMessage(fileMessage, messageModel);
		if(messageBox != null) {
			messageModel.setIsQueued(true);
			MessageId id = messageBox.getMessageId();
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

		BallotCreateMessage innerMsg = new BallotCreateMessage();
		innerMsg.setToIdentity(contactModel.getIdentity());
		innerMsg.setBallotCreator(identityStore.getIdentity());
		innerMsg.setBallotId(ballotId);
		innerMsg.setData(ballotData);

		MessageBox messageBox = wrapAndEnqueueMessage(innerMsg, messageModel);
		if(messageBox != null) {
			messageModel.setIsQueued(true);
			messageModel.setApiMessageId(messageBox.getMessageId().toString());
			contactService.setIsHidden(innerMsg.getToIdentity(), false);
			contactService.setIsArchived(innerMsg.getToIdentity(), false);
			return true;
		}
		return false;
	}

	@Override
	public boolean createBoxedBallotVoteMessage(BallotVote[] votes, BallotModel ballotModel) throws ThreemaException {
		final BallotId ballotId = new BallotId(Utils.hexStringToByteArray(ballotModel.getApiBallotId()));

		if (ballotModel.getType() == BallotModel.Type.RESULT_ON_CLOSE) {
			//if i am the creator do not send anything
			if (TestUtil.compare(ballotModel.getCreatorIdentity(), identityStore.getIdentity())) {
				return true;
			}
		}

		BallotVoteMessage innerMsg = new BallotVoteMessage();

		innerMsg.setBallotCreator(ballotModel.getCreatorIdentity());
		innerMsg.setBallotId(ballotId);
		innerMsg.setToIdentity(getContact().getIdentity());
		for(BallotVote v: votes) {
			innerMsg.getBallotVotes().add(v);
		}

		MessageBox messageBox = wrapAndEnqueueMessage(innerMsg, null);
		if (messageBox != null) {
			contactService.setIsHidden(innerMsg.getToIdentity(), false);
			contactService.setIsArchived(innerMsg.getToIdentity(), false);
			return true;
		}
		return false;

	}

	@Override
	public List<MessageModel> loadMessages(MessageService.MessageFilter filter) throws SQLException {
		return databaseServiceNew.getMessageModelFactory().find(
			contactModel.getIdentity(),
				filter);
	}

	/**
	 * Check if there is a call among the latest calls with the given call id.
	 *
	 * @param callId the call id
	 * @param limit the maximum number of latest calls
	 * @return {@code true} if there is a call with the given id within the latest calls, {@code false} otherwise
	 */
	public boolean hasVoipCallStatus(long callId, int limit) {
		return databaseServiceNew.getMessageModelFactory().hasVoipStatusForCallId(contactModel.getIdentity(), callId, limit);
	}

	@Override
	public long getMessagesCount() {
		return databaseServiceNew.getMessageModelFactory().countMessages(
			contactModel.getIdentity());
	}

	@Override
	public long getUnreadMessagesCount() {
		return databaseServiceNew.getMessageModelFactory().countUnreadMessages(
			contactModel.getIdentity());
	}

	@Override
	public List<MessageModel> getUnreadMessages() {
		return databaseServiceNew.getMessageModelFactory().getUnreadMessages(
			contactModel.getIdentity());
	}

	public MessageModel getLastMessage() {
		return databaseServiceNew.getMessageModelFactory().getLastMessage(
			contactModel.getIdentity());
	}

	public ContactModel getContact() {
		return contactModel;
	}

	@Override
	public boolean isEqual(MessageReceiver o) {
		return o instanceof ContactMessageReceiver && ((ContactMessageReceiver) o).getContact().getIdentity().equals(getContact().getIdentity());
	}

	@Override
	public String getDisplayName() {
		return NameUtil.getDisplayNameOrNickname(contactModel, true);
	}

	@Override
	public String getShortName() {
		return NameUtil.getShortName(contactModel);
	}

	@Override
	public void prepareIntent(Intent intent) {
		intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT, contactModel.getIdentity());
	}

	@Override
	@Nullable
	public Bitmap getNotificationAvatar() {
		if(avatar == null && contactService != null) {
			avatar = contactService.getAvatar(contactModel, false);
		}
		return avatar;
	}

	@Deprecated
	@Override
	public int getUniqueId() {
		return contactService.getUniqueId(contactModel);
	}

	@Override
	public String getUniqueIdString() {
		return contactService.getUniqueIdString(contactModel);
	}

	@Override
	public boolean isMessageBelongsToMe(AbstractMessageModel message) {
		return message instanceof MessageModel
			&& message.getIdentity().equals(contactModel.getIdentity());
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
		if(blackListIdentityService.has(contactModel.getIdentity())) {
			cannotSendResId = R.string.blocked_cannot_send;
		}
		else {
			if (contactModel.getState() != null) {
				switch (contactModel.getState()) {
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
		return new String[]{contactModel.getIdentity()};
	}

	@Override
	public String[] getIdentities(int requiredFeature) {
		if(ThreemaFeature.hasFeature(contactModel.getFeatureMask(), requiredFeature)) {
			return new String[] {contactModel.getIdentity()};
		}
		return new String[0];
	}

	@Override
	public @NonNull String toString() {
		return "ContactMessageReceiver (identity = " + contactModel.getIdentity() + ")";
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof ContactMessageReceiver)) return false;
		ContactMessageReceiver that = (ContactMessageReceiver) o;
		return Objects.equals(contactModel, that.contactModel);
	}

	@Override
	public int hashCode() {
		return Objects.hash(contactModel);
	}

	private void initNewAbstractMessage(MessageModel messageModel, AbstractMessage abstractMessage) {
		if(messageModel != null
				&& abstractMessage != null
				&& abstractMessage.getMessageId() != null
				&& (TestUtil.empty(messageModel.getApiMessageId()) || messageModel.getForwardSecurityMode() != abstractMessage.getForwardSecurityMode())) {
			messageModel.setApiMessageId(abstractMessage.getMessageId().toString());
			messageModel.setForwardSecurityMode(abstractMessage.getForwardSecurityMode());
			saveLocalModel(messageModel);
		}
	}

	private MessageBox wrapAndEnqueueMessage(@NonNull AbstractMessage innerMsg, @Nullable MessageModel messageModel) throws ThreemaException {
		// Check whether peer contact supports forward security
		if (ConfigUtils.isForwardSecurityEnabled() &&
			ThreemaFeature.canForwardSecurity(this.getContact().getFeatureMask()) &&
			this.getContact().isForwardSecurityEnabled()) {

			// Synchronize FS wrapping and enqueuing to ensure the order stays correct
			synchronized (fsmp) {
				AbstractMessage wrappedMessage = fsmp.makeMessage(this.getContact(), innerMsg);

				if (messageModel != null) {
					// Save model before enqueuing new message (fixes ANDR-512)
					initNewAbstractMessage(messageModel, wrappedMessage);
				}

				logger.info("Enqueue FS wrapped {} message ID {} to {}", innerMsg.getClass().getSimpleName(), wrappedMessage.getMessageId(), wrappedMessage.getToIdentity());
				return messageQueue.enqueue(wrappedMessage);
			}
		} else {
			// No forward security support or not enabled
			logger.debug("Recipient {} does not support forward security or it is not enabled", innerMsg.getToIdentity());

			if (messageModel != null) {
				// Save model before enqueuing new message (fixes ANDR-512)
				initNewAbstractMessage(messageModel, innerMsg);
			}

			logger.info("Enqueue {} message ID {} to {}", innerMsg.getClass().getSimpleName(), innerMsg.getMessageId(), innerMsg.getToIdentity());
			return messageQueue.enqueue(innerMsg);
		}
	}
}
