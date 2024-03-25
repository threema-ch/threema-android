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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.GroupMessagingService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.MessageService;
import ch.threema.app.utils.GroupUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.crypto.SymmetricEncryptionResult;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage;
import ch.threema.domain.protocol.csp.messages.GroupLocationMessage;
import ch.threema.domain.protocol.csp.messages.GroupTextMessage;
import ch.threema.domain.protocol.csp.messages.ballot.BallotData;
import ch.threema.domain.protocol.csp.messages.ballot.BallotId;
import ch.threema.domain.protocol.csp.messages.ballot.BallotVote;
import ch.threema.domain.protocol.csp.messages.ballot.GroupBallotCreateMessage;
import ch.threema.domain.protocol.csp.messages.ballot.GroupBallotVoteMessage;
import ch.threema.domain.protocol.csp.messages.file.FileData;
import ch.threema.domain.protocol.csp.messages.file.GroupFileMessage;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.GroupMessagePendingMessageIdModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.access.GroupAccessModel;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.data.LocationDataModel;
import ch.threema.storage.models.data.MessageContentsType;
import ch.threema.storage.models.data.media.FileDataModel;

public class GroupMessageReceiver implements MessageReceiver<GroupMessageModel> {
	private static final Logger logger = LoggingUtil.getThreemaLogger("GroupMessageReceiver");

	private final GroupModel group;
	private final GroupService groupService;
	private Bitmap avatar = null;
	private final DatabaseServiceNew databaseServiceNew;
	private final GroupMessagingService groupMessagingService;
	private final ContactService contactService;

	public GroupMessageReceiver(
		GroupModel group,
		GroupService groupService,
		DatabaseServiceNew databaseServiceNew,
		GroupMessagingService groupMessagingService,
	    ContactService contactService
	) {
		this.group = group;
		this.groupService = groupService;
		this.databaseServiceNew = databaseServiceNew;
		this.groupMessagingService = groupMessagingService;
		this.contactService = contactService;
	}

	@Override
	public GroupMessageModel createLocalModel(MessageType type, @MessageContentsType int messageContentsType, Date postedAt) {
		GroupMessageModel m = new GroupMessageModel();
		m.setType(type);
		m.setMessageContentsType(messageContentsType);
		m.setGroupId(group.getId());
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
		m.setGroupId(group.getId());
		m.setPostedAt(postedAt);
		m.setCreatedAt(new Date());
		m.setSaved(true);
		m.setUid(UUID.randomUUID().toString());
		m.setBody(statusBody);

		saveLocalModel(m);
		return m;
	}


	@Override
	public void saveLocalModel(GroupMessageModel save) {
		databaseServiceNew.getGroupMessageModelFactory().createOrUpdate(save);
	}

	@Override
	public boolean createBoxedTextMessage(final String text, final GroupMessageModel messageModel) throws ThreemaException {
		return sendMessage(messageId -> {
			GroupTextMessage boxedTextMessage = new GroupTextMessage();
			boxedTextMessage.setMessageId(messageId);
			boxedTextMessage.setText(text);

			if (messageId != null) {
				messageModel.setApiMessageId(messageId.toString());
			}

			return boxedTextMessage;
		}, messageModel);
	}

	@Override
	public boolean createBoxedLocationMessage(GroupMessageModel messageModel) throws ThreemaException {
		return sendMessage(messageId -> {
			final LocationDataModel locationDataModel = messageModel.getLocationData();
			final GroupLocationMessage msg = new GroupLocationMessage();
			msg.setMessageId(messageId);
			msg.setLatitude(locationDataModel.getLatitude());
			msg.setLongitude(locationDataModel.getLongitude());
			msg.setAccuracy(locationDataModel.getAccuracy());
			msg.setPoiName(locationDataModel.getPoi());
			msg.setPoiAddress(locationDataModel.getAddress());

			if (messageId != null) {
				messageModel.setApiMessageId(messageId.toString());
			}

			return msg;
		}, messageModel);
	}

	@Override
	public boolean createBoxedFileMessage(
		final byte[] thumbnailBlobId,
		final byte[] fileBlobId,
		SymmetricEncryptionResult encryptionResult,
		final GroupMessageModel messageModel
	) throws ThreemaException {
		List<ContactModel> supportedContacts = contactService.getByIdentities(groupService.getGroupIdentities(group));

		String[] identities = new String[supportedContacts.size()];
		for(int n = 0; n < supportedContacts.size(); n++) {
			identities[n] = supportedContacts.get(n).getIdentity();
		}

		final FileDataModel modelFileData = messageModel.getFileData();

		return sendMessage(messageId -> {
			final GroupFileMessage fileMessage = new GroupFileMessage();
			fileMessage.setMessageId(messageId);
			final FileData fileData = new FileData();
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

			if (messageId != null) {
				messageModel.setApiMessageId(messageId.toString());
			}

			logger.info(
				"Enqueue group file message ID {} to {}",
				fileMessage.getMessageId(),
				fileMessage.getToIdentity()
			);

			return fileMessage;
		}, messageModel, identities);
	}

	@Override
	public void createBoxedBallotMessage(final BallotData ballotData,
											final BallotModel ballotModel,
											final String[] filteredIdentities,
											@Nullable GroupMessageModel abstractMessageModel) throws ThreemaException {

		final BallotId ballotId = new BallotId(Utils.hexStringToByteArray(ballotModel.getApiBallotId()));

		sendMessage(messageId -> {
			final GroupBallotCreateMessage msg = new GroupBallotCreateMessage();
			msg.setMessageId(messageId);
			msg.setBallotCreator(ballotModel.getCreatorIdentity());
			msg.setBallotId(ballotId);
			msg.setData(ballotData);

			if (abstractMessageModel != null && messageId != null) {
				abstractMessageModel.setApiMessageId(messageId.toString());
			}

			logger.info("Enqueue ballot message ID {} to {}", msg.getMessageId(), msg.getToIdentity());

			return msg;
		}, null, filteredIdentities);

		// Save the message model as it now contains the message id
		saveLocalModel(abstractMessageModel);
	}

	@Override
	public void createBoxedBallotVoteMessage(final BallotVote[] votes, final BallotModel ballotModel) throws ThreemaException {
		final BallotId ballotId = new BallotId(Utils.hexStringToByteArray(ballotModel.getApiBallotId()));

		String[] toIdentities = groupService.getGroupIdentities(group);

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
		sendMessage(messageId -> {
			final GroupBallotVoteMessage msg = new GroupBallotVoteMessage();
			msg.setMessageId(messageId);
			msg.setBallotCreator(ballotModel.getCreatorIdentity());
			msg.setBallotId(ballotId);
			for (BallotVote v : votes) {
				msg.getBallotVotes().add(v);
			}
			return msg;
		}, null, toIdentities);
	}

	@Override
	public List<GroupMessageModel> loadMessages(MessageService.MessageFilter filter) {
		return databaseServiceNew.getGroupMessageModelFactory().find(
			group.getId(),
				filter);
	}

	@Override
	public long getMessagesCount() {
		return databaseServiceNew.getGroupMessageModelFactory().countMessages(
			group.getId());
	}

	@Override
	public long getUnreadMessagesCount() {
		return databaseServiceNew.getGroupMessageModelFactory().countUnreadMessages(
			group.getId());
	}

	@Override
	public List<GroupMessageModel> getUnreadMessages() {
		return databaseServiceNew.getGroupMessageModelFactory().getUnreadMessages(
			group.getId());
	}

	public GroupModel getGroup() {
		return group;
	}

	@Override
	public boolean isEqual(MessageReceiver o) {
		return o instanceof GroupMessageReceiver && ((GroupMessageReceiver) o).getGroup().getId() == getGroup().getId();
	}

	@Override
	public String getDisplayName() {
		return NameUtil.getDisplayName(group, groupService);
	}

	@Override
	public String getShortName() {
		return getDisplayName();
	}

	@Override
	public void prepareIntent(Intent intent) {
		intent.putExtra(ThreemaApplication.INTENT_DATA_GROUP, group.getId());
	}

	@Override
	public Bitmap getNotificationAvatar() {
		if(avatar == null && groupService != null) {
			avatar = groupService.getAvatar(group, false);
		}
		return avatar;
	}

	@Override
	public Bitmap getAvatar() {
		if(avatar == null && groupService != null) {
			avatar = groupService.getAvatar(group, true, true);
		}
		return avatar;
	}

	@Override
	@Deprecated
	public int getUniqueId() {
		if (groupService != null && group != null) {
			return groupService.getUniqueId(group);
		}
		return 0;
	}

	@Override
	public String getUniqueIdString() {
		if (groupService != null && group != null) {
			return groupService.getUniqueIdString(group);
		}
		return "";
	}

	@Override
	public boolean isMessageBelongsToMe(AbstractMessageModel message) {
		return message instanceof GroupMessageModel
				&& ((GroupMessageModel)message).getGroupId() == group.getId();
	}

	@Override
	public boolean sendMediaData() {
		// don't really send off group media if user is the only group member left - keep it local
		String[] groupIdentities = groupService.getGroupIdentities(group);
		return groupIdentities.length != 1 || !groupService.isGroupMember(group);
	}

	@Override
	public boolean offerRetry() {
		return false;
	}

	@Override
	public boolean validateSendingPermission(OnSendingPermissionDenied onSendingPermissionDenied) {
		//TODO: cache access? performance
		GroupAccessModel access = groupService.getAccess(getGroup(), true);

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
		return groupService.getGroupIdentities(group);
	}

	private boolean sendMessage(GroupMessagingService.CreateApiMessage createApiMessage, AbstractMessageModel messageModel) throws ThreemaException {
		return sendMessage(createApiMessage, messageModel, null);
	}

	/**
	 * Send a message to a group.
	 *
	 * @param createApiMessage A callback that creates the {@link AbstractGroupMessage} that will be sent.
	 * @param messageModel The model representing this message. It will be updated with status updates.
	 * @param groupIdentities List of group identities that will receive this group message. If set to null, the identities belonging to the current group will be used.
	 */
	private boolean sendMessage(
		@NonNull GroupMessagingService.CreateApiMessage createApiMessage,
		final AbstractMessageModel messageModel,
		@Nullable String[] groupIdentities
	) throws ThreemaException {
		if(groupIdentities == null) {
			groupIdentities = groupService.getGroupIdentities(group);
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
		if (groupIdentities.length == 1 && groupService.isGroupMember(group)) {
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

		int enqueuedMessagesCount = groupMessagingService.sendMessage(group, groupIdentities, createApiMessage, queuedGroupMessage -> {
			// Set as queued (first)
			groupService.setIsArchived(group, false);

			if(messageModel == null) {
				return;
			}
			if(!messageModel.isQueued()) {
				messageModel.setIsQueued(true);
			}
			//save identity message model
			databaseServiceNew.getGroupMessagePendingMessageIdModelFactory()
					.create(
							new GroupMessagePendingMessageIdModel(messageModel.getId(), queuedGroupMessage.getMessageId().toString()));
		});
		return enqueuedMessagesCount > 0;
	}

	@Override
	public @NonNull String toString() {
		return "GroupMessageReceiver (GroupId = " + group.getId() + ")";
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof GroupMessageReceiver)) return false;
		GroupMessageReceiver that = (GroupMessageReceiver) o;
		return Objects.equals(group, that.group);
	}

	@Override
	public int hashCode() {
		return Objects.hash(group);
	}
}
