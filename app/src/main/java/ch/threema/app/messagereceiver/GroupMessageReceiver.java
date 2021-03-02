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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.GroupApiService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.MessageService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.GroupUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.client.AbstractGroupMessage;
import ch.threema.client.BlobUploader;
import ch.threema.client.GroupLocationMessage;
import ch.threema.client.GroupTextMessage;
import ch.threema.client.MessageId;
import ch.threema.client.ProtocolDefines;
import ch.threema.client.ThreemaFeature;
import ch.threema.client.Utils;
import ch.threema.client.ballot.BallotData;
import ch.threema.client.ballot.BallotId;
import ch.threema.client.ballot.BallotVote;
import ch.threema.client.ballot.GroupBallotCreateMessage;
import ch.threema.client.ballot.GroupBallotVoteMessage;
import ch.threema.client.file.FileData;
import ch.threema.client.file.GroupFileMessage;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupMemberModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.GroupMessagePendingMessageIdModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.access.GroupAccessModel;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.data.MessageContentsType;
import ch.threema.storage.models.data.media.FileDataModel;

public class GroupMessageReceiver implements MessageReceiver<GroupMessageModel> {
	private static final Logger logger = LoggerFactory.getLogger(GroupMessageReceiver.class);

	private final GroupModel group;
	private final GroupService groupService;
	private Bitmap avatar = null;
	private final DatabaseServiceNew databaseServiceNew;
	private final GroupApiService groupApiService;
	private ContactService contactService;

	public GroupMessageReceiver(GroupModel group,
								GroupService groupService,
								DatabaseServiceNew databaseServiceNew,
								GroupApiService groupApiService,
	                            ContactService contactService){
		this.group = group;
		this.groupService = groupService;
		this.databaseServiceNew = databaseServiceNew;
		this.groupApiService = groupApiService;
		this.contactService = contactService;
	}

	@Override
	public List<MessageReceiver> getAffectedMessageReceivers() {
		return null;
	}

	@Override
	public GroupMessageModel createLocalModel(MessageType type, @MessageContentsType int messageContentsType, Date postedAt) {
		GroupMessageModel m = new GroupMessageModel();
		m.setType(type);
		m.setMessageContentsType(messageContentsType);
		m.setGroupId(this.group.getId());
		m.setPostedAt(postedAt);
		m.setCreatedAt(new Date());
		m.setSaved(false);
		m.setUid(UUID.randomUUID().toString());
		return m;
	}

	@Override
	@Deprecated
	public GroupMessageModel createAndSaveStatusModel(String statusBody, Date postedAt) {
		GroupMessageModel m = new GroupMessageModel(true);
		m.setType(MessageType.TEXT);
		m.setGroupId(this.group.getId());
		m.setPostedAt(postedAt);
		m.setCreatedAt(new Date());
		m.setSaved(true);
		m.setUid(UUID.randomUUID().toString());
		m.setBody(statusBody);

		this.saveLocalModel(m);
		return m;
	}


	@Override
	public void saveLocalModel(GroupMessageModel save) {
		this.databaseServiceNew.getGroupMessageModelFactory().createOrUpdate(save);
	}

	@Override
	public boolean createBoxedTextMessage(final String text, final GroupMessageModel messageModel) throws ThreemaException {
		return this.sendMessage(new GroupApiService.CreateApiMessage() {
			@Override
			public AbstractGroupMessage create(MessageId messageId) {
				GroupTextMessage boxedTextMessage = new GroupTextMessage();
				boxedTextMessage.setMessageId(messageId);
				boxedTextMessage.setText(text);

				if (messageId != null) {
					messageModel.setApiMessageId(messageId.toString());
				}

				return boxedTextMessage;
			}
		}, messageModel);
	}

	@Override
	public boolean createBoxedLocationMessage(final double lat, final double lng, final float acc, final String poiName, GroupMessageModel messageModel) throws ThreemaException {
		return this.sendMessage(new GroupApiService.CreateApiMessage() {
			@Override
			public AbstractGroupMessage create(MessageId messageId) {
				GroupLocationMessage msg = new GroupLocationMessage();
				msg.setMessageId(messageId);
				msg.setLatitude(lat);
				msg.setLongitude(lng);
				msg.setAccuracy(acc);
				msg.setPoiName(poiName);

				if (messageId != null) {
					messageModel.setApiMessageId(messageId.toString());
				}

				return msg;
			}
		}, messageModel);
	}

	@Override
	public boolean createBoxedFileMessage(final byte[] thumbnailBlobId,
										  final byte[] fileBlobId, final EncryptResult fileResult,
										  final GroupMessageModel messageModel) throws ThreemaException {
		//special, only send filemessages to identity with feature level FILE
		List<ContactModel> supportedContacts = Functional.filter(
				contactService.getByIdentities(this.groupService.getGroupIdentities(group)), new IPredicateNonNull<ContactModel>() {
			@Override
			public boolean apply(@NonNull ContactModel contactModel) {
					return ThreemaFeature.canFile(contactModel.getFeatureMask());
			}
		});

		String[] identities = new String[supportedContacts.size()];
		for(int n = 0; n < supportedContacts.size(); n++) {
			identities[n] = supportedContacts.get(n).getIdentity();
		}

		final FileDataModel modelFileData = messageModel.getFileData();

		return this.sendMessage(new GroupApiService.CreateApiMessage() {
			@Override
			public AbstractGroupMessage create(MessageId messageId) {
				GroupFileMessage fileMessage = new GroupFileMessage();
				fileMessage.setMessageId(messageId);
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

				if (messageId != null) {
					messageModel.setApiMessageId(messageId.toString());
				}

				logger.info("Enqueue group file message ID {} to {}", fileMessage.getMessageId(), fileMessage.getToIdentity());

				return fileMessage;
			}
		}, messageModel, identities);
	}

	@Override
	public boolean createBoxedBallotMessage(final BallotData ballotData,
											final BallotModel ballotModel,
											final String[] filteredIdentities,
											@Nullable GroupMessageModel abstractMessageModel) throws ThreemaException {

		final BallotId ballotId = new BallotId(Utils.hexStringToByteArray(ballotModel.getApiBallotId()));

		return this.sendMessage(new GroupApiService.CreateApiMessage() {
			@Override
			public AbstractGroupMessage create(MessageId messageId) {
				GroupBallotCreateMessage msg = new GroupBallotCreateMessage();
				msg.setMessageId(messageId);
				msg.setBallotCreator(ballotModel.getCreatorIdentity());
				msg.setBallotId(ballotId);
				msg.setData(ballotData);

				if (abstractMessageModel != null && messageId != null) {
					abstractMessageModel.setApiMessageId(messageId.toString());
				}

				logger.info("Enqueue ballot message ID {} to {}", msg.getMessageId(), msg.getToIdentity());

				return msg;
			}
		}, null, filteredIdentities);
	}

	@Override
	public boolean createBoxedBallotVoteMessage(final BallotVote[] votes, final BallotModel ballotModel) throws ThreemaException {
		final BallotId ballotId = new BallotId(Utils.hexStringToByteArray(ballotModel.getApiBallotId()));

		String[] toIdentities = this.groupService.getGroupIdentities(this.group);

		switch (ballotModel.getType()) {
			case RESULT_ON_CLOSE:
				String toIdentity = null;
				for(String i: toIdentities) {
					if(TestUtil.compare(i, ballotModel.getCreatorIdentity())) {
						toIdentity = i;
					}
				}

				if(toIdentity == null) {
					throw new ThreemaException("cannot send a ballot vote to another group!");
				}

				toIdentities = new String[] {toIdentity};
				//only to the creator
				break;
		}
		return this.sendMessage(new GroupApiService.CreateApiMessage() {
			@Override
			public AbstractGroupMessage create(MessageId messageId) {
				GroupBallotVoteMessage msg = new GroupBallotVoteMessage();
				msg.setMessageId(messageId);
				msg.setBallotCreator(ballotModel.getCreatorIdentity());
				msg.setBallotId(ballotId);
				for(BallotVote v: votes) {
					msg.getBallotVotes().add(v);
				}
				logger.info("Enqueue ballot vote message ID {} to {}", msg.getMessageId(), msg.getToIdentity());

				return msg;
			}
		}, null, toIdentities);
	}

	@Override
	public List<GroupMessageModel> loadMessages(MessageService.MessageFilter filter) throws SQLException {
		return this.databaseServiceNew.getGroupMessageModelFactory().find(
				this.group.getId(),
				filter);
	}

	@Override
	public long getMessagesCount() {
		return this.databaseServiceNew.getGroupMessageModelFactory().countMessages(
			this.group.getId());
	}

	@Override
	public long getUnreadMessagesCount() {
		return this.databaseServiceNew.getGroupMessageModelFactory().countUnreadMessages(
				this.group.getId());
	}

	@Override
	public List<GroupMessageModel> getUnreadMessages() {
		return this.databaseServiceNew.getGroupMessageModelFactory().getUnreadMessages(
			this.group.getId());
	}

	public GroupModel getGroup() {
		return this.group;
	}

	@Override
	public boolean isEqual(MessageReceiver o) {
		return o instanceof GroupMessageReceiver && ((GroupMessageReceiver) o).getGroup().getId() == this.getGroup().getId();
	}

	@Override
	public String getDisplayName() {
		return NameUtil.getDisplayName(this.group, this.groupService);
	}

	@Override
	public String getShortName() {
		return getDisplayName();
	}

	@Override
	public void prepareIntent(Intent intent) {
		intent.putExtra(ThreemaApplication.INTENT_DATA_GROUP, this.group.getId());
	}

	@Override
	public Bitmap getNotificationAvatar() {
		//lacy
		if(this.avatar == null && this.groupService != null) {
			this.avatar = this.groupService.getAvatar(group, false);
		}
		return this.avatar;
	}

	@Override
	@Deprecated
	public int getUniqueId() {
		if (this.groupService != null && this.group != null) {
			return this.groupService.getUniqueId(this.group);
		}
		return 0;
	}

	@Override
	public String getUniqueIdString() {
		if (this.groupService != null && this.group != null) {
			return this.groupService.getUniqueIdString(this.group);
		}
		return "";
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
	public boolean isMessageBelongsToMe(AbstractMessageModel message) {
		return message instanceof GroupMessageModel
				&& ((GroupMessageModel)message).getGroupId() == this.group.getId();
	}

	@Override
	public boolean sendMediaData() {
		// don't really send off group media if user is the only group member left - keep it local
		String[] groupIdentities = this.groupService.getGroupIdentities(this.group);
		return groupIdentities == null || groupIdentities.length != 1 || !groupService.isGroupMember(this.group);
	}

	@Override
	public boolean offerRetry() {
		return false;
	}

	@Override
	public boolean validateSendingPermission(OnSendingPermissionDenied onSendingPermissionDenied) {
		//TODO: cache access? performance
		GroupAccessModel access = this.groupService.getAccess(getGroup(), true);

		if(access == null) {
			//what?
			return false;
		}

		if(!access.getCanSendMessageAccess().isAllowed()) {
			if(onSendingPermissionDenied != null) {
				onSendingPermissionDenied.denied(access.getCanSendMessageAccess().getNotAllowedTestResourceId());
			}
			return false;
		}
		return true;
	}

	@Override
	@MessageReceiverType
	public int getType() {
		return Type_GROUP;
	}

	@Override
	public String[] getIdentities() {
		return this.groupService.getGroupIdentities(this.group);
	}

	@Override
	public String[] getIdentities(final int requiredFeature) {
		List<GroupMemberModel> members = Functional.filter(this.groupService.getGroupMembers(this.group), new IPredicateNonNull<GroupMemberModel>() {
			@Override
			public boolean apply(@NonNull GroupMemberModel groupMemberModel) {
				ContactModel model = contactService.getByIdentity(groupMemberModel.getIdentity());
				return model != null && ThreemaFeature.hasFeature(model.getFeatureMask(), requiredFeature);
			}
		});

		String[] identities = new String[members.size()];
		for(int p = 0; p < members.size(); p++) {
			identities[p] = members.get(p).getIdentity();
		}
		return identities;
	}


	private boolean sendMessage(GroupApiService.CreateApiMessage createApiMessage, AbstractMessageModel messageModel) throws ThreemaException {
		return this.sendMessage(createApiMessage, messageModel, null);
	}


	private boolean sendMessage(GroupApiService.CreateApiMessage createApiMessage, final AbstractMessageModel messageModel, String[] groupIdentities) throws ThreemaException {
		if(groupIdentities == null) {
			groupIdentities = this.groupService.getGroupIdentities(this.group);
		}

		// do not send messages to a broadcast/gateway group that does not receive and store incoming messages
		if (groupIdentities.length >= 2
			&& !GroupUtil.sendMessageToCreator(group)) {
			// remove creator from list of recipients
			ArrayList<String> fixedGroupIdentities = new ArrayList<>(Arrays.asList(groupIdentities));
			fixedGroupIdentities.remove(group.getCreatorIdentity());
			groupIdentities = fixedGroupIdentities.toArray(new String[0]);
		}

		// don't really send off messages if user is the only group member left - keep them local
		if (groupIdentities.length == 1 && groupService.isGroupMember(this.group)) {
			if(messageModel != null) {
				MessageId messageId = new MessageId();

				messageModel.setIsQueued(true);
				messageModel.setApiMessageId(messageId.toString());
				groupService.setIsArchived(group, false);
				messageModel.setState(MessageState.READ);
				messageModel.setModifiedAt(new Date());
				return true;
			}
		}

		this.groupApiService.sendMessage(this.group, groupIdentities, createApiMessage, new GroupApiService.GroupMessageQueued() {
			@Override
			public void onQueued(AbstractGroupMessage queuedGroupMessage) {
				//set as queued (first)
				groupService.setIsArchived(group, false);

				if(messageModel == null) {
					//its not a message model
					return;
				}
				if(!messageModel.isQueued()) {
					messageModel.setIsQueued(true);
				}
				//save identity message model
				databaseServiceNew.getGroupMessagePendingMessageIdModelFactory()
						.create(
								new GroupMessagePendingMessageIdModel(messageModel.getId(), queuedGroupMessage.getMessageId().toString()));
			}
		});
		return true;
	}

	@Override
	public String toString() {
		return "GroupMessageReceiver (GroupId = " + String.valueOf(this.group.getId()) + ")";
	}
}
