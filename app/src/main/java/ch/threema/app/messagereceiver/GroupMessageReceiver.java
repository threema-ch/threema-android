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

package ch.threema.app.messagereceiver;

import android.content.Intent;
import android.graphics.Bitmap;

import org.slf4j.Logger;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.emojis.EmojiUtil;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.multidevice.MultiDeviceManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.MessageService;
import ch.threema.app.tasks.OutboundIncomingGroupMessageUpdateReadTask;
import ch.threema.app.tasks.OutgoingFileMessageTask;
import ch.threema.app.tasks.OutgoingGroupDeleteMessageTask;
import ch.threema.app.tasks.OutgoingGroupDeliveryReceiptMessageTask;
import ch.threema.app.tasks.OutgoingGroupEditMessageTask;
import ch.threema.app.tasks.OutgoingGroupReactionMessageTask;
import ch.threema.app.tasks.OutgoingLocationMessageTask;
import ch.threema.app.tasks.OutgoingPollSetupMessageTask;
import ch.threema.app.tasks.OutgoingPollVoteGroupMessageTask;
import ch.threema.app.tasks.OutgoingTextMessageTask;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.GroupFeatureSupport;
import ch.threema.app.utils.GroupUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.crypto.SymmetricEncryptionResult;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;
import ch.threema.data.models.GroupModelData;
import ch.threema.data.repositories.ContactModelRepository;
import ch.threema.data.repositories.GroupModelRepository;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.ThreemaFeature;
import ch.threema.domain.protocol.csp.messages.ballot.BallotData;
import ch.threema.domain.protocol.csp.messages.ballot.BallotId;
import ch.threema.domain.protocol.csp.messages.ballot.BallotVote;
import ch.threema.domain.taskmanager.TaskManager;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.protobuf.csp.e2e.Reaction;
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

import static ch.threema.app.utils.MessageUtil.canSendUserAcknowledge;
import static ch.threema.domain.protocol.csp.ProtocolDefines.DELIVERYRECEIPT_MSGUSERACK;
import static ch.threema.domain.protocol.csp.ProtocolDefines.DELIVERYRECEIPT_MSGUSERDEC;

public class GroupMessageReceiver implements MessageReceiver<GroupMessageModel> {
    private static final Logger logger = LoggingUtil.getThreemaLogger("GroupMessageReceiver");

    private final GroupModel group;
    @Nullable
    private final ch.threema.data.models.GroupModel groupModel;
    private final GroupService groupService;
    private final DatabaseServiceNew databaseServiceNew;
    @NonNull
    private final ContactService contactService;
    @NonNull
    private final ContactModelRepository contactModelRepository;
    @NonNull
    private final GroupModelRepository groupModelRepository;
    private final @NonNull ServiceManager serviceManager;
    private final TaskManager taskManager;
    private final MultiDeviceManager multiDeviceManager;

    public GroupMessageReceiver(
        GroupModel group,
        GroupService groupService,
        DatabaseServiceNew databaseServiceNew,
        @NonNull ContactService contactService,
        @NonNull ContactModelRepository contactModelRepository,
        @NonNull GroupModelRepository groupModelRepository,
        @NonNull ServiceManager serviceManager
    ) {
        this.group = group;
        this.groupService = groupService;
        this.databaseServiceNew = databaseServiceNew;
        this.contactService = contactService;
        this.contactModelRepository = contactModelRepository;
        this.groupModelRepository = groupModelRepository;
        this.serviceManager = serviceManager;
        this.taskManager = serviceManager.getTaskManager();
        this.multiDeviceManager = serviceManager.getMultiDeviceManager();

        this.groupModel = group != null
            ? groupModelRepository.getByCreatorIdentityAndId(group.getCreatorIdentity(), group.getApiGroupId())
            : null;
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
        Set<String> otherMembers = groupService.getMembersWithoutUser(group);

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
        Set<String> otherMembers = groupService.getMembersWithoutUser(group);

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
        @NonNull final BallotData ballotData,
        @NonNull final BallotModel ballotModel,
        @NonNull GroupMessageModel messageModel,
        @Nullable MessageId messageId,
        @Nullable Collection<String> recipientIdentities,
        @NonNull TriggerSource triggerSource
    ) throws ThreemaException {
        final BallotId ballotId = new BallotId(Utils.hexStringToByteArray(ballotModel.getApiBallotId()));

        // Create a new message id if the given message id is null
        messageModel.setApiMessageId(messageId != null ? messageId.toString() : new MessageId().toString());
        saveLocalModel(messageModel);

        bumpLastUpdate();

        // Schedule outgoing text message task if this is triggered from local
        if (triggerSource == TriggerSource.LOCAL) {
            taskManager.schedule(new OutgoingPollSetupMessageTask(
                messageModel.getId(),
                Type_GROUP,
                getRecipientIdentities(recipientIdentities),
                ballotId,
                ballotData,
                serviceManager
            ));
        }
    }

    @Override
    public void createAndSendBallotVoteMessage(
        final BallotVote[] votes,
        final BallotModel ballotModel,
        @NonNull TriggerSource triggerSource
    ) throws ThreemaException {
        // Create message id
        MessageId messageId = new MessageId();

        final BallotId ballotId = new BallotId(Utils.hexStringToByteArray(ballotModel.getApiBallotId()));

        // Schedule outgoing text message task
        taskManager.schedule(new OutgoingPollVoteGroupMessageTask(
            messageId,
            Set.of(groupService.getGroupMemberIdentities(group)),
            ballotId,
            ballotModel.getCreatorIdentity(),
            votes,
            ballotModel.getType(),
            group.getApiGroupId(),
            group.getCreatorIdentity(),
            serviceManager
        ));
    }

    /**
     * Send an incoming message update to mark the message as read. This method only schedules the
     * outgoing group message update if multi device is activated.
     */
    public void sendIncomingMessageUpdateRead(@NonNull Set<MessageId> messageIds, long timestamp) {
        if (multiDeviceManager.isMultiDeviceActive()) {
            taskManager.schedule(
                new OutboundIncomingGroupMessageUpdateReadTask(
                    messageIds,
                    timestamp,
                    group.getApiGroupId(),
                    group.getCreatorIdentity(),
                    serviceManager
                )
            );
        }
    }

    public void sendEditMessage(int messageModelId, @NonNull String body, @NonNull Date editedAt) {
        taskManager.schedule(
            new OutgoingGroupEditMessageTask(
                messageModelId,
                new MessageId(),
                body,
                editedAt,
                GroupUtil.getRecipientIdentitiesByFeatureSupport(
                    getFeatureSupport(ThreemaFeature.EDIT_MESSAGES)
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
                    getFeatureSupport(ThreemaFeature.DELETE_MESSAGES)
                ),
                serviceManager
            )
        );
    }

    /**
     * Send a reaction message to the group. Members who do not support reactions will receive an ack/dec instead
     *
     * @param messageModel  MessageModel the reaction reacts to
     * @param actionCase    The action case of the reaction (WITHDRAW is not backwards compatible and wil not cause an ack/dec to be sent)
     * @param emojiSequence The emoji sequence of the reaction
     * @param reactedAt     The timestamp of the reaction
     */
    public void sendReaction(AbstractMessageModel messageModel, Reaction.ActionCase actionCase, @NonNull String emojiSequence, @NonNull Date reactedAt) {
        // identities that support receiving emoji reactions
        Set<String> emojiReactionsIdentities = GroupUtil.getRecipientIdentitiesByFeatureSupport(getFeatureSupport(ThreemaFeature.EMOJI_REACTIONS));
        // all group identities except sender
        Set<String> identitiesWithoutReactionSupport = groupService.getMembersWithoutUser(group);
        identitiesWithoutReactionSupport.removeAll(emojiReactionsIdentities);

        // Note that there might be no recipients that support emoji reactions but even then we need
        // to schedule this task as it is needed for reflection. It may just not send out any csp
        // messages.
        taskManager.schedule(
            new OutgoingGroupReactionMessageTask(
                messageModel.getId(),
                new MessageId(),
                actionCase,
                emojiSequence,
                reactedAt,
                emojiReactionsIdentities,
                serviceManager
            )
        );

        // Fall back to acks for users who do not yet support receiving emoji reactions
        if (actionCase == Reaction.ActionCase.APPLY && !identitiesWithoutReactionSupport.isEmpty() && canSendUserAcknowledge(messageModel)) {
            if (EmojiUtil.isThumbsUpEmoji(emojiSequence)) {
                // send ack to these receivers
                sendGroupDeliveryReceiptWithoutReflection(
                    identitiesWithoutReactionSupport,
                    (GroupMessageModel) messageModel,
                    DELIVERYRECEIPT_MSGUSERACK
                );
            } else if (EmojiUtil.isThumbsDownEmoji(emojiSequence)) {
                // send dec to these receivers
                sendGroupDeliveryReceiptWithoutReflection(
                    identitiesWithoutReactionSupport,
                    (GroupMessageModel) messageModel,
                    DELIVERYRECEIPT_MSGUSERDEC
                );
            }
        }
    }

    /**
     * Send a delivery receipt to the identities specified
     *
     * @param identities   Identities to send the receipt to
     * @param messageModel GroupMessageModel for which a receipt should be sent
     * @param receiptType  Type of receipt (currently only ACK and DEC are supported for groups)
     */
    private void sendGroupDeliveryReceiptWithoutReflection(
        @NonNull Set<String> identities,
        GroupMessageModel messageModel,
        int receiptType
    ) {
        serviceManager.getTaskManager().schedule(
            new OutgoingGroupDeliveryReceiptMessageTask(
                messageModel.getId(),
                receiptType,
                identities,
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

    @NonNull
    @Override
    public List<GroupMessageModel> getUnreadMessages() {
        return databaseServiceNew.getGroupMessageModelFactory().getUnreadMessages(
            group.getId());
    }

    public GroupModel getGroup() {
        return group;
    }

    @Nullable
    public ch.threema.data.models.GroupModel getGroupModel() {
        return groupModel;
    }

    @Override
    public boolean isEqual(MessageReceiver o) {
        return o instanceof GroupMessageReceiver && ((GroupMessageReceiver) o).getGroup().getId() == getGroup().getId();
    }

    @Override
    public String getDisplayName() {
        // Get new group model to ensure the display name is fresh
        ch.threema.data.models.GroupModel groupModel = groupModelRepository.getByCreatorIdentityAndId(
            group.getCreatorIdentity(),
            group.getApiGroupId()
        );
        if (groupModel != null) {
            GroupModelData groupModelData = groupModel.getData().getValue();
            if (groupModelData != null) {
                return NameUtil.getDisplayName(groupModelData, contactModelRepository, contactService);
            }
        }

        // In case the new group model cannot be found, we fall back to the old group model
        return NameUtil.getDisplayName(group, groupService);
    }

    @Override
    public String getShortName() {
        return getDisplayName();
    }

    @Override
    public void prepareIntent(Intent intent) {
        intent.putExtra(ThreemaApplication.INTENT_DATA_GROUP_DATABASE_ID, group.getId());
    }

    @Override
    public Bitmap getNotificationAvatar() {
        return groupService.getAvatar(group, false);
    }

    @Override
    public Bitmap getAvatar() {
        return groupService.getAvatar(group, true, true);
    }

    @Override
    @Deprecated
    public int getUniqueId() {
        return GroupUtil.getUniqueId(group);
    }

    @Override
    public String getUniqueIdString() {
        return GroupUtil.getUniqueIdString(group);
    }

    @Override
    public boolean isMessageBelongsToMe(AbstractMessageModel message) {
        return message instanceof GroupMessageModel
            && ((GroupMessageModel) message).getGroupId() == group.getId();
    }

    @Override
    public boolean sendMediaData() {
        if (multiDeviceManager.isMultiDeviceActive()) {
            // We need to upload the media in any case (also for notes groups) if multi device is
            // active. In this case the upload is needed as the message is reflected.
            return true;
        }

        // don't really send off group media if user is the only group member left - keep it local
        String[] groupIdentities = groupService.getGroupMemberIdentities(group);
        return groupIdentities.length != 1 || !groupService.isGroupMember(group);
    }

    @Override
    public boolean offerRetry() {
        return false;
    }

    @NonNull
    @Override
    public SendingPermissionValidationResult validateSendingPermission() {
        GroupAccessModel access = groupService.getAccess(getGroup(), true);

        if (access == null) {
            //what?
            return new SendingPermissionValidationResult.Denied();
        }

        if (!access.getCanSendMessageAccess().isAllowed()) {
            return new SendingPermissionValidationResult.Denied(
                access.getCanSendMessageAccess().getNotAllowedTestResourceId()
            );
        }
        return SendingPermissionValidationResult.Valid.INSTANCE;
    }

    @Override
    @MessageReceiverType
    public int getType() {
        return Type_GROUP;
    }

    @Override
    public String[] getIdentities() {
        return groupService.getGroupMemberIdentities(group);
    }

    @Override
    public void bumpLastUpdate() {
        if (group != null) {
            groupService.bumpLastUpdate(group);
        }
    }

    @Override
    @EmojiReactionsSupport
    public int getEmojiReactionSupport() {
        if (groupModel == null) {
            logger.error("Group model in group message receiver is null");
            return Reactions_NONE;
        }

        if (!ConfigUtils.canSendEmojiReactions()) {
            return Reactions_NONE;
        }

        GroupModelData groupModelData = groupModel.getData().getValue();

        if (groupModelData == null) {
            logger.warn("Group model data is null");
            return Reactions_NONE;
        }
        if (!groupModelData.isMember()) {
            return Reactions_NONE;
        }
        if (Boolean.TRUE.equals(groupModel.isNotesGroup())) {
            return Reactions_FULL;
        }

        switch (groupService.getFeatureSupport(groupModelData, ThreemaFeature.EMOJI_REACTIONS).getAdoptionRate()) {
            case PARTIAL:
                return Reactions_PARTIAL;
            case ALL:
                return Reactions_FULL;
            case NONE:
                // Fallthrough
            default:
                return Reactions_NONE; // Handle unknown adoption rates
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
            return Set.of(groupService.getGroupMemberIdentities(group));
        }
    }

    private GroupFeatureSupport getFeatureSupport(long feature) {
        if (groupModel == null) {
            logger.error("Cannot get feature support: Group model is null");
            return new GroupFeatureSupport(feature, List.of());
        }
        GroupModelData groupModelData = groupModel.getData().getValue();
        if (groupModelData == null) {
            logger.error("Cannot get feature support: Group model data is null");
            return new GroupFeatureSupport(feature, List.of());
        }
        return groupService.getFeatureSupport(groupModelData, feature);
    }
}
