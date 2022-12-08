/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2022 Threema GmbH
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

package ch.threema.app.services;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.AsyncQueryHandler;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Formatter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.LocusIdCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.IconCompat;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.BackupAdminActivity;
import ch.threema.app.activities.ComposeMessageActivity;
import ch.threema.app.activities.HomeActivity;
import ch.threema.app.activities.ServerMessageActivity;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.grouplinks.IncomingGroupRequestActivity;
import ch.threema.app.grouplinks.OutgoingGroupRequestActivity;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.notifications.NotificationBuilderWrapper;
import ch.threema.app.receivers.ReSendMessagesBroadcastReceiver;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DNDUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.SoundUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.TextUtil;
import ch.threema.app.utils.WidgetUtil;
import ch.threema.app.voip.activities.CallActivity;
import ch.threema.app.voip.activities.GroupCallActivity;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.ServerMessageModel;
import ch.threema.storage.models.group.IncomingGroupJoinRequestModel;
import ch.threema.storage.models.group.OutgoingGroupJoinRequestModel;
import java8.util.stream.StreamSupport;

import static android.provider.Settings.System.DEFAULT_NOTIFICATION_URI;
import static android.provider.Settings.System.DEFAULT_RINGTONE_URI;
import static androidx.core.app.NotificationCompat.MessagingStyle.MAXIMUM_RETAINED_MESSAGES;
import static ch.threema.app.ThreemaApplication.WORK_SYNC_NOTIFICATION_ID;
import static ch.threema.app.backuprestore.csv.RestoreService.RESTORE_COMPLETION_NOTIFICATION_ID;
import static ch.threema.app.notifications.NotificationBuilderWrapper.VIBRATE_PATTERN_GROUP_CALL;
import static ch.threema.app.utils.IntentDataUtil.PENDING_INTENT_FLAG_IMMUTABLE;
import static ch.threema.app.voip.services.VoipCallService.EXTRA_ACTIVITY_MODE;
import static ch.threema.app.voip.services.VoipCallService.EXTRA_CALL_ID;
import static ch.threema.app.voip.services.VoipCallService.EXTRA_CONTACT_IDENTITY;
import static ch.threema.app.voip.services.VoipCallService.EXTRA_IS_INITIATOR;

public class NotificationServiceImpl implements NotificationService {
	private static final Logger logger = LoggingUtil.getThreemaLogger("NotificationServiceImpl");
	private static final long NOTIFY_AGAIN_TIMEOUT = 30 * DateUtils.SECOND_IN_MILLIS;

	private final Context context;
	private final LockAppService lockAppService;
	private final DeadlineListService hiddenChatsListService;
	private final PreferenceService preferenceService;
	private final RingtoneService ringtoneService;
	private ContactService contactService = null;
	private GroupService groupService = null;
	private static final int MAX_TICKER_TEXT_LENGTH = 256;
	public static final int APP_RESTART_NOTIFICATION_ID = 481773;
	private static final int GC_PENDING_INTENT_BASE = 30000;

	private static final String GROUP_KEY_MESSAGES="threema_messages_key";
	private static final String PIN_LOCKED_NOTIFICATION_ID = "(transition to locked state)";
	private AsyncQueryHandler queryHandler;

	private final NotificationManagerCompat notificationManagerCompat;
	private final NotificationManager notificationManager;
	private final int pendingIntentFlags;

	private final LinkedList<ConversationNotification> conversationNotifications = new LinkedList<>();
	private MessageReceiver visibleConversationReceiver;

	public static class NotificationSchemaImpl implements NotificationSchema {
		private boolean vibrate = false;
		private int ringerMode = 0;
		private Uri soundUri = null;
		private int color = 0;

		public NotificationSchemaImpl(Context context) {
			AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
			this.setRingerMode(audioManager.getRingerMode());
		}

		@Override
		public boolean vibrate() {
			return this.vibrate;
		}

		@Override
		public int getRingerMode() {
			return this.ringerMode;
		}

		@Override
		public Uri getSoundUri() {
			return this.soundUri;
		}

		@Override
		public int getColor() {
			return this.color;
		}

		public NotificationSchemaImpl setColor(int color) {
			this.color = color;
			return this;
		}

		public NotificationSchemaImpl setSoundUri(Uri soundUri) {
			this.soundUri = soundUri;
			return this;
		}

		public NotificationSchemaImpl setRingerMode(int ringerMode) {
			this.ringerMode = ringerMode;
			return this;
		}

		public NotificationSchemaImpl setVibrate(boolean vibrate) {
			this.vibrate = vibrate;
			return this;
		}
	}

	public NotificationServiceImpl(Context context,
	                               LockAppService lockAppService,
	                               DeadlineListService hiddenChatsListService,
	                               PreferenceService preferenceService,
	                               RingtoneService ringtoneService) {
		this.context = context;
		this.lockAppService = lockAppService;
		this.hiddenChatsListService = hiddenChatsListService;
		this.preferenceService = preferenceService;
		this.ringtoneService = ringtoneService;
		this.notificationManagerCompat = NotificationManagerCompat.from(context);
		this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		// poor design by Google, as usual...
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			this.pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT | 0x02000000; // FLAG_MUTABLE
		} else {
			this.pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
		}

		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager != null) {
			try {
				this.contactService = serviceManager.getContactService();
				this.groupService = serviceManager.getGroupService();
			} catch (Exception e) {
				logger.error("Exception", e);
				return;
			}
		}

		/* create notification channels */
		createNotificationChannels();
	}

	@TargetApi(Build.VERSION_CODES.O)
	@Override
	public void deleteNotificationChannels() {
		if (!ConfigUtils.supportsNotificationChannels()) {
			return;
		}

		notificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_PASSPHRASE);
		notificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_WEBCLIENT);
		notificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_IN_CALL);
		notificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_ALERT);
		notificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_NOTICE);
		notificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_BACKUP_RESTORE_IN_PROGRESS);
		if (ConfigUtils.isWorkBuild()) {
			notificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_WORK_SYNC);
		}
		notificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_GROUP_CALL);
		notificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_IDENTITY_SYNC);
		notificationManager.deleteNotificationChannelGroup(NOTIFICATION_CHANNELGROUP_CHAT);
		notificationManager.deleteNotificationChannelGroup(NOTIFICATION_CHANNELGROUP_CHAT_UPDATE);
		notificationManager.deleteNotificationChannelGroup(NOTIFICATION_CHANNELGROUP_VOIP);
	}

	@TargetApi(Build.VERSION_CODES.O)
	@Override
	public void createNotificationChannels() {
		if (!ConfigUtils.supportsNotificationChannels()) {
			return;
		}

		NotificationChannel notificationChannel;

		// passphrase notification
		notificationChannel = new NotificationChannel(
				NOTIFICATION_CHANNEL_PASSPHRASE,
				context.getString(R.string.passphrase_service_name),
				NotificationManager.IMPORTANCE_LOW);
		notificationChannel.setDescription(context.getString(R.string.passphrase_service_description));
		notificationChannel.enableLights(false);
		notificationChannel.enableVibration(false);
		notificationChannel.setShowBadge(false);
		notificationChannel.setSound(null, null);
		notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
		notificationManager.createNotificationChannel(notificationChannel);

		// webclient notifications
		notificationChannel = new NotificationChannel(
				NOTIFICATION_CHANNEL_WEBCLIENT,
				context.getString(R.string.webclient),
				NotificationManager.IMPORTANCE_LOW);
		notificationChannel.setDescription(context.getString(R.string.webclient_service_description));
		notificationChannel.enableLights(false);
		notificationChannel.enableVibration(false);
		notificationChannel.setShowBadge(false);
		notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
		notificationChannel.setSound(null, null);
		notificationManager.createNotificationChannel(notificationChannel);

		// in call notifications (also used for group calls)
		notificationChannel = new NotificationChannel(
				NOTIFICATION_CHANNEL_IN_CALL,
				context.getString(R.string.call_ongoing),
				NotificationManager.IMPORTANCE_LOW);
		notificationChannel.enableLights(false);
		notificationChannel.enableVibration(false);
		notificationChannel.setShowBadge(false);
		notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
		notificationChannel.setSound(null, null);
		notificationManager.createNotificationChannel(notificationChannel);

		// alert notification
		notificationChannel = new NotificationChannel(
				NOTIFICATION_CHANNEL_ALERT,
				context.getString(R.string.notification_channel_alerts),
				NotificationManager.IMPORTANCE_HIGH);
		notificationChannel.enableLights(true);
		notificationChannel.enableVibration(true);
		notificationChannel.setShowBadge(false);
		notificationChannel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
				SoundUtil.getAudioAttributesForUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT));
		notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
		notificationManager.createNotificationChannel(notificationChannel);

		// notice notification
		notificationChannel = new NotificationChannel(
				NOTIFICATION_CHANNEL_NOTICE,
				context.getString(R.string.notification_channel_notices),
				NotificationManager.IMPORTANCE_LOW);
		notificationChannel.enableLights(false);
		notificationChannel.enableVibration(false);
		notificationChannel.setShowBadge(false);
		notificationChannel.setSound(null, null);
		notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
		notificationManager.createNotificationChannel(notificationChannel);

		// backup notification
		notificationChannel = new NotificationChannel(
			NOTIFICATION_CHANNEL_BACKUP_RESTORE_IN_PROGRESS,
				context.getString(R.string.backup_or_restore_progress),
				NotificationManager.IMPORTANCE_LOW);
		notificationChannel.enableLights(false);
		notificationChannel.enableVibration(false);
		notificationChannel.setShowBadge(false);
		notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
		notificationChannel.setSound(null, null);
		notificationManager.createNotificationChannel(notificationChannel);

		// work sync notification
		if (ConfigUtils.isWorkBuild()) {
			notificationChannel = new NotificationChannel(
				NOTIFICATION_CHANNEL_WORK_SYNC,
				context.getString(R.string.work_data_sync),
				NotificationManager.IMPORTANCE_LOW);
			notificationChannel.setDescription(context.getString(R.string.work_data_sync_desc));
			notificationChannel.enableLights(false);
			notificationChannel.enableVibration(false);
			notificationChannel.setShowBadge(false);
			notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
			notificationChannel.setSound(null, null);
			notificationManager.createNotificationChannel(notificationChannel);
		}

		// identity sync notification
		notificationChannel = new NotificationChannel(
				NOTIFICATION_CHANNEL_IDENTITY_SYNC,
				context.getString(R.string.work_data_sync),
				NotificationManager.IMPORTANCE_LOW);
		notificationChannel.setDescription(context.getString(R.string.contact_update));
		notificationChannel.enableLights(false);
		notificationChannel.enableVibration(false);
		notificationChannel.setShowBadge(false);
		notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
		notificationChannel.setSound(null, null);
		notificationManager.createNotificationChannel(notificationChannel);

		// new synced contact notification
		notificationChannel = new NotificationChannel(
				NOTIFICATION_CHANNEL_NEW_SYNCED_CONTACTS,
				context.getString(R.string.notification_channel_new_contact),
				NotificationManager.IMPORTANCE_HIGH);
		notificationChannel.setDescription(context.getString(R.string.notification_channel_new_contact_desc));
		notificationChannel.enableLights(true);
		notificationChannel.enableVibration(true);
		notificationChannel.setShowBadge(false);
		notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
		notificationChannel.setSound(null,null);
		notificationManager.createNotificationChannel(notificationChannel);

		// TODO(ANDR-2065) temporary - remove these two lines after beta
		notificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_GROUP_CALL_OLD);
		notificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_GROUP_CALL);

		// group join response notification channel
		if (ConfigUtils.supportsGroupLinks()) {
			notificationChannel = new NotificationChannel(
			NOTIFICATION_CHANNEL_GROUP_JOIN_RESPONSE,
			context.getString(R.string.group_response),
			NotificationManager.IMPORTANCE_DEFAULT);
			notificationChannel.setDescription(context.getString(R.string.notification_channel_group_join_response));
			notificationChannel.enableLights(true);
			notificationChannel.enableVibration(true);
			notificationChannel.setShowBadge(false);
			notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
			notificationChannel.setSound(null,null);
			notificationManager.createNotificationChannel(notificationChannel);

			// group join request notification channel
			notificationChannel = new NotificationChannel(
				NOTIFICATION_CHANNEL_GROUP_JOIN_REQUEST,
				context.getString(R.string.group_join_request),
				NotificationManager.IMPORTANCE_DEFAULT);
			notificationChannel.setDescription(context.getString(R.string.notification_channel_group_join_request));
			notificationChannel.enableLights(true);
			notificationChannel.enableVibration(true);
			notificationChannel.setShowBadge(false);
			notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
			notificationChannel.setSound(null,null);
			notificationManager.createNotificationChannel(notificationChannel);
		}
	}

	@Override
	public void setVisibleReceiver(MessageReceiver receiver) {
		if(receiver != null) {
			//cancel
			this.cancel(receiver);
		}
		this.visibleConversationReceiver = receiver;
	}

	@Override
	public void addGroupCallNotification(@NonNull GroupModel group, @NonNull ContactModel contactModel) {
		// Treat the visibility of a group call notification the same as a group message that contains a mention.
		MessageReceiver<?> messageReceiver = groupService.createReceiver(group);
		DNDUtil dndUtil = DNDUtil.getInstance();
		if (dndUtil.isMutedChat(messageReceiver) || dndUtil.isMutedWork()) {
			return;
		}

		NotificationCompat.Action joinAction = new NotificationCompat.Action(
			R.drawable.ic_group_call,
			context.getString(R.string.voip_gc_join_call),
			getGroupCallJoinPendingIntent(group.getId(), pendingIntentFlags)
		);

		Intent notificationIntent = new Intent(context, ComposeMessageActivity.class);
		notificationIntent.putExtra(ThreemaApplication.INTENT_DATA_GROUP, group.getId());
		notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
		PendingIntent openPendingIntent = createPendingIntentWithTaskStack(notificationIntent);

		String contentText = context.getString(R.string.voip_gc_notification_call_started, NameUtil.getShortName(contactModel), group.getName());
		NotificationSchema notificationSchema = new NotificationSchemaImpl(context)
			.setSoundUri(preferenceService.getGroupCallRingtone())
			.setVibrate(preferenceService.isGroupCallVibrate());

		// public version of the notification
		NotificationCompat.Builder publicBuilder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_GROUP_CALL)
			.setContentTitle(context.getString(R.string.group_call))
			.setContentText(context.getString(R.string.voip_gc_notification_new_call_public))
			.setSmallIcon(R.drawable.ic_group_call)
			.setColor(context.getResources().getColor(R.color.accent_light));

		// private version of the notification
		NotificationCompat.Builder builder = new NotificationBuilderWrapper(context, NOTIFICATION_CHANNEL_GROUP_CALL, notificationSchema, publicBuilder)
			.setStyle(new NotificationCompat.BigTextStyle().bigText(contentText))
			.setContentTitle(context.getString(R.string.group_call))
			.setContentText(contentText)
			.setContentIntent(openPendingIntent)
			.setSmallIcon(R.drawable.ic_group_call)
			.setLargeIcon(groupService.getAvatar(group, false))
			.setLocalOnly(true)
			.setCategory(NotificationCompat.CATEGORY_SOCIAL)
			.setPriority(NotificationCompat.PRIORITY_HIGH)
			.setColor(ResourcesCompat.getColor(context.getResources(), R.color.accent_light, context.getTheme()))
			.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
			.setPublicVersion(publicBuilder.build())
			.setSound(preferenceService.getGroupCallRingtone(), AudioManager.STREAM_VOICE_CALL)
			.addAction(joinAction);

		if (preferenceService.isGroupCallVibrate()) {
			builder.setVibrate(VIBRATE_PATTERN_GROUP_CALL);
		}

		String tag = "" + group.getId();
		try {
			notificationManagerCompat.notify(tag, ThreemaApplication.INCOMING_GROUP_CALL_NOTIFICATION_ID, builder.build());
		} catch (Exception e) {
			logger.error("Exception when notifying", e);
		}
	}

	@Override
	public void cancelGroupCallNotification(int groupId) {
		PendingIntent joinIntent = getGroupCallJoinPendingIntent(groupId, PendingIntent.FLAG_NO_CREATE | PENDING_INTENT_FLAG_IMMUTABLE);
		if (joinIntent != null) {
			joinIntent.cancel();
		}
		notificationManagerCompat.cancel("" + groupId, ThreemaApplication.INCOMING_GROUP_CALL_NOTIFICATION_ID);
	}

	private PendingIntent getGroupCallJoinPendingIntent(int groupId, int flags) {
		// To make sure a new PendingIntent only for this group is created, use the group id as request code.
		return PendingIntent.getActivity(
			context,
			GC_PENDING_INTENT_BASE + groupId,
			GroupCallActivity.getStartOrJoinCallIntent(context, groupId),
			flags
		);
	}

	@SuppressLint({"StaticFieldLeak"})
	@Override
	public void addConversationNotification(final ConversationNotification conversationNotification, boolean updateExisting) {
		logger.debug("addConversationNotifications");

		if (ConfigUtils.hasInvalidCredentials()) {
			logger.debug("Credentials are not (or no longer) valid. Suppressing notification.");
			return;
		}

		synchronized (this.conversationNotifications) {
			//check if current receiver is the receiver of the group
			if(this.visibleConversationReceiver != null &&
					conversationNotification.getGroup().getMessageReceiver().isEqual(this.visibleConversationReceiver)) {
				//ignore notification
				logger.info("No notification - chat visible");
				return;
			}

			String uniqueId = null;
			//check if notification not exist
			if(Functional.select(this.conversationNotifications, conversationNotification1 -> TestUtil.compare(conversationNotification1.getUid(), conversationNotification.getUid())) == null) {
				uniqueId = conversationNotification.getGroup().getMessageReceiver().getUniqueIdString();
				if (!DNDUtil.getInstance().isMuted(conversationNotification.getGroup().getMessageReceiver(), conversationNotification.getRawMessage())) {
					this.conversationNotifications.addFirst(conversationNotification);
				}
			} else if (updateExisting) {
				uniqueId = conversationNotification.getGroup().getMessageReceiver().getUniqueIdString();
			}

			Map<String, ConversationNotificationGroup> uniqueNotificationGroups = new HashMap<>();

			//to refactor on merge update and add
			final ConversationNotificationGroup newestGroup = conversationNotification.getGroup();

			int numberOfNotificationsForCurrentChat = 0;
			for(ConversationNotification notification: this.conversationNotifications) {
				ConversationNotificationGroup group = notification.getGroup();
				uniqueNotificationGroups.put(group.getGroupUid(), group);
				if (notification.getGroup().equals(newestGroup)) {
					numberOfNotificationsForCurrentChat++;
				}
			}

			if(!TestUtil.required(conversationNotification, newestGroup)) {
				logger.info("No notification - missing data");
				return;
			}

			if (updateExisting) {
				if (!ConfigUtils.canDoGroupedNotifications() || numberOfNotificationsForCurrentChat > 1) {
					return;
				}

				if (!this.preferenceService.isShowMessagePreview() || hiddenChatsListService.has(uniqueId)) {
					return;
				}

				if (this.lockAppService.isLocked()) {
					return;
				}
			}

			final String latestFullName = newestGroup.getName();
			int unreadMessagesCount = this.conversationNotifications.size();
			int unreadConversationsCount = uniqueNotificationGroups.size();
			NotificationSchema notificationSchema = this.createNotificationSchema(newestGroup, conversationNotification.getRawMessage());
			Bitmap latestThumbnail = null;

			if (notificationSchema == null) {
				logger.warn("No notification - no notification schema");
				return;
			}

			if(this.lockAppService.isLocked()) {
				this.showPinLockedNewMessageNotification(notificationSchema, conversationNotification.getUid(), false);
				return;
			}

			// make sure pin locked notification is canceled
			cancelPinLockedNewMessagesNotification();

			CharSequence tickerText;
			CharSequence singleMessageText;
			String summaryText = unreadConversationsCount > 1 ?
				ConfigUtils.getSafeQuantityString(context, R.plurals.new_messages_in_chats, unreadMessagesCount, unreadMessagesCount, unreadConversationsCount) :
				ConfigUtils.getSafeQuantityString(context, R.plurals.new_messages, unreadMessagesCount, unreadMessagesCount);
			String contentTitle;
			Intent notificationIntent;
			Bitmap summaryAvatar;
			NotificationCompat.InboxStyle inboxStyle = null;

			/* set avatar, intent and contentTitle */
			if (unreadConversationsCount > 1 && !ConfigUtils.canDoGroupedNotifications()) {
				/* notification is for more than one chat */
				summaryAvatar = getConversationNotificationAvatar();
				notificationIntent = new Intent(context, HomeActivity.class);
				contentTitle = context.getString(R.string.app_name);
			}
			else {
				/* notification is for single chat */
				summaryAvatar = newestGroup.getAvatar();
				notificationIntent = new Intent(context, ComposeMessageActivity.class);
				newestGroup.getMessageReceiver().prepareIntent(notificationIntent);
				contentTitle = latestFullName;
			}

			if (hiddenChatsListService.has(uniqueId)) {
				tickerText = summaryText;
				singleMessageText = summaryText;
			} else {
				if (this.preferenceService.isShowMessagePreview()) {
					tickerText = latestFullName + ": " + TextUtil.trim(conversationNotification.getMessage(), MAX_TICKER_TEXT_LENGTH, "...");
					inboxStyle = new NotificationCompat.InboxStyle();

					getInboxStyle(inboxStyle, unreadConversationsCount);

					inboxStyle.setBigContentTitle(contentTitle);
					inboxStyle.setSummaryText(summaryText);

					latestThumbnail = conversationNotification.getThumbnail();
					singleMessageText = conversationNotification.getMessage();
				} else {
					tickerText = latestFullName + ": " + summaryText;
					singleMessageText = summaryText;
				}
			}

			// Create PendingIntent for notification tab
			notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
			PendingIntent openPendingIntent = createPendingIntentWithTaskStack(notificationIntent);

			/* ************ ANDROID AUTO ************* */

			int conversationId = newestGroup.getNotificationId() * 10;

			Intent replyIntent = new Intent(context, NotificationActionService.class);
			replyIntent.setAction(NotificationActionService.ACTION_REPLY);
			IntentDataUtil.addMessageReceiverToIntent(replyIntent, newestGroup.getMessageReceiver());
			PendingIntent replyPendingIntent = PendingIntent.getService(context, conversationId, replyIntent, pendingIntentFlags);

			Intent markReadIntent = new Intent(context, NotificationActionService.class);
			markReadIntent.setAction(NotificationActionService.ACTION_MARK_AS_READ);
			IntentDataUtil.addMessageReceiverToIntent(markReadIntent, newestGroup.getMessageReceiver());
			PendingIntent markReadPendingIntent = PendingIntent.getService(context, conversationId + 1, markReadIntent, pendingIntentFlags);

			Intent ackIntent = new Intent(context, NotificationActionService.class);
			ackIntent.setAction(NotificationActionService.ACTION_ACK);
			IntentDataUtil.addMessageReceiverToIntent(ackIntent, newestGroup.getMessageReceiver());
			ackIntent.putExtra(ThreemaApplication.INTENT_DATA_MESSAGE_ID, conversationNotification.getId());
			PendingIntent ackPendingIntent = PendingIntent.getService(context, conversationId + 2, ackIntent, pendingIntentFlags);

			Intent decIntent = new Intent(context, NotificationActionService.class);
			decIntent.setAction(NotificationActionService.ACTION_DEC);
			IntentDataUtil.addMessageReceiverToIntent(decIntent, newestGroup.getMessageReceiver());
			decIntent.putExtra(ThreemaApplication.INTENT_DATA_MESSAGE_ID, conversationNotification.getId());
			PendingIntent decPendingIntent = PendingIntent.getService(context, conversationId + 3, decIntent, pendingIntentFlags);

			long timestamp = System.currentTimeMillis();
			boolean onlyAlertOnce = (timestamp - newestGroup.getLastNotificationDate()) < NOTIFY_AGAIN_TIMEOUT;
			newestGroup.setLastNotificationDate(timestamp);

			final NotificationCompat.Builder builder;

			if (ConfigUtils.canDoGroupedNotifications()) {
				summaryText = ConfigUtils.getSafeQuantityString(
					context,
					R.plurals.new_messages,
					numberOfNotificationsForCurrentChat,
					numberOfNotificationsForCurrentChat
				);

				if (!this.preferenceService.isShowMessagePreview() || hiddenChatsListService.has(uniqueId)) {
					singleMessageText = summaryText;
					tickerText = summaryText;
				}

				// public version of the notification
				NotificationCompat.Builder publicBuilder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_CHAT)
						.setContentTitle(summaryText)
						.setContentText(context.getString(R.string.notification_hidden_text))
						.setSmallIcon(R.drawable.ic_notification_small)
						.setColor(context.getResources().getColor(R.color.accent_light))
						.setOnlyAlertOnce(onlyAlertOnce);

				// private version
				builder = new NotificationBuilderWrapper(context, NOTIFICATION_CHANNEL_CHAT, notificationSchema, publicBuilder)
								.setContentTitle(contentTitle)
								.setContentText(singleMessageText)
								.setTicker(tickerText)
								.setSmallIcon(R.drawable.ic_notification_small)
								.setLargeIcon(newestGroup.getAvatar())
								.setColor(context.getResources().getColor(R.color.accent_light))
								.setGroup(newestGroup.getGroupUid())
								.setGroupSummary(false)
								.setOnlyAlertOnce(onlyAlertOnce)
								.setPriority(this.preferenceService.getNotificationPriority())
								.setCategory(NotificationCompat.CATEGORY_MESSAGE)
								.setVisibility(NotificationCompat.VISIBILITY_PRIVATE);

				// Add identity to notification for system DND priority override
				if (newestGroup.getLookupUri() != null) {
					builder.addPerson(newestGroup.getLookupUri());
				}

				if (this.preferenceService.isShowMessagePreview() && !hiddenChatsListService.has(uniqueId)) {
					builder.setStyle(getMessagingStyle(newestGroup, getConversationNotificationsForGroup(newestGroup)));
					if (uniqueId != null) {
						builder.setShortcutId(uniqueId);
						builder.setLocusId(new LocusIdCompat(uniqueId));
					}

					latestThumbnail = conversationNotification.getThumbnail();
					if (latestThumbnail != null && !latestThumbnail.isRecycled() && numberOfNotificationsForCurrentChat == 1) {
						// add image preview
						builder.setStyle(new NotificationCompat.BigPictureStyle()
								.bigPicture(latestThumbnail)
								.setSummaryText(conversationNotification.getMessage()));
					}
					addConversationNotificationActions(builder, replyPendingIntent, ackPendingIntent, markReadPendingIntent, conversationNotification, numberOfNotificationsForCurrentChat, unreadConversationsCount, uniqueId, newestGroup);
					addWearableExtender(builder, newestGroup, ackPendingIntent, decPendingIntent, replyPendingIntent, markReadPendingIntent, timestamp, latestThumbnail, numberOfNotificationsForCurrentChat, singleMessageText != null ? singleMessageText.toString() : "", uniqueId);
				}

				builder.setContentIntent(openPendingIntent);

				if (this.conversationNotifications.size() == 1 && updateExisting) {
					// we need to delay the updated notification to allow the sound of the first notification to play properly
					new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
						@TargetApi(Build.VERSION_CODES.M)
						@Override
						public void run() {

							try {
								NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
								if (notificationManager != null) {
									StatusBarNotification[] notifications = notificationManager.getActiveNotifications();
									for (StatusBarNotification notification : notifications) {
										if (notification.getId() == newestGroup.getNotificationId()) {
											NotificationServiceImpl.this.notify(newestGroup.getNotificationId(), builder, null, NOTIFICATION_CHANNEL_CHAT);
											break;
										}
									}
								}
							} catch (Exception e) {
								// catch IllegalStateException
							}
						}
					}, 4000);
				} else {
					this.notify(newestGroup.getNotificationId(), builder, notificationSchema, NOTIFICATION_CHANNEL_CHAT);
				}
			} else {
				createSingleNotification(newestGroup,
							conversationNotification,
							openPendingIntent,
							ackPendingIntent,
							decPendingIntent,
							replyPendingIntent,
							markReadPendingIntent,
							timestamp,
							latestThumbnail,
							numberOfNotificationsForCurrentChat,
							uniqueId);

				// summary notification
				builder = new NotificationBuilderWrapper(context, NOTIFICATION_CHANNEL_CHAT, notificationSchema)
								.setContentTitle(contentTitle)
								.setContentText(unreadConversationsCount > 1 ? summaryText : singleMessageText)
								.setTicker(tickerText)
								.setLargeIcon(summaryAvatar)
								.setColor(context.getResources().getColor(R.color.accent_light))
								.setNumber(unreadMessagesCount)
								.setGroup(GROUP_KEY_MESSAGES)
								// https://code.google.com/p/android/issues/detail?id=219876
								.setGroupSummary(true)
								.setWhen(timestamp)
								.setPriority(this.preferenceService.getNotificationPriority())
								.setCategory(NotificationCompat.CATEGORY_MESSAGE)
								.setOnlyAlertOnce(onlyAlertOnce);

				int smallIcon = getSmallIconResource(unreadConversationsCount);
				if (smallIcon > 0) {
					builder.setSmallIcon(smallIcon);
				}

				if (this.preferenceService.isShowMessagePreview() && !hiddenChatsListService.has(uniqueId)) {
					addConversationNotificationPreviews(builder, latestThumbnail, singleMessageText, contentTitle, conversationNotification.getMessage(), unreadMessagesCount);
					addConversationNotificationActions(builder, replyPendingIntent, ackPendingIntent, markReadPendingIntent, conversationNotification, unreadMessagesCount, unreadConversationsCount, uniqueId, newestGroup);
				}

				if (unreadMessagesCount > 1 && inboxStyle != null) {
					builder.setStyle(inboxStyle);
				}

				builder.setContentIntent(openPendingIntent);
				notificationManagerCompat.notify(ThreemaApplication.NEW_MESSAGE_NOTIFICATION_ID, builder.build());
			}

			logger.info("Showing notification {} sound: {}",
				conversationNotification.getUid(),
				notificationSchema.getSoundUri() != null ? notificationSchema.getSoundUri().toString() : "null");

			showIconBadge(unreadMessagesCount);
		}
	}

	private int getRandomRequestCode() {
		return (int) System.nanoTime();
	}

	private void getInboxStyle(NotificationCompat.InboxStyle inboxStyle, int unreadConversationsCount) {
		// show 8 lines max
		for(int i = 0; i < this.conversationNotifications.size() && i < 8; i++ ) {
			CharSequence message = conversationNotifications.get(i).getMessage();
			if (unreadConversationsCount > 1 && !conversationNotifications.get(i).getGroup().getGroupUid().startsWith("g")) {
				// we need to add a name prefix manually if a contact notification is part of a grouped notifications
				CharSequence shortName = conversationNotifications.get(i).getGroup().getShortName();
				message = TextUtils.concat(shortName, ": ", message);
			}
			inboxStyle.addLine(message);
		}
	}

	private NotificationCompat.MessagingStyle getMessagingStyle(ConversationNotificationGroup group, ArrayList<ConversationNotification> notifications) {
		String chatName = group.getName();
		boolean isGroupChat = group.getMessageReceiver() instanceof GroupMessageReceiver;
		Person.Builder builder = new Person.Builder()
			.setName(context.getString(R.string.me_myself_and_i))
			.setKey(contactService.getUniqueIdString(contactService.getMe()));

		Bitmap avatar = contactService.getAvatar(contactService.getMe(), false);
		if (avatar != null) {
			IconCompat iconCompat = IconCompat.createWithBitmap(avatar);
			builder.setIcon(iconCompat);
		}
		Person me = builder.build();

		NotificationCompat.MessagingStyle messagingStyle = new NotificationCompat.MessagingStyle(me);

		messagingStyle.setConversationTitle(isGroupChat ? chatName : null);
		messagingStyle.setGroupConversation(isGroupChat);

		for(int i = notifications.size() < MAXIMUM_RETAINED_MESSAGES ? notifications.size() - 1 : MAXIMUM_RETAINED_MESSAGES -1 ;
		    i >= 0; i-- ) {

			CharSequence message = notifications.get(i).getMessage();
			Date date = notifications.get(i).getWhen();

			Person person = notifications.get(i).getSenderPerson();

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

			long created = 0;

			if (date != null) {
				created = date.getTime();
			}
			messagingStyle.addMessage(message, created, person);
		}
		return messagingStyle;
	}

	private ArrayList<ConversationNotification> getConversationNotificationsForGroup(ConversationNotificationGroup group) {
		ArrayList<ConversationNotification> notifications = new ArrayList<>();
		for (ConversationNotification notification : conversationNotifications) {
			if (notification.getGroup().getGroupUid().equals(group.getGroupUid())) {
				notifications.add(notification);
			}
		}

		return notifications;
	}

	private void addConversationNotificationPreviews(NotificationCompat.Builder builder, Bitmap latestThumbnail, CharSequence singleMessageText, String contentTitle, CharSequence message, int unreadMessagesCount) {
		if (latestThumbnail != null && !latestThumbnail.isRecycled()) {
			// add image preview
			builder.setStyle(new NotificationCompat.BigPictureStyle()
					.bigPicture(latestThumbnail)
					.setSummaryText(message));
		} else {
			// add big text preview
			if (unreadMessagesCount == 1) {
				builder.setStyle(new NotificationCompat.BigTextStyle()
						.bigText(singleMessageText)
						.setBigContentTitle(contentTitle));
			}
		}
	}

	private void addConversationNotificationActions(NotificationCompat.Builder builder, PendingIntent replyPendingIntent, PendingIntent ackPendingIntent, PendingIntent markReadPendingIntent, ConversationNotification conversationNotification, int unreadMessagesCount, int unreadGroupsCount, String uniqueId, ConversationNotificationGroup newestGroup) {
		// add action buttons
		boolean showMarkAsReadAction = false;

		if (preferenceService.isShowMessagePreview() && !hiddenChatsListService.has(uniqueId)) {
			if (ConfigUtils.canDoGroupedNotifications()) {
				RemoteInput remoteInput = new RemoteInput.Builder(ThreemaApplication.EXTRA_VOICE_REPLY)
						.setLabel(context.getString(R.string.compose_message_and_enter))
						.build();

				NotificationCompat.Action.Builder replyActionBuilder = new NotificationCompat.Action.Builder(
						R.drawable.ic_reply_black_18dp, context.getString(R.string.wearable_reply), replyPendingIntent)
						.addRemoteInput(remoteInput)
						.setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY);

				if (Build.VERSION.SDK_INT >= 29) {
					replyActionBuilder.setAllowGeneratedReplies(!preferenceService.getDisableSmartReplies());
				}

				NotificationCompat.Action replyAction = replyActionBuilder.build();

				builder.addAction(replyAction);
			}

			if (newestGroup.getMessageReceiver() instanceof GroupMessageReceiver) {
				if (unreadMessagesCount == 1) {
					builder.addAction(getThumbsUpAction(ackPendingIntent));
				}
				showMarkAsReadAction = true;
			} else if (newestGroup.getMessageReceiver() instanceof ContactMessageReceiver) {

				if (conversationNotification.getMessageType().equals(MessageType.VOIP_STATUS))  {
					// Create an intent for the call action
					Intent callActivityIntent = new Intent(context, CallActivity.class);
					callActivityIntent.putExtra(EXTRA_ACTIVITY_MODE, CallActivity.MODE_OUTGOING_CALL);
					callActivityIntent.putExtra(EXTRA_CONTACT_IDENTITY, ((ContactMessageReceiver) newestGroup.getMessageReceiver()).getContact().getIdentity());
					callActivityIntent.putExtra(EXTRA_IS_INITIATOR, true);
					callActivityIntent.putExtra(EXTRA_CALL_ID, -1L);

					PendingIntent callPendingIntent = PendingIntent.getActivity(
							context,
							getRandomRequestCode(), // http://stackoverflow.com/questions/19031861/pendingintent-not-opening-activity-in-android-4-3
							callActivityIntent,
							this.pendingIntentFlags);
					if (unreadGroupsCount == 1 || ConfigUtils.canDoGroupedNotifications()) {
						builder.addAction(
							new NotificationCompat.Action.Builder(R.drawable.ic_call_white_24dp, context.getString(R.string.voip_return_call), callPendingIntent)
								.setShowsUserInterface(true)
								.setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_CALL)
								.build());
					}
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

	private void addGroupLinkActions(NotificationCompat.Builder builder, PendingIntent acceptIntent, PendingIntent rejectIntent) {
		NotificationCompat.Action.Builder acceptActionBuilder = new NotificationCompat.Action.Builder(
			R.drawable.ic_check, context.getString(R.string.accept), acceptIntent)
			.setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
			.setShowsUserInterface(false);

		if (Build.VERSION.SDK_INT >= 29) {
			acceptActionBuilder.setAllowGeneratedReplies(!preferenceService.getDisableSmartReplies());
		}

		NotificationCompat.Action.Builder rejectActionBuilder = new NotificationCompat.Action.Builder(
			R.drawable.ic_close, context.getString(R.string.reject), rejectIntent)
			.setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
			.setShowsUserInterface(false);

		if (Build.VERSION.SDK_INT >= 29) {
			rejectActionBuilder.setAllowGeneratedReplies(!preferenceService.getDisableSmartReplies());
		}

		NotificationCompat.Action acceptAction = acceptActionBuilder.build();
		NotificationCompat.Action rejectAction = rejectActionBuilder.build();

		builder.addAction(acceptAction);
		builder.addAction(rejectAction);
	}

	private NotificationCompat.Action getMarkAsReadAction(PendingIntent markReadPendingIntent) {
		return new NotificationCompat.Action.Builder(R.drawable.ic_mark_read_bitmap, context.getString(R.string.mark_read_short), markReadPendingIntent)
			.setShowsUserInterface(false)
			.setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
			.build();
	}

	private NotificationCompat.Action getThumbsUpAction(PendingIntent ackPendingIntent) {
		return new NotificationCompat.Action.Builder(R.drawable.ic_thumb_up_white_24dp, context.getString(R.string.acknowledge), ackPendingIntent)
			.setShowsUserInterface(false)
			.setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_THUMBS_UP)
			.build();
	}

	private void createSingleNotification(ConversationNotificationGroup newestGroup,
										 ConversationNotification newestNotification,
										 PendingIntent openPendingIntent,
										 PendingIntent ackPendingIntent,
										 PendingIntent decPendingIntent,
										 PendingIntent replyPendingIntent,
										 PendingIntent markReadPendingIntent,
										 long timestamp,
										 Bitmap latestThumbnail,
										 int numberOfUnreadMessagesForThisChat,
										 String uniqueId) {

		CharSequence messageText;
		messageText = newestNotification.getMessage();
		int conversationNotificationSize = this.conversationNotifications.size();
		if (this.preferenceService.isShowMessagePreview()) {
			messageText = newestNotification.getMessage();
		} else {
			ConfigUtils.getSafeQuantityString(context, R.plurals.new_messages, conversationNotificationSize, conversationNotificationSize);
		}

		// create a unified single notification for wearables and auto
		NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
				.setSmallIcon(R.drawable.ic_notification_small)
				.setLargeIcon(newestGroup.getAvatar())
				.setContentText(messageText)
				.setWhen(timestamp)
				.setContentTitle(newestGroup.getName())
				.setContentIntent(openPendingIntent)
				.setGroup(GROUP_KEY_MESSAGES)
				.setGroupSummary(false)
				.setCategory(NotificationCompat.CATEGORY_MESSAGE);

		addWearableExtender(builder, newestGroup, ackPendingIntent, decPendingIntent, replyPendingIntent, markReadPendingIntent, timestamp, latestThumbnail, numberOfUnreadMessagesForThisChat, messageText, uniqueId);
		notificationManagerCompat.notify(newestGroup.getNotificationId(), builder.build());
	}

	private void addWearableExtender(NotificationCompat.Builder builder,
	                                 ConversationNotificationGroup newestGroup,
	                                 PendingIntent ackPendingIntent,
	                                 PendingIntent decPendingIntent,
	                                 PendingIntent replyPendingIntent,
	                                 PendingIntent markReadPendingIntent,
	                                 long timestamp,
	                                 Bitmap latestThumbnail,
	                                 int numberOfUnreadMessagesForThisChat,
	                                 CharSequence messageText,
	                                 String uniqueId) {

		String replyLabel = String.format(context.getString(R.string.wearable_reply_label), newestGroup.getName());
		RemoteInput remoteInput = new RemoteInput.Builder(ThreemaApplication.EXTRA_VOICE_REPLY)
				.setLabel(replyLabel)
				.setChoices(context.getResources().getStringArray(R.array.wearable_reply_choices))
				.build();

		int numMessages = this.conversationNotifications.size();
		List<Notification> wearablePages = new ArrayList<>();

		// Create an unread conversation object to organize a group of messages
		// from a particular sender.
		NotificationCompat.CarExtender.UnreadConversation.Builder unreadConvBuilder =
				new NotificationCompat.CarExtender.UnreadConversation.Builder(newestGroup.getName())
						.setReadPendingIntent(markReadPendingIntent)
						.setReplyAction(replyPendingIntent, remoteInput)
						.setLatestTimestamp(timestamp);

		if (preferenceService.isShowMessagePreview() && !hiddenChatsListService.has(uniqueId)) {
			String wearableSummaryText = "";

			// Note: Add messages from oldest to newest to the UnreadConversation.Builder
			for (int i = numMessages - 1; i >= 0; i--) {
				if (conversationNotifications.get(i).getGroup() == newestGroup) {
					CharSequence message = conversationNotifications.get(i).getMessage();

					// auto
					unreadConvBuilder.addMessage(message.toString());

					// wearable
					if (wearableSummaryText.length() > 0) {
						wearableSummaryText += "\n\n";
					}
					wearableSummaryText += message;
				}
			}

			// add pic if image message
			if (latestThumbnail != null && !latestThumbnail.isRecycled()) {
				NotificationCompat.Builder wearableBuilder = new NotificationCompat.Builder(context)
								.setStyle(new NotificationCompat.BigPictureStyle().bigPicture(latestThumbnail))
								.extend(new NotificationCompat.WearableExtender().setHintShowBackgroundOnly(true));
				wearablePages.add(wearableBuilder.build());
			}

			if (wearableSummaryText.length() > 0) {
				NotificationCompat.BigTextStyle wearablePageStyle = new NotificationCompat.BigTextStyle();
				wearablePageStyle.setBigContentTitle(newestGroup.getName())
						.bigText(wearableSummaryText);
				NotificationCompat.Builder wearableBuilder = new NotificationCompat.Builder(context)
						.setStyle(wearablePageStyle);
				wearablePages.add(wearableBuilder.build());
			}
		} else {
			unreadConvBuilder.addMessage(messageText.toString());
		}

		NotificationCompat.Action.Builder replyActionBuilder =
				new NotificationCompat.Action.Builder(R.drawable.ic_wear_full_reply,
						context.getString(R.string.wearable_reply), replyPendingIntent)
						.addRemoteInput(remoteInput)
						.setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY);

		NotificationCompat.Action.WearableExtender replyActionExtender =
				new NotificationCompat.Action.WearableExtender()
						.setHintDisplayActionInline(true);

		NotificationCompat.WearableExtender wearableExtender = new NotificationCompat.WearableExtender()
				.addPages(wearablePages)
				.addAction(replyActionBuilder.extend(replyActionExtender).build());

		if (this.preferenceService.isShowMessagePreview() && !hiddenChatsListService.has(uniqueId)) {
			if (numberOfUnreadMessagesForThisChat == 1 && newestGroup.getMessageReceiver() instanceof ContactMessageReceiver && !hiddenChatsListService.has(uniqueId)) {
				NotificationCompat.Action ackAction =
						new NotificationCompat.Action.Builder(R.drawable.ic_wear_full_ack,
								context.getString(R.string.acknowledge), ackPendingIntent)
								.setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_THUMBS_UP)
								.build();
				wearableExtender.addAction(ackAction);

				NotificationCompat.Action decAction =
						new NotificationCompat.Action.Builder(R.drawable.ic_wear_full_decline,
								context.getString(R.string.decline), decPendingIntent)
								.setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_THUMBS_DOWN)
								.build();
				wearableExtender.addAction(decAction);
			}

			NotificationCompat.Action markReadAction =
					new NotificationCompat.Action.Builder(R.drawable.ic_mark_read,
							context.getString(R.string.mark_read), markReadPendingIntent)
							.setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
							.build();
			wearableExtender.addAction(markReadAction);
		}
		builder.extend(wearableExtender);
		builder.extend(new NotificationCompat.CarExtender()
				.setLargeIcon(newestGroup.getAvatar())
				.setUnreadConversation(unreadConvBuilder.build())
				.setColor(context.getResources().getColor(R.color.accent_light)));
	}

	@Override
	public void cancelConversationNotificationsOnLockApp(){
		// cancel cached notification ids if still available
		if (!conversationNotifications.isEmpty()) {
			cancelCachedConversationNotifications();
		}
		// get and cancel active conversations notifications trough notificationManager
		else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && cancelAllMessageCategoryNotifications()) {
			showDefaultPinLockedNewMessageNotification();
		}
		// hack to detect active conversation Notifications by checking for active pending Intent
		else if (isConversationNotificationVisible()) {
			cancel(ThreemaApplication.NEW_MESSAGE_NOTIFICATION_ID);
			showDefaultPinLockedNewMessageNotification();
		}
	}

	@Override
	public void cancelConversationNotification(final String... uids) {
		synchronized (this.conversationNotifications) {
			for(final String uid: uids) {
				ConversationNotification conversationNotification = Functional.select(this.conversationNotifications, new IPredicateNonNull<ConversationNotification>() {
					@Override
					public boolean apply(@NonNull ConversationNotification conversationNotification1) {
						return TestUtil.compare(conversationNotification1.getUid(), uid);
					}
				});

				if(conversationNotification != null) {
					conversationNotifications.remove(conversationNotification);
					cancelAndDestroyConversationNotification(conversationNotification);

					if (ConfigUtils.canDoGroupedNotifications()) {
						notificationManagerCompat.cancel(conversationNotification.getGroup().getNotificationId());
					}
				}
			}

			if (!ConfigUtils.canDoGroupedNotifications()) {
				this.updateConversationNotifications();
			} else {
				showIconBadge(this.conversationNotifications.size());
			}
			// no unread conversations left. make sure PIN locked notification is canceled as well
			if (this.conversationNotifications.size() == 0) {
				cancelPinLockedNewMessagesNotification();
			}
		}

		WidgetUtil.updateWidgets(context);
	}

	private void cancelAndDestroyConversationNotification(ConversationNotification conversationNotification) {
		if(conversationNotification != null) {
			//remove wearable
			cancel(conversationNotification.getGroup().getNotificationId());
			conversationNotification.destroy();
		}
	}

	@Override
	public void updateConversationNotifications() {
		int unreadMessagesCount = 0;

		if (!ConfigUtils.canDoGroupedNotifications()) {
			updateConversationNotificationsPreN();
		} else {

			synchronized (this.conversationNotifications) {
				cancelPinLockedNewMessagesNotification();

				//check if more than one group in the notification
				ConversationNotification newestNotification = null;
				HashSet<ConversationNotificationGroup> uniqueNotificationGroups = new HashSet<>();

				if (this.conversationNotifications.size() != 0) {
					for (ConversationNotification notification : this.conversationNotifications) {
						ConversationNotificationGroup group = notification.getGroup();
						newestNotification = notification;
						uniqueNotificationGroups.add(group);
					}

					if (newestNotification == null) {
						logger.info("Aborting notification update");
						return;
					}

					unreadMessagesCount = this.conversationNotifications.size();

					if (!this.lockAppService.isLocked()) {
						NotificationCompat.Builder builder;
						String summaryText;
						CharSequence singleMessageText;

						for (ConversationNotificationGroup group : uniqueNotificationGroups) {

							ArrayList<ConversationNotification> notifications = getConversationNotificationsForGroup(group);

							if (notifications.size() > 0) {
								ConversationNotification mostRecentNotification = notifications.get(notifications.size() - 1);
								String uniqueId = group.getMessageReceiver().getUniqueIdString();

								summaryText = ConfigUtils.getSafeQuantityString(context, R.plurals.new_messages, unreadMessagesCount, unreadMessagesCount);

								if (this.preferenceService.isShowMessagePreview() && !hiddenChatsListService.has(uniqueId)) {
									singleMessageText = mostRecentNotification.getMessage();
								} else {
									singleMessageText = summaryText;
								}

								NotificationCompat.Builder publicBuilder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_CHAT_UPDATE)
										.setContentTitle(summaryText)
										.setContentText(context.getString(R.string.notification_hidden_text))
										.setSmallIcon(R.drawable.ic_notification_small)
										.setColor(context.getResources().getColor(R.color.accent_light));

								builder = new NotificationBuilderWrapper(context, NOTIFICATION_CHANNEL_CHAT_UPDATE, null, publicBuilder)
												.setContentTitle(group.getName())
												.setContentText(singleMessageText)
												.setSmallIcon(R.drawable.ic_notification_small)
												.setLargeIcon(group.getAvatar())
												.setColor(context.getResources().getColor(R.color.accent_light))
												.setGroup(group.getGroupUid())
												.setGroupSummary(false)
												.setVisibility(NotificationCompat.VISIBILITY_PRIVATE);

								if (this.preferenceService.isShowMessagePreview() && !hiddenChatsListService.has(uniqueId) && notifications.size() > 1) {
									builder.setStyle(getMessagingStyle(group, notifications));
								}

								Intent notificationIntent = new Intent(context, ComposeMessageActivity.class);
								group.getMessageReceiver().prepareIntent(notificationIntent);
								notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
								PendingIntent pendingIntent = createPendingIntentWithTaskStack(notificationIntent);
								builder.setContentIntent(pendingIntent);

								try {
									notificationManagerCompat.notify(group.getNotificationId(), builder.build());
								} catch (RuntimeException e) {
									logger.error("Exception", e);
								}
							}
						}
						logger.info("Updating notification {}", newestNotification.getUid());
					}
				} else {
					this.cancelAllCachedConversationNotifications();
				}
				showIconBadge(unreadMessagesCount);
			}
		}

		// update widgets
		WidgetUtil.updateWidgets(context);
	}

	private void updateConversationNotificationsPreN() {
		int unreadMessagesCount = 0;

		synchronized (this.conversationNotifications) {
			cancelPinLockedNewMessagesNotification();

			//check if more than one group in the notification
			ConversationNotificationGroup newestGroup = null;
			ConversationNotification newestNotification = null;
			Map<String, ConversationNotificationGroup> uniqueNotificationGroups = new HashMap<>();
			if (this.conversationNotifications.size() != 0) {
				for (ConversationNotification notification : this.conversationNotifications) {
					ConversationNotificationGroup group = notification.getGroup();
					newestGroup = group;
					newestNotification = notification;
					uniqueNotificationGroups.put(group.getGroupUid(), group);
				}

				if (newestNotification == null) {
					logger.info("Aborting notification update");
					showIconBadge(0);
					return;
				}

				final String latestFullName = newestGroup.getName();
				unreadMessagesCount = this.conversationNotifications.size();
				int unreadConversationsCount = uniqueNotificationGroups.size();

				if (!this.lockAppService.isLocked()) {
					String summaryText = unreadMessagesCount > 1 && unreadConversationsCount > 1
						? ConfigUtils.getSafeQuantityString(context, R.plurals.new_messages_in_chats, unreadMessagesCount, unreadMessagesCount, unreadConversationsCount)
						: ConfigUtils.getSafeQuantityString(context, R.plurals.new_messages, unreadMessagesCount, unreadMessagesCount);
					CharSequence singleMessageText;
					String contentTitle;
					Intent notificationIntent;
					Bitmap avatar;
					NotificationCompat.InboxStyle inboxStyle = null;

					/* set avatar, intent and contentTitle */
					if (unreadConversationsCount > 1) {
						/* notification is for more than one chat */
						//HACK
						avatar = getConversationNotificationAvatar();
						notificationIntent = new Intent(context, HomeActivity.class);
						contentTitle = context.getString(R.string.app_name);
					} else {
						/* notification is for single chat */
						avatar = newestGroup.getAvatar();
						notificationIntent = new Intent(context, ComposeMessageActivity.class);
						newestGroup.getMessageReceiver().prepareIntent(notificationIntent);
						contentTitle = latestFullName;
					}

					/* fix for <4.1 - keeps android from re-using existing intent and stripping extras */
					notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

					if (this.preferenceService.isShowMessagePreview()) {
						inboxStyle = new NotificationCompat.InboxStyle();

						getInboxStyle(inboxStyle, unreadConversationsCount);

						inboxStyle.setBigContentTitle(contentTitle);
						inboxStyle.setSummaryText(summaryText);

						singleMessageText = newestNotification.getMessage();
					} else {
						singleMessageText = summaryText;
					}

					PendingIntent pendingIntent = createPendingIntentWithTaskStack(notificationIntent);

					// update summary notification
					NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
									.setContentTitle(contentTitle)
									.setContentText(unreadMessagesCount > 1 ? summaryText : singleMessageText)
									.setLargeIcon(avatar)
									.setColor(context.getResources().getColor(R.color.accent_light))
									.setNumber(unreadMessagesCount)
									.setGroup(GROUP_KEY_MESSAGES)
									.setOnlyAlertOnce(false);

					int smallIcon = getSmallIconResource(unreadConversationsCount);
					if (smallIcon > 0) {
						builder.setSmallIcon(smallIcon);
					}

					// https://code.google.com/p/android/issues/detail?id=219876
					builder.setGroupSummary(true);

					if (unreadMessagesCount > 1 && inboxStyle != null) {
						builder.setStyle(inboxStyle);
					}

					builder.setContentIntent(pendingIntent);
					NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
					notificationManager.notify(ThreemaApplication.NEW_MESSAGE_NOTIFICATION_ID, builder.build());

					logger.info("Updating notification {}", newestNotification.getUid());
				}
			} else {
				//cancel all
				this.cancel(ThreemaApplication.NEW_MESSAGE_NOTIFICATION_ID);
			}

			// update widgets
			showIconBadge(unreadMessagesCount);
		}
	}

	@Override
	public void cancelAllCachedConversationNotifications() {
		this.cancel(ThreemaApplication.NEW_MESSAGE_NOTIFICATION_ID);

		synchronized (this.conversationNotifications) {
			if (!conversationNotifications.isEmpty()) {
				for (ConversationNotification conversationNotification : conversationNotifications) {
					this.cancelAndDestroyConversationNotification(conversationNotification);
				}
				conversationNotifications.clear();
				showDefaultPinLockedNewMessageNotification();
			}
		}
	}

	private NotificationSchema createNotificationSchema(ConversationNotificationGroup notificationGroup, CharSequence rawMessage) {
		NotificationSchemaImpl notificationSchema = new NotificationSchemaImpl(this.context);
		MessageReceiver r = notificationGroup.getMessageReceiver();

		if(r instanceof  GroupMessageReceiver) {

			if (DNDUtil.getInstance().isMuted(r, rawMessage)) {
				return null;
			}

			notificationSchema
					.setSoundUri(this.ringtoneService.getGroupRingtone(r.getUniqueIdString()))
					.setColor(getColorValue(this.preferenceService.getGroupNotificationLight()))
					.setVibrate(this.preferenceService.isGroupVibrate());
		}
		else if(r instanceof ContactMessageReceiver) {

			if (DNDUtil.getInstance().isMuted(r, null)) {
				return null;
			}

			notificationSchema
					.setSoundUri(this.ringtoneService.getContactRingtone(r.getUniqueIdString()))
					.setColor(getColorValue(this.preferenceService.getNotificationLight()))
					.setVibrate(this.preferenceService.isVibrate());
		}
		return notificationSchema;
	}

	private Bitmap getConversationNotificationAvatar() {
		/* notification is for more than one chat */
		return BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_notification_multi_color);
	}

	private int getSmallIconResource(int unreadConversationsCount) {
		return unreadConversationsCount > 1 ? R.drawable.ic_notification_multi : R.drawable.ic_notification_small;
	}

	@Override
	public void cancel(ConversationModel conversationModel) {
		if(conversationModel != null) {
			this.cancel(conversationModel.getReceiver());
		}
	}

	@Override
	public void cancel(final MessageReceiver receiver) {
		if(receiver != null) {
			int id = receiver.getUniqueId();
			if (id != 0) {
				this.cancel(id);
			}

			//remove all cached notifications from the receiver
			synchronized (this.conversationNotifications) {
				for (Iterator<ConversationNotification> iterator = this.conversationNotifications.iterator(); iterator.hasNext(); ) {
					ConversationNotification conversationNotification = iterator.next();
					if (conversationNotification != null
							&& conversationNotification.getGroup() != null
							&& conversationNotification.getGroup().getMessageReceiver().isEqual(receiver)) {
						iterator.remove();
						//call destroy
						this.cancelAndDestroyConversationNotification(conversationNotification);
					}
				}
				showIconBadge(conversationNotifications.size());
			}
		}
		this.cancel(ThreemaApplication.NEW_MESSAGE_NOTIFICATION_ID);
	}

	@Override
	public void cancelCachedConversationNotifications() {
		/* called when pin lock becomes active */
		synchronized (this.conversationNotifications) {
			cancelAllCachedConversationNotifications();
			showIconBadge(this.conversationNotifications.size());
		}
	}

	@RequiresApi(api = Build.VERSION_CODES.M)
	@Override
	public boolean cancelAllMessageCategoryNotifications() {
		boolean cancelledIDs = false;
		try {
			if (notificationManager != null) {
				StatusBarNotification[] notifications = notificationManager.getActiveNotifications();
				if (notifications.length > 0) {
					for (StatusBarNotification notification : notifications) {
						if (notification.getNotification() != null && Notification.CATEGORY_MESSAGE.equals(notification.getNotification().category)) {
							notificationManager.cancel(notification.getId());
							cancelledIDs = true;
						}
					}
				}
			}
		} catch (Exception e) {
			logger.error("Could not cancel notifications of CATEGORY_MESSAGE ", e);
		}
		return cancelledIDs;
	}

	private NotificationSchema getDefaultNotificationSchema() {
		NotificationSchemaImpl notificationSchema = new NotificationSchemaImpl(this.context);
		notificationSchema
				.setVibrate(this.preferenceService.isVibrate())
				.setColor(this.getColorValue(preferenceService.getNotificationLight()))
				.setSoundUri(this.preferenceService.getNotificationSound());

		return notificationSchema;

	}

	@Override
	public boolean isConversationNotificationVisible() {
		Intent notificationIntent = new Intent(context, ComposeMessageActivity.class);
		PendingIntent test = PendingIntent.getActivity(context, ThreemaApplication.NEW_MESSAGE_NOTIFICATION_ID, notificationIntent, PendingIntent.FLAG_NO_CREATE | PENDING_INTENT_FLAG_IMMUTABLE);
		return test != null;
	}

	private void showDefaultPinLockedNewMessageNotification(){
		logger.debug("showDefaultPinLockedNewMessageNotification");
		this.showPinLockedNewMessageNotification(new NotificationService.NotificationSchema() {
			                                         @Override
			                                         public boolean vibrate() {
				                                         return false;
			                                         }

			                                         @Override
			                                         public int getRingerMode() {
				                                         return 0;
			                                         }

			                                         @Override
			                                         public Uri getSoundUri() {
				                                         return null;
			                                         }

			                                         @Override
			                                         public int getColor() {
				                                         return 0;
			                                         }
		                                         },
			PIN_LOCKED_NOTIFICATION_ID,
			true);
	}

	@Override
	public void showPinLockedNewMessageNotification(NotificationSchema notificationSchema, String uid, boolean quiet) {
		NotificationCompat.Builder builder =
			new NotificationBuilderWrapper(this.context, quiet ? NOTIFICATION_CHANNEL_CHAT_UPDATE : NOTIFICATION_CHANNEL_CHAT, notificationSchema)
					.setSmallIcon(R.drawable.ic_notification_small)
					.setContentTitle(this.context.getString(R.string.new_messages_locked))
					.setContentText(this.context.getString(R.string.new_messages_locked_description))
					.setTicker(this.context.getString(R.string.new_messages_locked))
					.setCategory(NotificationCompat.CATEGORY_MESSAGE)
					.setPriority(this.preferenceService.getNotificationPriority())
					.setOnlyAlertOnce(false)
					.setContentIntent(getPendingIntentForActivity(HomeActivity.class));

		this.notify(ThreemaApplication.NEW_MESSAGE_PIN_LOCKED_NOTIFICATION_ID, builder, null, quiet ? NOTIFICATION_CHANNEL_CHAT_UPDATE : NOTIFICATION_CHANNEL_CHAT);

		showIconBadge(0);

		// cancel this message as soon as the app is unlocked
		this.lockAppService.addOnLockAppStateChanged(new LockAppService.OnLockAppStateChanged() {
			@Override
			public boolean changed(boolean locked) {
				logger.debug("LockAppState changed. locked = " + locked);
				if (!locked) {
					cancelPinLockedNewMessagesNotification();
					return true;
				}
				return false;
			}
		});

		logger.info("Showing generic notification (pin locked) = {} quiet (unprotected > pin) = {} ", uid, quiet);
	}

	@Override
	public void showMasterKeyLockedNewMessageNotification() {
		this.showMasterKeyLockedNewMessageNotification(this.getDefaultNotificationSchema());
	}

	private void showMasterKeyLockedNewMessageNotification(NotificationSchema notificationSchema) {
		NotificationCompat.Builder builder =
			new NotificationBuilderWrapper(this.context, NOTIFICATION_CHANNEL_CHAT, notificationSchema)
					.setSmallIcon(R.drawable.ic_notification_small)
					.setContentTitle(this.context.getString(R.string.new_messages_locked))
					.setContentText(this.context.getString(R.string.new_messages_locked_description))
					.setTicker(this.context.getString(R.string.new_messages_locked))
					.setCategory(NotificationCompat.CATEGORY_MESSAGE)
					.setOnlyAlertOnce(false)
					.setContentIntent(getPendingIntentForActivity(HomeActivity.class));

		this.notify(ThreemaApplication.NEW_MESSAGE_LOCKED_NOTIFICATION_ID, builder, null, NOTIFICATION_CHANNEL_CHAT);

		logger.info("Showing generic notification (master key locked)");
	}

	@Override
	@TargetApi(Build.VERSION_CODES.N)
	public void showNetworkBlockedNotification(boolean noisy) {
		ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (connectivityManager != null) {
			if (connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) != null) {

				String message = String.format(this.context.getString(R.string.network_blocked_body), this.context.getString(R.string.app_name));

				NotificationCompat.Builder builder =
					new NotificationBuilderWrapper(this.context, noisy ? NOTIFICATION_CHANNEL_ALERT : NOTIFICATION_CHANNEL_NOTICE, noisy ? this.getDefaultNotificationSchema() : null)
						.setSmallIcon(R.drawable.ic_error_red_24dp)
						.setContentTitle(this.context.getString(R.string.network_blocked_title))
						.setContentText(message)
						.setStyle(new NotificationCompat.BigTextStyle().bigText(message))
						.setCategory(NotificationCompat.CATEGORY_ERROR)
						.setPriority(noisy ? preferenceService.getNotificationPriority() : NotificationCompat.PRIORITY_LOW)
						.setAutoCancel(true)
						.setTimeoutAfter(DateUtils.HOUR_IN_MILLIS)
						.setLocalOnly(true)
						.setOnlyAlertOnce(true);

				Intent notificationIntent = new Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS);
				notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				notificationIntent.setData(Uri.parse("package:" + BuildConfig.APPLICATION_ID));

				PackageManager packageManager = context.getPackageManager();
				if (notificationIntent.resolveActivity(packageManager) != null) {
					PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, this.pendingIntentFlags);
					createPendingIntentWithTaskStack(notificationIntent);
					builder.setContentIntent(pendingIntent);

					this.notify(ThreemaApplication.NETWORK_BLOCKED_NOTIFICATION_ID, builder, null, noisy ? NOTIFICATION_CHANNEL_ALERT : NOTIFICATION_CHANNEL_NOTICE);
					logger.info("Showing network blocked notification");
					return;
				}
			}
		}
		logger.warn("Failed showing network blocked notification");
	}

	@Override
	public void cancelNetworkBlockedNotification() {
		this.cancel(ThreemaApplication.NETWORK_BLOCKED_NOTIFICATION_ID);
		logger.info("Cancel network blocked notification");
	}

	private void cancelPinLockedNewMessagesNotification() {
		logger.debug("cancel Pin Locked New Messages");
		this.cancel(ThreemaApplication.NEW_MESSAGE_PIN_LOCKED_NOTIFICATION_ID);
	}

	@Override
	public void showServerMessage(ServerMessageModel m) {
		Intent intent = new Intent(context, ServerMessageActivity.class);
		IntentDataUtil.append(m, intent);
		PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PENDING_INTENT_FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

		NotificationCompat.Builder builder =
			new NotificationBuilderWrapper(context, NOTIFICATION_CHANNEL_NOTICE, null)
					.setSmallIcon(R.drawable.ic_error_red_24dp)
					.setTicker(this.context.getString(R.string.server_message_title))
					.setContentTitle(this.context.getString(R.string.server_message_title))
					.setContentText(this.context.getString(R.string.tap_here_for_more))
					.setContentIntent(pendingIntent)
					.setLocalOnly(true)
					.setPriority(NotificationCompat.PRIORITY_MAX)
					.setAutoCancel(true);

		this.notify(ThreemaApplication.SERVER_MESSAGE_NOTIFICATION_ID, builder, null, NOTIFICATION_CHANNEL_NOTICE);
	}

	@Override
	public void cancelServerMessage() {
		this.cancel(ThreemaApplication.SERVER_MESSAGE_NOTIFICATION_ID);
	}

	@Override
	public void showNotEnoughDiskSpace(long availableSpace, long requiredSpace) {
		logger.warn("Not enough diskspace. Available: {} required: {}", availableSpace, requiredSpace);

		String text = this.context.getString(R.string.not_enough_disk_space_text,
				Formatter.formatFileSize(this.context, requiredSpace));

		NotificationCompat.Builder builder =
			new NotificationBuilderWrapper(context, NOTIFICATION_CHANNEL_ALERT, null)
					.setSmallIcon(R.drawable.ic_notification_small)
					.setTicker(this.context.getString(R.string.not_enough_disk_space_title))
					.setPriority(NotificationCompat.PRIORITY_MAX)
					.setContentTitle(this.context.getString(R.string.not_enough_disk_space_title))
					.setLocalOnly(true)
					.setContentText(text)
					.setStyle(new NotificationCompat.BigTextStyle().bigText(text));

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
			Intent cleanupIntent = new Intent(StorageManager.ACTION_MANAGE_STORAGE);
			PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, cleanupIntent, PENDING_INTENT_FLAG_IMMUTABLE);
			builder.addAction(R.drawable.ic_sd_card_black_24dp, context.getString(R.string.check_now), pendingIntent);
		}

		this.notify(ThreemaApplication.NOT_ENOUGH_DISK_SPACE_NOTIFICATION_ID, builder, null, NOTIFICATION_CHANNEL_ALERT);
	}

	private PendingIntent createPendingIntentWithTaskStack(Intent intent) {
		intent.setData((Uri.parse("foobar://"+ SystemClock.elapsedRealtime())));

		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
		stackBuilder.addNextIntentWithParentStack(intent);
		return stackBuilder.getPendingIntent(0, this.pendingIntentFlags);
	}

	private PendingIntent getPendingIntentForActivity(Class<? extends Activity> activityClass) {
		Intent notificationIntent = new Intent(this.context, activityClass);
		notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
		return createPendingIntentWithTaskStack(notificationIntent);
	}

	@Override
	public void showUnsentMessageNotification(@NonNull ArrayList<AbstractMessageModel> failedMessages) {
		int num = failedMessages.size();
		boolean isFSKeyMismatch = StreamSupport.stream(failedMessages)
			.anyMatch(m -> m.getState() == MessageState.FS_KEY_MISMATCH);

		if (num > 0) {
			Intent sendIntent = new Intent(context, ReSendMessagesBroadcastReceiver.class);
			IntentDataUtil.appendMultipleMessageTypes(failedMessages, sendIntent);

			PendingIntent sendPendingIntent = PendingIntent.getBroadcast(
					context,
					ThreemaApplication.UNSENT_MESSAGE_NOTIFICATION_ID,
					sendIntent,
					this.pendingIntentFlags);

			NotificationCompat.Action tryAgainAction =
					new NotificationCompat.Action.Builder(R.drawable.ic_wear_full_retry,
							context.getString(R.string.try_again), sendPendingIntent)
							.build();
			NotificationCompat.WearableExtender wearableExtender = new NotificationCompat.WearableExtender();
			wearableExtender.addAction(tryAgainAction);

			String content = ConfigUtils.getSafeQuantityString(context, R.plurals.sending_message_failed, num, num);

			if (isFSKeyMismatch) {
				content += ". " + context.getString(R.string.forward_security_reset_simple);
			}

			NotificationCompat.Builder builder =
				new NotificationBuilderWrapper(context, NOTIFICATION_CHANNEL_ALERT, null)
						.setSmallIcon(isFSKeyMismatch ? R.drawable.ic_baseline_key_off_notification_24dp : R.drawable.ic_error_red_24dp)
						.setTicker(content)
						.setPriority(NotificationCompat.PRIORITY_HIGH)
						.setCategory(NotificationCompat.CATEGORY_ERROR)
						.setColor(context.getResources().getColor(R.color.material_red))
						.setContentIntent(getPendingIntentForActivity(HomeActivity.class))
						.extend(wearableExtender)
						.setContentTitle(this.context.getString(R.string.app_name))
						.setContentText(content)
						.setStyle(new NotificationCompat.BigTextStyle().bigText(content))
						.addAction(R.drawable.ic_refresh_white_24dp, context.getString(R.string.try_again), sendPendingIntent);

			this.notify(ThreemaApplication.UNSENT_MESSAGE_NOTIFICATION_ID, builder, null, NOTIFICATION_CHANNEL_ALERT);
		} else {
			this.cancel(ThreemaApplication.UNSENT_MESSAGE_NOTIFICATION_ID);
		}
	}

	@Override
	public void showSafeBackupFailed(int numDays) {
		if (numDays > 0 && preferenceService.getThreemaSafeEnabled()) {
			Intent intent = new Intent(context, BackupAdminActivity.class);
			PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PENDING_INTENT_FLAG_IMMUTABLE);

			String content = String.format(this.context.getString(R.string.safe_failed_notification), numDays);

			NotificationCompat.Builder builder =
					new NotificationBuilderWrapper(context, NOTIFICATION_CHANNEL_ALERT, null)
							.setSmallIcon(R.drawable.ic_error_red_24dp)
							.setTicker(content)
							.setLocalOnly(true)
							.setPriority(NotificationCompat.PRIORITY_HIGH)
							.setCategory(NotificationCompat.CATEGORY_ERROR)
							.setColor(context.getResources().getColor(R.color.material_red))
							.setContentIntent(pendingIntent)
							.setContentTitle(this.context.getString(R.string.app_name))
							.setContentText(content)
							.setStyle(new NotificationCompat.BigTextStyle().bigText(content));

			this.notify(ThreemaApplication.SAFE_FAILED_NOTIFICATION_ID, builder, null, NOTIFICATION_CHANNEL_ALERT);
		} else {
			this.cancel(ThreemaApplication.SAFE_FAILED_NOTIFICATION_ID);
		}
	}

	@Override
	public void cancelWorkSyncProgress() {
		this.cancel(WORK_SYNC_NOTIFICATION_ID);
	}

	@Override
	public void showIdentityStatesSyncProgress() {
		showSyncProgress(ThreemaApplication.IDENTITY_SYNC_NOTIFICATION_ID, NOTIFICATION_CHANNEL_IDENTITY_SYNC, R.string.synchronizing);
	}

	@Override
	public void cancelIdentityStatesSyncProgress() {
		this.cancel(ThreemaApplication.IDENTITY_SYNC_NOTIFICATION_ID);
	}

	private void showSyncProgress(final int notificationId, final String channelName, final @StringRes int textRes) {
		final NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		if (notificationManager == null) {
			return;
		}

		NotificationCompat.Builder builder =
			new NotificationBuilderWrapper(context, channelName, null)
				.setSound(null)
				.setSmallIcon(R.drawable.ic_sync_notification)
				.setContentTitle(this.context.getString(textRes))
				.setProgress(0, 0, true)
				.setPriority(Notification.PRIORITY_LOW)
				.setAutoCancel(true)
				.setLocalOnly(true)
				.setOnlyAlertOnce(true);

		this.notify(notificationId, builder, null, channelName);
	}

	@Override
	public void showNewSyncedContactsNotification(List<ContactModel> contactModels) {
		if (contactModels.size() > 0) {
			String message;
			Intent notificationIntent;

			if (contactModels.size() > 1) {
				StringBuilder contactListBuilder = new StringBuilder();
				for(ContactModel contactModel: contactModels) {
					if (contactListBuilder.length() > 0) {
						contactListBuilder.append(", ");
					}
					contactListBuilder.append(NameUtil.getDisplayName(contactModel));
				}
				message = this.context.getString(R.string.notification_contact_has_joined_multiple, contactModels.size(), this.context.getString(R.string.app_name), contactListBuilder.toString());
				notificationIntent = new Intent(context, HomeActivity.class);
				notificationIntent.putExtra(HomeActivity.EXTRA_SHOW_CONTACTS, true);
			} else {
				String name = NameUtil.getDisplayName(contactModels.get(0));
				message = String.format(this.context.getString(R.string.notification_contact_has_joined), name, this.context.getString(R.string.app_name));
				notificationIntent = new Intent(context, ComposeMessageActivity.class);
				contactService.createReceiver(contactModels.get(0)).prepareIntent(notificationIntent);
			}
			notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
			PendingIntent openPendingIntent = createPendingIntentWithTaskStack(notificationIntent);

			NotificationSchemaImpl notificationSchema = new NotificationSchemaImpl(this.context);
			notificationSchema
				.setVibrate(this.preferenceService.isVibrate())
				.setColor(this.getColorValue(preferenceService.getNotificationLight()));

			NotificationCompat.Builder builder =
					new NotificationBuilderWrapper(context, NOTIFICATION_CHANNEL_NEW_SYNCED_CONTACTS, notificationSchema)
							.setSmallIcon(R.drawable.ic_notification_small)
							.setContentTitle(this.context.getString(R.string.notification_channel_new_contact))
							.setContentText(message)
							.setContentIntent(openPendingIntent)
							.setStyle(new NotificationCompat.BigTextStyle().bigText(message))
							.setPriority(NotificationCompat.PRIORITY_HIGH)
							.setAutoCancel(true);

			this.notify(ThreemaApplication.NEW_SYNCED_CONTACTS_NOTIFICATION_ID, builder, null, NOTIFICATION_CHANNEL_NEW_SYNCED_CONTACTS);
		}
	}
	/**
	 * Create and show notification
	 */
	private void notify(int id, NotificationCompat.Builder builder, @Nullable NotificationSchema schema, @NonNull String channelName) {
		try {
			notificationManagerCompat.notify(id, builder.build());
		} catch (SecurityException e) {
			// some phones revoke access to selected sound files for notifications after an OS upgrade
			logger.error("Can't show notification. Falling back to default ringtone", e);

			if (NOTIFICATION_CHANNEL_CHAT.equals(channelName) ||
				NOTIFICATION_CHANNEL_CALL.equals(channelName) ||
				NOTIFICATION_CHANNEL_CHAT_UPDATE.equals(channelName) ||
				NOTIFICATION_CHANNEL_GROUP_CALL.equals(channelName)
			) {

				if (schema != null && schema.getSoundUri() != null && !DEFAULT_NOTIFICATION_URI.equals(schema.getSoundUri()) && !DEFAULT_RINGTONE_URI.equals(schema.getSoundUri())) {
					// create a new schema with default sound
					NotificationSchemaImpl newSchema = new NotificationSchemaImpl(this.context);
					newSchema.setSoundUri(NOTIFICATION_CHANNEL_CALL.equals(channelName) || NOTIFICATION_CHANNEL_GROUP_CALL.equals(channelName) ? DEFAULT_RINGTONE_URI: DEFAULT_NOTIFICATION_URI);
					newSchema.setVibrate(schema.vibrate()).setColor(schema.getColor());
					builder.setChannelId(NotificationBuilderWrapper.init(context, channelName, newSchema, false));
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
	public void cancel(int id) {
		//make sure that pending intent is also cancelled to allow to check for active conversation notifications pre SDK 23
		Intent intent = new Intent(context, ComposeMessageActivity.class);
		if (id == ThreemaApplication.NEW_MESSAGE_NOTIFICATION_ID){
			PendingIntent pendingConversationIntent = PendingIntent.getActivity(context, ThreemaApplication.NEW_MESSAGE_NOTIFICATION_ID, intent, this.pendingIntentFlags);
			if (pendingConversationIntent != null){
				pendingConversationIntent.cancel();
			}
		}
		notificationManager.cancel(id);
	}

	private int getColorValue(String colorString) {
		int[] colorsHex = context.getResources().getIntArray(R.array.list_light_color_hex);
		if (colorString != null && colorString.length() > 0) {
			return colorsHex[Integer.valueOf(colorString)];
		}

		return -1;
	}

	private void showIconBadge(int unreadMessages) {
		logger.info("Badge: showing " + unreadMessages + " unread");

		if (context.getPackageManager().resolveContentProvider("com.teslacoilsw.notifier", 0) != null) {
			// nova launcher / teslaunread
			try {
				String launcherClassName = context.getPackageManager().getLaunchIntentForPackage(BuildConfig.APPLICATION_ID).getComponent().getClassName();
				final ContentValues contentValues = new ContentValues();
				contentValues.put("tag", BuildConfig.APPLICATION_ID + "/" + launcherClassName);
				contentValues.put("count", unreadMessages);

				context.getApplicationContext().getContentResolver().insert(Uri.parse("content://com.teslacoilsw.notifier/unread_count"), contentValues);
			} catch (Exception e) {
				logger.error("Exception", e);
			}
		} else if (ConfigUtils.isHuaweiDevice()) {
			try {
				String launcherClassName = context.getPackageManager().getLaunchIntentForPackage(BuildConfig.APPLICATION_ID).getComponent().getClassName();
				Bundle localBundle = new Bundle();
				localBundle.putString("package", BuildConfig.APPLICATION_ID);
				localBundle.putString("class", launcherClassName);
				localBundle.putInt("badgenumber", unreadMessages);
				context.getContentResolver().call(Uri.parse("content://com.huawei.android.launcher.settings/badge/"), "change_badge", null, localBundle);
			} catch (Exception e) {
				logger.error("Exception", e);
			}
		} else if (ConfigUtils.isSonyDevice()) {
			try {
				String launcherClassName = context.getPackageManager().getLaunchIntentForPackage(BuildConfig.APPLICATION_ID).getComponent().getClassName();
				if (context.getPackageManager().resolveContentProvider("com.sonymobile.home.resourceprovider", 0) != null) {
					// use content provider
					final ContentValues contentValues = new ContentValues();
					contentValues.put("badge_count", unreadMessages);
					contentValues.put("package_name", BuildConfig.APPLICATION_ID);
					contentValues.put("activity_name", launcherClassName);

					if (RuntimeUtil.isOnUiThread()) {
						if (queryHandler == null) {
							queryHandler = new AsyncQueryHandler(
								context.getApplicationContext().getContentResolver()) {
							};
						}
						queryHandler.startInsert(0, null, Uri.parse("content://com.sonymobile.home.resourceprovider/badge"), contentValues);
					} else {
						context.getApplicationContext().getContentResolver().insert(Uri.parse("content://com.sonymobile.home.resourceprovider/badge"), contentValues);
					}
				} else {
					// use broadcast
					Intent intent = new Intent("com.sonyericsson.home.action.UPDATE_BADGE");
					intent.putExtra("com.sonyericsson.home.intent.extra.badge.PACKAGE_NAME", BuildConfig.APPLICATION_ID);
					intent.putExtra("com.sonyericsson.home.intent.extra.badge.ACTIVITY_NAME", launcherClassName);
					intent.putExtra("com.sonyericsson.home.intent.extra.badge.MESSAGE", String.valueOf(unreadMessages));
					intent.putExtra("com.sonyericsson.home.intent.extra.badge.SHOW_MESSAGE", unreadMessages > 0);
					context.sendBroadcast(intent);
				}
			} catch (Exception e) {
				logger.error("Exception", e);
			}
		} else {
			// also works on LG and later HTC devices
			try {
				String launcherClassName = context.getPackageManager().getLaunchIntentForPackage(BuildConfig.APPLICATION_ID).getComponent().getClassName();
				Intent intent = new Intent("android.intent.action.BADGE_COUNT_UPDATE");
				intent.putExtra("badge_count", unreadMessages);
				intent.putExtra("badge_count_package_name", BuildConfig.APPLICATION_ID);
				intent.putExtra("badge_count_class_name", launcherClassName);
				context.sendBroadcast(intent);
			} catch (Exception e) {
				logger.error("Exception", e);
			}
		}
	}

	@Override
	public void showWebclientResumeFailed(String msg) {
		NotificationCompat.Builder builder =
			new NotificationBuilderWrapper(this.context, NOTIFICATION_CHANNEL_NOTICE, null)
				.setSmallIcon(R.drawable.ic_web_notification)
				.setTicker(msg)
				.setLocalOnly(true)
				.setPriority(NotificationCompat.PRIORITY_HIGH)
				.setCategory(NotificationCompat.CATEGORY_ERROR)
				.setColor(this.context.getResources().getColor(R.color.material_red))
				.setContentTitle(this.context.getString(R.string.app_name))
				.setContentText(msg)
				.setStyle(new NotificationCompat.BigTextStyle().bigText(msg));
		this.notify(ThreemaApplication.WEB_RESUME_FAILED_NOTIFICATION_ID, builder, null, NOTIFICATION_CHANNEL_NOTICE);
	}

	@Override
	public void cancelRestartNotification() {
		cancel(APP_RESTART_NOTIFICATION_ID);
	}

	@Override
	public void cancelRestoreNotification() {
		cancel(RESTORE_COMPLETION_NOTIFICATION_ID);
	}

	@Override
	public void resetConversationNotifications(){
		conversationNotifications.clear();
	}

	@Override
	public void showGroupJoinResponseNotification(@NonNull OutgoingGroupJoinRequestModel outgoingGroupJoinRequestModel,
	                                              @NonNull OutgoingGroupJoinRequestModel.Status status,
	                                              @NonNull DatabaseServiceNew databaseService) {
		logger.info("handle join response, showGroupJoinResponseNotification with status {}", status);

		Intent notificationIntent;
		String message;

		switch (status) {
			case ACCEPTED:
				message = String.format(context.getString(R.string.group_response_accepted), outgoingGroupJoinRequestModel.getGroupName());
				break;
			case GROUP_FULL:
				message = String.format(context.getString(R.string.group_response_full), outgoingGroupJoinRequestModel.getGroupName());
				break;
			case REJECTED:
				message = String.format(context.getString(R.string.group_response_rejected), outgoingGroupJoinRequestModel.getGroupName());
				break;
			case UNKNOWN:
			default:
				logger.info("Unknown response state don't show notification");
				return;
		}

		if (outgoingGroupJoinRequestModel.getGroupApiId() != null) {
			GroupModel groupModel = databaseService
				.getGroupModelFactory()
				.getByApiGroupIdAndCreator(outgoingGroupJoinRequestModel.getGroupApiId().toString(), outgoingGroupJoinRequestModel.getAdminIdentity());
			if (groupModel != null) {
				notificationIntent = new Intent(context, ComposeMessageActivity.class);
				notificationIntent.putExtra(ThreemaApplication.INTENT_DATA_GROUP, groupModel.getId());
			} else {
				notificationIntent = new Intent(context, OutgoingGroupRequestActivity.class);
			}
		} else {
			notificationIntent = new Intent(context, OutgoingGroupRequestActivity.class);
		}
		notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
		PendingIntent openPendingIntent = createPendingIntentWithTaskStack(notificationIntent);

		NotificationSchemaImpl notificationSchema = new NotificationSchemaImpl(this.context);
		notificationSchema
			.setVibrate(this.preferenceService.isVibrate())
			.setColor(this.getColorValue(preferenceService.getNotificationLight()));

		NotificationCompat.Builder builder =
			new NotificationBuilderWrapper(context, NOTIFICATION_CHANNEL_GROUP_JOIN_RESPONSE, notificationSchema)
				.setSmallIcon(R.drawable.ic_notification_small)
				.setContentTitle(context.getString(R.string.group_response))
				.setContentText(message)
				.setContentIntent(openPendingIntent)
				.setStyle(new NotificationCompat.BigTextStyle().bigText(message))
				.setPriority(NotificationCompat.PRIORITY_HIGH)
				.setAutoCancel(true);

		this.notify(ThreemaApplication.GROUP_RESPONSE_NOTIFICATION_ID, builder, null, NOTIFICATION_CHANNEL_GROUP_JOIN_RESPONSE);
	}

	@Override
	public void showGroupJoinRequestNotification(@NonNull IncomingGroupJoinRequestModel incomingGroupJoinRequestModel, GroupModel groupModel) {

		Intent notificationIntent = new Intent(context, IncomingGroupRequestActivity.class);
		notificationIntent.putExtra(ThreemaApplication.INTENT_DATA_GROUP_API, groupModel.getApiGroupId());
		notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
		PendingIntent openPendingIntent = createPendingIntentWithTaskStack(notificationIntent);

		ContactModel senderContact = this.contactService.getByIdentity(incomingGroupJoinRequestModel.getRequestingIdentity());
		Person.Builder builder = new Person.Builder()
			.setName(NameUtil.getDisplayName(senderContact));

		Bitmap avatar = contactService.getAvatar(senderContact, false);
		if (avatar != null) {
			IconCompat iconCompat = IconCompat.createWithBitmap(avatar);
			builder.setIcon(iconCompat);
		}
		Person senderPerson = builder.build();

		NotificationCompat.MessagingStyle messagingStyle = new NotificationCompat.MessagingStyle(senderPerson);

		// bug: setting a conversation title implies a group chat
		messagingStyle.setConversationTitle(String.format(context.getString(R.string.group_join_request_for), groupModel.getName()));
		messagingStyle.setGroupConversation(false);

		messagingStyle.addMessage(
			incomingGroupJoinRequestModel.getMessage(),
			incomingGroupJoinRequestModel.getRequestTime().getTime(),
			senderPerson);

		int requestIdNonce = ThreemaApplication.GROUP_REQUEST_NOTIFICATION_ID + (int) SystemClock.elapsedRealtime();

		Intent acceptIntent = new Intent(context, NotificationActionService.class);
		acceptIntent.setAction(NotificationActionService.ACTION_GROUP_REQUEST_ACCEPT);
		acceptIntent.putExtra(ThreemaApplication.INTENT_DATA_INCOMING_GROUP_REQUEST, incomingGroupJoinRequestModel);
		acceptIntent.putExtra(ThreemaApplication.INTENT_DATA_GROUP_REQUEST_NOTIFICATION_ID, requestIdNonce);
		PendingIntent acceptPendingIntent = PendingIntent.getService(context, requestIdNonce + 1, acceptIntent, pendingIntentFlags);

		Intent rejectIntent = new Intent(context, NotificationActionService.class);
		rejectIntent.setAction(NotificationActionService.ACTION_GROUP_REQUEST_REJECT);
		rejectIntent.putExtra(ThreemaApplication.INTENT_DATA_INCOMING_GROUP_REQUEST, incomingGroupJoinRequestModel);
		rejectIntent.putExtra(ThreemaApplication.INTENT_DATA_GROUP_REQUEST_NOTIFICATION_ID, requestIdNonce);
		PendingIntent rejectPendingIntent = PendingIntent.getService(context, requestIdNonce + 2 , rejectIntent, pendingIntentFlags);

		NotificationSchemaImpl notificationSchema = new NotificationSchemaImpl(this.context);
		notificationSchema
			.setVibrate(this.preferenceService.isVibrate())
			.setColor(this.getColorValue(preferenceService.getNotificationLight()));

		NotificationCompat.Builder notifBuilder =
			new NotificationBuilderWrapper(context, NOTIFICATION_CHANNEL_GROUP_JOIN_REQUEST, notificationSchema)
				.setSmallIcon(R.drawable.ic_notification_small)
				.setContentTitle(context.getString(R.string.group_join_request))
				.setContentText(incomingGroupJoinRequestModel.getMessage())
				.setContentIntent(openPendingIntent)
				.setStyle(messagingStyle)
				.setPriority(NotificationCompat.PRIORITY_HIGH)
				.setAutoCancel(true);

		addGroupLinkActions(notifBuilder, acceptPendingIntent, rejectPendingIntent);

		this.notify(requestIdNonce, notifBuilder, null, NOTIFICATION_CHANNEL_GROUP_JOIN_REQUEST);
	}

}
