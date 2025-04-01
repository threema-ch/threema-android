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

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.multidevice.MultiDeviceManager;
import ch.threema.app.services.BlockedIdentitiesService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.MessageService;
import ch.threema.app.stores.IdentityStore;
import ch.threema.app.tasks.OutboundIncomingContactMessageUpdateReadTask;
import ch.threema.app.tasks.OutgoingContactDeliveryReceiptMessageTask;
import ch.threema.app.tasks.OutgoingContactDeleteMessageTask;
import ch.threema.app.tasks.OutgoingContactEditMessageTask;
import ch.threema.app.tasks.OutgoingFileMessageTask;
import ch.threema.app.tasks.OutgoingLocationMessageTask;
import ch.threema.app.tasks.OutgoingContactReactionMessageTask;
import ch.threema.app.tasks.OutgoingPollSetupMessageTask;
import ch.threema.app.tasks.OutgoingPollVoteContactMessageTask;
import ch.threema.app.tasks.OutgoingTextMessageTask;
import ch.threema.app.tasks.OutgoingTypingIndicatorMessageTask;
import ch.threema.app.tasks.OutgoingVoipCallAnswerMessageTask;
import ch.threema.app.tasks.OutgoingVoipCallHangupMessageTask;
import ch.threema.app.tasks.OutgoingVoipCallOfferMessageTask;
import ch.threema.app.tasks.OutgoingVoipCallRingingMessageTask;
import ch.threema.app.tasks.OutgoingVoipICECandidateMessageTask;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.crypto.SymmetricEncryptionResult;
import ch.threema.base.utils.Utils;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.ThreemaFeature;
import ch.threema.domain.protocol.csp.messages.ballot.BallotData;
import ch.threema.domain.protocol.csp.messages.ballot.BallotId;
import ch.threema.domain.protocol.csp.messages.ballot.BallotVote;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerData;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallHangupData;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallOfferData;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallRingingData;
import ch.threema.domain.protocol.csp.messages.voip.VoipICECandidatesData;
import ch.threema.domain.taskmanager.ActiveTaskCodec;
import ch.threema.domain.taskmanager.Task;
import ch.threema.domain.taskmanager.TaskManager;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.protobuf.csp.e2e.Reaction;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.data.MessageContentsType;
import ch.threema.storage.models.data.media.FileDataModel;

public class ContactMessageReceiver implements MessageReceiver<MessageModel> {
    private final ContactModel contactModel;
    private final ContactService contactService;
    @NonNull
    private final ServiceManager serviceManager;
    private final DatabaseServiceNew databaseServiceNew;
    private final IdentityStore identityStore;
    private final BlockedIdentitiesService blockedIdentitiesService;
    private final @NonNull TaskManager taskManager;
    private final @NonNull MultiDeviceManager multiDeviceManager;

    public ContactMessageReceiver(ContactModel contactModel,
                                  ContactService contactService,
                                  @NonNull ServiceManager serviceManager,
                                  DatabaseServiceNew databaseServiceNew,
                                  IdentityStore identityStore,
                                  @NonNull BlockedIdentitiesService blockedIdentitiesService
    ) {
        this.contactModel = contactModel;
        this.contactService = contactService;
        this.serviceManager = serviceManager;
        this.databaseServiceNew = databaseServiceNew;
        this.identityStore = identityStore;
        this.blockedIdentitiesService = blockedIdentitiesService;
        this.taskManager = serviceManager.getTaskManager();
        this.multiDeviceManager = serviceManager.getMultiDeviceManager();
    }

    protected ContactMessageReceiver(ContactMessageReceiver contactMessageReceiver) {
        this(
            contactMessageReceiver.contactModel,
            contactMessageReceiver.contactService,
            contactMessageReceiver.serviceManager,
            contactMessageReceiver.databaseServiceNew,
            contactMessageReceiver.identityStore,
            contactMessageReceiver.blockedIdentitiesService
        );
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
    public void createAndSendTextMessage(@NonNull MessageModel messageModel) {
        // Create and assign a new message id
        messageModel.setApiMessageId(new MessageId().toString());
        saveLocalModel(messageModel);

        // Mark the contact as non-hidden and unarchived
        contactService.setAcquaintanceLevel(contactModel.getIdentity(), ContactModel.AcquaintanceLevel.DIRECT);
        contactService.setIsArchived(contactModel.getIdentity(), false);

        bumpLastUpdate();

        // Schedule outgoing text message task
        scheduleTask(new OutgoingTextMessageTask(
            messageModel.getId(),
            Type_CONTACT,
            Set.of(messageModel.getIdentity()),
            serviceManager
        ));
    }

    public void resendTextMessage(@NonNull MessageModel messageModel) {
        contactService.setAcquaintanceLevel(contactModel.getIdentity(), ContactModel.AcquaintanceLevel.DIRECT);
        contactService.setIsArchived(contactModel.getIdentity(), false);

        scheduleTask(new OutgoingTextMessageTask(
            messageModel.getId(),
            Type_CONTACT,
            Set.of(messageModel.getIdentity()),
            serviceManager
        ));
    }

    @Override
    public void createAndSendLocationMessage(@NonNull MessageModel messageModel) {
        // Create and assign a new message id
        messageModel.setApiMessageId(new MessageId().toString());
        saveLocalModel(messageModel);

        // Mark the contact as non-hidden and unarchived
        contactService.setAcquaintanceLevel(contactModel.getIdentity(), ContactModel.AcquaintanceLevel.DIRECT);
        contactService.setIsArchived(contactModel.getIdentity(), false);

        bumpLastUpdate();

        // Schedule outgoing text message task
        scheduleTask(new OutgoingLocationMessageTask(
            messageModel.getId(),
            Type_CONTACT,
            Set.of(messageModel.getIdentity()),
            serviceManager
        ));
    }

    public void resendLocationMessage(@NonNull MessageModel messageModel) {
        // Mark the contact as non-hidden and unarchived
        contactService.setAcquaintanceLevel(contactModel.getIdentity(), ContactModel.AcquaintanceLevel.DIRECT);
        contactService.setIsArchived(contactModel.getIdentity(), false);

        // Schedule outgoing text message task
        scheduleTask(new OutgoingLocationMessageTask(
            messageModel.getId(),
            Type_CONTACT,
            Set.of(messageModel.getIdentity()),
            serviceManager
        ));
    }

    @Override
    public void createAndSendFileMessage(
        @Nullable byte[] thumbnailBlobId,
        @Nullable byte[] fileBlobId,
        @Nullable SymmetricEncryptionResult encryptionResult,
        @NonNull MessageModel messageModel,
        @Nullable MessageId messageId,
        @Nullable Collection<String> recipientIdentities
    ) throws ThreemaException {
        // Enrich file data model with blob id and encryption key
        FileDataModel modelFileData = messageModel.getFileData();
        modelFileData.setBlobId(fileBlobId);
        if (encryptionResult != null) {
            modelFileData.setEncryptionKey(encryptionResult.getKey());
        }

        // Set file data model again explicitly to enforce that the body of the message is rewritten
        // and therefore updated.
        messageModel.setFileDataModel(modelFileData);

        // Create a new message id if the given message id is null
        messageModel.setApiMessageId(messageId != null ? messageId.toString() : new MessageId().toString());
        saveLocalModel(messageModel);

        // Mark the contact as non-hidden and unarchived
        contactService.setAcquaintanceLevel(contactModel.getIdentity(), ContactModel.AcquaintanceLevel.DIRECT);
        contactService.setIsArchived(contactModel.getIdentity(), false);

        // Note that lastUpdate lastUpdate was bumped when the file message was created

        // Schedule outgoing text message task
        scheduleTask(new OutgoingFileMessageTask(
            messageModel.getId(),
            Type_CONTACT,
            Set.of(messageModel.getIdentity()),
            thumbnailBlobId,
            serviceManager
        ));
    }

    @Override
    public void createAndSendBallotSetupMessage(
        @NonNull BallotData ballotData,
        @NonNull BallotModel ballotModel,
        @NonNull MessageModel messageModel,
        @NonNull MessageId messageId,
        @Nullable Collection<String> recipientIdentities,
        @NonNull TriggerSource triggerSource
    ) throws ThreemaException {
        // Save the given message id to the model
        messageModel.setApiMessageId(messageId.toString());
        saveLocalModel(messageModel);

        final BallotId ballotId = new BallotId(Utils.hexStringToByteArray(ballotModel.getApiBallotId()));

        // Mark the contact as non-hidden and unarchived
        contactService.setAcquaintanceLevel(contactModel.getIdentity(), ContactModel.AcquaintanceLevel.DIRECT);
        contactService.setIsArchived(contactModel.getIdentity(), false);

        bumpLastUpdate();

        // Schedule outgoing text message task if this has been triggered by local
        if (triggerSource == TriggerSource.LOCAL) {
            scheduleTask(new OutgoingPollSetupMessageTask(
                messageModel.getId(),
                Type_CONTACT,
                Set.of(messageModel.getIdentity()),
                ballotId,
                ballotData,
                serviceManager
            ));
        }
    }

    @Override
    public void createAndSendBallotVoteMessage(
        BallotVote[] votes,
        BallotModel ballotModel,
        @NonNull TriggerSource triggerSource
    ) throws ThreemaException {
        final BallotId ballotId = new BallotId(Utils.hexStringToByteArray(ballotModel.getApiBallotId()));

        if (ballotModel.getType() == BallotModel.Type.RESULT_ON_CLOSE) {
            //if i am the creator do not send anything
            if (TestUtil.compare(ballotModel.getCreatorIdentity(), identityStore.getIdentity())) {
                return;
            }
        }

        // Mark the contact as non-hidden and unarchived
        contactService.setAcquaintanceLevel(contactModel.getIdentity(), ContactModel.AcquaintanceLevel.DIRECT);
        contactService.setIsArchived(contactModel.getIdentity(), false);


        if (triggerSource == TriggerSource.LOCAL) {
            // Create message id
            MessageId messageId = new MessageId();

            // Schedule outgoing text message task
            scheduleTask(new OutgoingPollVoteContactMessageTask(
                messageId,
                ballotId,
                ballotModel.getCreatorIdentity(),
                votes,
                contactModel.getIdentity(),
                serviceManager
            ));
        }
    }

    /**
     * Send a typing indicator to the receiver.
     *
     * @param isTyping true if the user is typing, false otherwise
     * @throws ThreemaException if enqueuing the message fails
     */
    public void sendTypingIndicatorMessage(boolean isTyping) throws ThreemaException {
        scheduleTask(new OutgoingTypingIndicatorMessageTask(isTyping, contactModel.getIdentity(), serviceManager));
    }

    /**
     * Send a delivery receipt to the receiver.
     *
     * @param receiptType the type of the delivery receipt
     * @param messageIds  the message ids
     */
    public void sendDeliveryReceipt(int receiptType, @NonNull MessageId[] messageIds, long time) {
        scheduleTask(
            new OutgoingContactDeliveryReceiptMessageTask(
                receiptType, messageIds, time, contactModel.getIdentity(), serviceManager
            )
        );
    }

    /**
     * Send an incoming message update to mark the message as read. Note that this is the
     * alternative of {@link ContactMessageReceiver#sendDeliveryReceipt(int, MessageId[], long)}
     * when no delivery receipt should be sent. This method only schedules the outgoing message
     * update if multi device is activated.
     */
    public void sendIncomingMessageUpdateRead(@NonNull Set<MessageId> messageIds, long timestamp) {
        if (multiDeviceManager.isMultiDeviceActive()) {
            scheduleTask(
                new OutboundIncomingContactMessageUpdateReadTask(
                    messageIds,
                    timestamp,
                    contactModel.getIdentity(),
                    serviceManager
                )
            );
        }
    }

    /**
     * Send a voip call offer message to the receiver.
     *
     * @param callOfferData the call offer data
     */
    public void sendVoipCallOfferMessage(@NonNull VoipCallOfferData callOfferData) {
        scheduleTask(
            new OutgoingVoipCallOfferMessageTask(
                callOfferData, contactModel.getIdentity(), serviceManager
            )
        );
    }

    /**
     * Send a voip call answer message to the receiver.
     *
     * @param callAnswerData the call answer data
     */
    public void sendVoipCallAnswerMessage(@NonNull VoipCallAnswerData callAnswerData) {
        scheduleTask(
            new OutgoingVoipCallAnswerMessageTask(
                callAnswerData, contactModel.getIdentity(), serviceManager
            )
        );
    }

    /**
     * Send a voip ICE candidates message to the receiver.
     *
     * @param voipICECandidatesData the voip ICE candidate data
     */
    public void sendVoipICECandidateMessage(@NonNull VoipICECandidatesData voipICECandidatesData) {
        scheduleTask(
            new OutgoingVoipICECandidateMessageTask(
                voipICECandidatesData, contactModel.getIdentity(), serviceManager
            )
        );
    }

    /**
     * Send a voip call hangup message to the receiver.
     *
     * @param callHangupData the call hangup data
     */
    public void sendVoipCallHangupMessage(@NonNull VoipCallHangupData callHangupData) {
        scheduleTask(
            new OutgoingVoipCallHangupMessageTask(
                callHangupData, contactModel.getIdentity(), serviceManager
            )
        );
    }

    /**
     * Send a voip call ringing message to the receiver.
     *
     * @param callRingingData the call ringing data
     */
    public void sendVoipCallRingingMessage(@NonNull VoipCallRingingData callRingingData) {
        scheduleTask(
            new OutgoingVoipCallRingingMessageTask(
                callRingingData, contactModel.getIdentity(), serviceManager
            )
        );
    }

    public void sendEditMessage(int messageModelId, @NonNull String newText, @NonNull Date editedAt) {
        scheduleTask(
            new OutgoingContactEditMessageTask(
                contactModel.getIdentity(),
                messageModelId,
                new MessageId(),
                newText,
                editedAt,
                serviceManager
            )
        );
    }

    public void sendDeleteMessage(int messageModelId, @NonNull Date deletedAt) {
        scheduleTask(
            new OutgoingContactDeleteMessageTask(
                contactModel.getIdentity(),
                messageModelId,
                new MessageId(),
                deletedAt,
                serviceManager
            )
        );
    }

    public void sendReactionMessage(AbstractMessageModel messageModel, Reaction.ActionCase actionCase, @NonNull String emojiSequence, @NonNull Date reactedAt) {
        scheduleTask(
            new OutgoingContactReactionMessageTask(
                contactModel.getIdentity(),
                messageModel.getId(),
                new MessageId(),
                actionCase,
                emojiSequence,
                reactedAt,
                serviceManager
            )
        );
    }

    @Override
    public List<MessageModel> loadMessages(MessageService.MessageFilter filter) {
        return databaseServiceNew.getMessageModelFactory().find(
            contactModel.getIdentity(),
            filter);
    }

    /**
     * Check if there is a call among the latest calls with the given call id.
     *
     * @param callId the call id
     * @param limit  the maximum number of latest calls
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
        return contactService.getAvatar(contactModel, false);
    }

    @Override
    @Nullable
    public Bitmap getAvatar() {
        return contactService.getAvatar(contactModel, true, true);
    }

    @Deprecated
    @Override
    public int getUniqueId() {
        return contactModel != null
            ? ContactUtil.getUniqueId(contactModel.getIdentity())
            : 0;
    }

    @Override
    public String getUniqueIdString() {
        return contactModel != null
            ? ContactUtil.getUniqueIdString(contactModel.getIdentity())
            : "";
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

    @NonNull
    @Override
    public SendingPermissionValidationResult validateSendingPermission() {
        int cannotSendResId = 0;
        if (blockedIdentitiesService.isBlocked(contactModel.getIdentity())) {
            cannotSendResId = R.string.blocked_cannot_send;
        } else {
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

        return cannotSendResId > 0
            ? new SendingPermissionValidationResult.Denied(cannotSendResId)
            : SendingPermissionValidationResult.Valid.INSTANCE;
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
    public void bumpLastUpdate() {
        contactService.bumpLastUpdate(contactModel.getIdentity());
    }

    /**
     * Check whether we should send emoji reactions to this particular MessageReceiver
     *
     * @return true if we should send emoji reactions to this MessageReceiver, false otherwise
     */
    @Override
    @EmojiReactionsSupport
    public int getEmojiReactionSupport() {
        if (!ConfigUtils.canSendEmojiReactions()) {
            return Reactions_NONE;
        }

        return ThreemaFeature.canEmojiReactions((this).getContact().getFeatureMask())
            ? Reactions_FULL
            : Reactions_NONE;
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

    private void scheduleTask(@NonNull Task<?, ActiveTaskCodec> task) {
        taskManager.schedule(task);
    }
}
