/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2021 Threema GmbH
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

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.format.DateUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.preference.PreferenceManager;
import ch.threema.app.FcmRegistrationIntentService;
import ch.threema.app.R;
import ch.threema.app.jobs.FcmRegistrationJobService;

public class PushUtil {
	private static final Logger logger = LoggerFactory.getLogger(PushUtil.class);

	/**
	 * Clears the "token last updated" setting in shared preferences
	 * @param context Context
	 */
	public static void clearPushTokenSentDate(Context context) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		if (sharedPreferences != null) {
			sharedPreferences
					.edit()
					.putLong(context.getString(R.string.preferences__gcm_token_sent_date), 0L)
					.apply();
		}
	}

	/**
	 * Directly send push token to server
	 * @param context Context
	 * @param clear Remove token from sever
	 * @param withCallback Send broadcast after token refresh has been completed or failed
	 */
	public static void sendPushTokenToServer(Context context, boolean clear, boolean withCallback) {
		logger.debug("Update FCM token now");
		// Start IntentService to register this application with FCM.
		Intent intent = new Intent(context, FcmRegistrationIntentService.class);
		if (clear) {
			intent.putExtra(FcmRegistrationIntentService.EXTRA_CLEAR_TOKEN, true);
		}
		if (withCallback) {
			intent.putExtra(FcmRegistrationIntentService.EXTRA_WITH_CALLBACK, true);
		}
		FcmRegistrationIntentService.enqueueWork(context, intent);
	}

	/**
	 * Schedule a push token refresh as soon as the device has become online
	 * Will perform an instant refresh on Jelly Bean and Kitkat
	 * @param context Context
	 */
	public static void scheduleSendPushTokenToServer(Context context) {
		if (!ConfigUtils.isPlayServicesInstalled(context)) {
			return;
		}

		logger.debug("Scheduling FCM token update");

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
			if (jobScheduler != null) {
				ComponentName serviceComponent = new ComponentName(context, FcmRegistrationJobService.class);
				JobInfo.Builder builder = new JobInfo.Builder(9991, serviceComponent);
				builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
				jobScheduler.schedule(builder.build());
				return;
			}
		}

		sendPushTokenToServer(context, false, false);
	}

	/**
	 * Check if the token needs to be uploaded to the server i.e. no more than once a day.
	 * @param context Context
	 * @return true if more than a day has passed since the token has been last sent to the server, false otherwise
	 */
	public static boolean pushTokenNeedsRefresh(Context context) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

		if (sharedPreferences != null) {
			long lastDate = sharedPreferences.getLong(context.getString(R.string.preferences__gcm_token_sent_date), 0L);
			// refresh push token at least once a day
			return (System.currentTimeMillis() - lastDate) > DateUtils.DAY_IN_MILLIS;
		}
		return true;
	}

	/**
	 * Check if push services are enabled and polling is not used.
	 * @param context Context
	 * @return true if polling is disabled or shared preferences are not available, false otherwise
	 */
	public static boolean isPushEnabled(Context context) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

		if (sharedPreferences != null) {
			return !sharedPreferences.getBoolean(context.getString(R.string.preferences__polling_switch), false);
		}
		return true;
	}
}
