/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2023 Threema GmbH
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

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.slf4j.Logger;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

import androidx.annotation.NonNull;
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
			PushUtil.sendTokenToServer(this, token, ProtocolDefines.PUSHTOKEN_TYPE_GCM);
		} catch (ThreemaException e) {
			logger.error("onNewToken, could not send token to server ", e);
		}
	}

	public static void deleteToken(Context context) {
		try {
			FirebaseInstanceId.getInstance().deleteInstanceId();
			PushUtil.sendTokenToServer(context,"", ProtocolDefines.PUSHTOKEN_TYPE_NONE);
		} catch (IOException | ThreemaException e) {
			logger.warn("Could not delete FCM token", e);
		}
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
