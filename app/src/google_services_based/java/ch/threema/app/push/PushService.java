/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2024 Threema GmbH
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

package ch.threema.app.push;

import android.content.Context;
import android.text.format.DateUtils;

import androidx.annotation.NonNull;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.installations.FirebaseInstallations;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.slf4j.Logger;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import ch.threema.app.utils.PushUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.ProtocolDefines;

public class PushService extends FirebaseMessagingService {
	private static final Logger logger = LoggingUtil.getThreemaLogger("PushService");

	public static final String EXTRA_CLEAR_TOKEN = "clear";
	public static final String EXTRA_WITH_CALLBACK = "cb";

	@Override
	public void onNewToken(@NonNull String token) {
		logger.info("New FCM token received");
		try {
			PushUtil.sendTokenToServer(token, ProtocolDefines.PUSHTOKEN_TYPE_FCM);
		} catch (ThreemaException e) {
			logger.error("onNewToken, could not send token to server ", e);
		}
	}

	public static String deleteToken(Context context) {
		try {
			FirebaseMessaging.getInstance().deleteToken();
			Tasks.await(FirebaseInstallations.getInstance().delete());
			PushUtil.sendTokenToServer("", ProtocolDefines.PUSHTOKEN_TYPE_NONE);
		} catch (ThreemaException | ExecutionException | InterruptedException e) {
			logger.warn("Could not delete FCM token", e);
			return e.getMessage();
		}
		return null;
	}

	@Override
	public void onDeletedMessages() {
		logger.info("Too many messages stored on the Firebase server. Messages have been dropped.");
	}

	@Override
	public void onMessageSent(@NonNull String msgId) {
		logger.info("onMessageSent called for message id: {}", msgId);
	}

	@Override
	public void onSendError(@NonNull String msgId, @NonNull Exception exception) {
		logger.info("onSendError called for message id: {} exception: {}", msgId, exception);
	}

	@Override
	public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
		logger.info("Handling incoming FCM intent.");

		RuntimeUtil.runInWakelock(getApplicationContext(), DateUtils.MINUTE_IN_MILLIS * 10, "PushService", () -> processFcmMessage(remoteMessage));
	}

	private void processFcmMessage(RemoteMessage remoteMessage) {
		logger.info("Received FCM message: {}", remoteMessage.getMessageId());

		// Log message sent time
		try {
			Date sentDate = new Date(remoteMessage.getSentTime());

			logger.info("*** Message sent     : " + sentDate.toString(), true);
			logger.info("*** Message received : " + new Date().toString(), true);
			logger.info("*** Original priority: " + remoteMessage.getOriginalPriority());
			logger.info("*** Current priority: " + remoteMessage.getPriority());
		} catch (Exception ignore) {
		}

		Map<String, String> data = remoteMessage.getData();
		PushUtil.processRemoteMessage(data);
	}

	// following services check are handled here and not in ConfigUtils to minimize number of duplicating classes
	/**
	 * check for specific huawei services
	 */
	public static boolean hmsServicesInstalled(Context context) {
		return false;
	}

	/**
	 * check for specific google services
	 */
	public static boolean playServicesInstalled(Context context) {
		GoogleApiAvailability apiAvailability = com.google.android.gms.common.GoogleApiAvailability.getInstance();
		int resultCode = apiAvailability.isGooglePlayServicesAvailable(context);
		return RuntimeUtil.isInTest() || (resultCode == ConnectionResult.SUCCESS);
	}

	/**
	 * check for available push service
	 */
	public static boolean servicesInstalled(Context context) {
		return playServicesInstalled(context) || hmsServicesInstalled(context);
	}
}
