/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2023 Threema GmbH
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

package ch.threema.app.utils;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;

import org.slf4j.Logger;

import java.util.Calendar;
import java.util.Set;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationChannelGroupCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.stores.IdentityStore;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.ContactModel;

public class DNDUtil {
	private static final Logger logger = LoggingUtil.getThreemaLogger("DNDUtil");
	private final DeadlineListService mutedChatsListService;
	private final DeadlineListService mentionOnlyChatsListService;
	private final IdentityStore identityStore;
	private final Context context;

	// Singleton stuff
	private static DNDUtil sInstance = null;

	public static synchronized DNDUtil getInstance() {
		if (sInstance == null) {
			sInstance = new DNDUtil();
		}
		return sInstance;
	}

	private DNDUtil() {
		this.context = ThreemaApplication.getAppContext();

		this.mutedChatsListService = ThreemaApplication.getServiceManager().getMutedChatsListService();
		this.mentionOnlyChatsListService = ThreemaApplication.getServiceManager().getMentionOnlyChatsListService();
		this.identityStore = ThreemaApplication.getServiceManager().getIdentityStore();
	}

	/**
	 * Returns true if the user is mentioned in the provided message text or the text contains an "@All" mention
	 * @param rawMessageText Raw message text without san substitutions for mentions
	 * @return true if the user is addressed by a mention, false otherwise
	 */
	private boolean isUserMentioned(@Nullable CharSequence rawMessageText) {
		if (rawMessageText != null) {
			if (rawMessageText.length() > 10) {
				return rawMessageText.toString().contains("@[" + ContactService.ALL_USERS_PLACEHOLDER_ID + "]") ||
						rawMessageText.toString().contains("@[" + identityStore.getIdentity() + "]");
			}
			// message text can't possibly contain a mention - too short
		}
		// no message text - no mention
		return false;
	}

	/**
	 * Returns true if the chat for the provided MessageReceiver is set to mention only at this time.
	 * @param messageReceiver MessageReceiver to check for DND status
	 * @return true if the chat is muted
	 */
	public boolean isMentionOnlyChat(@Nullable MessageReceiver<?> messageReceiver) {
		if (messageReceiver instanceof  GroupMessageReceiver) {
			String uniqueId = messageReceiver.getUniqueIdString();
			return mentionOnlyChatsListService != null && mentionOnlyChatsListService.has(uniqueId);
		}
		return false;
	}

	/**
	 * Returns true if the chat for the provided MessageReceiver is muted at this time. This does
	 * NOT check for work hours (use {@link #isMutedWork()} for this) or mention-only chats (use
	 * {@link #isMentionOnlyChat(MessageReceiver)} to check this).
	 * @param messageReceiver MessageReceiver to check for DND status
	 * @return true if the chat is muted
	 */
	public boolean isMutedChat(@Nullable MessageReceiver<?> messageReceiver) {
		if (messageReceiver != null) {
			String uniqueId = messageReceiver.getUniqueIdString();
			return mutedChatsListService != null && mutedChatsListService.has(uniqueId);
		}
		return false;
	}

	/**
	 * Returns true if the chat for the provided MessageReceiver is permanently or temporarily muted AT THIS TIME and
	 * no intrusive notification should be shown for an incoming message
	 * If a message text is provided it is checked for possible mentions - group messages only
	 * @param messageReceiver MessageReceiver to check for DND status
	 * @param rawMessageText Text of the incoming message (optional, group messages only)
	 * @return true if chat is muted
	 */
	public boolean isMuted(MessageReceiver messageReceiver, CharSequence rawMessageText) {
		// ok, it's muted
		return isMutedPrivate(messageReceiver, rawMessageText) || isMutedWork();
	}

	public boolean isMutedPrivate(MessageReceiver messageReceiver, CharSequence rawMessageText) {
		String uniqueId = messageReceiver.getUniqueIdString();

		if (isMutedChat(messageReceiver)) {
			// user has set DND option on this chat
			logger.info("Chat is muted");
			return true;
		}
		if (isMentionOnlyChat(messageReceiver)) {
			// user has "DND except when mentioned" option enabled on this chat
			logger.info("Chat is mention only");
			// user is not mentioned => mute
			return !isUserMentioned(rawMessageText);
		}
		return false;
	}

	/**
	 * Check if Work DND schedule is currently active
	 * @return true if we're currently outside of the working hours set by the user and Work DND is currently enabled, false otherwise
	 */
	public boolean isMutedWork() {
		if (ConfigUtils.isWorkBuild()) {
			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
			if (sharedPreferences.getBoolean(context.getString(R.string.preferences__working_days_enable), false)) {
				// user has working hours DND enabled
				int dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1; // day of week starts with 1 in Java

				Set<String> selectedWorkingDays = sharedPreferences.getStringSet(context.getString(R.string.preferences__working_days), null);
				if (selectedWorkingDays != null) {
					if (!selectedWorkingDays.contains(String.valueOf(dayOfWeek))) {
						// it's not a working day today
						return true;
					} else {
						// check if hours match as well
						int currentTimeStamp = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) * 60 + Calendar.getInstance().get(Calendar.MINUTE);
						try {
							String[] startTime = sharedPreferences.getString(context.getString(R.string.preferences__work_time_start), "00:00").split(":");
							String[] endTime = sharedPreferences.getString(context.getString(R.string.preferences__work_time_end), "23:59").split(":");

							int startTimeStamp = Integer.parseInt(startTime[0]) * 60 + Integer.parseInt(startTime[1]);
							int endTimeStamp = Integer.parseInt(endTime[0]) * 60 + Integer.parseInt(endTime[1]);

							if (currentTimeStamp < startTimeStamp || currentTimeStamp > endTimeStamp) {
								logger.info("Off-hours active");
								return true;
							}
						} catch (Exception ignored) {
							//
						}
					}
				}
			}
		}
		return false;
	}

	@TargetApi(Build.VERSION_CODES.M)
	public boolean isStarredContact(MessageReceiver messageReceiver) {
		if (!(messageReceiver instanceof ContactMessageReceiver)) {
			return false;
		}

		ContactModel contactModel = ((ContactMessageReceiver) messageReceiver).getContact();

		PreferenceService preferenceService;
		try {
			preferenceService = ThreemaApplication.getServiceManager().getPreferenceService();
		} catch (NullPointerException e) {
			return false;
		}

		if (!preferenceService.isSyncContacts()) {
			return false;
		}

		if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
			return false;
		}

		Uri contactUri;
		String lookupKey = contactModel.getAndroidContactLookupKey();

		if (lookupKey != null) {
			try {
				contactUri = AndroidContactUtil.getInstance().getAndroidContactUri(contactModel);
			} catch (Exception e) {
				logger.error("Could not get Android contact URI", e);
				return false;
			}

			if (contactUri != null) {
				String[] projection = {ContactsContract.Contacts._ID};
				String selection = ContactsContract.Contacts.STARRED + "=1";
				try (Cursor cursor = context.getContentResolver().query(contactUri, projection, selection, null, null)) {

					if (cursor != null && cursor.getCount() > 0) {
						logger.info("Contact is starred");
						return true;
					}
				} catch (Exception e) {
					logger.error("Contact lookup failed", e);
				}
			}
		}
		return false;
	}

	/**
	 * Check if the contact specified in messageReceiver is muted in the system.
	 *
	 * Notifications in Android are one big mess!
	 *
	 * "Starred" contacts may override the global DND setting in "priority" mode
	 * and should be signalled (needless to say this applies to contacts that are linked with a system contact only).
	 * Also, notification channels can be configured to override the system's DND setting. Accordingly we should signal them too.
	 * Additionally, notifications may be blocked on a notification channel group level. In that case, ignore any DND override settings
	 *
	 * I dare not imagine if this will work on Xiaomi devices with their hideous tinkering on top of the normal Android notification system...
	 *
	 * @param messageReceiver A MessageReceiver representing a ContactModel
	 * @param notification The notification
	 * @param notificationManager
	 * @param notificationManagerCompat
	 * @return true if no ringtone should ne played, false otherwise
	 */
	public boolean isSystemMuted(MessageReceiver messageReceiver, @Nullable Notification notification, NotificationManager notificationManager, NotificationManagerCompat notificationManagerCompat) {
		boolean isSystemMuted = !notificationManagerCompat.areNotificationsEnabled();
		boolean canBypassDND = false;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notification != null) {
			NotificationChannelCompat notificationChannelCompat = notificationManagerCompat.getNotificationChannelCompat(notification.getChannelId());
			if (notificationChannelCompat != null) {
				String groupName = notificationChannelCompat.getGroup();
				if (groupName != null) {
					NotificationChannelGroupCompat notificationChannelGroupCompat = notificationManagerCompat.getNotificationChannelGroupCompat(groupName);
					if (notificationChannelGroupCompat != null) {
						if (notificationChannelGroupCompat.isBlocked()) {
							return true;
						}
					}
				}
				canBypassDND = notificationChannelCompat.canBypassDnd();
			}
		}

		if (messageReceiver instanceof ContactMessageReceiver) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				/* we do not play a ringtone sound if system-wide DND is enabled - except for starred contacts */
				switch (notificationManager.getCurrentInterruptionFilter()) {
					case NotificationManager.INTERRUPTION_FILTER_NONE:
						isSystemMuted = true;
						break;
					case NotificationManager.INTERRUPTION_FILTER_PRIORITY:
						isSystemMuted = !isStarredContact(messageReceiver);
						break;
					default:
						break;
				}
			}
		}

		return isSystemMuted && !canBypassDND;
	}
}
