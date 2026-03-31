package ch.threema.app.utils;

import android.content.Context;

import org.slf4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.adapters.decorators.GroupStatusAdapterDecorator;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.UserService;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import ch.threema.app.ui.models.MessageViewElement;
import ch.threema.data.datatypes.ContactNameFormat;
import ch.threema.data.models.GroupModel;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.messages.DeleteMessage;
import ch.threema.domain.protocol.csp.messages.file.FileData;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerData;
import ch.threema.localcrypto.exceptions.MasterKeyLockedException;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.data.media.AudioDataModel;
import ch.threema.storage.models.data.media.BallotDataModel;
import ch.threema.storage.models.data.media.FileDataModel;
import ch.threema.storage.models.data.media.VideoDataModel;
import ch.threema.storage.models.data.status.StatusDataModel;
import ch.threema.storage.models.group.GroupMessageModel;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.data.LocationDataModel;
import ch.threema.storage.models.data.MessageContentsType;
import ch.threema.storage.models.data.status.ForwardSecurityStatusDataModel;
import ch.threema.storage.models.data.status.GroupCallStatusDataModel;
import ch.threema.storage.models.data.status.GroupStatusDataModel;
import ch.threema.storage.models.data.status.VoipStatusDataModel;

public class MessageUtil {
    private static final Logger logger = getThreemaLogger("MessageUtil");

    private final static Set<MessageType> fileMessageModelTypes = EnumSet.of(
        MessageType.IMAGE,
        MessageType.VOICEMESSAGE,
        MessageType.VIDEO,
        MessageType.FILE);

    private final static Set<MessageType> thumbnailFileMessageModelTypes = EnumSet.of(
        MessageType.IMAGE,
        MessageType.VIDEO,
        MessageType.FILE);

    private final static Set<MessageType> lowProfileMessageModelTypes = EnumSet.of(
        MessageType.IMAGE,
        MessageType.VOICEMESSAGE);

    @NonNull
    public static String getDisplayDate(
        @NonNull Context context,
        @Nullable Date postedAt,
        boolean isOutbox,
        @Nullable Date modifiedAt,
        boolean full
    ) {
        if (postedAt == null && modifiedAt == null) {
            return "";
        }
        final @Nullable Date date = getDisplayDate(postedAt, isOutbox, modifiedAt);
        if (date != null) {
            return LocaleUtil.formatTimeStampString(context, date.getTime(), full);
        } else {
            return "";
        }
    }

    @Nullable
    public static Date getDisplayDate(
        @Nullable Date postedAt,
        boolean isOutbox,
        @Nullable Date modifiedAt
    ) {
        if (isOutbox && modifiedAt != null) {
            return modifiedAt;
        } else {
            return postedAt;
        }
    }

    @Nullable
    public static Instant getDisplayInstant(
        @Nullable Date postedAt,
        boolean isOutbox,
        @Nullable Date modifiedAt
    ) {
        var date = getDisplayDate(postedAt, isOutbox, modifiedAt);
        return date != null ? date.toInstant() : null;
    }

    public static boolean hasDataFile(AbstractMessageModel messageModel) {
        return messageModel != null && fileMessageModelTypes.contains(messageModel.getType());
    }

    /**
     * Checks whether the message holds a file which should be rendered as a file attachment, i.e., not as media
     */
    public static boolean hasFileWithDefaultRendering(@NonNull AbstractMessageModel message) {
        return message.getType() == MessageType.FILE && message.getFileData().getRenderingType() == FileData.RENDERING_DEFAULT;
    }

    /**
     * This method indicates whether the message is a type that can have a thumbnail.
     * Note that it's still possible that a message does not (yet) have a thumbnail stored,
     * even though this method returns true.
     */
    public static boolean canHaveThumbnailFile(AbstractMessageModel messageModel) {
        return messageModel != null && thumbnailFileMessageModelTypes.contains(messageModel.getType());
    }

    public static Set<MessageType> getFileTypes() {
        return fileMessageModelTypes;
    }

    public static Set<MessageType> getLowProfileMessageModelTypes() {
        return lowProfileMessageModelTypes;
    }

    public static boolean canSendDeliveryReceipt(AbstractMessageModel message, int receiptType) {
        if (ConfigUtils.isGroupAckEnabled() && (receiptType == ProtocolDefines.DELIVERYRECEIPT_MSGUSERACK || receiptType == ProtocolDefines.DELIVERYRECEIPT_MSGUSERDEC)) {
            return (message instanceof MessageModel || message instanceof GroupMessageModel)
                && !message.isOutbox()
                && !message.isRead()
                && !message.isStatusMessage()
                && message.getType() != MessageType.VOIP_STATUS
                && !((message.getMessageFlags() & ProtocolDefines.MESSAGE_FLAG_NO_DELIVERY_RECEIPTS) == ProtocolDefines.MESSAGE_FLAG_NO_DELIVERY_RECEIPTS);
        } else {
            return message instanceof MessageModel
                && !message.isOutbox()
                && !message.isRead()
                && !message.isStatusMessage()
                && message.getType() != MessageType.VOIP_STATUS
                && !((message.getMessageFlags() & ProtocolDefines.MESSAGE_FLAG_NO_DELIVERY_RECEIPTS) == ProtocolDefines.MESSAGE_FLAG_NO_DELIVERY_RECEIPTS);
        }
    }

    /**
     * @return true if the message model can mark as read
     */
    public static boolean canMarkAsRead(AbstractMessageModel message) {
        return message != null
            && !message.isOutbox()
            && !message.isRead();
    }

    /**
     * @return true if the message model can mark as consumed
     */
    public static boolean canMarkAsConsumed(@Nullable AbstractMessageModel message) {
        return
            (message instanceof MessageModel || message instanceof GroupMessageModel)
                && !message.isStatusMessage()
                && !message.isOutbox()
                && message.getState() != MessageState.CONSUMED
                && (message.getMessageContentsType() == MessageContentsType.VOICE_MESSAGE ||
                message.getMessageContentsType() == MessageContentsType.AUDIO)
                && (message.getState() == null || canChangeToState(message.getState(), MessageState.CONSUMED, message instanceof GroupMessageModel));
    }

    /**
     * @return true, if the user-acknowledge flag can be set
     */
    public static boolean canSendUserAcknowledge(AbstractMessageModel messageModel) {
        if (ConfigUtils.isGroupAckEnabled()) {
            return
                messageModel != null
                    && (!messageModel.isOutbox() || messageModel instanceof GroupMessageModel)
                    && messageModel.getState() != MessageState.USERACK
                    && messageModel.getType() != MessageType.VOIP_STATUS
                    && messageModel.getType() != MessageType.GROUP_CALL_STATUS
                    && !messageModel.isStatusMessage()
                    && !(messageModel instanceof DistributionListMessageModel)
                    && !messageModel.isDeleted();
        } else {
            return
                messageModel != null
                    && !messageModel.isOutbox()
                    && messageModel.getState() != MessageState.USERACK
                    && messageModel.getType() != MessageType.VOIP_STATUS
                    && messageModel.getType() != MessageType.GROUP_CALL_STATUS
                    && !messageModel.isStatusMessage()
                    && !(messageModel instanceof DistributionListMessageModel)
                    && !(messageModel instanceof GroupMessageModel)
                    && !messageModel.isDeleted();
        }
    }

    /**
     * @return true, if the user-decline flag can be set
     */
    public static boolean canSendUserDecline(AbstractMessageModel messageModel) {
        if (ConfigUtils.isGroupAckEnabled()) {
            return
                messageModel != null
                    && (!messageModel.isOutbox() || messageModel instanceof GroupMessageModel)
                    && messageModel.getState() != MessageState.USERDEC
                    && messageModel.getType() != MessageType.VOIP_STATUS
                    && messageModel.getType() != MessageType.GROUP_CALL_STATUS
                    && !messageModel.isStatusMessage()
                    && !(messageModel instanceof DistributionListMessageModel)
                    && !messageModel.isDeleted();
        } else {
            return
                messageModel != null
                    && !messageModel.isOutbox()
                    && messageModel.getState() != MessageState.USERDEC
                    && messageModel.getType() != MessageType.VOIP_STATUS
                    && messageModel.getType() != MessageType.GROUP_CALL_STATUS
                    && !messageModel.isStatusMessage()
                    && !(messageModel instanceof DistributionListMessageModel)
                    && !(messageModel instanceof GroupMessageModel)
                    && !messageModel.isDeleted();
        }
    }

    public static boolean canSendImageReply(@Nullable AbstractMessageModel messageModel) {
        if (messageModel == null ||
            messageModel.getMessageContentsType() != MessageContentsType.IMAGE ||
            messageModel.isDeleted()) {
            return false;
        }
        try {
            return messageModel.getFileData().isDownloaded();
        } catch (ClassCastException exception) {
            // No file data
            logger.warn("No file data available");
        }
        try {
            return messageModel.getImageData().isDownloaded();
        } catch (ClassCastException ignored) {
            // No image data
            logger.warn("No image data available");
            return false;
        }
    }

    /**
     * @return true if the user-acknowledge flag visible
     */
    public static boolean showStatusIcon(AbstractMessageModel messageModel) {
        boolean showState = false;
        if (messageModel != null) {
            if (messageModel.getType() == MessageType.VOIP_STATUS) {
                return false;
            }

            MessageState messageState = messageModel.getState();

            //group message/distribution list message icons only on pending or failing states
            if (messageModel instanceof GroupMessageModel) {
                if (messageState != null) {
                    if (messageModel.isOutbox()) {
                        showState = messageState == MessageState.SENDFAILED
                            || messageState == MessageState.FS_KEY_MISMATCH
                            || messageState == MessageState.SENDING
                            || (messageState == MessageState.PENDING && messageModel.getType() != MessageType.BALLOT);
                    } else {
                        showState = messageModel.getState() == MessageState.CONSUMED;
                    }
                }
            } else if (messageModel instanceof MessageModel) {
                if (!messageModel.isOutbox()) {
                    // inbox show icon only on acknowledged/declined or consumed
                    showState = messageState != null
                        && messageModel.getState() == MessageState.CONSUMED;
                } else {
                    // on outgoing message
                    if (ContactUtil.isGatewayContact(messageModel.getIdentity())) {
                        showState = messageState == MessageState.SENDFAILED
                            || messageState == MessageState.FS_KEY_MISMATCH
                            || messageState == MessageState.PENDING
                            || messageState == MessageState.SENDING;
                    } else {
                        showState = true;
                    }
                }
            }
        }
        return showState;
    }

    public static boolean isUnread(@Nullable AbstractMessageModel messageModel) {
        return messageModel != null
            && !messageModel.isStatusMessage()
            && !messageModel.isOutbox()
            && !messageModel.isRead();
    }

    /**
     * @return true, if the "system" automatically can generate a thumbnail file
     */
    public static boolean autoGenerateThumbnail(AbstractMessageModel messageModel) {
        return messageModel != null
            && messageModel.getType() == MessageType.IMAGE;
    }

    /**
     * Returns all affected receivers of a distribution list (including itself)
     *
     * @return ArrayList of all MessageReceivers
     */
    public static ArrayList<MessageReceiver> getAllReceivers(final MessageReceiver messageReceiver) {
        ArrayList<MessageReceiver> allReceivers = new ArrayList<>();
        allReceivers.add(messageReceiver);

        List<MessageReceiver> affectedReceivers = messageReceiver.getAffectedMessageReceivers();
        if (affectedReceivers != null && !affectedReceivers.isEmpty()) {
            allReceivers.addAll(
                affectedReceivers.stream()
                    .filter(receiver -> receiver != null && !receiver.isEqual(messageReceiver))
                    .collect(Collectors.toList())
            );
        }
        return allReceivers;
    }

    /**
     * Expand list of MessageReceivers to contain distribution list receivers as single recipients
     *
     * @param allReceivers - list of MessageReceivers including distribution lists
     * @return - expanded list of receivers with duplicates removed
     */
    public static MessageReceiver[] addDistributionListReceivers(MessageReceiver[] allReceivers) {
        // Use LinkedHashSet in order to preserve insertion order.
        // If the order is not preserved sending of files to distribution lists is likely to fail
        Set<MessageReceiver> resolvedReceivers = new LinkedHashSet<>();
        for (MessageReceiver receiver : allReceivers) {
            if (receiver.getType() == MessageReceiver.Type_DISTRIBUTION_LIST) {
                resolvedReceivers.addAll(MessageUtil.getAllReceivers(receiver));
            } else {
                resolvedReceivers.add(receiver);
            }
        }
        return resolvedReceivers.toArray(new MessageReceiver[0]);
    }

    /**
     * Check if a MessageState change from fromState to toState is allowed
     *
     * @param fromState      State from which a state change is requested
     * @param toState        State to which a state change is requested
     * @param isGroupMessage true, if it's a group message
     * @return true if a state change is allowed, false otherwise
     */
    public static boolean canChangeToState(@Nullable MessageState fromState, @Nullable MessageState toState, boolean isGroupMessage) {
        if (fromState == null || toState == null) {
            //invalid data
            return false;
        }

        if (fromState == toState) {
            return false;
        }

        switch (toState) {
            case DELIVERED:
                return fromState == MessageState.SENDING
                    || fromState == MessageState.SENDFAILED
                    || fromState == MessageState.FS_KEY_MISMATCH
                    || fromState == MessageState.PENDING
                    || fromState == MessageState.SENT;
            case READ:
                return fromState == MessageState.SENDING
                    || fromState == MessageState.SENDFAILED
                    || fromState == MessageState.FS_KEY_MISMATCH
                    || fromState == MessageState.PENDING
                    || fromState == MessageState.SENT
                    || fromState == MessageState.DELIVERED;
            case SENDFAILED:
                return fromState == MessageState.SENDING
                    || fromState == MessageState.PENDING
                    || fromState == MessageState.TRANSCODING
                    || fromState == MessageState.UPLOADING;
            case FS_KEY_MISMATCH:
                return fromState == MessageState.SENDING
                    || fromState == MessageState.PENDING
                    || fromState == MessageState.TRANSCODING
                    || fromState == MessageState.SENT;
            case SENT:
                return fromState == MessageState.SENDING
                    || fromState == MessageState.SENDFAILED
                    || fromState == MessageState.FS_KEY_MISMATCH
                    || fromState == MessageState.PENDING
                    || fromState == MessageState.TRANSCODING
                    || fromState == MessageState.UPLOADING;
            case USERACK:
                return true;
            case USERDEC:
                return true;
            case CONSUMED:
                return fromState != MessageState.USERACK
                    && fromState != MessageState.USERDEC;
            case PENDING:
                return fromState == MessageState.SENDFAILED
                    || (fromState == MessageState.FS_KEY_MISMATCH && !isGroupMessage);
            case SENDING:
                return fromState == MessageState.SENDFAILED
                    || (fromState == MessageState.FS_KEY_MISMATCH && !isGroupMessage)
                    || fromState == MessageState.PENDING
                    || fromState == MessageState.TRANSCODING
                    || fromState == MessageState.UPLOADING;
            case UPLOADING:
                return fromState == MessageState.SENDFAILED
                    || fromState == MessageState.FS_KEY_MISMATCH
                    || fromState == MessageState.PENDING
                    || fromState == MessageState.TRANSCODING;
            default:
                logger.debug("message state {} not handled", toState);
                return false;
        }
    }

    public static String getCaptionText(AbstractMessageModel messageModel) {
        if (messageModel != null) {
            switch (messageModel.getType()) {
                case FILE:
                    return messageModel.getFileData().getCaption();
                case IMAGE:
                    return messageModel.getCaption();
                default:
                    break;
            }
        }
        return null;
    }

    /**
     * Check if the provided MessageState is a user reaction.
     *
     * @param state the message state
     * @return true if it is a user reaction, false otherwise
     */
    public static boolean isReaction(MessageState state) {
        return state == MessageState.USERACK || state == MessageState.USERDEC;
    }

    @NonNull
    public static MessageViewElement getViewElement(
        @NonNull Context context,
        @Nullable AbstractMessageModel messageModel,
        @NonNull final ContactNameFormat contactNameFormat
    ) {
        if (messageModel == null) {
            return new MessageViewElement(null, null, null, null, null);
        }
        return getViewElement(
            context,
            messageModel.getType(),
            messageModel.getBody(),
            messageModel.getCaption(),
            messageModel.isOutbox(),
            contactNameFormat
        );
    }

    @NonNull
    public static MessageViewElement getViewElement(
        @NonNull Context context,
        @Nullable MessageType messageType,
        @Nullable String messageBody,
        @Nullable String messageCaption,
        @Nullable Boolean isOutbox,
        @NonNull final ContactNameFormat contactNameFormat
    ) {
        if (messageType == null || isOutbox == null) {
            return new MessageViewElement(null, null, null, null, null);
        }
        switch (messageType) {
            case TEXT:
                return new MessageViewElement(
                    null,
                    null,
                    QuoteUtil.getMessageBody(messageType, messageBody, messageCaption, isOutbox, false, contactNameFormat),
                    null,
                    null
                );
            case IMAGE:
                return new MessageViewElement(
                    R.drawable.ic_photo_filled,
                    context.getString(R.string.image_placeholder),
                    TestUtil.isEmptyOrNull(messageCaption) ? null : messageCaption,
                    null,
                    null
                );
            case VIDEO:
                final @NonNull VideoDataModel videoDataModel;
                if (messageBody != null && !messageBody.isEmpty()) {
                    videoDataModel = VideoDataModel.create(messageBody);
                } else {
                    videoDataModel = VideoDataModel.createEmpty();
                }
                return new MessageViewElement(
                    R.drawable.ic_movie_filled,
                    context.getString(R.string.video_placeholder),
                    videoDataModel.getDurationString(),
                    null,
                    null
                );
            case LOCATION:
                final @NonNull LocationDataModel locationDataModel;
                if (messageBody != null && !messageBody.isEmpty()) {
                    locationDataModel = LocationDataModel.fromStringOrDefault(messageBody);
                } else {
                    locationDataModel = LocationDataModel.createEmpty();
                }
                @Nullable String locationText = null;
                if (locationDataModel.poiNameOrNull != null) {
                    locationText = locationDataModel.poiNameOrNull;
                } else if (locationDataModel.poiAddressOrNull != null) {
                    locationText = locationDataModel.poiAddressOrNull;
                }
                return new MessageViewElement(
                    R.drawable.ic_location_on_filled,
                    context.getString(R.string.location_placeholder),
                    locationText,
                    null,
                    null
                );
            case VOICEMESSAGE:
                int duration = 0;
                if (messageBody != null && !messageBody.isEmpty()) {
                    final @NonNull AudioDataModel audioDataModel = AudioDataModel.create(messageBody);
                    duration = audioDataModel.getDuration();
                }
                return new MessageViewElement(
                    R.drawable.ic_mic_filled,
                    context.getString(R.string.audio_placeholder),
                    ElapsedTimeFormatter.secondsToString(duration),
                    ". " + context.getString(R.string.duration) + " " + ElapsedTimeFormatter.getDurationStringHuman(context, duration) + ". ",
                    null
                );
            case FILE:

                final @NonNull FileDataModel fileDataModel;
                if (messageBody != null && !messageBody.isEmpty()) {
                    fileDataModel = FileDataModel.create(messageBody);
                } else {
                    fileDataModel = FileDataModel.createEmpty();
                }

                if (MimeUtil.isImageFile(fileDataModel.getMimeType())) {
                    return new MessageViewElement(
                        R.drawable.ic_photo_filled,
                        context.getString(R.string.image_placeholder),
                        TestUtil.isEmptyOrNull(fileDataModel.getCaption())
                            ? null
                            : fileDataModel.getCaption(),
                        null,
                        null
                    );
                }

                if (MimeUtil.isVideoFile(fileDataModel.getMimeType())) {
                    String durationString = fileDataModel.getDurationString();

                    return new MessageViewElement(
                        R.drawable.ic_movie_filled,
                        context.getString(R.string.video_placeholder),
                        TestUtil.isEmptyOrNull(fileDataModel.getCaption())
                            ? durationString
                            : fileDataModel.getCaption(),
                        null,
                        null
                    );
                }

                if (MimeUtil.isAudioFile(fileDataModel.getMimeType())) {
                    String durationString = fileDataModel.getDurationString();

                    if (fileDataModel.getRenderingType() == FileData.RENDERING_MEDIA) {
                        return new MessageViewElement(
                            R.drawable.ic_mic_filled,
                            context.getString(R.string.audio_placeholder),
                            durationString,
                            ". " + context.getString(R.string.duration) + " " + ElapsedTimeFormatter.getDurationStringHuman(context, fileDataModel.getDurationSeconds()) + ". ",
                            null
                        );
                    } else {
                        return new MessageViewElement(
                            R.drawable.ic_doc_audio,
                            context.getString(R.string.audio_placeholder),
                            TestUtil.isEmptyOrNull(fileDataModel.getCaption())
                                ? ("00:00".equals(durationString) ? null : durationString)
                                : fileDataModel.getCaption(),
                            null,
                            null
                        );
                    }
                }

                return new MessageViewElement(
                    IconUtil.getMimeIcon(fileDataModel.getMimeType()),
                    context.getString(R.string.file_placeholder),
                    TestUtil.isEmptyOrNull(fileDataModel.getCaption())
                        ? fileDataModel.getFileName()
                        : fileDataModel.getCaption(),
                    null,
                    null
                );

            case BALLOT:
                @Nullable String messageString = null;
                if (messageBody != null && !messageBody.isEmpty()) {
                    final @Nullable BallotDataModel ballotDataModel = BallotDataModel.create(messageBody);
                    messageString = BallotUtil.getNotificationString(context, ballotDataModel.getBallotId());
                }
                return new MessageViewElement(
                    R.drawable.ic_baseline_rule,
                    context.getString(R.string.ballot_placeholder),
                    TestUtil.isEmptyOrNull(messageString)
                        ? null
                        : messageString,
                    null,
                    null
                );
            case GROUP_STATUS:
                final @Nullable GroupStatusDataModel groupStatusDataModel = (GroupStatusDataModel) StatusDataModel.convert(messageBody);
                if (groupStatusDataModel == null) {
                    return new MessageViewElement(null, null, null, null, null);
                }
                final @Nullable ServiceManager serviceManager = ThreemaApplication.getServiceManager();
                @Nullable ContactService contactService = null;
                @Nullable UserService userService = null;
                if (serviceManager != null) {
                    try {
                        contactService = serviceManager.getContactService();
                    } catch (MasterKeyLockedException e) {
                        logger.error("Could not get contact service", e);
                        // Don't abort: if the contact service cannot be created, then the
                        // status messages only show the threema id instead of the display name
                    }
                    userService = serviceManager.getUserService();
                }

                String statusText = GroupStatusAdapterDecorator.getStatusText(
                    groupStatusDataModel,
                    userService,
                    contactService,
                    contactNameFormat,
                    context
                );

                return new MessageViewElement(
                    null,
                    statusText,
                    statusText,
                    null,
                    null
                );
            case VOIP_STATUS:
                final @Nullable VoipStatusDataModel voipStatusDataModel = (VoipStatusDataModel) StatusDataModel.convert(messageBody);
                if (voipStatusDataModel != null) {
                    switch (voipStatusDataModel.getStatus()) {
                        case VoipStatusDataModel.REJECTED:
                            // Determine reject reason
                            final Byte reasonCodeByte = voipStatusDataModel.getReason();
                            final byte reasonCode = reasonCodeByte == null
                                ? VoipCallAnswerData.RejectReason.UNKNOWN
                                : reasonCodeByte;

                            // Default values
                            int rejectColor = R.color.material_red;
                            String rejectPlaceholder = isOutbox
                                ? context.getString(R.string.voip_call_status_rejected)
                                : context.getString(R.string.voip_call_status_missed);

                            // Provide more details for certain reject reasons
                            //noinspection NestedSwitchStatement
                            switch (reasonCode) {
                                case VoipCallAnswerData.RejectReason.BUSY:
                                    rejectPlaceholder = isOutbox
                                        ? context.getString(R.string.voip_call_status_busy)
                                        : context.getString(R.string.voip_call_status_missed) + " (" + context.getString(R.string.voip_call_status_busy_short) + ")";
                                    break;
                                case VoipCallAnswerData.RejectReason.TIMEOUT:
                                    rejectPlaceholder = isOutbox
                                        ? context.getString(R.string.voip_call_status_unavailable)
                                        : context.getString(R.string.voip_call_status_missed);
                                    break;
                                case VoipCallAnswerData.RejectReason.REJECTED:
                                    rejectPlaceholder = context.getString(R.string.voip_call_status_rejected);
                                    rejectColor = isOutbox
                                        ? R.color.material_red
                                        : R.color.material_orange;
                                    break;
                                case VoipCallAnswerData.RejectReason.DISABLED:
                                    rejectPlaceholder = isOutbox
                                        ? context.getString(R.string.voip_call_status_disabled)
                                        : context.getString(R.string.voip_call_status_rejected);
                                    rejectColor = isOutbox
                                        ? R.color.material_red
                                        : R.color.material_orange;
                                    break;
                                case VoipCallAnswerData.RejectReason.OFF_HOURS:
                                    rejectPlaceholder = context.getString(R.string.voip_call_status_off_hours);
                                    rejectColor = isOutbox
                                        ? R.color.material_red
                                        : R.color.material_orange;
                                    break;
                            }
                            return new MessageViewElement(
                                isOutbox
                                    ? R.drawable.ic_call_missed_outgoing_black_24dp
                                    : R.drawable.ic_call_missed_black_24dp,
                                rejectPlaceholder,
                                rejectPlaceholder,
                                null,
                                rejectColor
                            );
                        case VoipStatusDataModel.ABORTED:
                            return new MessageViewElement(
                                R.drawable.ic_call_missed_outgoing_black_24dp,
                                context.getString(R.string.voip_call_status_aborted),
                                context.getString(R.string.voip_call_status_aborted),
                                null,
                                R.color.material_orange
                            );
                        case VoipStatusDataModel.MISSED:
                            return new MessageViewElement(
                                isOutbox
                                    ? R.drawable.ic_call_missed_outgoing_black_24dp
                                    : R.drawable.ic_call_missed_black_24dp,
                                context.getString(R.string.voip_call_status_missed),
                                context.getString(R.string.voip_call_status_missed),
                                null,
                                R.color.material_red
                            );
                        case VoipStatusDataModel.FINISHED:
                            return new MessageViewElement(
                                isOutbox
                                    ? R.drawable.ic_call_made_black_24dp
                                    : R.drawable.ic_call_received_black_24dp,
                                context.getString(
                                    isOutbox
                                        ? R.string.voip_call_finished_outbox
                                        : R.string.voip_call_finished_inbox
                                ),
                                context.getString(
                                    isOutbox
                                        ? R.string.voip_call_finished_outbox
                                        : R.string.voip_call_finished_inbox
                                ),
                                null,
                                R.color.material_green
                            );
                    }
                }
                break;
            case GROUP_CALL_STATUS:
                final @Nullable GroupCallStatusDataModel groupCallStatusDataModel = (GroupCallStatusDataModel) StatusDataModel.convert(messageBody);
                if (groupCallStatusDataModel != null) {
                    switch (groupCallStatusDataModel.getStatus()) {
                        case GroupCallStatusDataModel.STATUS_STARTED:
                            String body = context.getString(R.string.voip_gc_call_started);
                            if (groupCallStatusDataModel.getCallerIdentity() != null) {
                                try {
                                    contactService = ThreemaApplication.getServiceManager().getContactService();
                                    body = String.format(
                                        context.getString(
                                            isOutbox
                                                ? R.string.voip_gc_notification_call_started_generic_outbox
                                                : R.string.voip_gc_notification_call_started_generic
                                        ),
                                        NameUtil.getShortName(groupCallStatusDataModel.getCallerIdentity(), contactService, contactNameFormat)
                                    );
                                } catch (Exception e) {
                                    logger.debug("Contact service unavailable");
                                }
                            }

                            return new MessageViewElement(
                                R.drawable.ic_phone_locked_outline,
                                context.getString(R.string.voip_gc_call_started),
                                body,
                                null,
                                null
                            );
                        case GroupCallStatusDataModel.STATUS_ENDED:
                            return new MessageViewElement(
                                R.drawable.ic_phone_locked_outline,
                                context.getString(R.string.voip_gc_call_ended),
                                context.getString(R.string.voip_gc_call_ended),
                                null,
                                null
                            );
                    }
                    break;
                }
            case FORWARD_SECURITY_STATUS:
                final @Nullable ForwardSecurityStatusDataModel forwardSecurityStatusDataModel =
                    (ForwardSecurityStatusDataModel) StatusDataModel.convert(messageBody);
                if (forwardSecurityStatusDataModel != null) {
                    switch (forwardSecurityStatusDataModel.getStatus()) {
                        case ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_RESET:
                            return new MessageViewElement(
                                R.drawable.ic_baseline_key_off_24,
                                context.getString(R.string.forward_security_reset_simple),
                                context.getString(R.string.forward_security_reset_simple),
                                null,
                                R.color.material_red
                            );
                        case ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_ESTABLISHED:
                            return new MessageViewElement(
                                R.drawable.ic_baseline_key_24,
                                context.getString(R.string.forward_security_established),
                                context.getString(R.string.forward_security_established),
                                null,
                                R.color.material_green
                            );
                        case ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_ESTABLISHED_RX:
                            return new MessageViewElement(
                                R.drawable.ic_baseline_key_24,
                                context.getString(R.string.forward_security_established_rx),
                                context.getString(R.string.forward_security_established_rx),
                                null,
                                R.color.material_green
                            );
                        case ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_MESSAGES_SKIPPED:
                            String body = ConfigUtils.getSafeQuantityString(context, R.plurals.forward_security_messages_skipped, forwardSecurityStatusDataModel.getQuantity(), forwardSecurityStatusDataModel.getQuantity());
                            return new MessageViewElement(
                                R.drawable.ic_baseline_key_24,
                                body,
                                body,
                                null,
                                null
                            );
                        case ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_MESSAGE_OUT_OF_ORDER:
                            return new MessageViewElement(
                                R.drawable.ic_baseline_key_24,
                                context.getString(R.string.forward_security_message_out_of_order),
                                context.getString(R.string.forward_security_message_out_of_order),
                                null,
                                null
                            );
                        case ForwardSecurityStatusDataModel.ForwardSecurityStatusType.MESSAGE_WITHOUT_FORWARD_SECURITY:
                            return new MessageViewElement(
                                R.drawable.ic_baseline_key_off_24,
                                context.getString(R.string.message_without_forward_security),
                                context.getString(R.string.message_without_forward_security),
                                null,
                                null
                            );
                        case ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_UNAVAILABLE_DOWNGRADE:
                            return new MessageViewElement(
                                R.drawable.ic_baseline_key_24,
                                context.getString(R.string.forward_security_downgraded_status_message),
                                context.getString(R.string.forward_security_downgraded_status_message),
                                null,
                                null
                            );
                        case ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_ILLEGAL_SESSION_STATE:
                            return new MessageViewElement(
                                R.drawable.ic_baseline_key_off_24,
                                context.getString(R.string.forward_security_illegal_session_status_message),
                                context.getString(R.string.forward_security_illegal_session_status_message),
                                null,
                                null
                            );
                        // TODO(ANDR-2519): Can this be removed when md supports fs? Only if this
                        //  type has never been stored to the database
                        case ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_DISABLED:
                            return new MessageViewElement(
                                R.drawable.ic_baseline_key_off_24,
                                context.getString(R.string.forward_security_disabled),
                                context.getString(R.string.forward_security_disabled),
                                null,
                                null
                            );
                        default:
                            return new MessageViewElement(
                                R.drawable.ic_baseline_key_24,
                                forwardSecurityStatusDataModel.getStaticText(),
                                forwardSecurityStatusDataModel.getStaticText(),
                                null,
                                null
                            );
                    }
                }
                break;
        }

        return new MessageViewElement(null, null, null, null, null);
    }

    /**
     * Check whether a file message is being sent. This includes the states PENDING, UPLOADING, and
     * SENDING. This method only returns true for outgoing messages. Note that for file messages
     * that are in state TRANSCODING this method returns false.
     */
    public static boolean isFileMessageBeingSent(@NonNull AbstractMessageModel model) {
        MessageState messageState = model.getState();
        return model.isOutbox() && (
            messageState == MessageState.PENDING ||
                messageState == MessageState.UPLOADING ||
                messageState == MessageState.SENDING
        );
    }

    /**
     * Check whether the given message type allows remote deletion of messages. Note that only the
     * message type is considered. To check whether the user should be able to delete a message for
     * everyone, {@link #canDeleteRemotely(AbstractMessageModel, MessageReceiver)} should be used.
     */
    public static boolean doesMessageTypeAllowRemoteDeletion(@Nullable MessageType messageType) {
        if (messageType == null) {
            return false;
        }

        switch (messageType) {
            case TEXT:
            case IMAGE:
            case VIDEO:
            case VOICEMESSAGE:
            case LOCATION:
            case CONTACT:
            case FILE:
                return true;
            default:
                return false;
        }
    }

    /**
     * Check whether the user should be able to delete the given message remotely at this point in time.
     */
    // TODO(ANDR-4222): Refactor this method
    public static boolean canDeleteRemotely(
        @NonNull AbstractMessageModel message,
        @NonNull MessageReceiver receiver
    ) {
        return doesMessageTypeAllowRemoteDeletion(message.getType())
            && !message.isStatusMessage()
            && message.isOutbox()
            && isStillInValidTimeFrameToDeleteRemotely(message, receiver)
            && (message instanceof MessageModel || message instanceof GroupMessageModel)
            && (message.getPostedAt() != null && message.getState() != MessageState.SENDFAILED)
            && !message.isDeleted();
    }

    /**
     * @return true if the message is not older then a defined age. Messages in notes groups can be deleted indefinitely.
     */
    private static boolean isStillInValidTimeFrameToDeleteRemotely(
        @NonNull AbstractMessageModel message,
        @NonNull MessageReceiver receiver
    ) {
        final @Nullable GroupModel groupModel;
        if (receiver instanceof GroupMessageReceiver) {
            groupModel = ((GroupMessageReceiver) receiver).getGroupModel();
        } else {
            groupModel = null;
        }
        final boolean isNotesGroup = groupModel != null && Boolean.TRUE.equals(groupModel.isNotesGroup());
        if (isNotesGroup) {
            return true;
        }
        final @Nullable Date createdAt = message.getCreatedAt();
        if (createdAt == null) {
            return false;
        }
        final long deltaTime = new Date().getTime() - createdAt.getTime();
        return deltaTime <= DeleteMessage.DELETE_MESSAGES_MAX_AGE;
    }

    @Nullable
    public static MessageState receiptTypeToMessageState(int receiptType) {
        switch (receiptType) {
            case ProtocolDefines.DELIVERYRECEIPT_MSGRECEIVED:
                return MessageState.DELIVERED;
            case ProtocolDefines.DELIVERYRECEIPT_MSGREAD:
                return MessageState.READ;
            case ProtocolDefines.DELIVERYRECEIPT_MSGUSERACK:
                return MessageState.USERACK;
            case ProtocolDefines.DELIVERYRECEIPT_MSGUSERDEC:
                return MessageState.USERDEC;
            default:
                return null;
        }
    }

    /**
     * Check whether the user should be able to star the given message.
     */
    public static boolean canStarMessage(AbstractMessageModel message) {
        return (message instanceof MessageModel || message instanceof GroupMessageModel)
            && message.getType() != null
            && (message.getType().equals(MessageType.TEXT) ||
            message.getType().equals(MessageType.FILE) ||
            message.getType().equals(MessageType.LOCATION) ||
            message.getType().equals(MessageType.BALLOT) ||
            message.getType().equals(MessageType.CONTACT) ||
            message.getType().equals(MessageType.IMAGE) ||
            message.getType().equals(MessageType.VIDEO) ||
            message.getType().equals(MessageType.VOICEMESSAGE));
    }

    /**
     * Check whether the user should be able to react to the given message with emojis
     */
    public static boolean canEmojiReact(@Nullable AbstractMessageModel messageModel) {
        if (messageModel == null) {
            return false;
        }

        if (messageModel.isDeleted()) {
            return false;
        }

        if (messageModel.isStatusMessage()) {
            return false;
        }

        return (messageModel instanceof MessageModel || messageModel instanceof GroupMessageModel)
            && messageModel.getType() != null
            && (messageModel.getType().equals(MessageType.TEXT) ||
            messageModel.getType().equals(MessageType.FILE) ||
            messageModel.getType().equals(MessageType.LOCATION) ||
            messageModel.getType().equals(MessageType.BALLOT));
    }
}
