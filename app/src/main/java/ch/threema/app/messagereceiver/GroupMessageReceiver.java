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

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.MessageService;
import ch.threema.app.tasks.OutgoingFileMessageTask;
import ch.threema.app.tasks.OutgoingGroupDeleteMessageTask;
import ch.threema.app.tasks.OutgoingGroupEditMessageTask;
import ch.threema.app.tasks.OutgoingLocationMessageTask;
import ch.threema.app.tasks.OutgoingPollSetupMessageTask;
import ch.threema.app.tasks.OutgoingPollVoteGroupMessageTask;
import ch.threema.app.tasks.OutgoingTextMessageTask;
import ch.threema.app.utils.GroupUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.crypto.SymmetricEncryptionResult;
import ch.threema.base.utils.Utils;
import ch.threema.domain.models.Contact;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.ThreemaFeature;
import ch.threema.domain.protocol.csp.messages.ballot.BallotData;
import ch.threema.domain.protocol.csp.messages.ballot.BallotId;
import ch.threema.domain.protocol.csp.messages.ballot.BallotVote;
import ch.threema.domain.taskmanager.TaskManager;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.access.GroupAccessModel;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.data.MessageContentsType;
import ch.threema.storage.models.data.media.FileDataModel;

public class GroupMessageReceiver implements MessageReceiver<GroupMessageModel> {

	private final GroupModel group;
	private final GroupService groupService;
	private Bitmap avatar = null;
	private final DatabaseServiceNew databaseServiceNew;
	private final @NonNull ServiceManager serviceManager;
	private final TaskManager taskManager;

	public GroupMessageReceiver(
		GroupModel group,
		GroupService groupService,
		DatabaseServiceNew databaseServiceNew,
		@NonNull ServiceManager serviceManager
	) {
		this.group = group;
		this.groupService = groupService;
		this.databaseServiceNew = databaseServiceNew;
		this.serviceManager = serviceManager;
		this.taskManager = serviceManager.getTaskManager();
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
	public void createAndSendTextMessage(@NonNull GroupMessageModel messageModel) {
		Set<String> otherMembers = groupService.getOtherMembers(group);

		if (otherMembers.isEmpty()) {
			// In case the recipients set is empty, we are sending the message in a notes group. In
			// this case we directly set the message state to sent to prevent confusion when the
			// user is offline and therefore the task has not yet been run.
			messageModel.setState(MessageState.SENT);
		}

		// Create and assign a new message id
		messageModel.setApiMessageId(new MessageId().toString());
		saveLocalModel(messageModel);

		bumpLastUpdate();

		// Schedule outgoing text message task
		taskManager.schedule(new OutgoingTextMessageTask(
			messageModel.getId(),
			Type_GROUP,
			otherMembers,
			serviceManager
		));
	}

	public void resendTextMessage(@NonNull GroupMessageModel messageModel, @NonNull Collection<String> recipientIdentities) {
		taskManager.schedule(new OutgoingTextMessageTask(
			messageModel.getId(),
			Type_GROUP,
			getRecipientIdentities(recipientIdentities),
			serviceManager
		));
	}

	@Override
	public void createAndSendLocationMessage(@NonNull GroupMessageModel messageModel) {
		Set<String> otherMembers = groupService.getOtherMembers(group);

		if (otherMembers.isEmpty()) {
			// In case the recipients set is empty, we are sending the message in a notes group. In
			// this case we directly set the message state to sent to prevent confusion when the
			// user is offline and therefore the task has not yet been run.
			messageModel.setState(MessageState.SENT);
		}

		// Create and assign a new message id
		messageModel.setApiMessageId(new MessageId().toString());
		saveLocalModel(messageModel);

		bumpLastUpdate();

		// Schedule outgoing text message task
		taskManager.schedule(new OutgoingLocationMessageTask(
			messageModel.getId(),
			Type_GROUP,
			otherMembers,
			serviceManager
		));
	}

	public void resendLocationMessage(
		@NonNull GroupMessageModel messageModel,
		@NonNull Collection<String> recipientIdentities
	) {
		// Schedule outgoing location message task
		taskManager.schedule(new OutgoingLocationMessageTask(
			messageModel.getId(),
			Type_GROUP,
			getRecipientIdentities(recipientIdentities),
			serviceManager
		));
	}

	@Override
	public void createAndSendFileMessage(
		@Nullable final byte[] thumbnailBlobId,
		@Nullable final byte[] fileBlobId,
		@Nullable SymmetricEncryptionResult encryptionResult,
		@NonNull final GroupMessageModel messageModel,
		@Nullable MessageId messageId,
		@Nullable Collection<String> recipientIdentities
	) {
		// Enrich file data model with blob id and encryption key
		FileDataModel modelFileData = messageModel.getFileData();
		modelFileData.setBlobId(fileBlobId);
		if (encryptionResult != null) {
			modelFileData.setEncryptionKey(encryptionResult.getKey());
		}

		// Set file data model again explicitly to enforce that the body of the message is rewritten
		// and therefore updated.
		messageModel.setFileData(modelFileData);

		// Create a new message id if the given message id is null
		messageModel.setApiMessageId(messageId != null ? messageId.toString() : new MessageId().toString());
		saveLocalModel(messageModel);

		// Note that lastUpdate lastUpdate was bumped when the file message was created

		// Schedule outgoing text message task
		taskManager.schedule(new OutgoingFileMessageTask(
			messageModel.getId(),
			Type_GROUP,
			getRecipientIdentities(recipientIdentities),
			thumbnailBlobId,
			serviceManager
		));
	}

	@Override
	public void createAndSendBallotSetupMessage(
		final BallotData ballotData,
		final BallotModel ballotModel,
		@NonNull GroupMessageModel messageModel,
		@Nullable MessageId messageId,
		@Nullable Collection<String> recipientIdentities
	) throws ThreemaException {
		final BallotId ballotId = new BallotId(Utils.hexStringToByteArray(ballotModel.getApiBallotId()));

		// Create a new message id if the given message id is null
		messageModel.setApiMessageId(messageId != null ? messageId.toString() : new MessageId().toString());
		saveLocalModel(messageModel);

		bumpLastUpdate();

		// Schedule outgoing text message task
		taskManager.schedule(new OutgoingPollSetupMessageTask(
			messageModel.getId(),
			Type_GROUP,
			getRecipientIdentities(recipientIdentities),
			ballotId,
			ballotData,
			serviceManager
		));
	}

	@Override
	public void createAndSendBallotVoteMessage(final BallotVote[] votes, final BallotModel ballotModel) throws ThreemaException {
		// Create message id
		MessageId messageId = new MessageId();

		final BallotId ballotId = new BallotId(Utils.hexStringToByteArray(ballotModel.getApiBallotId()));

		// Schedule outgoing text message task
		taskManager.schedule(new OutgoingPollVoteGroupMessageTask(
			messageId,
			Set.of(groupService.getGroupIdentities(group)),
			ballotId,
			ballotModel.getCreatorIdentity(),
			votes,
			ballotModel.getType(),
			group.getApiGroupId(),
			group.getCreatorIdentity(),
			serviceManager
		));
	}

	public void sendEditMessage(int messageModelId, @NonNull String body, @NonNull Date editedAt) {
		taskManager.schedule(
			new OutgoingGroupEditMessageTask(
				messageModelId,
				new MessageId(),
				body,
				editedAt,
				GroupUtil.getRecipientIdentitiesByFeatureSupport(
					groupService.getFeatureSupport(group, ThreemaFeature.EDIT_MESSAGES)
				),
				serviceManager
			)
		);
	}

	public void sendDeleteMessage(int messageModelId, @NonNull Date deletedAt) {
		taskManager.schedule(
			new OutgoingGroupDeleteMessageTask(
				messageModelId,
				new MessageId(),
				deletedAt,
				GroupUtil.getRecipientIdentitiesByFeatureSupport(
					groupService.getFeatureSupport(group, ThreemaFeature.DELETE_MESSAGES)
				),
				serviceManager
			)
		);
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
		if (avatar == null && groupService != null) {
			avatar = groupService.getAvatar(group, false);
		}
		return avatar;
	}

	@Override
	public Bitmap getAvatar() {
		if (avatar == null && groupService != null) {
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
			&& ((GroupMessageModel) message).getGroupId() == group.getId();
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

		if (access == null) {
			//what?
			return false;
		}

		if (!access.getCanSendMessageAccess().isAllowed()) {
			if (onSendingPermissionDenied != null) {
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

	@Override
	public void bumpLastUpdate() {
		if (group != null) {
			groupService.bumpLastUpdate(group);
		}
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

	@NonNull
	private Set<String> getRecipientIdentities(@Nullable Collection<String> recipients) {
		if (recipients != null) {
			return new HashSet<>(recipients);
		} else {
			return Set.of(groupService.getGroupIdentities(group));
		}
	}
}
