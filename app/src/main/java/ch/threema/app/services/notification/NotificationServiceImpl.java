package ch.threema.app.services.notification;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.AsyncQueryHandler;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.text.format.DateUtils;

import org.jetbrains.annotations.Contract;
import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;
import androidx.core.content.LocusIdCompat;
import androidx.core.graphics.drawable.IconCompat;
import ch.threema.app.AppConstants;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.BackupAdminActivity;
import ch.threema.app.activities.ComposeMessageActivity;
import ch.threema.app.home.HomeActivity;
import ch.threema.app.activities.ServerMessageActivity;
import ch.threema.app.notifications.NotificationIDs;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.ConversationCategoryService;
import ch.threema.app.stores.IdentityProvider;
import ch.threema.app.utils.DoNotDisturbUtil;
import ch.threema.app.widget.WidgetUpdater;
import ch.threema.data.datatypes.NotificationTriggerPolicyOverride;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.notifications.ForwardSecurityNotificationManager;
import ch.threema.app.notifications.NotificationChannels;
import ch.threema.app.notifications.NotificationGroups;
import ch.threema.app.receivers.CancelResendMessagesBroadcastReceiver;
import ch.threema.app.receivers.ReSendMessagesBroadcastReceiver;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.LockAppService;
import ch.threema.app.services.NotificationPreferenceService;
import ch.threema.app.services.RingtoneService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.voip.activities.CallActivity;
import ch.threema.app.voip.activities.GroupCallActivity;

import static ch.threema.android.ThreadUtilKt.isMainThread;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import ch.threema.data.models.ContactModel;
import ch.threema.data.models.ContactModelData;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.group.GroupModelOld;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.ServerMessageModel;

import static android.provider.Settings.System.DEFAULT_NOTIFICATION_URI;
import static android.provider.Settings.System.DEFAULT_RINGTONE_URI;
import static androidx.core.app.NotificationCompat.MessagingStyle.MAXIMUM_RETAINED_MESSAGES;
import static ch.threema.app.notifications.NotificationIDs.WORK_SYNC_NOTIFICATION_ID;
import static ch.threema.app.backuprestore.csv.RestoreService.RESTORE_COMPLETION_NOTIFICATION_ID;
import static ch.threema.app.voip.services.VoipCallService.EXTRA_ACTIVITY_MODE;
import static ch.threema.app.voip.services.VoipCallService.EXTRA_CALL_ID;
import static ch.threema.app.voip.services.VoipCallService.EXTRA_CONTACT_IDENTITY;
import static ch.threema.app.voip.services.VoipCallService.EXTRA_IS_INITIATOR;
import static ch.threema.common.CharSequenceExtensionsKt.truncate;

public class NotificationServiceImpl implements NotificationService {
    private static final Logger logger = getThreemaLogger("NotificationServiceImpl");
    private static final long NOTIFY_AGAIN_TIMEOUT = 30 * DateUtils.SECOND_IN_MILLIS;
    private static final String NAME_PREPEND_SEPARATOR = ": ";

    private final @NonNull Context appContext;
    private final @NonNull LockAppService lockAppService;
    private final @NonNull ConversationCategoryService conversationCategoryService;
    private final @NonNull NotificationPreferenceService notificationPreferenceService;
    private final @NonNull RingtoneService ringtoneService;
    private final @NonNull PreferenceService preferenceService;
    private final @NonNull IdentityProvider identityProvider;
    private @Nullable ContactService _contactService = null;
    private @Nullable GroupService groupService = null;

    @NonNull
    private final DoNotDisturbUtil doNotDisturbUtil = KoinJavaComponent.get(DoNotDisturbUtil.class);

    private static final int MAX_TICKER_TEXT_LENGTH = 256;
    public static final int APP_RESTART_NOTIFICATION_ID = 481773;
    private static final int GC_PENDING_INTENT_BASE = 30000;

    private static final String PIN_LOCKED_NOTIFICATION_ID = "(transition to locked state)";
    private AsyncQueryHandler queryHandler;

    private final NotificationManagerCompat notificationManagerCompat;
    private final int pendingIntentFlags;

    private final LinkedList<ConversationNotification> conversationNotificationsCache = new LinkedList<>();
    private MessageReceiver visibleConversationReceiver;

    @NonNull
    private final ForwardSecurityNotificationManager fsNotificationManager;

    public NotificationServiceImpl(
        @NonNull Context appContext,
        @NonNull LockAppService lockAppService,
        @NonNull ConversationCategoryService conversationCategoryService,
        @NonNull NotificationPreferenceService notificationPreferenceService,
        @NonNull RingtoneService ringtoneService,
        @NonNull PreferenceService preferenceService,
        @NonNull IdentityProvider identityProvider
    ) {
        this.appContext = appContext;
        this.lockAppService = lockAppService;
        this.conversationCategoryService = conversationCategoryService;
        this.notificationPreferenceService = notificationPreferenceService;
        this.ringtoneService = ringtoneService;
        this.preferenceService = preferenceService;
        this.identityProvider = identityProvider;
        this.notificationManagerCompat = NotificationManagerCompat.from(appContext);
        this.fsNotificationManager = new ForwardSecurityNotificationManager(appContext, conversationCategoryService, preferenceService);
        this.pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE;

        initContactService();
        initGroupService();

        // create or update notification channels
        if (ConfigUtils.supportsNotificationChannels()) {
            NotificationChannels.INSTANCE.createOrMigrateNotificationChannels(appContext);
        }
    }

    private void initContactService() {
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager != null) {
            try {
                this._contactService = serviceManager.getContactService();
            } catch (Exception e) {
                logger.error("Could not initialize ContactService", e);
            }
        }
    }

    private void initGroupService() {
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager != null) {
            try {
                this.groupService = serviceManager.getGroupService();
            } catch (Exception e) {
                logger.error("Could not initialize GroupService", e);
            }
        }
    }

    @Nullable
    private ContactService getContactService() {
        if (_contactService == null) {
            initContactService();
        }
        return _contactService;
    }

    @Nullable
    private GroupService getGroupService() {
        if (groupService == null) {
            initGroupService();
        }
        return groupService;
    }

    @Override
    public void recreateNotificationChannels() {
        if (ConfigUtils.supportsNotificationChannels()) {
            NotificationChannels.INSTANCE.recreateNotificationChannels(appContext);
        }
    }

    @Override
    public void setVisibleReceiver(MessageReceiver receiver) {
        if (receiver != null) {
            //cancel
            this.cancel(receiver);
        }
        this.visibleConversationReceiver = receiver;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void addGroupCallNotification(@NonNull GroupModelOld group, @NonNull ContactModelData contactModelData) {
        if (getGroupService() == null) {
            logger.error("Group service is null; cannot show notification");
            return;
        }

        // If the notificationTriggerPolicyOverride setting is "MutedIndefiniteExceptMentions" we will always show the call notification.
        // Otherwise we check if the mute setting would apply at the current system time.
        final @NonNull NotificationTriggerPolicyOverride notificationTriggerPolicyOverride = group.currentNotificationTriggerPolicyOverride();
        final boolean shouldBlockGroupCallNotification = !(notificationTriggerPolicyOverride instanceof NotificationTriggerPolicyOverride.MutedIndefiniteExceptMentions)
            && notificationTriggerPolicyOverride.getMuteAppliesRightNow();

        if (shouldBlockGroupCallNotification || doNotDisturbUtil.isDoNotDisturbActive()) {
            return;
        }

        NotificationCompat.Action joinAction = new NotificationCompat.Action(
            R.drawable.ic_phone_locked_outline,
            appContext.getString(R.string.voip_gc_join_call),
            getGroupCallJoinPendingIntent(group.getId(), pendingIntentFlags)
        );

        Intent notificationIntent = new Intent(appContext, ComposeMessageActivity.class);
        notificationIntent.putExtra(AppConstants.INTENT_DATA_GROUP_DATABASE_ID, (long) group.getId());
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        PendingIntent openPendingIntent = createPendingIntentWithTaskStack(notificationIntent);

        String contentText = appContext.getString(R.string.voip_gc_notification_call_started, contactModelData.getShortName(), group.getName());

        // public version of the notification
        NotificationCompat.Builder publicBuilder = new NotificationCompat.Builder(appContext, NotificationChannels.NOTIFICATION_CHANNEL_INCOMING_GROUP_CALLS)
            .setContentTitle(appContext.getString(R.string.group_call))
            .setContentText(appContext.getString(R.string.voip_gc_notification_new_call_public))
            .setSmallIcon(R.drawable.ic_phone_locked_outline)
            .setGroup(NotificationGroups.CALLS)
            .setGroupSummary(false)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setChannelId(NotificationChannels.NOTIFICATION_CHANNEL_INCOMING_GROUP_CALLS);

        // private version of the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, NotificationChannels.NOTIFICATION_CHANNEL_INCOMING_GROUP_CALLS)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentTitle(appContext.getString(R.string.group_call))
            .setContentText(contentText)
            .setContentIntent(openPendingIntent)
            .setSmallIcon(R.drawable.ic_phone_locked_outline)
            .setLargeIcon(getGroupService().getAvatar(group, false))
            .setLocalOnly(true)
            .setGroup(NotificationGroups.CALLS)
            .setGroupSummary(false)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicBuilder.build())
            .setTimeoutAfter(TimeUnit.SECONDS.toMillis(30))
            .addAction(joinAction);

        if (!ConfigUtils.supportsNotificationChannels()) {
            builder.setSound(notificationPreferenceService.getLegacyGroupCallRingtone(), AudioManager.STREAM_RING);

            if (notificationPreferenceService.isLegacyGroupCallVibrate()) {
                builder.setVibrate(NotificationChannels.VIBRATE_PATTERN_GROUP_CALL);
            }
        }

        String tag = "" + group.getId();
        try {
            notificationManagerCompat.notify(tag, NotificationIDs.INCOMING_GROUP_CALL_NOTIFICATION_ID, builder.build());
        } catch (Exception e) {
            logger.error("Exception when notifying", e);
        }
    }

    @Override
    public void cancelGroupCallNotification(int groupId) {
        PendingIntent joinIntent = getGroupCallJoinPendingIntent(groupId, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (joinIntent != null) {
            joinIntent.cancel();
        }
        notificationManagerCompat.cancel("" + groupId, NotificationIDs.INCOMING_GROUP_CALL_NOTIFICATION_ID);
    }

    private PendingIntent getGroupCallJoinPendingIntent(int groupId, int flags) {
        // To make sure a new PendingIntent only for this group is created, use the group id as request code.
        return PendingIntent.getActivity(
            appContext,
            GC_PENDING_INTENT_BASE + groupId,
            GroupCallActivity.createJoinCallIntent(appContext, groupId),
            flags
        );
    }

    @Override
    public void showConversationNotification(final ConversationNotification conversationNotification, boolean updateExisting) {
        logger.debug("showConversationNotifications");

        if (ConfigUtils.hasInvalidCredentials()) {
            logger.debug("Credentials are not (or no longer) valid. Suppressing notification.");
            return;
        }

        if (notificationPreferenceService.getWizardRunning()) {
            logger.debug("Wizard in progress. Notification suppressed.");
            return;
        }

        synchronized (this.conversationNotificationsCache) {
            final @NonNull ConversationNotificationGroup currentNotificationsGroup = conversationNotification.getGroup();

            //check if current receiver is the receiver of the group
            if (this.visibleConversationReceiver != null &&
                currentNotificationsGroup.messageReceiver.isEqual(this.visibleConversationReceiver)) {
                //ignore notification
                logger.info("No notification - chat visible");
                return;
            }

            @Nullable String uniqueMessageReceiverId = null;

            // If the conversationNotification does not already exist in local cache,
            // and the receiver is not muted, we add it to the beginning of the cached list
            final boolean notificationAlreadyExistsInCache = this.conversationNotificationsCache.stream().anyMatch(
                cachedConversationNotification -> cachedConversationNotification.getUid().equals(conversationNotification.getUid())
            );
            if (!notificationAlreadyExistsInCache) {
                uniqueMessageReceiverId = currentNotificationsGroup.messageReceiver.getUniqueIdString();
                if (!doNotDisturbUtil.isMessageMuted(currentNotificationsGroup.messageReceiver.getNotificationTriggerPolicyOverrideOrNull(), conversationNotification.getRawMessage())) {
                    this.conversationNotificationsCache.addFirst(conversationNotification);
                }
            } else if (updateExisting) {
                uniqueMessageReceiverId = currentNotificationsGroup.messageReceiver.getUniqueIdString();
            }

            Map<String, ConversationNotificationGroup> uniqueNotificationGroups = new HashMap<>();
            int numberOfNotificationsForCurrentChat = 0;

            ListIterator<ConversationNotification> cacheIterator = this.conversationNotificationsCache.listIterator();
            while (cacheIterator.hasNext()) {
                ConversationNotification cachedConversationNotification = cacheIterator.next();

                if (conversationNotification.getUid().equals(cachedConversationNotification.getUid()) && updateExisting) {
                    if (conversationNotification.isMessageDeleted()) {
                        cacheIterator.remove();
                        continue;
                    }

                    cacheIterator.set(conversationNotification);
                }

                ConversationNotificationGroup cachedConversationNotificationsGroup = cachedConversationNotification.getGroup();
                uniqueNotificationGroups.put(cachedConversationNotificationsGroup.uid, cachedConversationNotificationsGroup);

                if (cachedConversationNotificationsGroup.equals(currentNotificationsGroup)) {
                    numberOfNotificationsForCurrentChat++;
                }
            }

            if (conversationNotificationsCache.isEmpty()) {
                cancelPinLockedNewMessagesNotification();
            }

            final boolean cacheDoesNotContainCurrentNotificationsGroup = this.conversationNotificationsCache.stream().noneMatch(
                cachedConversationNotification -> cachedConversationNotification.getGroup().uid.equals(currentNotificationsGroup.uid)
            );
            if (cacheDoesNotContainCurrentNotificationsGroup) {
                cancelConversationNotification(conversationNotification);
                showIconBadge(conversationNotificationsCache.size());
                return;
            }

            if (updateExisting && lockAppService.isLocked()) {
                return;
            }

            final String latestFullName = currentNotificationsGroup.name;
            boolean isGroupChat = currentNotificationsGroup.messageReceiver instanceof GroupMessageReceiver;
            String parentChannelId = isGroupChat
                ? NotificationChannels.NOTIFICATION_CHANNEL_GROUP_CHATS_DEFAULT
                : NotificationChannels.NOTIFICATION_CHANNEL_CHATS_DEFAULT;
            final int totalUnreadMessagesCount = this.conversationNotificationsCache.size();
            final int totalUnreadConversationsCount = uniqueNotificationGroups.size();
            String channelId = uniqueMessageReceiverId != null && NotificationChannels.doesPerConversationChannelExist(appContext, uniqueMessageReceiverId) ? uniqueMessageReceiverId : parentChannelId;
            NotificationSchema notificationSchema = this.createNotificationSchema(currentNotificationsGroup, conversationNotification.getRawMessage());

            if (notificationSchema == null) {
                logger.warn("No notification - no notification schema");
                return;
            }

            if (lockAppService.isLocked()) {
                showPinLockedNewMessageNotification(notificationSchema, conversationNotification.getUid(), parentChannelId);
                return;
            }

            // make sure pin locked notification is canceled
            cancelPinLockedNewMessagesNotification();

            CharSequence tickerText;
            CharSequence singleMessageText;
            String summaryText = totalUnreadConversationsCount > 1 ?
                ConfigUtils.getSafeQuantityString(appContext, R.plurals.new_messages_in_chats, totalUnreadMessagesCount, totalUnreadMessagesCount, totalUnreadConversationsCount) :
                ConfigUtils.getSafeQuantityString(appContext, R.plurals.new_messages, totalUnreadMessagesCount, totalUnreadMessagesCount);
            String contentTitle;
            Intent notificationIntent;

            /* set avatar, intent and contentTitle */
            notificationIntent = new Intent(appContext, ComposeMessageActivity.class);
            currentNotificationsGroup.messageReceiver.prepareIntent(notificationIntent);
            contentTitle = latestFullName;

            if (conversationCategoryService.isPrivateChat(uniqueMessageReceiverId)) {
                tickerText = summaryText;
                singleMessageText = summaryText;
            } else {
                if (notificationPreferenceService.isShowMessagePreview()) {
                    tickerText = latestFullName + NAME_PREPEND_SEPARATOR + truncate(conversationNotification.getMessage(), MAX_TICKER_TEXT_LENGTH);
                    singleMessageText = conversationNotification.getMessage();
                } else {
                    tickerText = latestFullName + NAME_PREPEND_SEPARATOR + summaryText;
                    singleMessageText = summaryText;
                }
            }

            // Create PendingIntent for notification tab
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
            PendingIntent openPendingIntent = createPendingIntentWithTaskStack(notificationIntent);

            /* ************ ANDROID AUTO ************* */

            int conversationId = currentNotificationsGroup.notificationId * 10;

            Intent replyIntent = new Intent(appContext, NotificationActionService.class);
            replyIntent.setAction(NotificationActionService.ACTION_REPLY);
            IntentDataUtil.addMessageReceiverToIntent(replyIntent, currentNotificationsGroup.messageReceiver);
            PendingIntent replyPendingIntent = PendingIntent.getService(appContext, conversationId, replyIntent, pendingIntentFlags);

            Intent markReadIntent = new Intent(appContext, NotificationActionService.class);
            markReadIntent.setAction(NotificationActionService.ACTION_MARK_AS_READ);
            IntentDataUtil.addMessageReceiverToIntent(markReadIntent, currentNotificationsGroup.messageReceiver);
            PendingIntent markReadPendingIntent = PendingIntent.getService(appContext, conversationId + 1, markReadIntent, pendingIntentFlags);

            Intent ackIntent = new Intent(appContext, NotificationActionService.class);
            ackIntent.setAction(NotificationActionService.ACTION_ACK);
            IntentDataUtil.addMessageReceiverToIntent(ackIntent, currentNotificationsGroup.messageReceiver);
            ackIntent.putExtra(AppConstants.INTENT_DATA_MESSAGE_ID, conversationNotification.getId());
            PendingIntent ackPendingIntent = PendingIntent.getService(appContext, conversationId + 2, ackIntent, pendingIntentFlags);

            Intent decIntent = new Intent(appContext, NotificationActionService.class);
            decIntent.setAction(NotificationActionService.ACTION_DEC);
            IntentDataUtil.addMessageReceiverToIntent(decIntent, currentNotificationsGroup.messageReceiver);
            decIntent.putExtra(AppConstants.INTENT_DATA_MESSAGE_ID, conversationNotification.getId());
            PendingIntent decPendingIntent = PendingIntent.getService(appContext, conversationId + 3, decIntent, pendingIntentFlags);

            long timestamp = System.currentTimeMillis();
            boolean onlyAlertOnce = conversationNotification.isMessageEdited() ||
                conversationNotification.isMessageDeleted() ||
                (timestamp - currentNotificationsGroup.lastNotificationDate) < NOTIFY_AGAIN_TIMEOUT;
            currentNotificationsGroup.lastNotificationDate = timestamp;

            final NotificationCompat.Builder builder;

            summaryText = ConfigUtils.getSafeQuantityString(
                appContext,
                R.plurals.new_messages,
                numberOfNotificationsForCurrentChat,
                numberOfNotificationsForCurrentChat
            );

            if (!notificationPreferenceService.isShowMessagePreview() || conversationCategoryService.isPrivateChat(uniqueMessageReceiverId)) {
                singleMessageText = summaryText;
                tickerText = summaryText;
            }

            // public version of the notification
            NotificationCompat.Builder publicBuilder = new NotificationCompat.Builder(appContext, channelId)
                .setContentTitle(summaryText)
                .setContentText(appContext.getString(R.string.notification_hidden_text))
                .setSmallIcon(R.drawable.ic_notification_small)
                .setOnlyAlertOnce(onlyAlertOnce);

            // private version
            builder = new NotificationCompat.Builder(appContext, channelId)
                .setContentTitle(contentTitle)
                .setContentText(singleMessageText)
                .setTicker(tickerText)
                .setSmallIcon(R.drawable.ic_notification_small)
                .setLargeIcon(currentNotificationsGroup.loadAvatar())
                .setGroup(currentNotificationsGroup.uid)
                .setGroupSummary(false)
                .setOnlyAlertOnce(onlyAlertOnce)
                .setPriority(notificationPreferenceService.getLegacyNotificationPriority())
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicBuilder.build());

            if (!ConfigUtils.supportsNotificationChannels()) {
                if (notificationSchema.soundUri != null) {
                    builder.setSound(notificationSchema.soundUri, AudioManager.STREAM_NOTIFICATION);
                }
                if (notificationSchema.shouldVibrate) {
                    builder.setVibrate(NotificationChannels.VIBRATE_PATTERN_REGULAR);
                }
                if (notificationSchema.shouldUseLight) {
                    builder.setLights(ContextCompat.getColor(appContext, R.color.md_theme_light_primary), 2500, 2500);
                }
            }

            // Add identity to notification for system DND priority override
            builder.addPerson(conversationNotification.getSenderPerson());

            if (notificationPreferenceService.isShowMessagePreview() && !conversationCategoryService.isPrivateChat(uniqueMessageReceiverId)) {
                builder.setStyle(getMessagingStyle(currentNotificationsGroup, getConversationNotificationsForGroup(currentNotificationsGroup)));
                if (uniqueMessageReceiverId != null) {
                    builder.setShortcutId(uniqueMessageReceiverId);
                    builder.setLocusId(new LocusIdCompat(uniqueMessageReceiverId));
                }
                addConversationNotificationActions(builder, replyPendingIntent, ackPendingIntent, markReadPendingIntent, conversationNotification, numberOfNotificationsForCurrentChat, uniqueMessageReceiverId, currentNotificationsGroup);
                addWearableExtender(builder, currentNotificationsGroup, ackPendingIntent, decPendingIntent, replyPendingIntent, markReadPendingIntent, numberOfNotificationsForCurrentChat, uniqueMessageReceiverId);
            }

            builder.setContentIntent(openPendingIntent);

            if (updateExisting) {
                List<StatusBarNotification> notifications = notificationManagerCompat.getActiveNotifications();
                for (StatusBarNotification notification : notifications) {
                    if (notification.getId() == currentNotificationsGroup.notificationId) {
                        this.notify(currentNotificationsGroup.notificationId, builder, notificationSchema, parentChannelId);
                        break;
                    }
                }
            } else {
                this.notify(currentNotificationsGroup.notificationId, builder, notificationSchema, parentChannelId);
            }

            logger.info(
                "Showing notification {} sound: {}",
                conversationNotification.getUid(),
                notificationSchema.soundUri != null ? notificationSchema.soundUri.toString() : null
            );

            showIconBadge(totalUnreadMessagesCount);
        }
    }

    private int getRandomRequestCode() {
        return (int) System.nanoTime();
    }

    @Nullable
    private NotificationCompat.MessagingStyle getMessagingStyle(ConversationNotificationGroup group, ArrayList<ConversationNotification> notifications) {
        ContactService contactService = getContactService();

        if (contactService == null) {
            logger.warn("Contact service is null");
            return null;
        }

        String myIdentity = identityProvider.getIdentityString();

        String chatName = group.name;
        boolean isGroupChat = group.messageReceiver instanceof GroupMessageReceiver;
        Person.Builder builder = new Person.Builder()
            .setName(appContext.getString(R.string.me_myself_and_i))
            .setKey(ContactUtil.getUniqueIdString(myIdentity));

        Bitmap avatar = contactService.getAvatar(myIdentity, false);
        if (avatar != null) {
            IconCompat iconCompat = IconCompat.createWithBitmap(avatar);
            builder.setIcon(iconCompat);
        }
        Person me = builder.build();

        NotificationCompat.MessagingStyle messagingStyle = new NotificationCompat.MessagingStyle(me);
        messagingStyle.setConversationTitle(isGroupChat ? chatName : null);
        messagingStyle.setGroupConversation(isGroupChat);

        List<NotificationCompat.MessagingStyle.Message> messages = new ArrayList<>();

        for (int i = 0; i < Math.min(notifications.size(), MAXIMUM_RETAINED_MESSAGES); i++) {
            ConversationNotification notification = notifications.get(i);

            CharSequence messageText = notification.getMessage();
            Instant time = notification.getCreatedAt();
            long created = time == null ? 0 : time.toEpochMilli();

            Person person = notification.getSenderPerson();

            // hack to show full name in non-group chats
            if (!isGroupChat) {
                if (person == null) {
                    person = new Person.Builder()
                        .setName(chatName).build();
                } else {
                    person = person.toBuilder()
                        .setName(chatName).build();
                }
            }

            NotificationCompat.MessagingStyle.Message message = new NotificationCompat.MessagingStyle.Message(messageText, created, person);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && notification.getThumbnailMimeType() != null) {
                var thumbnailUri = notification.getOrCreateThumbnail();
                if (thumbnailUri != null) {
                    message.setData(notification.getThumbnailMimeType(), thumbnailUri);
                }
            }
            messages.add(message);
        }

        Collections.reverse(messages);

        for (NotificationCompat.MessagingStyle.Message message : messages) {
            messagingStyle.addMessage(message);
        }

        return messagingStyle;
    }

    @NonNull
    private ArrayList<ConversationNotification> getConversationNotificationsForGroup(ConversationNotificationGroup group) {
        ArrayList<ConversationNotification> notifications = new ArrayList<>();
        for (ConversationNotification notification : conversationNotificationsCache) {
            if (notification.getGroup().uid.equals(group.uid)) {
                notifications.add(notification);
            }
        }
        return notifications;
    }

    private void addConversationNotificationActions(
        NotificationCompat.Builder builder,
        PendingIntent replyPendingIntent,
        PendingIntent ackPendingIntent,
        PendingIntent markReadPendingIntent,
        ConversationNotification conversationNotification,
        int unreadMessagesCount,
        String uniqueId,
        ConversationNotificationGroup newestGroup
    ) {
        // add action buttons
        boolean showMarkAsReadAction = false;

        if (notificationPreferenceService.isShowMessagePreview() && !conversationCategoryService.isPrivateChat(uniqueId)) {
            RemoteInput remoteInput = new RemoteInput.Builder(AppConstants.EXTRA_VOICE_REPLY)
                .setLabel(appContext.getString(R.string.compose_message_and_enter))
                .build();

            NotificationCompat.Action.Builder replyActionBuilder = new NotificationCompat.Action.Builder(
                R.drawable.ic_reply_black_18dp, appContext.getString(R.string.wearable_reply), replyPendingIntent)
                .addRemoteInput(remoteInput)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .setShowsUserInterface(false);

            if (Build.VERSION.SDK_INT >= 29) {
                replyActionBuilder.setAllowGeneratedReplies(!notificationPreferenceService.getDisableSmartReplies());
            }

            builder.addAction(replyActionBuilder.build());
            if (newestGroup.messageReceiver instanceof GroupMessageReceiver) {
                if (unreadMessagesCount == 1) {
                    builder.addAction(getThumbsUpAction(ackPendingIntent));
                }
                showMarkAsReadAction = true;
            } else if (newestGroup.messageReceiver instanceof ContactMessageReceiver) {
                if (MessageType.VOIP_STATUS.equals(conversationNotification.getMessageType())) {
                    // Create an intent for the call action
                    Intent callActivityIntent = new Intent(appContext, CallActivity.class);
                    callActivityIntent.putExtra(EXTRA_ACTIVITY_MODE, CallActivity.MODE_OUTGOING_CALL);
                    callActivityIntent.putExtra(EXTRA_CONTACT_IDENTITY, ((ContactMessageReceiver) newestGroup.messageReceiver).getContact().getIdentity());
                    callActivityIntent.putExtra(EXTRA_IS_INITIATOR, true);
                    callActivityIntent.putExtra(EXTRA_CALL_ID, -1L);

                    PendingIntent callPendingIntent = PendingIntent.getActivity(
                        appContext,
                        getRandomRequestCode(), // http://stackoverflow.com/questions/19031861/pendingintent-not-opening-activity-in-android-4-3
                        callActivityIntent,
                        this.pendingIntentFlags);

                    builder.addAction(
                        new NotificationCompat.Action.Builder(R.drawable.ic_call_white_24dp, appContext.getString(R.string.voip_return_call), callPendingIntent)
                            .setShowsUserInterface(true)
                            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_CALL)
                            .build());
                } else {
                    if (unreadMessagesCount == 1) {
                        builder.addAction(getThumbsUpAction(ackPendingIntent));
                    }
                    showMarkAsReadAction = true;
                }
            }
        }

        if (showMarkAsReadAction) {
            builder.addAction(getMarkAsReadAction(markReadPendingIntent));
        } else {
            builder.addInvisibleAction(getMarkAsReadAction(markReadPendingIntent));
        }
    }

    @NonNull
    @Contract("_ -> new")
    private NotificationCompat.Action getMarkAsReadAction(PendingIntent markReadPendingIntent) {
        return new NotificationCompat.Action.Builder(R.drawable.ic_mark_read_bitmap, appContext.getString(R.string.mark_read_short), markReadPendingIntent)
            .setShowsUserInterface(false)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .build();
    }

    @NonNull
    @Contract("_ -> new")
    private NotificationCompat.Action getThumbsUpAction(PendingIntent ackPendingIntent) {
        return new NotificationCompat.Action.Builder(R.drawable.emoji_thumbs_up, appContext.getString(R.string.acknowledge), ackPendingIntent)
            .setShowsUserInterface(false)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_THUMBS_UP)
            .build();
    }

    private void addWearableExtender(
        NotificationCompat.Builder builder,
        ConversationNotificationGroup newestGroup,
        PendingIntent ackPendingIntent,
        PendingIntent decPendingIntent,
        PendingIntent replyPendingIntent,
        PendingIntent markReadPendingIntent,
        int numberOfUnreadMessagesForThisChat,
        String uniqueId
    ) {

        String replyLabel = String.format(appContext.getString(R.string.wearable_reply_label), newestGroup.name);
        RemoteInput remoteInput = new RemoteInput.Builder(AppConstants.EXTRA_VOICE_REPLY)
            .setLabel(replyLabel)
            .setChoices(appContext.getResources().getStringArray(R.array.wearable_reply_choices))
            .build();

        NotificationCompat.Action.Builder replyActionBuilder =
            new NotificationCompat.Action.Builder(R.drawable.ic_wear_full_reply,
                appContext.getString(R.string.wearable_reply), replyPendingIntent)
                .addRemoteInput(remoteInput)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .setShowsUserInterface(false);

        NotificationCompat.Action.WearableExtender replyActionExtender =
            new NotificationCompat.Action.WearableExtender()
                .setHintDisplayActionInline(true);

        NotificationCompat.WearableExtender wearableExtender = new NotificationCompat.WearableExtender()
            .addAction(replyActionBuilder.extend(replyActionExtender).build());

        if (notificationPreferenceService.isShowMessagePreview() && !conversationCategoryService.isPrivateChat(uniqueId)) {
            if (numberOfUnreadMessagesForThisChat == 1 && newestGroup.messageReceiver instanceof ContactMessageReceiver) {
                NotificationCompat.Action ackAction = new NotificationCompat.Action.Builder(R.drawable.emoji_thumbs_up,
                    appContext.getString(R.string.acknowledge), ackPendingIntent)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_THUMBS_UP)
                    .build();
                wearableExtender.addAction(ackAction);

                NotificationCompat.Action decAction = new NotificationCompat.Action.Builder(R.drawable.emoji_thumbs_down,
                    appContext.getString(R.string.decline), decPendingIntent)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_THUMBS_DOWN)
                    .build();
                wearableExtender.addAction(decAction);
            }

            NotificationCompat.Action markReadAction = new NotificationCompat.Action.Builder(R.drawable.ic_mark_read,
                appContext.getString(R.string.mark_read), markReadPendingIntent)
                .setShowsUserInterface(false)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                .build();
            wearableExtender.addAction(markReadAction);
        }
        builder.extend(wearableExtender);
        builder.extend(
            new NotificationCompat.CarExtender().setLargeIcon(newestGroup.loadAvatar())
        );
    }

    @Override
    public void cancelConversationNotificationsOnLockApp() {
        // cancel cached notification ids if still available
        if (!conversationNotificationsCache.isEmpty()) {
            boolean containedAnyNotificationToAnUnMutedReceiver = conversationNotificationsCache
                .stream()
                .anyMatch(
                    conversationNotification ->
                        !doNotDisturbUtil.isMessageMuted(
                            conversationNotification.getGroup().messageReceiver.getNotificationTriggerPolicyOverrideOrNull(),
                            conversationNotification.getRawMessage()
                        )
                );
            cancelCachedConversationNotifications();
            /*
             * We do not want to show the pin-locked-new-message notification if all the cached notifications
             * originated from NOW muted receivers
             */
            if (containedAnyNotificationToAnUnMutedReceiver) {
                showDefaultPinLockedNewMessageNotification();
            }
        } else if (cancelAllMessageCategoryNotifications()) {
            // get and cancel active conversations notifications trough notificationManager
            /*
             * In this case we cant really tell if all the cancelled system notifications are from blocked
             * receivers or not. That all the system notifications that were cancelled here belonging to NOW
             * muted receivers is an extreme edge case. So we display the pin-locked-new-message notification.
             *
             * Note: One could determine the actual receiver of the cancelled system notifications by its tag.
             * But still than we would be missing the raw-message required for `DNDUtils.isMuted` method.
             */
            showDefaultPinLockedNewMessageNotification();
        }
        // hack to detect active conversation Notifications by checking for active pending Intent
        else if (isConversationNotificationVisible()) {
            cancel(NotificationIDs.NEW_MESSAGE_NOTIFICATION_ID);
            showDefaultPinLockedNewMessageNotification();
        }
    }

    @Override
    public void cancelConversationNotification(@Nullable final String... uids) {
        if (uids == null) {
            logger.warn("Unique id array must not be null! Ignoring.");
            return;
        }
        synchronized (this.conversationNotificationsCache) {
            logger.info("Cancel {} conversation notifications", uids.length);
            for (final String uid : uids) {
                ConversationNotification conversationNotification = conversationNotificationsCache.stream()
                    .filter(conversationNotification1 -> TestUtil.compare(conversationNotification1.getUid(), uid))
                    .findFirst()
                    .orElse(null);

                if (conversationNotification != null) {
                    logger.info("Cancel notification {}", uid);
                    cancelConversationNotification(conversationNotification);
                } else {
                    logger.info("Notification {} not found", uid);
                }
            }

            showIconBadge(this.conversationNotificationsCache.size());

            // no unread conversations left. make sure PIN locked notification is canceled as well
            if (this.conversationNotificationsCache.isEmpty()) {
                cancelPinLockedNewMessagesNotification();
            }
        }

        WidgetUpdater.update();
    }

    private void cancelConversationNotification(@Nullable ConversationNotification conversationNotification) {
        if (conversationNotification == null) {
            return;
        }
        synchronized (this.conversationNotificationsCache) {
            logger.info("Destroy notification {}", conversationNotification.getUid());
            cancel(conversationNotification.getGroup().notificationId);
            conversationNotification.getGroup().conversations.remove(conversationNotification);
        }
    }

    @Override
    public void cancelAllCachedConversationNotifications() {
        this.cancel(NotificationIDs.NEW_MESSAGE_NOTIFICATION_ID);

        synchronized (this.conversationNotificationsCache) {
            if (!conversationNotificationsCache.isEmpty()) {
                for (ConversationNotification conversationNotification : conversationNotificationsCache) {
                    this.cancelConversationNotification(conversationNotification);
                }
                conversationNotificationsCache.clear();
            }
        }
    }

    @Nullable
    private NotificationSchema createNotificationSchema(@NonNull ConversationNotificationGroup notificationGroup, @Nullable CharSequence rawMessage) {
        final @NonNull MessageReceiver messageReceiver = notificationGroup.messageReceiver;
        if (doNotDisturbUtil.isMessageMuted(messageReceiver.getNotificationTriggerPolicyOverrideOrNull(), rawMessage)) {
            return null;
        }
        if (messageReceiver instanceof GroupMessageReceiver) {
            return new NotificationSchema(
                notificationPreferenceService.isLegacyGroupVibrate(),
                ringtoneService.getGroupRingtone(messageReceiver.getUniqueIdString()),
                notificationPreferenceService.isLegacyGroupNotificationLightEnabled()
            );
        } else if (messageReceiver instanceof ContactMessageReceiver) {
            return new NotificationSchema(
                notificationPreferenceService.isLegacyNotificationVibrate(),
                ringtoneService.getContactRingtone(messageReceiver.getUniqueIdString()),
                notificationPreferenceService.isLegacyNotificationLightEnabled()
            );
        }
        return new NotificationSchema();
    }

    @Override
    public void cancel(ConversationModel conversationModel) {
        if (conversationModel != null) {
            this.cancel(conversationModel.messageReceiver);
        }
    }

    @Override
    public void cancel(@Nullable final MessageReceiver receiver) {
        if (receiver != null) {
            int id = receiver.getUniqueId();
            String uniqueIdString = receiver.getUniqueIdString();

            this.cancel(id, uniqueIdString);
        }
        this.cancel(NotificationIDs.NEW_MESSAGE_NOTIFICATION_ID);
    }

    @Override
    public void cancelCachedConversationNotifications() {
        /* called when pin lock becomes active */
        synchronized (this.conversationNotificationsCache) {
            cancelAllCachedConversationNotifications();
            showIconBadge(this.conversationNotificationsCache.size());
        }
    }

    @Override
    public boolean cancelAllMessageCategoryNotifications() {
        boolean cancelledIDs = false;
        try {
            List<StatusBarNotification> notifications = notificationManagerCompat.getActiveNotifications();
            if (!notifications.isEmpty()) {
                for (StatusBarNotification notification : notifications) {
                    if (notification.getNotification() != null && Notification.CATEGORY_MESSAGE.equals(notification.getNotification().category)) {
                        notificationManagerCompat.cancel(notification.getId());
                        cancelledIDs = true;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Could not cancel notifications of CATEGORY_MESSAGE ", e);
        }
        return cancelledIDs;
    }

    @NonNull
    private NotificationSchema getDefaultNotificationSchema() {
        return new NotificationSchema(
            notificationPreferenceService.isLegacyNotificationVibrate(),
            notificationPreferenceService.getLegacyNotificationSound(),
            notificationPreferenceService.isLegacyNotificationLightEnabled()
        );
    }

    @Override
    public boolean isConversationNotificationVisible() {
        Intent notificationIntent = new Intent(appContext, ComposeMessageActivity.class);
        PendingIntent test = PendingIntent.getActivity(
            appContext,
            NotificationIDs.NEW_MESSAGE_NOTIFICATION_ID,
            notificationIntent,
            PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        return test != null;
    }

    private void showDefaultPinLockedNewMessageNotification() {
        logger.debug("showDefaultPinLockedNewMessageNotification");
        this.showPinLockedNewMessageNotification(
            new NotificationSchema(),
            PIN_LOCKED_NOTIFICATION_ID,
            NotificationChannels.NOTIFICATION_CHANNEL_CHAT_UPDATE
        );
    }

    @Override
    public void showPinLockedNewMessageNotification(@NonNull NotificationSchema notificationSchema, String uid, String channelId) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(appContext.getString(R.string.new_messages_locked))
            .setContentText(appContext.getString(R.string.new_messages_locked_description))
            .setTicker(appContext.getString(R.string.new_messages_locked))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(notificationPreferenceService.getLegacyNotificationPriority())
            .setOnlyAlertOnce(false)
            .setContentIntent(getPendingIntentForActivity(HomeActivity.class))
            .setSound(notificationSchema.soundUri, AudioManager.STREAM_NOTIFICATION);

        if (notificationSchema.shouldVibrate) {
            builder.setVibrate(NotificationChannels.VIBRATE_PATTERN_REGULAR);
        }

        this.notify(NotificationIDs.NEW_MESSAGE_PIN_LOCKED_NOTIFICATION_ID, builder, null, channelId);

        showIconBadge(0);

        // cancel this message as soon as the app is unlocked
        this.lockAppService.addOnLockAppStateListener(isLocked -> {
            logger.debug("LockAppState changed. locked = {}", isLocked);
            if (!isLocked) {
                cancelPinLockedNewMessagesNotification();
                return true;
            }
            return false;
        });

        logger.info("Showing generic notification (pin locked) = {} channelId = {} ", uid, channelId);
    }

    @Override
    public void showMasterKeyLockedNewMessageNotification() {
        this.showMasterKeyLockedNewMessageNotification(this.getDefaultNotificationSchema());
    }

    private void showMasterKeyLockedNewMessageNotification(@NonNull NotificationSchema notificationSchema) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, NotificationChannels.NOTIFICATION_CHANNEL_CHATS_DEFAULT)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(appContext.getString(R.string.new_messages_locked))
            .setContentText(appContext.getString(R.string.new_messages_locked_description))
            .setTicker(appContext.getString(R.string.new_messages_locked))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setOnlyAlertOnce(false)
            .setContentIntent(getPendingIntentForActivity(HomeActivity.class))
            .setSound(notificationSchema.soundUri, AudioManager.STREAM_NOTIFICATION);

        if (notificationSchema.shouldVibrate) {
            builder.setVibrate(NotificationChannels.VIBRATE_PATTERN_REGULAR);
        }

        this.notify(
            NotificationIDs.NEW_MESSAGE_LOCKED_NOTIFICATION_ID,
            builder,
            null,
            NotificationChannels.NOTIFICATION_CHANNEL_CHATS_DEFAULT
        );

        logger.info("Showing generic notification (master key locked)");
    }

    private void cancelPinLockedNewMessagesNotification() {
        logger.debug("cancel Pin Locked New Messages");
        this.cancel(NotificationIDs.NEW_MESSAGE_PIN_LOCKED_NOTIFICATION_ID);
    }

    @Override
    public void showServerMessage(ServerMessageModel m) {
        if (doNotDisturbUtil.isDoNotDisturbActive()) {
            return;
        }

        Intent intent = new Intent(appContext, ServerMessageActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, NotificationChannels.NOTIFICATION_CHANNEL_NOTICE)
            .setSmallIcon(R.drawable.ic_error_red_24dp)
            .setTicker(appContext.getString(R.string.server_message_title))
            .setContentTitle(appContext.getString(R.string.server_message_title))
            .setContentText(appContext.getString(R.string.tap_here_for_more))
            .setContentIntent(pendingIntent)
            .setLocalOnly(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true);

        this.notify(NotificationIDs.SERVER_MESSAGE_NOTIFICATION_ID, builder, null, NotificationChannels.NOTIFICATION_CHANNEL_NOTICE);
    }

    private PendingIntent createPendingIntentWithTaskStack(@NonNull Intent intent) {
        intent.setData((Uri.parse("foobar://" + SystemClock.elapsedRealtime())));

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(appContext);
        stackBuilder.addNextIntentWithParentStack(intent);
        return stackBuilder.getPendingIntent(0, this.pendingIntentFlags);
    }

    private PendingIntent getPendingIntentForActivity(Class<? extends Activity> activityClass) {
        Intent notificationIntent = new Intent(appContext, activityClass);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        return createPendingIntentWithTaskStack(notificationIntent);
    }

    @Override
    public void showUnsentMessageNotification(@NonNull List<AbstractMessageModel> failedMessages) {
        int num = failedMessages.size();

        if (num > 0) {
            Intent sendIntent = new Intent(appContext, ReSendMessagesBroadcastReceiver.class);
            IntentDataUtil.appendMultipleMessageTypes(failedMessages, sendIntent);

            PendingIntent sendPendingIntent = PendingIntent.getBroadcast(
                appContext,
                NotificationIDs.UNSENT_MESSAGE_NOTIFICATION_ID,
                sendIntent,
                this.pendingIntentFlags
            );

            NotificationCompat.Action tryAgainAction = new NotificationCompat.Action.Builder(
                R.drawable.ic_wear_full_retry,
                appContext.getString(R.string.try_again),
                sendPendingIntent
            ).build();
            NotificationCompat.WearableExtender wearableExtender = new NotificationCompat.WearableExtender();
            wearableExtender.addAction(tryAgainAction);

            Intent cancelIntent = new Intent(appContext, CancelResendMessagesBroadcastReceiver.class);
            IntentDataUtil.appendMultipleMessageTypes(failedMessages, cancelIntent);

            PendingIntent cancelSendingMessages = PendingIntent.getBroadcast(
                appContext,
                NotificationIDs.UNSENT_MESSAGE_NOTIFICATION_ID,
                cancelIntent,
                this.pendingIntentFlags
            );

            String content = ConfigUtils.getSafeQuantityString(appContext, R.plurals.sending_message_failed, num, num);

            NotificationCompat.Builder builder =
                new NotificationCompat.Builder(appContext, NotificationChannels.NOTIFICATION_CHANNEL_ALERT)
                    .setSmallIcon(R.drawable.ic_error_red_24dp)
                    .setTicker(content)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ERROR)
                    .setContentIntent(getPendingIntentForActivity(HomeActivity.class))
                    .extend(wearableExtender)
                    .setContentTitle(appContext.getString(R.string.app_name))
                    .setContentText(content)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                    .setDeleteIntent(cancelSendingMessages)
                    .addAction(R.drawable.ic_refresh_white_24dp, appContext.getString(R.string.try_again), sendPendingIntent);

            this.notify(NotificationIDs.UNSENT_MESSAGE_NOTIFICATION_ID, builder, null, NotificationChannels.NOTIFICATION_CHANNEL_ALERT);
        } else {
            this.cancel(NotificationIDs.UNSENT_MESSAGE_NOTIFICATION_ID);
        }
    }

    @Override
    public void showForwardSecurityMessageRejectedNotification(@NonNull MessageReceiver<?> messageReceiver) {
        fsNotificationManager.showForwardSecurityNotification(messageReceiver);
    }

    @Override
    public void showSafeBackupFailed(int fullDaysSinceLastBackup) {
        Intent intent = new Intent(appContext, BackupAdminActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(appContext, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        String content = String.format(appContext.getString(R.string.safe_failed_notification), fullDaysSinceLastBackup);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, NotificationChannels.NOTIFICATION_CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_error_red_24dp)
            .setTicker(content)
            .setLocalOnly(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(pendingIntent)
            .setContentTitle(appContext.getString(R.string.app_name))
            .setContentText(content)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(content));

        this.notify(NotificationIDs.SAFE_FAILED_NOTIFICATION_ID, builder, null, NotificationChannels.NOTIFICATION_CHANNEL_ALERT);
    }

    @Override
    public void cancelSafeBackupFailed() {
        this.cancel(NotificationIDs.SAFE_FAILED_NOTIFICATION_ID);
    }

    @Override
    public void cancelWorkSyncProgress() {
        this.cancel(WORK_SYNC_NOTIFICATION_ID);
    }

    @Override
    public void showNewSyncedContactsNotification(@Nullable List<ContactModel> contactModels) {
        if (contactModels != null && !contactModels.isEmpty()) {
            String message;
            Intent notificationIntent;

            if (contactModels.size() > 1) {
                StringBuilder contactListBuilder = new StringBuilder();
                for (ContactModel contactModel : contactModels) {
                    if (contactListBuilder.length() > 0) {
                        contactListBuilder.append(", ");
                    }
                    contactListBuilder.append(
                        NameUtil.getContactDisplayName(contactModel, preferenceService.getContactNameFormat())
                    );
                }
                message = appContext.getString(R.string.notification_contact_has_joined_multiple, contactModels.size(), contactListBuilder.toString());
                notificationIntent = HomeActivity.createIntent(appContext, true);
            } else {
                String name = NameUtil.getContactDisplayName(contactModels.get(0), preferenceService.getContactNameFormat());
                message = String.format(appContext.getString(R.string.notification_contact_has_joined), name);
                notificationIntent = new Intent(appContext, ComposeMessageActivity.class);
                if (getContactService() != null) {
                    getContactService().createReceiver(contactModels.get(0)).prepareIntent(notificationIntent);
                }
            }
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
            PendingIntent openPendingIntent = createPendingIntentWithTaskStack(notificationIntent);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, NotificationChannels.NOTIFICATION_CHANNEL_NEW_SYNCED_CONTACTS)
                .setSmallIcon(R.drawable.ic_notification_small)
                .setContentTitle(appContext.getString(R.string.notification_channel_new_contact))
                .setContentText(message)
                .setContentIntent(openPendingIntent)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

            if (notificationPreferenceService.isLegacyNotificationVibrate()) {
                builder.setVibrate(NotificationChannels.VIBRATE_PATTERN_REGULAR);
            }

            this.notify(
                NotificationIDs.NEW_SYNCED_CONTACTS_NOTIFICATION_ID,
                builder,
                null,
                NotificationChannels.NOTIFICATION_CHANNEL_NEW_SYNCED_CONTACTS
            );
        }
    }

    /**
     * Create and show notification
     */
    private void notify(int id, NotificationCompat.Builder builder, @Nullable NotificationSchema schema, @NonNull String channelId) {
        try {
            notificationManagerCompat.notify(id, builder.build());
        } catch (SecurityException e) {
            // some phones revoke access to selected sound files for notifications after an OS upgrade
            logger.error("Can't show notification. Falling back to default ringtone", e);

            if (NotificationChannels.NOTIFICATION_CHANNEL_CHATS_DEFAULT.equals(channelId) ||
                NotificationChannels.NOTIFICATION_CHANNEL_GROUP_CHATS_DEFAULT.equals(channelId) ||
                NotificationChannels.NOTIFICATION_CHANNEL_INCOMING_CALLS.equals(channelId) ||
                NotificationChannels.NOTIFICATION_CHANNEL_INCOMING_GROUP_CALLS.equals(channelId)
            ) {
                if (schema != null &&
                    schema.soundUri != null &&
                    !DEFAULT_NOTIFICATION_URI.equals(schema.soundUri) &&
                    !DEFAULT_RINGTONE_URI.equals(schema.soundUri)
                ) {
                    // post notification to a silent channel
                    builder.setChannelId(NotificationChannels.NOTIFICATION_CHANNEL_CHAT_UPDATE);
                    try {
                        notificationManagerCompat.notify(id, builder.build());
                    } catch (Exception ex) {
                        logger.error("Failed to show fallback notification", ex);
                    }
                }
            }
        } catch (Exception e) {
            // catch FileUriExposedException - see https://commonsware.com/blog/2016/09/07/notifications-sounds-android-7p0-aggravation.html
            logger.error("Exception", e);
        }
    }

    @Override
    public void cancel(int notificationId) {
        //make sure that pending intent is also cancelled to allow to check for active conversation notifications pre SDK 23
        Intent intent = new Intent(appContext, ComposeMessageActivity.class);
        if (notificationId == NotificationIDs.NEW_MESSAGE_NOTIFICATION_ID) {
            PendingIntent pendingConversationIntent = PendingIntent.getActivity(
                appContext,
                NotificationIDs.NEW_MESSAGE_NOTIFICATION_ID,
                intent,
                this.pendingIntentFlags
            );
            if (pendingConversationIntent != null) {
                pendingConversationIntent.cancel();
            }
        }
        notificationManagerCompat.cancel(notificationId);
    }

    @Override
    public void cancel(@NonNull String identity) {
        int uniqueId = ContactUtil.getUniqueId(identity);
        String uniqueIdString = ContactUtil.getUniqueIdString(identity);

        this.cancel(uniqueId, uniqueIdString);
    }

    private void cancel(int uniqueId, @Nullable String uniqueIdString) {
        if (uniqueId != 0) {
            this.cancel(uniqueId);
        }

        // remove all cached notifications from the receiver
        synchronized (this.conversationNotificationsCache) {
            for (Iterator<ConversationNotification> iterator = this.conversationNotificationsCache.iterator(); iterator.hasNext(); ) {
                ConversationNotification conversationNotification = iterator.next();
                if (conversationNotification != null
                    && conversationNotification.getGroup().messageReceiver.getUniqueIdString().equals(uniqueIdString)) {
                    iterator.remove();
                    cancelConversationNotification(conversationNotification);
                }
            }
            showIconBadge(conversationNotificationsCache.size());
        }
        this.cancel(NotificationIDs.NEW_MESSAGE_NOTIFICATION_ID);

        WidgetUpdater.update();
    }

    private void showIconBadge(int unreadMessages) {
        logger.info("Badge: showing {} unread", unreadMessages);

        if (appContext.getPackageManager().resolveContentProvider("com.teslacoilsw.notifier", 0) != null) {
            // nova launcher / teslaunread
            try {
                String launcherClassName = appContext.getPackageManager().getLaunchIntentForPackage(BuildConfig.APPLICATION_ID).getComponent().getClassName();
                final ContentValues contentValues = new ContentValues();
                contentValues.put("tag", BuildConfig.APPLICATION_ID + "/" + launcherClassName);
                contentValues.put("count", unreadMessages);

                appContext.getApplicationContext().getContentResolver().insert(Uri.parse("content://com.teslacoilsw.notifier/unread_count"), contentValues);
            } catch (Exception e) {
                logger.error("Exception", e);
            }
        } else if (ConfigUtils.isHuaweiDevice()) {
            try {
                String launcherClassName = appContext.getPackageManager().getLaunchIntentForPackage(BuildConfig.APPLICATION_ID).getComponent().getClassName();
                Bundle localBundle = new Bundle();
                localBundle.putString("package", BuildConfig.APPLICATION_ID);
                localBundle.putString("class", launcherClassName);
                localBundle.putInt("badgenumber", unreadMessages);
                appContext.getContentResolver().call(Uri.parse("content://com.huawei.android.launcher.settings/badge/"), "change_badge", null, localBundle);
            } catch (Exception e) {
                logger.error("Exception", e);
            }
        } else if (ConfigUtils.isSonyDevice()) {
            try {
                String launcherClassName = appContext.getPackageManager().getLaunchIntentForPackage(BuildConfig.APPLICATION_ID).getComponent().getClassName();
                if (appContext.getPackageManager().resolveContentProvider("com.sonymobile.home.resourceprovider", 0) != null) {
                    // use content provider
                    final ContentValues contentValues = new ContentValues();
                    contentValues.put("badge_count", unreadMessages);
                    contentValues.put("package_name", BuildConfig.APPLICATION_ID);
                    contentValues.put("activity_name", launcherClassName);

                    if (isMainThread()) {
                        if (queryHandler == null) {
                            queryHandler = new AsyncQueryHandler(
                                appContext.getApplicationContext().getContentResolver()) {
                            };
                        }
                        queryHandler.startInsert(0, null, Uri.parse("content://com.sonymobile.home.resourceprovider/badge"), contentValues);
                    } else {
                        appContext.getApplicationContext().getContentResolver().insert(Uri.parse("content://com.sonymobile.home.resourceprovider/badge"), contentValues);
                    }
                } else {
                    // use broadcast
                    Intent intent = new Intent("com.sonyericsson.home.action.UPDATE_BADGE");
                    intent.putExtra("com.sonyericsson.home.intent.extra.badge.PACKAGE_NAME", BuildConfig.APPLICATION_ID);
                    intent.putExtra("com.sonyericsson.home.intent.extra.badge.ACTIVITY_NAME", launcherClassName);
                    intent.putExtra("com.sonyericsson.home.intent.extra.badge.MESSAGE", String.valueOf(unreadMessages));
                    intent.putExtra("com.sonyericsson.home.intent.extra.badge.SHOW_MESSAGE", unreadMessages > 0);
                    appContext.sendBroadcast(intent);
                }
            } catch (Exception e) {
                logger.error("Exception", e);
            }
        } else {
            // also works on LG and later HTC devices
            try {
                String launcherClassName = appContext.getPackageManager().getLaunchIntentForPackage(BuildConfig.APPLICATION_ID).getComponent().getClassName();
                Intent intent = new Intent("android.intent.action.BADGE_COUNT_UPDATE");
                intent.putExtra("badge_count", unreadMessages);
                intent.putExtra("badge_count_package_name", BuildConfig.APPLICATION_ID);
                intent.putExtra("badge_count_class_name", launcherClassName);
                appContext.sendBroadcast(intent);
            } catch (Exception e) {
                logger.error("Exception", e);
            }
        }
    }

    @Override
    public void showWebclientResumeFailed(String msg) {
        NotificationCompat.Builder builder =
            new NotificationCompat.Builder(appContext, NotificationChannels.NOTIFICATION_CHANNEL_NOTICE)
                .setSmallIcon(R.drawable.ic_web_notification)
                .setTicker(msg)
                .setLocalOnly(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setContentTitle(appContext.getString(R.string.app_name))
                .setContentText(msg)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(msg));
        this.notify(NotificationIDs.WEB_RESUME_FAILED_NOTIFICATION_ID, builder, null, NotificationChannels.NOTIFICATION_CHANNEL_NOTICE);
    }

    @Override
    public void cancelRestartNotification() {
        cancel(APP_RESTART_NOTIFICATION_ID);
    }

    @Override
    public void cancelRestoreNotification() {
        cancel(RESTORE_COMPLETION_NOTIFICATION_ID);
    }
}
