/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2025 Threema GmbH
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

package ch.threema.app.webclient.converter;

import static ch.threema.storage.models.MessageState.DELIVERED;
import static ch.threema.storage.models.MessageState.USERACK;
import static ch.threema.storage.models.MessageState.USERDEC;

import android.content.Context;
import android.graphics.Bitmap;
import android.webkit.MimeTypeMap;

import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.slf4j.Logger;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import ch.threema.app.BuildConfig;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.FileService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.QuoteUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.utils.ThumbnailUtils;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.models.EmojiReactionData;
import ch.threema.domain.protocol.csp.messages.file.FileData;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.FirstUnreadMessageModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.data.LocationDataModel;
import ch.threema.storage.models.data.media.AudioDataModel;
import ch.threema.storage.models.data.media.FileDataModel;
import ch.threema.storage.models.data.media.VideoDataModel;
import ch.threema.storage.models.data.status.ForwardSecurityStatusDataModel;
import ch.threema.storage.models.data.status.GroupCallStatusDataModel;
import ch.threema.storage.models.data.status.GroupStatusDataModel;
import ch.threema.storage.models.data.status.VoipStatusDataModel;

@AnyThread
public class Message extends Converter {
    private static final Logger logger = LoggingUtil.getThreemaLogger("Message");

    // TODO(ANDR-3517): Remove
    public final static String REACTIONS = "reactions";
    public final static String REACTIONS_ACK = "ack";
    public final static String REACTIONS_DEC = "dec";

    public final static String ID = "id";
    public final static String TYPE = "type";
    public final static String BODY = "body";
    public final static String QUOTE = "quote";
    public final static String IS_OUTBOX = "isOutbox";
    public final static String IS_STATUS = "isStatus";
    public final static String PARTNER_ID = "partnerId";
    public final static String STATE = "state";
    public final static String EMOJI_REACTIONS = "emojiReactions";
    public final static String EMOJI_REACTIONS_KEY_EMOJI = "emoji";
    public final static String EMOJI_REACTIONS_KEY_IDENTITIES = "identities";
    public final static String DATE = "date";
    public final static String LAST_EDITED_AT = "lastEditedAt";
    public final static String EVENTS = "events";
    public final static String SORT_KEY = "sortKey";
    public final static String THUMBNAIL = "thumbnail";
    public final static String THUMBNAIL_SIZE_WIDTH = "width";
    public final static String THUMBNAIL_SIZE_HEIGHT = "height";
    public final static String THUMBNAIL_PREVIEW = "preview";
    public final static String CAPTION = "caption";
    public final static String STATUS_TYPE = "statusType";
    public final static String LOCATION = "location";
    public final static String DATA_FILE = "file";
    public final static String DATA_AUDIO = "audio";
    public final static String DATA_VIDEO = "video";
    public final static String IS_UNREAD = "unread";
    private static final String DATA_LOCATION = "location";
    private static final String DATA_AUDIO_DURATION = "duration";
    private static final String DATA_VIDEO_DURATION = "duration";
    private static final String DATA_VIDEO_SIZE = "size";
    private static final String DATA_VOIP_STATUS = "voip";
    private static final String DATA_FILE_NAME = "name";
    private static final String DATA_FILE_SIZE = "size";
    private static final String DATA_FILE_MIME_TYPE = "type";
    private static final String DATA_FILE_IN_APP_MESSAGE = "inApp";
    private static final String DATA_LOCATION_LATITUDE = "lat";
    private static final String DATA_LOCATION_LONGITUDE = "lon";
    private static final String DATA_LOCATION_ADDRESS = "address";
    private static final String DATA_LOCATION_DESCRIPTION = "description";
    private static final String DATA_LOCATION_ACCURACY = "accuracy";
    private static final String DATA_VOIP_STATUS_STATUS = "status";
    private static final String DATA_VOIP_STATUS_DURATION = "duration";
    private static final String DATA_VOIP_STATUS_REASON = "reason";

    /**
     * Only include the required fields of a message (id, isOutbox, isStatus, type).
     */
    public static final int DETAILS_MINIMAL = 0;
    /**
     * Full message details, but no quote.
     */
    public static final int DETAILS_NO_QUOTE = 1;
    /**
     * Full message details.
     */
    public static final int DETAILS_FULL = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({DETAILS_MINIMAL, DETAILS_NO_QUOTE, DETAILS_FULL})
    public @interface DetailLevel {
    }

    /**
     * Converts multiple message models to MsgpackObjectBuilder instances.
     */
    public static List<MsgpackBuilder> convert(
        List<AbstractMessageModel> messages,
        MessageReceiver messageReceiver,
        boolean sendThumbnail
    ) throws ConversionException {
        final List<MsgpackBuilder> builders = new ArrayList<>();
        // Cloning the list to reverse it is not exactly the most elegant way, but could be
        // replaced by a view based approach later on. As a sidenote, at the point of this writing
        // I'm not sure whether the reversing is necessary at all.
        ArrayList<AbstractMessageModel> messagesCopy = new ArrayList<>(messages);
        Collections.reverse(messagesCopy);
        for (AbstractMessageModel message : messagesCopy) {
            builders.add(Message.convert(message, messageReceiver, sendThumbnail, DETAILS_FULL));
        }
        return builders;
    }

    /**
     * Converts a message model to a MsgpackObjectBuilder instance.
     *
     * @param messageReceiver Must be provided if `detailLevel` is `FULL`.
     * @param detailLevel     If set to true, then only the most important fields will be serialized.
     */
    public static MsgpackObjectBuilder convert(
        AbstractMessageModel message,
        MessageReceiver messageReceiver,
        boolean sendThumbnail,
        @DetailLevel int detailLevel
    ) throws ConversionException {
        // Services
        final ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager == null) {
            throw new ConversionException("Could not get service manager");
        }
        MessageService messageService;
        UserService userService;
        FileService fileService;
        try {
            messageService = serviceManager.getMessageService();
            userService = serviceManager.getUserService();
            fileService = serviceManager.getFileService();
        } catch (Exception e) {
            logger.error("Exception", e);
            throw new ConversionException("Services not available");
        }

        // Determine message type. Potentially override the type if it's a media file message.
        ch.threema.storage.models.MessageType virtualMessageType = message.getType();
        if (virtualMessageType == ch.threema.storage.models.MessageType.FILE) {
            final FileDataModel data = message.getFileData();
            final String mediaType = data.getMimeType();
            switch (data.getRenderingType()) {
                case FileData.RENDERING_DEFAULT:
                    // Nothing to be done
                    break;
                case FileData.RENDERING_MEDIA:
                    if (MimeUtil.isSupportedImageFile(mediaType) && !MimeUtil.isGifFile(mediaType)) {
                        virtualMessageType = ch.threema.storage.models.MessageType.IMAGE;
                    } else if (MimeUtil.isAudioFile(mediaType)) {
                        virtualMessageType = ch.threema.storage.models.MessageType.VOICEMESSAGE;
                    } else if (MimeUtil.isVideoFile(mediaType)) {
                        virtualMessageType = ch.threema.storage.models.MessageType.VIDEO;
                    }
                    break;
                case FileData.RENDERING_STICKER:
                    if (MimeUtil.isSupportedImageFile(mediaType)) {
                        virtualMessageType = ch.threema.storage.models.MessageType.IMAGE;
                    }
                    break;
            }
        }

        if (virtualMessageType == ch.threema.storage.models.MessageType.GROUP_STATUS) {
            return convertGroupStatus((GroupMessageModel) message);
        }

        if (message instanceof GroupMessageModel && virtualMessageType == ch.threema.storage.models.MessageType.GROUP_CALL_STATUS) {
            return convertGroupCallStatus((GroupMessageModel) message);
        }

        if (virtualMessageType == ch.threema.storage.models.MessageType.FORWARD_SECURITY_STATUS) {
            return convertForwardSecurityStatus(message);
        }

        // Serialize
        final MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
        try {
            builder.put(ID, String.valueOf(message.getId()));
            builder.put(TYPE, MessageType.convert(virtualMessageType));
            builder.put(SORT_KEY, message.getId());
            builder.put(IS_OUTBOX, message.isOutbox());
            builder.put(IS_STATUS, message.isStatusMessage());

            if (detailLevel != DETAILS_MINIMAL) {
                // Extract quote. (Note: We need this even for `DETAILS_NO_QUOTE` because v1 quotes
                // should be stripped from the body.)
                final Context context = ThreemaApplication.getAppContext();
                final QuoteUtil.QuoteContent quoteContent = QuoteUtil.getQuoteContent(
                    message, messageReceiver, true, null,
                    context, messageService, userService, fileService
                );

                if (quoteContent != null) {
                    // If a message contains a quote, we want to use the message body
                    // that does not include the quote itself
                    builder.put(BODY, quoteContent.bodyText);

                    // Attach quote
                    if (detailLevel != DETAILS_NO_QUOTE) {
                        builder.put(QUOTE, Quote.convert(quoteContent));
                    }
                } else {
                    builder.put(BODY, getBody(message));
                }

                builder.put(PARTNER_ID, message.getIdentity());
                builder.put(IS_UNREAD, MessageUtil.isUnread(message));

                // TODO(ANDR-3517): Remove
                final @NonNull List<EmojiReactionData> reactions = getReactions(message);

                ch.threema.storage.models.MessageState messageState = message.getState();
                if (message instanceof GroupMessageModel
                    && (messageState == USERACK || messageState == USERDEC)) {
                    // web/webtop does not know how to handle group acks
                    messageState = DELIVERED;
                } else if (message instanceof MessageModel) {
                    // TODO(ANDR-3517): Remove else if branch
                    messageState = EmojiReactionConverterUtil
                        .getContactAckDecFromReactions((MessageModel) message, reactions)
                        .orElse(messageState);
                }
                if (BuildConfig.EMOJI_REACTIONS_WEB_ENABLED) {
                    maybePutEmojiReactions(builder, getReactionBuckets(message));
                } else {
                    if (message instanceof GroupMessageModel) {
                        maybePutReactions(
                            builder,
                            REACTIONS,
                            EmojiReactionConverterUtil.getGroupAckDecFromReactions(reactions)
                        );
                    } else if (message instanceof MessageModel) {
                        messageState = EmojiReactionConverterUtil
                            .getContactAckDecFromReactions((MessageModel) message, reactions)
                            .orElse(messageState);
                    }
                }

                maybePutState(builder, STATE, messageState);
                maybePutDate(builder, DATE, message);
                maybePutLastEditedAt(builder, LAST_EDITED_AT, message);
                maybePutEvents(builder, EVENTS, message);
                maybePutCaption(builder, CAPTION, message);
                maybePutStatusType(builder, STATUS_TYPE, message);
                if (sendThumbnail) {
                    maybePutThumbnail(builder, THUMBNAIL, message);
                }

                switch (message.getType()) {
                    case VIDEO:
                        maybePutVideo(builder, DATA_VIDEO, message.getVideoData());
                        break;
                    case VOICEMESSAGE:
                        maybePutAudio(builder, DATA_AUDIO, message.getAudioData());
                        break;
                    case FILE:
                        switch (virtualMessageType) {
                            case IMAGE:
                                // Already handled by setting thumbnail and type
                                break;
                            case VIDEO:
                                maybePutVideo(builder, DATA_VIDEO, VideoDataModel.fromFileData(message.getFileData()));
                                break;
                            default:
                                maybePutFile(builder, DATA_FILE, message, message.getFileData());
                        }
                        break;
                    case LOCATION:
                        maybePutLocation(builder, DATA_LOCATION, message.getLocationData());
                        break;
                    case VOIP_STATUS:
                        maybePutVoipStatus(builder, DATA_VOIP_STATUS, message.getVoipStatusData());
                        break;
                }
            }
        } catch (NullPointerException e) {
            throw new ConversionException(e);
        }
        return builder;
    }

    @NonNull
    private static List<ReactionBucket> getReactionBuckets(@NonNull AbstractMessageModel message) throws ConversionException {
        try {
            final @NonNull List<EmojiReactionData> reactions = Objects.requireNonNull(getServiceManager())
                .getModelRepositories()
                .getEmojiReaction()
                .safeGetReactionsByMessage(message);
            return ReactionBucket.fromReactions(reactions);
        } catch (NullPointerException e) {
            throw new ConversionException(e);
        }
    }

    @NonNull
    private static List<EmojiReactionData> getReactions(AbstractMessageModel message) throws ConversionException {
        try {
            return Objects.requireNonNull(getServiceManager())
                .getModelRepositories()
                .getEmojiReaction()
                .safeGetReactionsByMessage(message);
        } catch (NullPointerException e) {
            throw new ConversionException(e);
        }
    }

    private static MsgpackObjectBuilder convertGroupStatus(GroupMessageModel message) throws ConversionException {
        final MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
        try {
            GroupStatusDataModel groupStatusDataModel = message.getGroupStatusDataModel();
            if (groupStatusDataModel != null) {
                builder
                    .put(ID, String.valueOf(message.getId()))
                    .put(TYPE, MessageType.TEXT)
                    .put(SORT_KEY, message.getId())
                    .put(IS_OUTBOX, message.isOutbox())
                    .put(IS_STATUS, true)
                    .put(PARTNER_ID, message.getIdentity())
                    .put(BODY, MessageUtil.getViewElement(ThreemaApplication.getAppContext(), message).text)
                    .put(IS_UNREAD, false)
                    .put(STATUS_TYPE, "text");
                maybePutState(builder, STATE, DELIVERED);
                maybePutDate(builder, DATE, message);
                maybePutEvents(builder, EVENTS, message);
            }
        } catch (Exception e) {
            throw new ConversionException(e);
        }
        return builder;
    }

    private static MsgpackObjectBuilder convertGroupCallStatus(GroupMessageModel message) throws ConversionException {
        final MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
        try {
            GroupCallStatusDataModel groupCallStatusDataModel = message.getGroupCallStatusData();
            if (groupCallStatusDataModel != null) {
                builder
                    .put(ID, String.valueOf(message.getId()))
                    .put(TYPE, MessageType.TEXT)
                    .put(SORT_KEY, message.getId())
                    .put(IS_OUTBOX, message.isOutbox())
                    .put(IS_STATUS, true)
                    .put(PARTNER_ID, groupCallStatusDataModel.getStatus() == GroupCallStatusDataModel.STATUS_STARTED ?
                        groupCallStatusDataModel.getCallerIdentity() :
                        getContactService().getMe().getIdentity())
                    .put(BODY, MessageUtil.getViewElement(ThreemaApplication.getAppContext(), message).text)
                    .put(IS_UNREAD, false)
                    .put(STATUS_TYPE, "text");
                maybePutState(builder, STATE, DELIVERED);
                maybePutDate(builder, DATE, message);
                maybePutEvents(builder, EVENTS, message);
            }
        } catch (NullPointerException e) {
            throw new ConversionException(e);
        }
        return builder;
    }

    private static MsgpackObjectBuilder convertForwardSecurityStatus(AbstractMessageModel message) throws ConversionException {
        final MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
        try {
            ForwardSecurityStatusDataModel forwardSecurityStatusDataModel = message.getForwardSecurityStatusData();
            if (forwardSecurityStatusDataModel != null) {
                builder
                    .put(ID, String.valueOf(message.getId()))
                    .put(TYPE, MessageType.TEXT)
                    .put(SORT_KEY, message.getId())
                    .put(IS_OUTBOX, message.isOutbox())
                    .put(IS_STATUS, true)
                    .put(PARTNER_ID, message.getIdentity())
                    .put(BODY, MessageUtil.getViewElement(ThreemaApplication.getAppContext(), message).text)
                    .put(IS_UNREAD, false)
                    .put(STATUS_TYPE, "text");
                maybePutState(builder, STATE, DELIVERED);
                maybePutDate(builder, DATE, message);
                maybePutEvents(builder, EVENTS, message);
            }
        } catch (NullPointerException e) {
            throw new ConversionException(e);
        }
        return builder;
    }

    /**
     * Return the body for text, location and status messages. Everything else needs to be
     * requested on demand.
     */
    private static String getBody(AbstractMessageModel message) {
        switch (message.getType()) {
            case TEXT:
            case STATUS:
            case BALLOT:
                return message.getBody();
        }
        return null;
    }

    private static void maybePutState(MsgpackObjectBuilder builder, String field, ch.threema.storage.models.MessageState state)
        throws ConversionException {
        if (state != null) {
            builder.put(field, MessageState.convert(state));
        }
    }

    // TODO(ANDR-3517): Remove
    private static void maybePutReactions(MsgpackObjectBuilder builder, String field, @Nullable Map<String, ch.threema.storage.models.MessageState> messageStates) {
        if (messageStates != null) {
            final MsgpackArrayBuilder ackBuilder = new MsgpackArrayBuilder();
            final MsgpackArrayBuilder decBuilder = new MsgpackArrayBuilder();

            for (Map.Entry<String, ch.threema.storage.models.MessageState> entry : messageStates.entrySet()) {
                if (ch.threema.storage.models.MessageState.USERACK == entry.getValue()) {
                    ackBuilder.put(entry.getKey());
                } else if (ch.threema.storage.models.MessageState.USERDEC == entry.getValue()) {
                    decBuilder.put(entry.getKey());
                }
            }

            builder.put(field,
                new MsgpackObjectBuilder()
                    .put(REACTIONS_ACK, ackBuilder)
                    .put(REACTIONS_DEC, decBuilder)
            );
        }
    }

    private static void maybePutEmojiReactions(
        @NonNull MsgpackObjectBuilder builder,
        @NonNull List<ReactionBucket> reactionBuckets
    ) {
        if (reactionBuckets.isEmpty()) {
            return;
        }

        MsgpackArrayBuilder reactionBucketsBuilder = new MsgpackArrayBuilder();

        for (ReactionBucket bucket : reactionBuckets) {
            MsgpackArrayBuilder identityListBuilder = new MsgpackArrayBuilder();
            for (String identity : bucket.getIdentities()) {
                identityListBuilder.put(identity);
            }
            MsgpackObjectBuilder reactionBucketBuilder = new MsgpackObjectBuilder();
            reactionBucketBuilder.put(EMOJI_REACTIONS_KEY_EMOJI, bucket.getReaction());
            reactionBucketBuilder.put(EMOJI_REACTIONS_KEY_IDENTITIES, identityListBuilder);
            reactionBucketsBuilder.put(reactionBucketBuilder);
        }

        builder.put(EMOJI_REACTIONS, reactionBucketsBuilder);
    }

    private static void maybePutDate(MsgpackObjectBuilder builder, String field, AbstractMessageModel message) {
        Date date = message.getPostedAt();

        // Update time?
        if (message.isOutbox()) {
            if (message.getModifiedAt() != null) {
                date = message.getModifiedAt();
            }
        }

        // Get display date
        if (date != null) {
            builder.put(field, date.getTime() / 1000);
        }
    }

    private static void maybePutLastEditedAt(MsgpackObjectBuilder builder, String field, AbstractMessageModel message) {
        Date editedAt = message.getEditedAt();
        if (editedAt != null) {
            builder.put(field, editedAt.getTime() / 1000);
        }
    }

    /**
     * If available, add message events to message.
     */
    private static void maybePutEvents(MsgpackObjectBuilder builder, String field, AbstractMessageModel message) {
        final Date createdAt = message.getCreatedAt();
        final Date sentAt = message.getPostedAt(false);
        final Date modifiedAt = message.getModifiedAt();

        final MsgpackArrayBuilder arrayBuilder = new MsgpackArrayBuilder();

        if (createdAt != null) {
            arrayBuilder.put(MessageEvent.convert(MessageEvent.TYPE_CREATED, createdAt));
        }
        if (sentAt != null) {
            arrayBuilder.put(MessageEvent.convert(MessageEvent.TYPE_SENT, sentAt));
        }
        if (modifiedAt != null) {
            arrayBuilder.put(MessageEvent.convert(MessageEvent.TYPE_MODIFIED, modifiedAt));
        }

        if (!arrayBuilder.isEmpty()) {
            builder.put(field, arrayBuilder);
        }
    }

    /**
     * If a caption exists, add it to the MsgpackObjectBuilder.
     */
    private static void maybePutCaption(MsgpackObjectBuilder builder, String field, AbstractMessageModel message) {
        String caption = message.getCaption();
        if (TestUtil.isEmptyOrNull(caption) && message.getType() == ch.threema.storage.models.MessageType.FILE) {
            //use description as caption!
            //hack!
            caption = message.getFileData().getCaption();
        }
        if (message.getType() == ch.threema.storage.models.MessageType.LOCATION) {
            // No caption for locations
            return;
        }
        if (caption != null) {
            builder.put(field, caption);
        }
    }

    /**
     * If this is a status message, add the status type.
     */
    private static void maybePutStatusType(MsgpackObjectBuilder builder, String field, AbstractMessageModel message) {
        if (message.isStatusMessage()) {
            if (message instanceof FirstUnreadMessageModel) {
                builder.put(field, "firstUnreadMessage");
            } else {
                builder.put(field, "text");
            }
        }
    }

    private static void maybePutThumbnail(
        MsgpackObjectBuilder builder,
        String field,
        AbstractMessageModel message
    ) {
        if (MessageUtil.canHaveThumbnailFile(message)) {
            try {
                // Load thumbnail bitmap
                Bitmap previewBitmap = getServiceManager()
                    .getFileService()
                    .getMessageThumbnailBitmap(message, null);

                if (previewBitmap != null) {
                    // Get thumbnail dimensions within bounds
                    final ThumbnailUtils.Size newSize = ThumbnailUtils
                        .resizeProportionally(
                            previewBitmap.getWidth(),
                            previewBitmap.getHeight(),
                            Protocol.SIZE_THUMBNAIL_MAX_PX
                        );

                    // Resize bitmap for preview
                    previewBitmap = ThumbnailUtils
                        .resize(previewBitmap, Protocol.SIZE_PREVIEW_MAX_PX);

                    // Convert bitmap to bytes
                    final byte[] previewBytes = BitmapUtil
                        .bitmapToByteArray(previewBitmap, Protocol.FORMAT_THUMBNAIL, Protocol.QUALITY_THUMBNAIL);

                    builder.put(field,
                        new MsgpackObjectBuilder()
                            .put(THUMBNAIL_SIZE_WIDTH, newSize.width)
                            .put(THUMBNAIL_SIZE_HEIGHT, newSize.height)
                            .put(THUMBNAIL_PREVIEW, previewBytes)
                    );
                }

                // Recycle bitmaps to save memory
                BitmapUtil.recycle(previewBitmap);
            } catch (Exception x) {
                logger.error("Exception", x);
            }
        }
    }

    private static void maybePutVideo(MsgpackObjectBuilder builder, String field, VideoDataModel videoData) {
        if (videoData != null) {
            final int videoDuration = videoData.getDuration();
            final int videoSize = videoData.getVideoSize();
            builder.put(field, new MsgpackObjectBuilder()
                .put(DATA_VIDEO_DURATION, videoDuration)
                .maybePut(DATA_VIDEO_SIZE, videoSize == 0 ? null : videoSize));
        }
    }

    private static void maybePutAudio(MsgpackObjectBuilder builder, String field, AudioDataModel audioData) {
        if (audioData != null) {
            builder.put(field, new MsgpackObjectBuilder()
                .put(DATA_AUDIO_DURATION, audioData.getDuration()));
        }
    }

    /**
     * Workaround for filenames sent without file extension.
     */
    public static @NonNull String fixFileName(@NonNull String fileName, @Nullable String mimeType) {
        if (mimeType == null) {
            return fileName;
        }
        if (!fileName.contains(".")) {
            final String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            if (extension != null) {
                if (extension.equals("jpeg")) {
                    // Samsung seems to prefer jpeg over jpg
                    return fileName + ".jpg";
                } else {
                    return fileName + "." + extension;
                }
            }
        }
        return fileName;
    }

    static void maybePutFile(
        MsgpackObjectBuilder builder, String field,
        @NonNull AbstractMessageModel message,
        @Nullable FileDataModel fileData
    ) {
        if (fileData != null) {
            final String mimeType = fileData.getMimeType();
            String fileName = fileData.getFileName();

            // The Threema protocol does not require a file name in a file message,
            // but ARP does! If the file name is null, generate a new one.
            if (fileName == null) {
                fileName = FileUtil.getMediaFilenamePrefix(message);
            }

            // Ensure that the file has an extension (if not, derive one from the media type)
            fileName = fixFileName(fileName, mimeType);

            builder.put(field, new MsgpackObjectBuilder()
                .put(DATA_FILE_NAME, fileName)
                .put(DATA_FILE_SIZE, fileData.getFileSize())
                .put(DATA_FILE_MIME_TYPE, mimeType)
                .put(DATA_FILE_IN_APP_MESSAGE, fileData.getRenderingType() == FileData.RENDERING_MEDIA));
        }
    }

    private static void maybePutLocation(MsgpackObjectBuilder builder, String field, LocationDataModel locationData) {
        if (locationData != null) {
            builder.put(field, new MsgpackObjectBuilder()
                .put(DATA_LOCATION_LATITUDE, locationData.latitude)
                .put(DATA_LOCATION_LONGITUDE, locationData.longitude)
                .put(DATA_LOCATION_ACCURACY, locationData.accuracy)
                .maybePut(DATA_LOCATION_ADDRESS, locationData.poiAddressOrNull)
                .put(DATA_LOCATION_DESCRIPTION, locationData.poiNameOrNull));
        }
    }

    private static void maybePutVoipStatus(MsgpackObjectBuilder builder, String field, VoipStatusDataModel voipStatusDataModel) {
        if (voipStatusDataModel != null) {
            builder.put(field, new MsgpackObjectBuilder()
                .put(DATA_VOIP_STATUS_STATUS, voipStatusDataModel.getStatus())
                .put(DATA_VOIP_STATUS_DURATION, voipStatusDataModel.getDuration())
                .put(DATA_VOIP_STATUS_REASON, voipStatusDataModel.getReason() != null ?
                    voipStatusDataModel.getReason().intValue() : null));
        }
    }
}
