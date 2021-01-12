/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2021 Threema GmbH
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

package ch.threema.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.core.app.FixedJobIntentService;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.base.ThreemaException;
import ch.threema.client.ProtocolDefines;
import ch.threema.client.ThreemaConnection;

public class FcmRegistrationIntentService extends FixedJobIntentService {
	private static final Logger logger = LoggerFactory.getLogger(FcmRegistrationIntentService.class);

	public static final String EXTRA_CLEAR_TOKEN = "clear";
	public static final String EXTRA_WITH_CALLBACK = "cb";

	private static final int JOB_ID = 2002;

	public static void enqueueWork(Context context, Intent work) {
		enqueueWork(context, FcmRegistrationIntentService.class, JOB_ID, work);
	}

	@Override
	protected void onHandleWork(@NonNull Intent intent) {
		final boolean clearToken = intent.hasExtra(EXTRA_CLEAR_TOKEN);
		final boolean withCallback = intent.hasExtra(EXTRA_WITH_CALLBACK);
		final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

		if (clearToken) {
			String error = null;
			try {
				FirebaseInstanceId.getInstance().deleteInstanceId();
			} catch (IOException e) {
				error = "could not delete firebase instance id";
			}
			sendRegistrationToServer("", sharedPreferences);
			signalRegistrationFinished(error, withCallback, clearToken, sharedPreferences);
		} else {
			try {
				FirebaseInstanceId.getInstance().getInstanceId()
						.addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
							@Override
							public void onComplete(@NonNull Task<InstanceIdResult> task) {
								String error = null;
								if (task.isSuccessful() && task.getResult() != null && !TextUtils.isEmpty(task.getResult().getToken())) {
									String token = task.getResult().getToken();
									logger.debug(String.format("Got FCM Registration Token: %s", token));
									sendRegistrationToServer(token, sharedPreferences);
								} else {
									error = task.getException().getMessage();
								}
								signalRegistrationFinished(error, withCallback, clearToken, sharedPreferences);
							}
						})
						.addOnFailureListener(new OnFailureListener() {
							@Override
							public void onFailure(@NonNull Exception e) {
								signalRegistrationFinished(e.getMessage(), withCallback, clearToken, sharedPreferences);
							}
						});
			} catch (IllegalStateException e) {
				signalRegistrationFinished(e.getMessage(), withCallback, clearToken, sharedPreferences);
			}
		}
	}

	private void signalRegistrationFinished(String error, boolean withCallback, boolean clearToken, SharedPreferences sharedPreferences) {
		if (error != null) {
			logger.warn(String.format("Failed to get FCM token from Firebase: %s", error));
			sharedPreferences.edit().putLong(getString(R.string.preferences__gcm_token_sent_date), 0L).apply();
		}

		if (withCallback) {
			// Notify UI that registration has completed, so the progress indicator can be hidden.

			final Intent intent = new Intent(ThreemaApplication.INTENT_GCM_REGISTRATION_COMPLETE);
			intent.putExtra(EXTRA_CLEAR_TOKEN, clearToken);
			LocalBroadcastManager.getInstance(FcmRegistrationIntentService.this).sendBroadcast(intent);
		}
	}

	private boolean sendRegistrationToServer(String token, SharedPreferences sharedPreferences) {
		ServiceManager serviceManager = ThreemaApplication.getServiceManager();

		if (serviceManager != null) {
			ThreemaConnection connection = serviceManager.getConnection();

			if (connection != null) {
				try {
					connection.setPushToken(ProtocolDefines.PUSHTOKEN_TYPE_GCM, token);
					logger.info("FCM token successfully sent to server");

					// Save current token
					sharedPreferences.edit().putLong(getString(R.string.preferences__gcm_token_sent_date), System.currentTimeMillis()).apply();
					// Used in the Webclient Sessions
					serviceManager.getPreferenceService().setPushToken(token);
					return true;
				} catch (ThreemaException e) {
					logger.warn("Unable to send token to server: " + e.getMessage());
				}
			} else {
				logger.warn("FCM token send failed: no connection");
			}
		} else {
			logger.error("FCM token send failed: no servicemanager");
		}
		sharedPreferences.edit().putLong(getString(R.string.preferences__gcm_token_sent_date), 0L).apply();
		return false;
	}
}
