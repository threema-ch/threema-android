/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2021 Threema GmbH
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

package ch.threema.app.notifications;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.NotificationService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.voip.services.VoipCallService;

import static androidx.core.app.NotificationCompat.VISIBILITY_PRIVATE;
import static ch.threema.app.services.NotificationService.NOTIFICATION_CHANNELGROUP_CHAT;
import static ch.threema.app.services.NotificationService.NOTIFICATION_CHANNELGROUP_CHAT_UPDATE;
import static ch.threema.app.services.NotificationService.NOTIFICATION_CHANNELGROUP_VOIP;
import static ch.threema.app.services.NotificationService.NOTIFICATION_CHANNEL_CHAT_ID_PREFIX;
import static ch.threema.app.services.NotificationService.NOTIFICATION_CHANNEL_CHAT_UPDATE_ID_PREFIX;
import static ch.threema.app.services.NotificationService.NOTIFICATION_CHANNEL_VOIP_ID_PREFIX;

public class NotificationBuilderWrapper extends NotificationCompat.Builder {
	private static final Logger logger = LoggerFactory.getLogger(NotificationBuilderWrapper.class);

	public static long[] VIBRATE_PATTERN_SHORT = new long[]{0, 100, 150, 100};
	public static long[] VIBRATE_PATTERN_REGULAR = new long[]{0, 250, 250, 250};
	public static long[] VIBRATE_PATTERN_INCOMING_CALL = new long[]{0, 1000, 1000, 0};
	public static long[] VIBRATE_PATTERN_SILENT = new long[]{0};

	private NotificationService.NotificationSchema notificationSchema;

	private static String newChannelId;

	/**
	 * A wrapper around NotificationCompat.Builder that sets up notification channels based on notification schemas and takes care of initializing the public version of a notification with the correct channel id
	 *
	 * @param context - Context
	 * @param channelId - suggested Notification Channel ID
	 * @param notificationSchema - NotificationSchema (sound, lights, vibration etc.) to be used with this notification
	 * @param publicBuilder - a public version of this notification to be shown on secure lockscreens
	 */
	public NotificationBuilderWrapper(@NonNull Context context, @NonNull String channelId, NotificationService.NotificationSchema notificationSchema, NotificationCompat.Builder publicBuilder) {
		super(context, init(context, channelId, notificationSchema, false));

		if (publicBuilder != null) {
			publicBuilder.setChannelId(newChannelId);
			setPublicVersion(publicBuilder.build());
		}

		this.notificationSchema = notificationSchema;
	}

	/**
	 * A wrapper around NotificationCompat.Builder that sets up notification channels based on a notification schema
	 *
	 * @param context - Context
	 * @param channelId - suggested Notification Channel ID
	 * @param notificationSchema - NotificationSchema (sound, lights, vibration etc.) to be used with this notification
	 */
	public NotificationBuilderWrapper(@NonNull Context context, @NonNull String channelId, NotificationService.NotificationSchema notificationSchema) {
		super(context, init(context, channelId, notificationSchema, false));

		this.notificationSchema = notificationSchema;
	}

	/**
	 * A wrapper around NotificationCompat.Builder that sets up notification channels based on a notification schema
	 *
	 * @param context - Context
	 * @param channelId - suggested Notification Channel ID
	 * @param isMuted - Mute flag used for incoming call notifications
	 */
	public NotificationBuilderWrapper(@NonNull Context context, @NonNull String channelId, boolean isMuted) {
		super(context, init(context, channelId, null, isMuted));

		this.notificationSchema = null;
	}

	public static String init(@NonNull Context context, @NonNull String channelId, NotificationService.NotificationSchema notificationSchema, boolean isMuted) {
		newChannelId = channelId;

		if (ConfigUtils.supportsNotificationChannels()) {

			int colorValue = notificationSchema != null ? notificationSchema.getColor() : 0;
			Uri ringtone = notificationSchema != null ? notificationSchema.getSoundUri() : null;
			boolean vibrate = notificationSchema != null && notificationSchema.vibrate();

			NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			if (notificationManager != null) {
				NotificationChannel notificationChannel;

				if (NotificationService.NOTIFICATION_CHANNEL_CHAT.equals(newChannelId)) {
					notificationChannel = getNewChatNotificationChannel(context, notificationManager, ringtone, vibrate, colorValue);
					if (notificationChannel != null) {
						notificationManager.createNotificationChannel(notificationChannel);
						newChannelId = notificationChannel.getId();
					}
				} else if (NotificationService.NOTIFICATION_CHANNEL_CALL.equals(newChannelId)) {
					notificationChannel = getNewVoipNotificationChannel(context, notificationManager, isMuted);
					if (notificationChannel != null) {
						notificationManager.createNotificationChannel(notificationChannel);
						newChannelId = notificationChannel.getId();
					}
				} else if (NotificationService.NOTIFICATION_CHANNEL_CHAT_UPDATE.equals(newChannelId)) {
					notificationChannel = getNewChatUpdateNotificationChannel(context, notificationManager);
					if (notificationChannel != null) {
						notificationManager.createNotificationChannel(notificationChannel);
						newChannelId = notificationChannel.getId();
					}
				}
			}
		}
		return newChannelId;
	}

	@TargetApi(Build.VERSION_CODES.O)
	private static NotificationChannel getNewChatNotificationChannel(Context context, NotificationManager notificationManager, Uri ringtone, boolean vibrate, int colorValue) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

		NotificationChannelSettings notificationChannelSettings = new NotificationChannelSettings(NOTIFICATION_CHANNELGROUP_CHAT,
				NOTIFICATION_CHANNEL_CHAT_ID_PREFIX, sharedPreferences, NotificationUtil.getNotificationImportance(context), true, VISIBILITY_PRIVATE,
				context.getString(R.string.chats), context.getString(R.string.notification_setting_ignored),
				context.getString(R.string.preferences__noti_channel_chat_seq));

		if (VoipCallService.isRunning()) {
			if (colorValue != 0) {
				notificationChannelSettings.setLightColor(colorValue);
				notificationChannelSettings.setSound(null);
			}
		} else {
			if (ringtone != null && ringtone.toString().length() > 4) {
				if (ContentResolver.SCHEME_FILE.equalsIgnoreCase(ringtone.getScheme())) {
					// https://commonsware.com/blog/2016/09/07/notifications-sounds-android-7p0-aggravation.html
					ThreemaApplication.getAppContext().grantUriPermission("com.android.systemui", ringtone, Intent.FLAG_GRANT_READ_URI_PERMISSION);
				}
				notificationChannelSettings.setSound(ringtone);
			} else {
				notificationChannelSettings.setSound(null);
			}
			if (vibrate) {
				notificationChannelSettings.setVibrationPattern(VIBRATE_PATTERN_REGULAR);
			}
			if (colorValue != 0) {
				notificationChannelSettings.setLightColor(colorValue);
			}
		}

		return validateNotificationChannel(notificationManager, sharedPreferences, notificationChannelSettings);
	}

	@TargetApi(Build.VERSION_CODES.O)
	private static NotificationChannel getNewVoipNotificationChannel(Context context, NotificationManager notificationManager, boolean isMuted) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

		NotificationChannelSettings notificationChannelSettings = new NotificationChannelSettings(NOTIFICATION_CHANNELGROUP_VOIP,
				NOTIFICATION_CHANNEL_VOIP_ID_PREFIX, sharedPreferences, NotificationManager.IMPORTANCE_HIGH, false, VISIBILITY_PRIVATE,
				context.getString(R.string.prefs_title_voip), context.getString(R.string.notification_setting_ignored),
				context.getString(R.string.preferences__noti_channel_voip_seq));

		if (NotificationUtil.isVoiceCallVibrate(context) && !isMuted) {
			notificationChannelSettings.setVibrationPattern(VIBRATE_PATTERN_INCOMING_CALL);
		}
		notificationChannelSettings.setSound(null);

		return validateNotificationChannel(notificationManager, sharedPreferences, notificationChannelSettings);
	}

	@TargetApi(Build.VERSION_CODES.O)
	private static NotificationChannel getNewChatUpdateNotificationChannel(Context context, NotificationManager notificationManager) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

		NotificationChannelSettings notificationChannelSettings = new NotificationChannelSettings(NOTIFICATION_CHANNELGROUP_CHAT_UPDATE,
				NOTIFICATION_CHANNEL_CHAT_UPDATE_ID_PREFIX, sharedPreferences, NotificationManager.IMPORTANCE_LOW, true, VISIBILITY_PRIVATE,
				context.getString(R.string.chat_updates), context.getString(R.string.notification_setting_ignored),
				context.getString(R.string.preferences__noti_channel_chat_update_seq));

		notificationChannelSettings.setVibrationPattern(null);
		notificationChannelSettings.setSound(null);
		notificationChannelSettings.setLightColor(null);

		return validateNotificationChannel(notificationManager, sharedPreferences, notificationChannelSettings);
	}

	/**
	 * Creates a channel based on the provided notificationChannelSettings.
	 * If a channel of the same name already exists, it will create a new one and compare its settings with the settings of the existing channel
	 * to see if they are identical or if they have been tampered with by the user. If the settings are different, it will create a new
	 * channel with an incremented sequence number and delete the existing channel.
	 * @param notificationManager The system's NotificationManager
	 * @param sharedPreferences The system's SharedPreferences
	 * @param notificationChannelSettings The desired notification settings
	 * @return NotificationChannel which is guaranteed to have the correct settings
	 */
	@TargetApi(Build.VERSION_CODES.O)
	private static NotificationChannel validateNotificationChannel(NotificationManager notificationManager, SharedPreferences sharedPreferences, NotificationChannelSettings notificationChannelSettings) {
		String hash = notificationChannelSettings.hash();
		NotificationChannel existingNotificationChannel = notificationManager.getNotificationChannel(hash);
		// create a new channel with the requested settings. if no previous channel exists with the same settings, directly use the new channel, otherwise use it for testing
		NotificationChannel newNotificationChannel = createNotificationChannel(hash, notificationChannelSettings);

		// compare existing channel with new channel to find out if they're identical
		if (!compareChannelSettings(existingNotificationChannel, newNotificationChannel)) {
			logger.info("Settings of channel {} in group \"{}\" have been tampered with. Discarding group.", hash, notificationChannelSettings.getChannelGroupId());

			// a channel with these settings already exists but has been tampered with - set new sequence number
			notificationChannelSettings.setSeqNum(System.currentTimeMillis());

			// clear all chats in this channel group and re-create it
			if (getNotificationChannelGroup(notificationManager, notificationChannelSettings.getChannelGroupId()) != null) {
				notificationManager.deleteNotificationChannelGroup(notificationChannelSettings.getChannelGroupId());
			}
			createNotificationChannelGroupIfNotExists(notificationManager, notificationChannelSettings);
			newNotificationChannel = createNotificationChannel(notificationChannelSettings.hash(), notificationChannelSettings);

			logger.info("Creating new channel {} in group \"{}\"", newNotificationChannel.getId(), notificationChannelSettings.getChannelGroupId());

			// update prefs
			sharedPreferences.edit().putLong(notificationChannelSettings.getSeqPrefKey(), notificationChannelSettings.getSeqNum()).apply();
		} else {
			createNotificationChannelGroupIfNotExists(notificationManager, notificationChannelSettings);

			if (existingNotificationChannel == null) {
				sharedPreferences.edit().putLong(notificationChannelSettings.getSeqPrefKey(), notificationChannelSettings.getSeqNum()).apply();

				logger.info("Creating new channel {} in group \"{}\"", newNotificationChannel.getId(), notificationChannelSettings.getChannelGroupId());
			} else {
				logger.info("Re-using channel {} in group \"{}\"", hash, notificationChannelSettings.getChannelGroupId());
			}
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			NotificationChannelGroup notificationChannelGroup = getNotificationChannelGroup(notificationManager, notificationChannelSettings.getChannelGroupId());
			if (notificationChannelGroup != null && notificationChannelGroup.isBlocked()) {
				logger.info("User has disabled notification channel group \"{}\"", notificationChannelSettings.getChannelGroupId());
			}
		}

		return newNotificationChannel;
	}

	@TargetApi(Build.VERSION_CODES.O)
	private static void createNotificationChannelGroupIfNotExists(NotificationManager notificationManager, NotificationChannelSettings notificationChannelSettings) {
		if (getNotificationChannelGroup(notificationManager, notificationChannelSettings.getChannelGroupId()) == null) {
			notificationManager.createNotificationChannelGroup(new NotificationChannelGroup(notificationChannelSettings.getChannelGroupId(), notificationChannelSettings.getGroupName()));
		}
	}

	@TargetApi(Build.VERSION_CODES.O)
	private static boolean compareChannelSettings(NotificationChannel existingNotificationChannel, NotificationChannel newNotificationChannel) {
		if (existingNotificationChannel == null) {
			logger.warn("A channel with hash {} does not exist", newNotificationChannel.getId());
			return true;
		}

		try {
			logger.info("Importance: {} -> {}", existingNotificationChannel.getImportance(), newNotificationChannel.getImportance());
		} catch (Exception e) { /* */ }

		if (!Objects.equals(existingNotificationChannel.getImportance(), newNotificationChannel.getImportance())) {
			return false;
		}

		try {
			logger.info("Sound: {} -> {}", existingNotificationChannel.getSound(), newNotificationChannel.getSound());
		} catch (Exception e) { /* */ }

		if (!Objects.equals(existingNotificationChannel.getSound(), newNotificationChannel.getSound())) {
			return false;
		}

		try {
			logger.info("Vibrate: {} -> {}", existingNotificationChannel.shouldVibrate(), newNotificationChannel.shouldVibrate());
		} catch (Exception e) { /* */ }

		if (!Objects.equals(existingNotificationChannel.shouldVibrate(), newNotificationChannel.shouldVibrate())) {
			return false;
		}

		final String oldPattern = Arrays.toString(existingNotificationChannel.getVibrationPattern());
		final String newPattern = Arrays.toString(newNotificationChannel.getVibrationPattern());
		logger.info("Pattern: {} -> {}", oldPattern, newPattern);

		if (!Arrays.equals(existingNotificationChannel.getVibrationPattern(), newNotificationChannel.getVibrationPattern())) {
			return false;
		}

		logger.info("Badge (ignored): {} -> {}", existingNotificationChannel.canShowBadge(), newNotificationChannel.canShowBadge());
/*
		if (!Objects.equals(existingNotificationChannel.canShowBadge(), newNotificationChannel.canShowBadge())) {
			return false;
		}
*/
		logger.info("ShowLights: {} -> {}", existingNotificationChannel.shouldShowLights(), newNotificationChannel.shouldShowLights());
		if (!Objects.equals(existingNotificationChannel.shouldShowLights(), newNotificationChannel.shouldShowLights())) {
			return false;
		}

		logger.info("LightColor: {} -> {}", existingNotificationChannel.getLightColor(), newNotificationChannel.getLightColor());

		if (!Objects.equals(existingNotificationChannel.getLightColor(), newNotificationChannel.getLightColor())) {
			return false;
		}

		logger.info("LockScreenVisibility (ignored): {} -> {}", existingNotificationChannel.getLockscreenVisibility(), newNotificationChannel.getLockscreenVisibility());
/*
		// Visibility value changes after channel has been created
		if (!Objects.equals(existingNotificationChannel.getLockscreenVisibility(), newNotificationChannel.getLockscreenVisibility()) {
			return false;
		}

		// canBypassDND changes after set on MIUI 10
		if (existingNotificationChannel.canBypassDnd()) {
			MessageLogFileUtil.log(TAG, "canBypassDnd: " + existingNotificationChannel.canBypassDnd(), true);
			return false;
		}
*/
		// identical
		return true;
	}

	/**
	 * Create a notification channel with the supplied settings. The system will recycle an existing channel if it has the same hash value as its ID
	 * @param hash Hash value of the NotificationChannelSettings
	 * @param notificationChannelSettings
	 * @return the newly created (or recycled) notification channel
	 */
	@TargetApi(Build.VERSION_CODES.O)
	private static NotificationChannel createNotificationChannel(String hash, NotificationChannelSettings notificationChannelSettings) {
		AudioAttributes audioAttributes = new AudioAttributes.Builder()
				.setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
				.build();

		NotificationChannel newNotificationChannel = new NotificationChannel(
				hash, hash.substring(0, 16),
				notificationChannelSettings.getImportance());

		newNotificationChannel.setShowBadge(notificationChannelSettings.isShowBadge());
		newNotificationChannel.setGroup(notificationChannelSettings.getChannelGroupId());
		newNotificationChannel.setDescription(notificationChannelSettings.getDescription());
//		newNotificationChannel.setBypassDnd(false);

		if (notificationChannelSettings.getSound() != null) {
			newNotificationChannel.setSound(notificationChannelSettings.getSound(), audioAttributes);
		} else {
			newNotificationChannel.setSound(null, null);
		}

		if (notificationChannelSettings.getVibrationPattern() != null) {
			newNotificationChannel.enableVibration(true);
			newNotificationChannel.setVibrationPattern(notificationChannelSettings.getVibrationPattern());
		} else {
			newNotificationChannel.enableVibration(false);
		}

		if (notificationChannelSettings.getLightColor() != null) {
			newNotificationChannel.enableLights(true);
			newNotificationChannel.setLightColor(notificationChannelSettings.getLightColor());
		} else {
			newNotificationChannel.enableLights(false);
		}

		newNotificationChannel.setLockscreenVisibility(notificationChannelSettings.getVisibility());

		return newNotificationChannel;
	}

	@Override
	public Notification build() {
		if (notificationSchema != null && !ConfigUtils.supportsNotificationChannels()) {
			boolean noisy = false;

			if (!VoipCallService.isRunning()) {

				if (notificationSchema.vibrate()
					&& (notificationSchema.getRingerMode() != AudioManager.RINGER_MODE_SILENT)) {
					setDefaults(NotificationCompat.DEFAULT_VIBRATE);
					noisy = true;
				} else if (notificationSchema.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE
					&& notificationSchema.getSoundUri() != null
					&& notificationSchema.getSoundUri().toString().length() > 0) {
					setVibrate(VIBRATE_PATTERN_SHORT);
					noisy = true;
				}

				if (notificationSchema.getSoundUri() != null && notificationSchema.getSoundUri().toString().length() > 0) {
					if (ContentResolver.SCHEME_FILE.equals(notificationSchema.getSoundUri().getScheme())) {
						// https://commonsware.com/blog/2016/09/07/notifications-sounds-android-7p0-aggravation.html
						ThreemaApplication.getAppContext().grantUriPermission("com.android.systemui", notificationSchema.getSoundUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
					}
					setSound(notificationSchema.getSoundUri());
					noisy = true;
				}
			}

			if (notificationSchema.getColor() != 0) {
				if (!noisy && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
					// HACK: fix Android 7 non-blinking LED bug by providing a silent vibration
					setVibrate(VIBRATE_PATTERN_SILENT);
				}
				setLights(notificationSchema.getColor(), 1000, 1000);
			}

		}
		return super.build();
	}

	/**
	 * Check if a notification group with the given groupId exists
	 * @param notificationManager
	 * @param groupId
	 * @return NotificationChannelGroup object of matching group, null otherwise
	 */
	@TargetApi(Build.VERSION_CODES.O)
	private static NotificationChannelGroup getNotificationChannelGroup(NotificationManager notificationManager, @NonNull String groupId) {
		// getNotificationChannelGroup() currently throws DeadSystemException on some phones
		/*
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			return notificationManager.getNotificationChannelGroup(groupId);
		}
		*/

		List<NotificationChannelGroup> groups = notificationManager.getNotificationChannelGroups();
		for (NotificationChannelGroup group: groups) {
			if (groupId.equals(group.getId())) {
				return group;
			}
		}
		return null;
	}
}
