package ch.threema.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.HashMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.app.Person;
import androidx.core.graphics.drawable.IconCompat;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ConversationCategoryService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.notification.ConversationNotification;
import ch.threema.app.services.notification.ConversationNotificationGroup;
import ch.threema.app.services.notification.NotificationService;
import ch.threema.base.ThreemaException;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import ch.threema.data.datatypes.ContactNameFormat;
import ch.threema.domain.protocol.csp.messages.file.FileData;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.group.GroupMessageModel;
import ch.threema.storage.models.group.GroupModelOld;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.data.MessageContentsType;

public class ConversationNotificationUtil {
    private static final Logger logger = getThreemaLogger("ConversationNotificationUtil");

    protected static final HashMap<String, ConversationNotificationGroup> notificationGroupHashMap = new HashMap<>();
    private static final int MAX_NOTIFICATION_THUMBNAIL_SIZE_BYTES = 1024 * 1024;

    public static ConversationNotification convert(
        @NonNull Context context,
        @NonNull AbstractMessageModel messageModel,
        @NonNull ContactService contactService,
        @NonNull GroupService groupService,
        @NonNull ConversationCategoryService conversationCategoryService,
        @NonNull ContactNameFormat contactNameFormat
    ) {
        ConversationNotification conversationNotification = null;
        if (messageModel instanceof MessageModel) {
            conversationNotification = create(context, (MessageModel) messageModel, contactService, conversationCategoryService, contactNameFormat);
        } else if (messageModel instanceof GroupMessageModel) {
            conversationNotification = create(context, (GroupMessageModel) messageModel, groupService, conversationCategoryService, contactNameFormat);
        }

        return conversationNotification;
    }

    private static MessageService.MessageString getMessage(AbstractMessageModel messageModel) {
        try {
            return ThreemaApplication.getServiceManager().getMessageService()
                .getMessageString(messageModel, -1, false);
        } catch (ThreemaException e) {
            logger.error("Exception", e);
            return new MessageService.MessageString(null);
        }
    }

    private static @Nullable Person getSenderPerson(@NonNull AbstractMessageModel messageModel) {
        try {
            final PreferenceService preferenceService = ThreemaApplication.getServiceManager().getPreferenceService();
            final ContactService contactService = ThreemaApplication.getServiceManager().getContactService();
            final ContactModel contactModel = contactService.getByIdentity(messageModel.getIdentity());
            return getPerson(
                contactService,
                contactModel,
                NameUtil.getShortName(
                    ThreemaApplication.getAppContext(),
                    messageModel,
                    contactService,
                    preferenceService.getContactNameFormat()
                )
            );
        } catch (ThreemaException e) {
            logger.error("ThreemaException", e);
            return null;
        }
    }

    public static @Nullable Person getPerson(@Nullable ContactService contactService, ContactModel contactModel, String name) {
        if (contactService == null) {
            return null;
        }

        String identity = contactModel != null ? contactModel.getIdentity() : null;

        Person.Builder builder = new Person.Builder()
            .setKey(ContactUtil.getUniqueIdString(identity))
            .setName(name);
        Bitmap avatar = contactService.getAvatar(identity, false);
        if (avatar != null) {
            IconCompat iconCompat = IconCompat.createWithBitmap(avatar);
            builder.setIcon(iconCompat);
        }
        if (contactModel != null && contactModel.isLinkedToAndroidContact()) {
            builder.setUri(contactService.getAndroidContactLookupUriString(contactModel));
        }
        return builder.build();
    }

    private static MessageType getMessageType(@NonNull AbstractMessageModel messageModel) {
        return messageModel.getType();
    }

    @Nullable
    private static Instant getCreatedAt(AbstractMessageModel messageModel) {
        var createdAt = messageModel.getCreatedAt();
        return createdAt != null ? createdAt.toInstant() : null;
    }

    @NonNull
    private static ConversationNotification create(
        @NonNull final Context context,
        @NonNull final MessageModel messageModel,
        @NonNull final ContactService contactService,
        @NonNull final ConversationCategoryService conversationCategoryService,
        @NonNull final ContactNameFormat contactNameFormat
    ) {
        final ContactModel contactModel = contactService.getByIdentity(messageModel.getIdentity());
        String groupUid = "i" + messageModel.getIdentity();
        synchronized (notificationGroupHashMap) {
            ConversationNotificationGroup group = notificationGroupHashMap.get(groupUid);
            boolean isPrivateChat = conversationCategoryService.isPrivateChat(
                ContactUtil.getUniqueIdString(messageModel.getIdentity())
            );
            String longName, shortName;
            if (isPrivateChat) {
                longName = shortName = context.getString(R.string.private_chat_subject);
            } else {
                longName = NameUtil.getContactDisplayNameOrNickname(contactModel, true, contactNameFormat);
                shortName = NameUtil.getShortName(contactModel, contactNameFormat);
            }

            if (group == null) {
                group = new ConversationNotificationGroup(
                    groupUid,
                    longName,
                    shortName,
                    contactService.createReceiver(contactModel),
                    () -> {
                        if (contactModel != null) {
                            String identity = isPrivateChat
                                ? null
                                : contactModel.getIdentity();
                            return contactService.getAvatar(identity, false);
                        }
                        return null;
                    }
                );
                notificationGroupHashMap.put(groupUid, group);
            } else {
                // contact name may change between notifications - set it again
                group.name = longName;
                group.shortName = shortName;
            }

            var conversationNotification = new ConversationNotification(
                getMessage(messageModel),
                getCreatedAt(messageModel),
                getId(messageModel),
                getUid(messageModel),
                group,
                getFetchThumbnail(messageModel),
                getThumbnailMimeType(messageModel),
                getSenderPerson(messageModel),
                getMessageType(messageModel),
                messageModel.isEdited(),
                messageModel.isDeleted()
            );
            group.conversations.add(conversationNotification);
            return conversationNotification;
        }
    }

    @Nullable
    private static String getThumbnailMimeType(AbstractMessageModel messageModel) {
        if (MessageType.FILE.equals(messageModel.getType()) &&
            (messageModel.getMessageContentsType() == MessageContentsType.IMAGE ||
                messageModel.getMessageContentsType() == MessageContentsType.VIDEO
            )) {
            return MimeUtil.MIME_TYPE_IMAGE_PNG.equals(messageModel.getFileData().getThumbnailMimeType()) ? MimeUtil.MIME_TYPE_IMAGE_PNG : MimeUtil.MIME_TYPE_IMAGE_JPEG;
        }
        return null;
    }

    @NonNull
    private static ConversationNotification create(
        final Context context,
        @NonNull final GroupMessageModel messageModel,
        @NonNull final GroupService groupService,
        @NonNull final ConversationCategoryService conversationCategoryService,
        @NonNull final ContactNameFormat contactNameFormat
    ) {
        final GroupModelOld groupModel = groupService.getById(messageModel.getGroupId());

        String groupUid = "g" + messageModel.getGroupId();
        synchronized (notificationGroupHashMap) {
            @Nullable ConversationNotificationGroup group = notificationGroupHashMap.get(groupUid);
            String name = conversationCategoryService.isPrivateChat(GroupUtil.getUniqueIdString(groupModel))
                ? context.getString(R.string.private_chat_subject)
                : NameUtil.getGroupDisplayName(
                    groupService.getById(messageModel.getGroupId()),
                    groupService,
                    contactNameFormat
                );

            if (group == null) {
                group = new ConversationNotificationGroup(
                    groupUid,
                    name,
                    name,
                    groupService.createReceiver(groupModel),
                    () -> groupService.getAvatar(
                        conversationCategoryService.isPrivateChat(GroupUtil.getUniqueIdString(groupModel)) ? null : groupModel,
                        false
                    )
                );
                notificationGroupHashMap.put(groupUid, group);
            } else {
                // group name may change between notifications - set it again
                group.name = name;
                group.shortName = name;
            }

            var conversationNotification = new ConversationNotification(
                getMessage(messageModel),
                getCreatedAt(messageModel),
                getId(messageModel),
                getUid(messageModel),
                group,
                getFetchThumbnail(messageModel),
                getThumbnailMimeType(messageModel),
                getSenderPerson(messageModel),
                getMessageType(messageModel),
                messageModel.isEdited(),
                messageModel.isDeleted()
            );
            group.conversations.add(conversationNotification);
            return conversationNotification;
        }
    }

    public static String getUid(AbstractMessageModel messageModel) {
        return messageModel.getUid();
    }

    public static int getId(AbstractMessageModel messageModel) {
        return messageModel.getId();
    }

    public static NotificationService.FetchCacheUri getFetchThumbnail(final AbstractMessageModel messageModel) {
        if (messageModel.getMessageContentsType() == MessageContentsType.IMAGE ||
            messageModel.getMessageContentsType() == MessageContentsType.VIDEO
        ) {
            if (messageModel.getType() == MessageType.FILE && messageModel.getFileData().getRenderingType() != FileData.RENDERING_MEDIA) {
                return null;
            }

            return new NotificationService.FetchCacheUri() {
                @Override
                @Nullable
                @WorkerThread
                public Uri fetch() {
                    final FileService fileService = KoinJavaComponent.get(FileService.class);
                    return fileService.getThumbnailShareFileUri(messageModel, MAX_NOTIFICATION_THUMBNAIL_SIZE_BYTES);
                }
            };
        }
        return null;
    }
}
