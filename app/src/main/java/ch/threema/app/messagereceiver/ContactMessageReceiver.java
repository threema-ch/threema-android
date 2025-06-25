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
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.AppConstants;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.emojis.EmojiUtil;
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
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.crypto.SymmetricEncryptionResult;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;
import ch.threema.data.repositories.ContactModelRepository;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.ThreemaFeature;
import ch.threema.domain.protocol.csp.ProtocolDefines;
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
import ch.threema.storage.DatabaseService;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.data.MessageContentsType;
import ch.threema.storage.models.data.media.FileDataModel;

public class ContactMessageReceiver implements MessageReceiver<MessageModel> {

    private static final Logger logger = LoggingUtil.getThreemaLogger("ContactMessageReceiver");

    private final ContactModel contact;
    private final @Nullable ch.threema.data.models.ContactModel contactModel;
    private final @NonNull ContactModelRepository contactModelRepository;
    private final ContactService contactService;
    private final @NonNull ServiceManager serviceManager;
    private final DatabaseService databaseService;
    private final IdentityStore identityStore;
    private final BlockedIdentitiesService blockedIdentitiesService;
    private final @NonNull TaskManager taskManager;
    private final @NonNull MultiDeviceManager multiDeviceManager;

    public ContactMessageReceiver(
        ContactModel contact,
        ContactService contactService,
        @NonNull ServiceManager serviceManager,
        DatabaseService databaseService,
        IdentityStore identityStore,
        @NonNull BlockedIdentitiesService blockedIdentitiesService,
        @NonNull ContactModelRepository contactModelRepository
    ) {
        this.contact = contact;
        this.contactService = contactService;
        this.serviceManager = serviceManager;
        this.databaseService = databaseService;
        this.identityStore = identityStore;
        this.blockedIdentitiesService = blockedIdentitiesService;
        this.taskManager = serviceManager.getTaskManager();
        this.multiDeviceManager = serviceManager.getMultiDeviceManager();
        this.contactModelRepository = contactModelRepository;

        contactModel = (contact != null) ? contactModelRepository.getByIdentity(contact.getIdentity()) : null;
    }

    protected ContactMessageReceiver(@NonNull ContactMessageReceiver contactMessageReceiver) {
        this(
            contactMessageReceiver.contact,
            contactMessageReceiver.contactService,
            contactMessageReceiver.serviceManager,
            contactMessageReceiver.databaseService,
            contactMessageReceiver.identityStore,
            contactMessageReceiver.blockedIdentitiesService,
            contactMessageReceiver.contactModelRepository
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
        m.setIdentity(contact.getIdentity());
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
        m.setIdentity(contact.getIdentity());
        m.setBody(statusBody);

        saveLocalModel(m);

        return m;
    }

    @Override
    public void saveLocalModel(MessageModel save) {
        databaseService.getMessageModelFactory().createOrUpdate(save);
    }

    @Override
    public void createAndSendTextMessage(@NonNull MessageModel messageModel) {
        // Create and assign a new message id
        messageModel.setApiMessageId(MessageId.random().toString());
        saveLocalModel(messageModel);

        // Mark the contact as non-hidden and unarchived
        contactService.setAcquaintanceLevel(contact.getIdentity(), ContactModel.AcquaintanceLevel.DIRECT);
        contactService.setIsArchived(contact.getIdentity(), false, TriggerSource.LOCAL);

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
        contactService.setAcquaintanceLevel(contact.getIdentity(), ContactModel.AcquaintanceLevel.DIRECT);
        contactService.setIsArchived(contact.getIdentity(), false, TriggerSource.LOCAL);

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
        messageModel.setApiMessageId(MessageId.random().toString());
        saveLocalModel(messageModel);

        // Mark the contact as non-hidden and unarchived
        contactService.setAcquaintanceLevel(contact.getIdentity(), ContactModel.AcquaintanceLevel.DIRECT);
        contactService.setIsArchived(contact.getIdentity(), false, TriggerSource.LOCAL);

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
        contactService.setAcquaintanceLevel(contact.getIdentity(), ContactModel.AcquaintanceLevel.DIRECT);
        contactService.setIsArchived(contact.getIdentity(), false, TriggerSource.LOCAL);

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
        messageModel.setFileData(modelFileData);

        // Create a new message id if the given message id is null
        messageModel.setApiMessageId(messageId != null ? messageId.toString() : MessageId.random().toString());
        saveLocalModel(messageModel);

        // Mark the contact as non-hidden and unarchived
        contactService.setAcquaintanceLevel(contact.getIdentity(), ContactModel.AcquaintanceLevel.DIRECT);
        contactService.setIsArchived(contact.getIdentity(), false, TriggerSource.LOCAL);

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
        contactService.setAcquaintanceLevel(contact.getIdentity(), ContactModel.AcquaintanceLevel.DIRECT);
        contactService.setIsArchived(contact.getIdentity(), false, TriggerSource.LOCAL);

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
        @NonNull BallotModel ballotModel,
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
        contactService.setAcquaintanceLevel(contact.getIdentity(), ContactModel.AcquaintanceLevel.DIRECT);
        contactService.setIsArchived(contact.getIdentity(), false, TriggerSource.LOCAL);


        if (triggerSource == TriggerSource.LOCAL) {
            // Create message id
            MessageId messageId = MessageId.random();

            // Schedule outgoing text message task
            scheduleTask(new OutgoingPollVoteContactMessageTask(
                messageId,
                ballotId,
                ballotModel.getCreatorIdentity(),
                votes,
                contact.getIdentity(),
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
        scheduleTask(new OutgoingTypingIndicatorMessageTask(isTyping, contact.getIdentity(), serviceManager));
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
                receiptType, messageIds, time, contact.getIdentity(), serviceManager
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
                    contact.getIdentity(),
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
                callOfferData, contact.getIdentity(), serviceManager
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
                callAnswerData, contact.getIdentity(), serviceManager
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
                voipICECandidatesData, contact.getIdentity(), serviceManager
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
                callHangupData, contact.getIdentity(), serviceManager
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
                callRingingData, contact.getIdentity(), serviceManager
            )
        );
    }

    public void sendEditMessage(int messageModelId, @NonNull String newText, @NonNull Date editedAt) {
        scheduleTask(
            new OutgoingContactEditMessageTask(
                contact.getIdentity(),
                messageModelId,
                MessageId.random(),
                newText,
                editedAt,
                serviceManager
            )
        );
    }

    public void sendDeleteMessage(int messageModelId, @NonNull Date deletedAt) {
        scheduleTask(
            new OutgoingContactDeleteMessageTask(
                contact.getIdentity(),
                messageModelId,
                MessageId.random(),
                deletedAt,
                serviceManager
            )
        );
    }

    public void sendReaction(AbstractMessageModel messageModel, Reaction.ActionCase actionCase, @NonNull String emojiSequence, @NonNull Date reactedAt) {
        if (getEmojiReactionSupport() == MessageReceiver.Reactions_NONE) {
            sendLegacyReaction(messageModel, actionCase, emojiSequence, reactedAt);
        } else {
            scheduleTask(
                new OutgoingContactReactionMessageTask(
                    contact.getIdentity(),
                    messageModel.getId(),
                    MessageId.random(),
                    actionCase,
                    emojiSequence,
                    reactedAt,
                    serviceManager
                )
            );
        }
    }

    private void sendLegacyReaction(
        AbstractMessageModel messageModel,
        Reaction.ActionCase actionCase,
        @NonNull String emojiSequence,
        @NonNull Date reactedAt
    ) {
        if (actionCase == Reaction.ActionCase.WITHDRAW) {
            // In case we withdraw the reaction we do not send a delivery receipt because
            // withdrawing is not supported with delivery receipts.
            logger.info("Cannot withdraw legacy reaction");
            return;
        }

        // fallback to ack/dec
        if (EmojiUtil.isThumbsUpEmoji(emojiSequence)) {
            if (MessageUtil.canSendUserAcknowledge(messageModel)) {
                try {
                    sendDeliveryReceipt(
                        ProtocolDefines.DELIVERYRECEIPT_MSGUSERACK,
                        new MessageId[] {MessageId.fromString(messageModel.getApiMessageId())},
                        reactedAt.getTime()
                    );
                } catch (ThreemaException e) {
                    logger.error("Could not sent ack message", e);
                }
            } else {
                logger.error("Unable to send ack message.");
            }
        } else if (EmojiUtil.isThumbsDownEmoji(emojiSequence)) {
            if (MessageUtil.canSendUserDecline(messageModel)) {
                try {
                    sendDeliveryReceipt(
                        ProtocolDefines.DELIVERYRECEIPT_MSGUSERACK,
                        new MessageId[] {MessageId.fromString(messageModel.getApiMessageId())},
                        reactedAt.getTime()
                    );
                } catch (ThreemaException e) {
                    logger.error("Could not sent ack message", e);
                }
            } else {
                logger.error("Unable to send dec message");
            }
        }
    }

    @Override
    public List<MessageModel> loadMessages(MessageService.MessageFilter filter) {
        return databaseService.getMessageModelFactory().find(
            contact.getIdentity(),
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
        return databaseService.getMessageModelFactory().hasVoipStatusForCallId(contact.getIdentity(), callId, limit);
    }

    @Override
    public long getMessagesCount() {
        return databaseService.getMessageModelFactory().countMessages(
            contact.getIdentity());
    }

    @Override
    public long getUnreadMessagesCount() {
        return databaseService.getMessageModelFactory().countUnreadMessages(
            contact.getIdentity());
    }

    @NonNull
    @Override
    public List<MessageModel> getUnreadMessages() {
        return databaseService.getMessageModelFactory().getUnreadMessages(
            contact.getIdentity());
    }

    public MessageModel getLastMessage() {
        return databaseService.getMessageModelFactory().getLastMessage(
            contact.getIdentity());
    }

    public ContactModel getContact() {
        return contact;
    }

    @Nullable
    public ch.threema.data.models.ContactModel getContactModel() {
        return contactModel;
    }

    @Override
    public boolean isEqual(MessageReceiver o) {
        return o instanceof ContactMessageReceiver && ((ContactMessageReceiver) o).getContact().getIdentity().equals(getContact().getIdentity());
    }

    @Override
    public String getDisplayName() {
        return NameUtil.getDisplayNameOrNickname(contact, true);
    }

    @Override
    public String getShortName() {
        return NameUtil.getShortName(contact);
    }

    @Override
    public void prepareIntent(Intent intent) {
        intent.putExtra(AppConstants.INTENT_DATA_CONTACT, contact.getIdentity());
    }

    @Override
    @Nullable
    public Bitmap getNotificationAvatar() {
        return contactService.getAvatar(contact, false);
    }

    @Override
    @Nullable
    public Bitmap getAvatar() {
        return contactService.getAvatar(contact, true, true);
    }

    @Deprecated
    @Override
    public int getUniqueId() {
        return contact != null
            ? ContactUtil.getUniqueId(contact.getIdentity())
            : 0;
    }

    @NonNull
    @Override
    public String getUniqueIdString() {
        return contact != null
            ? ContactUtil.getUniqueIdString(contact.getIdentity())
            : "";
    }

    @Override
    public boolean isMessageBelongsToMe(AbstractMessageModel message) {
        return message instanceof MessageModel
            && message.getIdentity().equals(contact.getIdentity());
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
        if (blockedIdentitiesService.isBlocked(contact.getIdentity())) {
            cannotSendResId = R.string.blocked_cannot_send;
        } else {
            if (contact.getState() != null) {
                switch (contact.getState()) {
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
        return new String[]{contact.getIdentity()};
    }

    @Override
    public void bumpLastUpdate() {
        contactService.bumpLastUpdate(contact.getIdentity());
    }

    /**
     * Check whether we should send emoji reactions to this particular MessageReceiver
     *
     * @return [Reactions_FULL] if we should send emoji reactions to this MessageReceiver, [Reactions_NONE] otherwise
     */
    @Override
    @EmojiReactionsSupport
    public int getEmojiReactionSupport() {
        return ThreemaFeature.canEmojiReactions((this).getContact().getFeatureMask())
            ? Reactions_FULL
            : Reactions_NONE;
    }

    @Override
    public @NonNull String toString() {
        return "ContactMessageReceiver (identity = " + contact.getIdentity() + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContactMessageReceiver)) return false;
        ContactMessageReceiver that = (ContactMessageReceiver) o;
        return Objects.equals(contact, that.contact);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contact);
    }

    private void scheduleTask(@NonNull Task<?, ActiveTaskCodec> task) {
        taskManager.schedule(task);
    }
}
